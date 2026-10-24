package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplatePackLoaderTest {

    @TempDir
    Path temporaryDirectory;

    private final TemplatePackLoader loader = new TemplatePackLoader();

    @Test
    void loadsExecutablePackAndReadsItsVariablesAndTemplates() throws Exception {
        Path root = root();
        writeDemo(root);

        TemplatePack pack = loader.load(resources(root), "demo");

        assertEquals(2, pack.manifest().manifestVersion());
        assertEquals("demo", pack.manifest().id());
        assertEquals("1.0.0", pack.manifest().packVersion());
        assertEquals(2, pack.manifest().variables().size());
        assertEquals("author", pack.manifest().variables().get(0).name());
        assertEquals(List.of("fast", "safe"), pack.manifest().variables().get(1).enumValues());
        assertEquals(1, pack.manifest().templates().size());
        assertEquals("templates/hello.ftl", pack.manifest().templates().get(0).source());
        assertEquals("java", pack.manifest().templates().get(0).kind());
        assertTrue(pack.resources().exists("demo/templates/hello.ftl"));
    }

    @Test
    void listsGroupsFromIndexAndIgnoresComments() throws Exception {
        Path root = root();
        writeDemo(root);
        writeMetadata(root);

        assertEquals(List.of("demo", "meta"), loader.listGroups(resources(root)));
    }

    @Test
    void rejectsDuplicateGroupsInIndex() throws Exception {
        Path root = root();
        Files.writeString(root.resolve("index.txt"), "demo\ndemo\n");
        writeDemo(root);

        assertThrows(TemplatePackException.class, () -> loader.listGroups(resources(root)));
    }

    @Test
    void loadsBuiltInCrudFromV2Manifest() {
        TemplatePack pack = loader.loadBuiltin("crud");

        assertEquals("builtin-1", pack.manifest().packVersion());
        assertEquals(10, pack.manifest().templates().size());
        assertTrue(pack.manifest().templates().stream()
                .allMatch(template -> template.source().startsWith("templates/")));
    }

    @Test
    void rejectsV1Manifest() throws Exception {
        Path root = root();
        Files.writeString(root.resolve("index.txt"), "demo\n");
        Files.createDirectories(root.resolve("demo/templates"));
        Files.writeString(root.resolve("demo/manifest.properties"), """
                manifestVersion=1
                id=demo
                displayName=Demo
                description=Demo pack.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=1.0.0
                template.count=1
                template.0.source=templates/hello.ftl
                template.0.output=${crud.basePackagePath}/Hello.java
                """);
        Files.writeString(root.resolve("demo/templates/hello.ftl"), "hello");

        assertThrows(TemplatePackException.class, () -> loader.load(resources(root), "demo"));
    }

    @Test
    void rejectsUnknownManifestKeys() throws Exception {
        Path root = root();
        writeDemo(root);
        Path manifest = root.resolve("demo/manifest.properties");
        Files.writeString(manifest, Files.readString(manifest) + "surprise=value\n");

        assertThrows(TemplatePackException.class, () -> loader.readManifest(resources(root), "demo"));
    }

    @Test
    void rejectsMissingTemplateSource() throws Exception {
        Path root = root();
        writeDemo(root);
        Files.delete(root.resolve("demo/templates/hello.ftl"));

        TemplatePackException exception = assertThrows(
                TemplatePackException.class, () -> loader.load(resources(root), "demo"));
        assertTrue(exception.getMessage().contains("missing template source"));
    }

    @Test
    void refusesToExecuteMetadataOnlyPacks() throws Exception {
        Path root = root();
        writeMetadata(root);

        TemplatePackException exception = assertThrows(
                TemplatePackException.class, () -> loader.load(resources(root), "meta"));
        assertTrue(exception.getMessage().contains("not executable"));
    }

    @Test
    void rejectsUnsafeTemplateSources() throws Exception {
        Path root = root();
        Files.writeString(root.resolve("index.txt"), "demo\n");
        Files.createDirectories(root.resolve("demo"));
        Files.writeString(root.resolve("demo/manifest.properties"), """
                manifestVersion=2
                id=demo
                displayName=Demo
                description=Demo pack.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=1.0.0
                template.count=1
                template.0.source=../escape.ftl
                template.0.output=${crud.basePackagePath}/Hello.java
                """);

        assertThrows(TemplatePackException.class, () -> loader.readManifest(resources(root), "demo"));
    }

    @Test
    void rejectsDefaultsForComplexVariableTypes() throws Exception {
        Path root = root();
        Files.writeString(root.resolve("index.txt"), "demo\n");
        Files.createDirectories(root.resolve("demo"));
        Files.writeString(root.resolve("demo/manifest.properties"), """
                manifestVersion=2
                id=demo
                displayName=Demo
                description=Demo pack.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=1.0.0
                variable.count=1
                variable.0.name=names
                variable.0.type=list<string>
                variable.0.default=a
                template.count=1
                template.0.source=templates/hello.ftl
                template.0.output=${crud.basePackagePath}/Hello.java
                """);

        assertThrows(TemplatePackException.class, () -> loader.readManifest(resources(root), "demo"));
    }

    @Test
    void wrapsMissingManifestAsIoFailure() throws Exception {
        Path root = root();

        TemplatePackException exception = assertThrows(
                TemplatePackException.class, () -> loader.readManifest(resources(root), "missing"));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void metadataOnlyManifestNeedsNoTemplatesDirectory() throws Exception {
        Path root = root();
        writeMetadata(root);

        TemplatePackManifest manifest = loader.readManifest(resources(root), "meta");
        assertEquals("metadata-only", manifest.status());
        assertTrue(manifest.templates().isEmpty());
        assertTrue(manifest.variables().isEmpty());
    }

    private Path root() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("templates-root"));
        Files.writeString(root.resolve("index.txt"), "demo\nmeta\n");
        return root;
    }

    private static FileSystemTemplateResourceRoot resources(Path root) throws IOException {
        return new FileSystemTemplateResourceRoot(root);
    }

    private static void writeDemo(Path root) throws IOException {
        Files.createDirectories(root.resolve("demo/templates"));
        Files.writeString(root.resolve("demo/manifest.properties"), """
                manifestVersion=2
                id=demo
                displayName=Demo
                description=Demo pack.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=1.0.0
                variable.count=2
                variable.0.name=author
                variable.0.type=string
                variable.0.required=true
                variable.1.name=mode
                variable.1.type=enum
                variable.1.values=fast,safe
                template.count=1
                template.0.source=templates/hello.ftl
                template.0.output=${crud.basePackagePath}/Hello.java
                template.0.kind=java
                """);
        Files.writeString(root.resolve("demo/templates/hello.ftl"), "hello");
    }

    private static void writeMetadata(Path root) throws IOException {
        Files.createDirectories(root.resolve("meta"));
        Files.writeString(root.resolve("meta/manifest.properties"), """
                manifestVersion=2
                id=meta
                displayName=Metadata
                description=Metadata-only route.
                status=metadata-only
                outputKind=readable-source
                ownership=business-project
                packVersion=builtin-1
                variable.count=0
                template.count=0
                """);
    }
}
