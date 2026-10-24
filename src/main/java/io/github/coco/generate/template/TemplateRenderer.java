package io.github.coco.generate.template;

import freemarker.cache.TemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.coco.generate.core.GenerationException;
import io.github.coco.generate.core.SafePaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Renders every template declared by a template pack. */
public final class TemplateRenderer {

    public List<RenderedTemplateFile> render(
            TemplatePack pack,
            Map<String, Object> model,
            Charset encoding) {
        Objects.requireNonNull(pack, "pack must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(encoding, "encoding must not be null");
        Configuration configuration = configuration(pack, encoding);
        List<RenderedTemplateFile> files = new ArrayList<>(pack.manifest().templates().size());
        int index = 0;
        for (TemplateDefinition definition : pack.manifest().templates()) {
            String output = processInline(
                    configuration,
                    definition.output(),
                    model,
                    "output:" + pack.group() + ":" + index);
            if (output.trim().isEmpty()) {
                throw new TemplatePackException(
                        "template output path rendered to blank: " + pack.group() + "/" + definition.source());
            }
            String relativePath;
            try {
                relativePath = SafePaths.normalize(output.trim());
            }
            catch (GenerationException ex) {
                throw new TemplatePackException(
                        "unsafe template output path for " + pack.group() + "/"
                                + definition.source() + ": " + output.trim(), ex);
            }
            String content = processTemplate(
                    configuration, definition.source(), model, pack.group(), encoding);
            files.add(new RenderedTemplateFile(
                    relativePath, content, pack.group(), definition.kind()));
            index++;
        }
        return List.copyOf(files);
    }

    private static Configuration configuration(TemplatePack pack, Charset encoding) {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setTemplateLoader(new PackTemplateLoader(pack));
        configuration.setDefaultEncoding(encoding.name());
        configuration.setLocalizedLookup(false);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        configuration.setAPIBuiltinEnabled(false);
        return configuration;
    }

    private static String processTemplate(
            Configuration configuration,
            String source,
            Map<String, Object> model,
            String group,
            Charset encoding) {
        try {
            Template template = configuration.getTemplate(source, encoding.name());
            return process(template, model);
        }
        catch (IOException | TemplateException ex) {
            throw new TemplatePackException(
                    "failed to render template " + group + "/" + source + ": " + ex.getMessage(), ex);
        }
    }

    private static String processInline(
            Configuration configuration,
            String value,
            Map<String, Object> model,
            String name) {
        try {
            Template template = new Template(name, new StringReader(value), configuration);
            return process(template, model);
        }
        catch (IOException | TemplateException ex) {
            throw new TemplatePackException(
                    "failed to render template output path '" + name + "': " + ex.getMessage(), ex);
        }
    }

    private static String process(Template template, Map<String, Object> model)
            throws TemplateException, IOException {
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    private static final class PackTemplateLoader implements TemplateLoader {

        private final TemplatePack pack;

        private PackTemplateLoader(TemplatePack pack) {
            this.pack = pack;
        }

        @Override
        public Object findTemplateSource(String name) {
            String path = pack.group() + "/" + name;
            return pack.resources().exists(path) ? name : null;
        }

        @Override
        public long getLastModified(Object templateSource) {
            return 0L;
        }

        @Override
        public Reader getReader(Object templateSource, String encoding) throws IOException {
            String path = pack.group() + "/" + templateSource;
            byte[] bytes = pack.resources().read(path);
            return new InputStreamReader(new ByteArrayInputStream(bytes), encoding);
        }

        @Override
        public void closeTemplateSource(Object templateSource) {
        }
    }
}
