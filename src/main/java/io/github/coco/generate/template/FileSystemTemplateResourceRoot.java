package io.github.coco.generate.template;

import io.github.coco.generate.core.SafePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Reads template-pack resources below a regular filesystem directory. */
public final class FileSystemTemplateResourceRoot implements TemplateResourceRoot {

    private final Path root;

    private final Path realRoot;

    private final String description;

    public FileSystemTemplateResourceRoot(Path root) throws IOException {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(this.root)) {
            throw new IOException("template root is not a regular directory: " + this.root);
        }
        this.realRoot = this.root.toRealPath();
        this.description = "file:" + this.root.toString().replace('\\', '/');
    }

    @Override
    public byte[] read(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new IOException("template file exceeds 1 MiB: " + file);
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("template file exceeds 1 MiB: " + file);
        }
        return bytes;
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            return Files.isRegularFile(resolve(relativePath), LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    @Override
    public String describe() {
        return description;
    }

    private Path resolve(String relativePath) throws IOException {
        String safe = SafePaths.normalize(relativePath);
        Path candidate = root.resolve(safe).normalize();
        if (!candidate.startsWith(root)) {
            throw new IOException("template path escapes template root: " + relativePath);
        }
        Path current = root;
        for (Path segment : root.relativize(candidate)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("template path contains a symbolic link: " + current);
            }
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("missing template file: " + candidate);
        }
        if (!candidate.toRealPath().startsWith(realRoot)) {
            throw new IOException("template path resolves outside template root: " + candidate);
        }
        return candidate;
    }
}
