package io.github.coco.generate.engine;

import io.github.coco.generate.config.CrudYamlParser;
import io.github.coco.generate.config.CrudYamlSpec;
import io.github.coco.generate.core.GenerationException;
import io.github.coco.generate.core.SafePaths;
import io.github.coco.generate.crud.CocoCrudIdStrategy;
import io.github.coco.generate.crud.CocoCrudSpec;
import io.github.coco.generate.template.RenderedTemplateFile;
import io.github.coco.generate.template.TemplateLocation;
import io.github.coco.generate.template.TemplatePack;
import io.github.coco.generate.template.TemplatePackLoader;
import io.github.coco.generate.template.TemplateRenderer;
import io.github.coco.generate.template.VariableResolver;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds deterministic generation plans from the configured template pack. */
public final class GenerationEngine {
    public static final String CONFIG_FILE = "coco-generate.yml";
    public static final String LEGACY_CONFIG_FILE = "coco-codegen.yml";
    public static final String OUTPUT_ROOT = "src/main/java";

    private final CrudYamlParser yamlParser = new CrudYamlParser();
    private final TemplatePackLoader packLoader = new TemplatePackLoader();
    private final TemplateRenderer renderer = new TemplateRenderer();
    private final VariableResolver variableResolver = new VariableResolver();

    /** Builds a plan without changing the requested project directory. */
    public GenerationPlan plan(Path projectDirectory) throws IOException {
        return plan(projectDirectory, GenerationOptions.none());
    }

    /** Builds a plan using optional CLI overrides. */
    public GenerationPlan plan(Path projectDirectory, GenerationOptions options) throws IOException {
        if (options == null) {
            throw new IllegalArgumentException("generation options must not be null");
        }
        Path project = requireProject(projectDirectory);
        Path configuration = configuration(project, options);
        CrudYamlSpec yaml = yamlParser.parse(configuration, StandardCharsets.UTF_8);
        Charset encoding = Charset.forName(yaml.template().encoding());
        String templateRoot = options.templateRoot() == null
                ? yaml.template().location()
                : options.templateRoot();
        String defaultGroup = options.templateGroup() == null
                ? yaml.template().group()
                : options.templateGroup();
        TemplateLocation location = TemplateLocation.parse(templateRoot, project);
        Map<String, TemplatePack> packs = new LinkedHashMap<>();
        TemplatePack defaultPack = pack(packs, location, defaultGroup);

        Path output = project.resolve(OUTPUT_ROOT).normalize();
        SafeFileSystem.verifyProjectTo(project, output);
        Set<String> paths = new LinkedHashSet<>();
        List<GenerationPlan.PlannedFile> files = new ArrayList<>();
        for (CrudYamlSpec.Resource resource : yaml.resources()) {
            String group = resource.template() == null ? defaultGroup : resource.template();
            TemplatePack pack = pack(packs, location, group);
            CocoCrudSpec spec = spec(yaml.basePackage(), resource);
            Map<String, Object> vars = variableResolver.resolve(
                    pack.manifest().variables(),
                    yaml.vars(),
                    resource.vars(),
                    options.variables());
            Map<String, Object> model = model(location, output, pack, spec, resource, vars);
            for (RenderedTemplateFile rendered : renderer.render(pack, model, encoding)) {
                String relative = SafePaths.normalize(rendered.relativePath());
                if (!paths.add(relative)) {
                    throw new GenerationException("duplicate generated output: " + relative);
                }
                files.add(new GenerationPlan.PlannedFile(
                        relative,
                        sha256(rendered.content()),
                        "CREATE_NEW",
                        rendered.content(),
                        rendered.templateGroup(),
                        rendered.kind()));
            }
        }
        return new GenerationPlan(
                project,
                output,
                location.description(),
                defaultGroup,
                defaultPack.manifest().packVersion(),
                files);
    }

    /** Resolves the project's configured template location without loading a pack. */
    public TemplateLocation templateLocation(Path projectDirectory) throws IOException {
        Path project = requireProject(projectDirectory);
        Path configuration = configuration(project, GenerationOptions.none());
        CrudYamlSpec yaml = yamlParser.parse(configuration, StandardCharsets.UTF_8);
        return TemplateLocation.parse(yaml.template().location(), project);
    }

    private TemplatePack pack(
            Map<String, TemplatePack> packs,
            TemplateLocation location,
            String group) {
        TemplatePack existing = packs.get(group);
        if (existing != null) {
            return existing;
        }
        TemplatePack loaded = packLoader.load(location.resources(), group);
        packs.put(group, loaded);
        return loaded;
    }

    private static CocoCrudSpec spec(String basePackage, CrudYamlSpec.Resource resource) {
        CocoCrudSpec.Builder builder = CocoCrudSpec.builder(
                basePackage, resource.name(), resource.table());
        if (resource.apiPath() != null) {
            builder.apiPath(resource.apiPath());
        }
        CrudYamlSpec.Id id = resource.id();
        builder.id(id.name(), id.column(), id.type(), CocoCrudIdStrategy.valueOf(id.strategy()));
        for (CrudYamlSpec.Field field : resource.fields()) {
            builder.field(field.name(), field.column(), field.type(), field.required());
        }
        return builder.build();
    }

    private static Map<String, Object> model(
            TemplateLocation location,
            Path output,
            TemplatePack pack,
            CocoCrudSpec spec,
            CrudYamlSpec.Resource resource,
            Map<String, Object> vars) {
        Map<String, Object> coco = new LinkedHashMap<>();
        coco.put("templateGroup", pack.group());
        coco.put("templateLocation", location.description());
        coco.put("packVersion", pack.manifest().packVersion());
        coco.put("outputRoot", output.toString());

        Map<String, Object> resourceModel = new LinkedHashMap<>();
        resourceModel.put("name", resource.name());
        resourceModel.put("table", resource.table());
        resourceModel.put("apiPath", resource.apiPath() == null ? "" : resource.apiPath());
        resourceModel.put("template", resource.template() == null ? "" : resource.template());
        resourceModel.put("vars", resource.vars());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("_coco", Collections.unmodifiableMap(coco));
        model.put("vars", vars);
        model.put("crud", spec.templateModel());
        model.put("resource", Collections.unmodifiableMap(resourceModel));
        return Collections.unmodifiableMap(model);
    }

    private static Path requireProject(Path value) throws IOException {
        Path project = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("project directory is not a regular directory: " + project);
        }
        SafeFileSystem.verifyProjectDirectory(project);
        return project;
    }

    private static Path configuration(Path project, GenerationOptions options) throws IOException {
        if (options.configFile() != null) {
            Path configured = options.configFile();
            Path resolved = configured.isAbsolute()
                    ? configured.normalize()
                    : project.resolve(configured).normalize();
            if (Files.isSymbolicLink(resolved)) {
                throw new IOException("Coco Generate configuration must not be a symbolic link");
            }
            if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("missing Coco Generate configuration: " + resolved);
            }
            return resolved;
        }
        return configuration(project);
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
