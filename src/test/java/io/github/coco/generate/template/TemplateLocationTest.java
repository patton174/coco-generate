package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateLocationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesBuiltinClasspathAndFileRoots() throws Exception {
        TemplateLocation builtin = TemplateLocation.parse("builtin", temporaryDirectory);
        assertEquals("builtin", builtin.description());
        assertTrue(builtin.resources().exists("crud/manifest.properties"));

        TemplateLocation classpath = TemplateLocation.parse(
                "classpath:/META-INF/coco-generate/templates", temporaryDirectory);
        assertEquals("classpath:META-INF/coco-generate/templates", classpath.description());
        assertTrue(classpath.resources().exists("crud/manifest.properties"));

        Path root = Files.createDirectory(temporaryDirectory.resolve("templates"));
        TemplateLocation file = TemplateLocation.parse("./templates", temporaryDirectory);
        assertEquals("file:" + root.toAbsolutePath().normalize(), file.description());
        assertEquals(file.description(), file.resources().describe());
    }

    @Test
    void rejectsRemoteAndArchiveSchemes() {
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("https://example.com/templates", temporaryDirectory));
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("http://example.com/templates", temporaryDirectory));
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("jar:file:/tmp/templates.jar!/templates", temporaryDirectory));
    }

    @Test
    void rejectsUnsafeClasspathAndEmptyFileLocations() {
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("classpath:../templates", temporaryDirectory));
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("classpath:", temporaryDirectory));
        assertThrows(
                TemplatePackException.class,
                () -> TemplateLocation.parse("file:", temporaryDirectory));
    }
}
