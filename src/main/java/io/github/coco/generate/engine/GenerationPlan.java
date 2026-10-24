package io.github.coco.generate.engine;

import java.nio.file.Path;
import java.util.List;

/** Immutable, deterministic generation plan that can be previewed or applied. */
public record GenerationPlan(
        Path projectDirectory,
        Path outputDirectory,
        String templateRoot,
        String defaultGroup,
        String packVersion,
        List<PlannedFile> files) {

    public GenerationPlan {
        projectDirectory = projectDirectory.toAbsolutePath().normalize();
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        templateRoot = blankToNull(templateRoot);
        defaultGroup = blankToNull(defaultGroup);
        packVersion = blankToNull(packVersion);
        files = List.copyOf(files);
    }

    /** Compatibility constructor for plans that do not carry template metadata. */
    public GenerationPlan(Path projectDirectory, Path outputDirectory, List<PlannedFile> files) {
        this(projectDirectory, outputDirectory, null, null, null, files);
    }

    /** A target path, content digest and intended write action. */
    public record PlannedFile(
            String relativePath,
            String sha256,
            String action,
            String content,
            String templateGroup,
            String kind) {

        public PlannedFile {
            relativePath = require(relativePath, "relativePath");
            sha256 = require(sha256, "sha256");
            action = require(action, "action");
            content = require(content, "content");
            templateGroup = blankToNull(templateGroup);
            kind = blankToNull(kind);
        }

        /** Compatibility constructor for files that do not carry template metadata. */
        public PlannedFile(String relativePath, String sha256, String action, String content) {
            this(relativePath, sha256, action, content, null, null);
        }

        private static String require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
