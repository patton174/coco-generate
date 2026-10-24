package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersOutputPathAndTemplateWithTheFullModel() {
        TemplatePack pack = pack(
                "${crud.basePackagePath}/${crud.resourceName}.java",
                """
                package ${crud.basePackage};
                // ${vars.author} ${_coco.templateGroup} ${_coco.packVersion} ${resource.name}
                public class ${crud.resourceName} {}
                """);

        List<RenderedTemplateFile> files = renderer.render(pack, model(), StandardCharsets.UTF_8);

        assertEquals(1, files.size());
        RenderedTemplateFile file = files.get(0);
        assertEquals("com/example/Product.java", file.relativePath());
        assertEquals("demo", file.templateGroup());
        assertEquals("java", file.kind());
        assertTrue(file.content().contains("// platform-team demo 1.0.0 Product"));
        assertTrue(file.content().contains("public class Product {}"));
    }

    @Test
    void rejectsUnsafeRenderedOutputPaths() {
        TemplatePack pack = pack("${vars.path}.java", "package demo;");
        Map<String, Object> model = Map.of(
                "vars", Map.of("path", "../escape"),
                "crud", Map.of("basePackagePath", "com/example", "resourceName", "Product"),
                "_coco", Map.of("templateGroup", "demo"),
                "resource", Map.of("name", "Product"));

        TemplatePackException exception = assertThrows(
                TemplatePackException.class,
                () -> renderer.render(pack, model, StandardCharsets.UTF_8));
        assertTrue(exception.getMessage().contains("unsafe template output path"));
    }

    @Test
    void disablesNewBuiltin() {
        TemplatePack pack = pack(
                "com/example/Product.java",
                "${" + '"' + "java.lang.Runtime" + '"' + "?new()}");

        assertThrows(
                TemplatePackException.class,
                () -> renderer.render(pack, model(), StandardCharsets.UTF_8));
    }

    @Test
    void disablesApiBuiltin() {
        TemplatePack pack = pack(
                "com/example/Product.java",
                "${" + '"' + "value" + '"' + "?api}");

        assertThrows(
                TemplatePackException.class,
                () -> renderer.render(pack, model(), StandardCharsets.UTF_8));
    }

    private static Map<String, Object> model() {
        return Map.of(
                "vars", Map.of("author", "platform-team"),
                "crud", Map.of("basePackage", "com.example", "basePackagePath", "com/example",
                        "resourceName", "Product"),
                "_coco", Map.of(
                        "templateGroup", "demo", "packVersion", "1.0.0",
                        "templateLocation", "memory", "outputRoot", "/tmp/output"),
                "resource", Map.of("name", "Product"));
    }

    private static TemplatePack pack(String output, String template) {
        TemplateDefinition definition = new TemplateDefinition(
                "templates/sample.ftl", output, "java");
        TemplatePackManifest manifest = new TemplatePackManifest(
                2,
                "demo",
                "Demo",
                "Demo pack.",
                TemplatePackManifest.EXECUTABLE,
                "readable-source",
                "business-project",
                "1.0.0",
                List.of(),
                List.of(definition));
        TemplateResourceRoot resources = new TemplateResourceRoot() {
            @Override
            public byte[] read(String relativePath) throws IOException {
                if (!"demo/templates/sample.ftl".equals(relativePath)) {
                    throw new IOException("missing template: " + relativePath);
                }
                return template.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public boolean exists(String relativePath) {
                return "demo/templates/sample.ftl".equals(relativePath);
            }

            @Override
            public String describe() {
                return "memory";
            }
        };
        return new TemplatePack(manifest, resources, "memory");
    }
}
