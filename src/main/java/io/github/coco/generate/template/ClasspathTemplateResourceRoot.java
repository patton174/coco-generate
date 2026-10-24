package io.github.coco.generate.template;

import io.github.coco.generate.core.SafePaths;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Reads template-pack resources from the classpath. */
public final class ClasspathTemplateResourceRoot implements TemplateResourceRoot {

    private final ClassLoader classLoader;

    private final String root;

    private final String description;

    public ClasspathTemplateResourceRoot(ClassLoader classLoader, String root, String description) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
        this.root = normalizeRoot(root);
        this.description = requireText(description);
    }

    @Override
    public byte[] read(String relativePath) throws IOException {
        String path = path(relativePath);
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing classpath template resource: " + path);
            }
            byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IOException("template resource exceeds 1 MiB: " + path);
            }
            return bytes;
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return classLoader.getResource(path(relativePath)) != null;
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public String describe() {
        return description;
    }

    private String path(String relativePath) {
        return root + "/" + SafePaths.normalize(relativePath);
    }

    private static String normalizeRoot(String value) {
        String normalized = requireText(value).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("classpath template root must not be blank");
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("classpath template root must not contain '..'");
        }
        return normalized;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return value.trim();
    }
}
