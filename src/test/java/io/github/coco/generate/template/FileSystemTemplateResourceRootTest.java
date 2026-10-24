package io.github.coco.generate.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.core.GenerationException;

class FileSystemTemplateResourceRootTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsRegularFilesBelowTheRoot() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("templates"));
        Files.createDirectories(root.resolve("demo/templates"));
        Files.writeString(root.resolve("demo/templates/hello.ftl"), "hello");
        FileSystemTemplateResourceRoot resources = new FileSystemTemplateResourceRoot(root);

        assertTrue(resources.exists("demo/templates/hello.ftl"));
        assertFalse(resources.exists("demo/templates/missing.ftl"));
        assertEquals("hello", new String(
                resources.read("demo/templates/hello.ftl"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("templates"));
        FileSystemTemplateResourceRoot resources = new FileSystemTemplateResourceRoot(root);

        assertThrows(GenerationException.class, () -> resources.read("../escape.ftl"));
        assertThrows(GenerationException.class, () -> resources.read("/absolute.ftl"));
        assertFalse(resources.exists("../escape.ftl"));
    }

    @Test
    void rejectsSymbolicLinksInsideTheRoot() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("templates"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.writeString(outside.resolve("secret.ftl"), "secret");
        Path link = root.resolve("link.ftl");
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.ftl"));
        }
        catch (UnsupportedOperationException | IOException ex) {
            Assumptions.abort("symbolic links are unavailable on this test host: " + ex.getMessage());
        }
        FileSystemTemplateResourceRoot resources = new FileSystemTemplateResourceRoot(root);

        assertFalse(resources.exists("link.ftl"));
        assertThrows(IOException.class, () -> resources.read("link.ftl"));
    }

    @Test
    void rejectsSymbolicLinkRoots() throws Exception {
        Path real = Files.createDirectory(temporaryDirectory.resolve("real-root"));
        Path link = temporaryDirectory.resolve("linked-root");
        try {
            Files.createSymbolicLink(link, real);
        }
        catch (UnsupportedOperationException | IOException ex) {
            Assumptions.abort("symbolic links are unavailable on this test host: " + ex.getMessage());
        }

        assertThrows(IOException.class, () -> new FileSystemTemplateResourceRoot(link));
    }
}
