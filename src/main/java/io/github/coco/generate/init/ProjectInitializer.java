package io.github.coco.generate.init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Safely initializes the project-local Coco Generate configuration. */
public final class ProjectInitializer {

    public static final String CONFIG_FILE_NAME = "coco-generate.yml";

    private static final String INITIAL_CONFIGURATION = """
            # Coco Generate CRUD specification. Output is src/main/java.
            base-package: com.example.application
            resources:
              - name: Example
                table: example
                id:
                  name: id
                  column: id
                  type: Long
                  strategy: AUTO
                fields:
                  - name: name
                    column: name
                    type: String
                    required: true
            """;

    /**
     * Creates a new configuration under the requested directory.
     *
     * @param directory project directory, created when absent
     * @return normalized absolute path to the new configuration
     * @throws IOException when the directory is unsafe or the file cannot be written
     * @throws ConfigurationExistsException when a configuration already exists
     */
    public Path initialize(Path directory) throws IOException {
        Path root = Objects.requireNonNull(directory, "directory must not be null")
                .toAbsolutePath()
                .normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Initialization target is not a regular directory: " + root);
            }
        }
        else {
            Files.createDirectories(root);
        }

        Path configuration = root.resolve(CONFIG_FILE_NAME);
        try {
            Files.writeString(
                    configuration,
                    INITIAL_CONFIGURATION,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
        catch (FileAlreadyExistsException ex) {
            throw new ConfigurationExistsException(configuration, ex);
        }
        return configuration;
    }

    /** Raised when safe initialization refuses to replace an existing file. */
    public static final class ConfigurationExistsException extends IOException {

        private final Path path;

        public ConfigurationExistsException(Path path, Throwable cause) {
            super("Refusing to overwrite existing configuration: " + path, cause);
            this.path = path;
        }

        public Path path() {
            return path;
        }
    }
}
