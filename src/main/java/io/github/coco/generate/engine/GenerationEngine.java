package io.github.coco.generate.engine;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.coco.generate.config.CrudYamlParser;
import io.github.coco.generate.config.CrudYamlSpec;
import io.github.coco.generate.crud.CocoCrudIdStrategy;
import io.github.coco.generate.crud.CocoCrudSpec;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Builds deterministic CRUD generation plans using only bundled templates. */
public final class GenerationEngine {
    public static final String CONFIG_FILE = "coco-generate.yml";
    public static final String LEGACY_CONFIG_FILE = "coco-codegen.yml";
    private static final String TEMPLATE_ROOT = "/META-INF/coco-generate/templates/crud/templates";
    private final CrudYamlParser yamlParser = new CrudYamlParser();
    private final Configuration templates = templates();

    /** Builds a plan without changing the requested project directory. */
    public GenerationPlan plan(Path projectDirectory) throws IOException {
        Path project = requireProject(projectDirectory);
        Path configuration = configuration(project);
        CrudYamlSpec yaml = yamlParser.parse(configuration, StandardCharsets.UTF_8);
        List<GeneratedFile> rendered = new ArrayList<>();
        for (CrudYamlSpec.Resource resource : yaml.resources()) {
            CocoCrudSpec.Builder builder = CocoCrudSpec.builder(yaml.basePackage(), resource.name(), resource.table());
            if (resource.apiPath() != null) {
                builder.apiPath(resource.apiPath());
            }
            CrudYamlSpec.Id id = resource.id();
            builder.id(id.name(), id.column(), id.type(), CocoCrudIdStrategy.valueOf(id.strategy()));
            for (CrudYamlSpec.Field field : resource.fields()) {
                builder.field(field.name(), field.column(), field.type(), field.required());
            }
            rendered.addAll(render(builder.build().toRequest()));
        }
        Path output = project.resolve("src/main/java").normalize();
        SafeFileSystem.verifyProjectTo(project, output);
        Set<String> paths = new LinkedHashSet<>();
        List<GenerationPlan.PlannedFile> files = new ArrayList<>();
        for (GeneratedFile file : rendered) {
            String relative = SafePaths.normalize(file.relativePath());
            if (!paths.add(relative)) {
                throw new GenerationException("duplicate generated output: " + relative);
            }
            files.add(new GenerationPlan.PlannedFile(relative, sha256(file.content()), "CREATE_NEW", file.content()));
        }
        return new GenerationPlan(project, output, files);
    }

    private List<GeneratedFile> render(GenerationRequest request) {
        if (!"crud".equals(request.templateGroup())) {
            throw new GenerationException("unsupported executable route: " + request.templateGroup());
        }
        Properties manifest = new Properties();
        try (var input = GenerationEngine.class.getResourceAsStream(
                "/META-INF/coco-generate/templates/crud/generation-manifest.properties")) {
            if (input == null) throw new GenerationException("missing built-in CRUD manifest");
            manifest.load(input);
        } catch (IOException ex) {
            throw new GenerationException("unable to read built-in CRUD manifest", ex);
        }
        int count = Integer.parseInt(manifest.getProperty("template.count"));
        List<GeneratedFile> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String source = manifest.getProperty("template." + i + ".source");
            String output = manifest.getProperty("template." + i + ".output");
            if (source == null || output == null) throw new GenerationException("invalid built-in CRUD manifest");
            files.add(new GeneratedFile(processInline(output, request.attributes()), process(source, request.attributes())));
        }
        return List.copyOf(files);
    }

    private String process(String source, Map<String, Object> model) {
        try {
            return process(templates.getTemplate(source, StandardCharsets.UTF_8.name()), model);
        } catch (IOException ex) {
            throw new GenerationException("missing bundled template: " + source, ex);
        }
    }

    private String processInline(String value, Map<String, Object> model) {
        try {
            return process(new Template("generated-output", new StringReader(value), templates), model);
        } catch (IOException ex) {
            throw new GenerationException("invalid built-in output template", ex);
        }
    }

    private static String process(Template template, Map<String, Object> model) {
        try {
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (TemplateException | IOException ex) {
            throw new GenerationException("failed to render bundled CRUD template", ex);
        }
    }

    private static Configuration templates() {
        Configuration config = new Configuration(Configuration.VERSION_2_3_32);
        config.setTemplateLoader(new ClassTemplateLoader(GenerationEngine.class, TEMPLATE_ROOT));
        config.setDefaultEncoding(StandardCharsets.UTF_8.name());
        config.setLocalizedLookup(false);
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        config.setLogTemplateExceptions(false);
        config.setWrapUncheckedExceptions(true);
        config.setFallbackOnNullLoopVariable(false);
        return config;
    }

    private static Path requireProject(Path value) throws IOException {
        Path project = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("project directory is not a regular directory: " + project);
        }
        SafeFileSystem.verifyProjectDirectory(project);
        return project;
    }

    private static Path configuration(Path project) throws IOException {
        Path current = project.resolve(CONFIG_FILE);
        Path legacy = project.resolve(LEGACY_CONFIG_FILE);
        if ((Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                || (Files.exists(legacy, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(legacy))) {
            throw new IOException("Coco Generate configuration must not be a symbolic link");
        }
        boolean hasCurrent = Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS);
        boolean hasLegacy = Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS);
        if (hasCurrent && hasLegacy) throw new IOException("both " + CONFIG_FILE + " and " + LEGACY_CONFIG_FILE + " exist");
        if (!hasCurrent && !hasLegacy) throw new IOException("missing " + CONFIG_FILE + " (or compatible " + LEGACY_CONFIG_FILE + ")");
        return hasCurrent ? current : legacy;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
