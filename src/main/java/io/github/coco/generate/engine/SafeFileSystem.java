package io.github.coco.generate.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** NOFOLLOW filesystem checks used to keep generation inside the project tree. */
final class SafeFileSystem {
    private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;

    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private SafeFileSystem() {
    }

    static void verifyProjectDirectory(Path project) throws IOException {
        verifyDirectory(project, "project directory");
    }

    static void verifyProjectTo(Path project, Path target) throws IOException {
        if (!target.startsWith(project)) {
            throw new GenerationException("generation target escapes project directory: " + target);
        }
        Path canonicalProject = project.toRealPath();
        Path current = project;
        for (Path component : project.relativize(target)) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            verifyDirectory(current, "generation directory");
            if (!current.toRealPath().startsWith(canonicalProject)) {
                throw new GenerationException("generation directory resolves outside project: " + current);
            }
            current = current.resolve(component);
        }
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return;
        verifyDirectory(current, "generation directory");
        if (!current.toRealPath().startsWith(canonicalProject)) {
            throw new GenerationException("generation directory resolves outside project: " + current);
        }
    }

    static void verifyDirectory(Path directory, String role) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther() || isReparsePoint(directory)) {
            throw new GenerationException(role + " is not a regular directory: " + directory);
        }
    }

    private static boolean isReparsePoint(Path path) throws IOException {
        if (!WINDOWS) {
            return false;
        }
        try {
            Object value = Files.getAttribute(path, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
            return value instanceof Integer attributes && (attributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
        } catch (UnsupportedOperationException ex) {
            throw new IOException("DOS attributes are unavailable while checking Windows reparse point: " + path, ex);
        }
    }
}
