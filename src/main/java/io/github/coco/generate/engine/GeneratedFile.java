package io.github.coco.generate.engine;

import java.util.Objects;

/** One rendered relative output and its UTF-8 source content. */
public record GeneratedFile(String relativePath, String content) {
    public GeneratedFile {
        relativePath = requireText(relativePath, "relativePath");
        content = Objects.requireNonNull(content, "content must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
