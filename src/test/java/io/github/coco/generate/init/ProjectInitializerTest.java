package io.github.coco.generate.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.init.ProjectInitializer.ConfigurationExistsException;

class ProjectInitializerTest {

    @TempDir
    Path temporaryDirectory;

    private final ProjectInitializer initializer = new ProjectInitializer();

    @Test
    void createsConfigurationAtTheNormalizedTargetPath() throws IOException {
        Path requested = temporaryDirectory.resolve("unused").resolve("..").resolve("application");

        Path configuration = initializer.initialize(requested);

        assertEquals(
                temporaryDirectory.toAbsolutePath().resolve("application")
                        .resolve(ProjectInitializer.CONFIG_FILE_NAME),
                configuration);
        assertTrue(Files.isRegularFile(configuration));
        String content = Files.readString(configuration, StandardCharsets.UTF_8);
        assertTrue(content.contains("base-package:"));
        assertTrue(content.contains("resources:"));
        String lowerCase = content.toLowerCase(Locale.ROOT);
        assertFalse(lowerCase.contains("password"));
        assertFalse(lowerCase.contains("privatekey"));
        assertFalse(lowerCase.contains("token:"));
    }

    @Test
    void refusesToOverwriteBusinessOwnedConfiguration() throws IOException {
        Path configuration = initializer.initialize(temporaryDirectory);
        String businessContent = "business-owned: true\n";
        Files.writeString(configuration, businessContent, StandardCharsets.UTF_8);

        ConfigurationExistsException exception = assertThrows(
                ConfigurationExistsException.class,
                () -> initializer.initialize(temporaryDirectory));

        assertEquals(configuration, exception.path());
        assertEquals(businessContent, Files.readString(configuration, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAFileAsTheInitializationDirectory() throws IOException {
        Path file = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> initializer.initialize(file));

        assertTrue(exception.getMessage().contains("not a regular directory"));
    }
}
