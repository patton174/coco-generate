package io.github.coco.generate.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Applies a pre-built plan only after all targets pass CREATE_NEW preflight. */
public final class GenerationPlanApplier {
    private final FileWriter fileWriter;

    public GenerationPlanApplier() {
        this((target, content) -> Files.writeString(target, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
    }

    GenerationPlanApplier(FileWriter fileWriter) {
        this.fileWriter = java.util.Objects.requireNonNull(fileWriter, "fileWriter must not be null");
    }

    /** Applies all planned files or fails before writing any generated source. */
    public List<Path> apply(GenerationPlan plan) throws IOException {
        Path root = plan.outputDirectory();
        SafeFileSystem.verifyProjectTo(plan.projectDirectory(), root);
        preflight(root, plan.files());
        List<Path> written = new ArrayList<>();
        List<Path> createdDirectories = new ArrayList<>();
        try {
            for (GenerationPlan.PlannedFile file : plan.files()) {
                Path target = target(root, file.relativePath());
                ensureDirectories(plan.projectDirectory(), target.getParent(), createdDirectories);
                SafeFileSystem.verifyProjectTo(plan.projectDirectory(), target.getParent());
                validateDirectoryChain(root, target.getParent());
                this.fileWriter.write(target, file.content());
                written.add(target);
            }
            return List.copyOf(written);
        } catch (FileAlreadyExistsException ex) {
            GenerationException failure = new GenerationException("generated file collision: " + ex.getFile(), ex);
            cleanup(written, createdDirectories, failure);
            throw failure;
        } catch (IOException ex) {
            cleanup(written, createdDirectories, ex);
            throw ex;
        } catch (RuntimeException ex) {
            cleanup(written, createdDirectories, ex);
            throw ex;
        }
    }

    private static void preflight(Path root, List<GenerationPlan.PlannedFile> files) throws IOException {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))) {
            throw new GenerationException("output directory is not a regular directory: " + root);
        }
        List<Path> collisions = new ArrayList<>();
        for (GenerationPlan.PlannedFile file : files) {
            Path target = target(root, file.relativePath());
            validateExistingDirectoryChain(root, target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) collisions.add(target);
        }
        if (!collisions.isEmpty()) throw new GenerationException("generated file collision: " + collisions);
    }

    private static Path target(Path root, String relativePath) {
        Path target = root.resolve(SafePaths.normalize(relativePath)).normalize();
        if (!target.startsWith(root)) throw new GenerationException("generated path escapes output directory: " + relativePath);
        return target;
    }

    private static void ensureDirectories(Path anchor, Path parent, List<Path> createdDirectories) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path current = parent; !current.equals(anchor); current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) break;
            missing.add(current);
        }
        if (!Files.exists(anchor, LinkOption.NOFOLLOW_LINKS)) missing.add(anchor);
        for (int index = missing.size() - 1; index >= 0; index--) {
            Path directory = missing.get(index);
            try {
                Files.createDirectory(directory);
                createdDirectories.add(directory);
            } catch (FileAlreadyExistsException ex) {
                SafeFileSystem.verifyDirectory(directory, "generated file parent");
            }
        }
    }

    private static void validateDirectoryChain(Path root, Path parent) {
        for (Path current = parent; current != null && current.startsWith(root); current = current.getParent()) {
            validateDirectory(current);
            if (current.equals(root)) return;
        }
    }

    private static void validateExistingDirectoryChain(Path root, Path parent) {
        for (Path current = parent; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) validateDirectory(current);
            if (current.equals(root)) return;
        }
    }

    private static void validateDirectory(Path directory) {
        try {
            SafeFileSystem.verifyDirectory(directory, "generated file parent");
        } catch (IOException ex) {
            throw new GenerationException("unable to inspect generated file parent: " + directory, ex);
        }
    }

    private static void cleanup(List<Path> written, List<Path> createdDirectories, Throwable failure) {
        for (int index = written.size() - 1; index >= 0; index--) {
            delete(written.get(index), failure);
        }
        for (int index = createdDirectories.size() - 1; index >= 0; index--) {
            delete(createdDirectories.get(index), failure);
        }
    }

    private static void delete(Path path, Throwable failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            failure.addSuppressed(new IOException("failed to clean up generated path: " + path, ex));
        }
    }

    @FunctionalInterface
    interface FileWriter {
        void write(Path target, String content) throws IOException;
    }
}
