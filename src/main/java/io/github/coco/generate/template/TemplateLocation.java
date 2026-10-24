package io.github.coco.generate.template;

import io.github.coco.generate.core.GenerationException;
import io.github.coco.generate.core.SafePaths;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Resolves the configured template location into a safe resource root. */
public record TemplateLocation(String description, TemplateResourceRoot resources) {

    public TemplateLocation {
        if (description == null || description.isBlank()) {
            throw new TemplatePackException("template location description must not be blank");
        }
        if (resources == null) {
            throw new TemplatePackException("template location resources must not be null");
        }
        description = description.trim();
    }

    public static TemplateLocation parse(String value, Path projectDirectory) {
        if (value == null || value.isBlank()) {
            throw new TemplatePackException("template location must not be blank");
        }
        String location = value.trim();
        if ("builtin".equals(location)) {
            return new TemplateLocation(
                    "builtin",
                    new ClasspathTemplateResourceRoot(
                            TemplateLocation.class.getClassLoader(),
                            TemplatePackLoader.BUILTIN_ROOT,
                            "builtin"));
        }
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length()).trim();
            if (path.isEmpty()) {
                throw new TemplatePackException("classpath template location must not be blank");
            }
            String normalized = path.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            try {
                normalized = SafePaths.normalize(normalized);
            }
            catch (GenerationException ex) {
                throw new TemplatePackException(
                        "unsafe classpath template location: " + location, ex);
            }
            return new TemplateLocation(
                    "classpath:" + normalized,
                    new ClasspathTemplateResourceRoot(
                            TemplateLocation.class.getClassLoader(), normalized, "classpath:" + normalized));
        }
        if (location.startsWith("file:")) {
            return file(location, location.substring("file:".length()).trim(), projectDirectory);
        }
        if (hasUnsupportedScheme(location)) {
            throw new TemplatePackException("unsupported template location scheme: " + location);
        }
        return file(location, location, projectDirectory);
    }

    private static TemplateLocation file(String location, String value, Path projectDirectory) {
        if (value.isBlank()) {
            throw new TemplatePackException("file template location must not be blank");
        }
        Path root;
        try {
            root = Path.of(value);
        }
        catch (InvalidPathException ex) {
            throw new TemplatePackException("invalid template location path: " + location, ex);
        }
        if (!root.isAbsolute()) {
            if (projectDirectory == null) {
                throw new TemplatePackException(
                        "relative template location requires a project directory: " + location);
            }
            root = projectDirectory.resolve(root);
        }
        try {
            FileSystemTemplateResourceRoot resources = new FileSystemTemplateResourceRoot(root);
            return new TemplateLocation(resources.describe(), resources);
        }
        catch (IOException ex) {
            throw new TemplatePackException(
                    "unable to use template location '" + location + "': " + ex.getMessage(), ex);
        }
    }

    private static boolean hasUnsupportedScheme(String value) {
        if (value.matches("^[A-Za-z]:[\\\\/].*")) {
            return false;
        }
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        return value.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*");
    }
}
