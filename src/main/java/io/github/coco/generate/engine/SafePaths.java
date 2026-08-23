package io.github.coco.generate.engine;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Normalizes output paths before any filesystem access. */
final class SafePaths {
    private static final Set<String> DEVICES = Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
            "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private SafePaths() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw new GenerationException("unsafe generated path");
        String path = value.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) throw new GenerationException("generated path must be relative: " + value);
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            String device = segment.contains(".") ? segment.substring(0, segment.indexOf('.')) : segment;
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || segment.indexOf(':') >= 0
                    || DEVICES.contains(device.toUpperCase(Locale.ROOT))) {
                throw new GenerationException("unsafe generated path: " + value);
            }
        }
        if (Path.of(path).isAbsolute()) throw new GenerationException("generated path must be relative: " + value);
        return path;
    }
}
