package io.github.coco.generate.engine;

import java.nio.file.Path;
import java.util.List;

/** Immutable, deterministic generation plan that can be previewed or applied. */
public record GenerationPlan(Path projectDirectory, Path outputDirectory, List<PlannedFile> files) {
    public GenerationPlan {
        projectDirectory = projectDirectory.toAbsolutePath().normalize();
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        files = List.copyOf(files);
    }

    /** A target path, content digest and intended write action. */
    public record PlannedFile(String relativePath, String sha256, String action, String content) {
        public PlannedFile {
            relativePath = require(relativePath, "relativePath");
            sha256 = require(sha256, "sha256");
            action = require(action, "action");
            content = require(content, "content");
        }

        private static String require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
