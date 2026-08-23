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
    /** Applies all planned files or fails before writing any generated source. */
    public List<Path> apply(GenerationPlan plan) throws IOException {
        Path root = plan.outputDirectory();
        preflight(root, plan.files());
        List<Path> written = new ArrayList<>();
        try {
            for (GenerationPlan.PlannedFile file : plan.files()) {
                Path target = target(root, file.relativePath());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                written.add(target);
            }
            return List.copyOf(written);
        } catch (FileAlreadyExistsException ex) {
            throw new GenerationException("generated file collision: " + ex.getFile(), ex);
        } catch (IOException ex) {
            throw new GenerationException("failed to write generated file", ex);
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
            validateParents(root, target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) collisions.add(target);
        }
        if (!collisions.isEmpty()) throw new GenerationException("generated file collision: " + collisions);
    }

    private static Path target(Path root, String relativePath) {
        Path target = root.resolve(SafePaths.normalize(relativePath)).normalize();
        if (!target.startsWith(root)) throw new GenerationException("generated path escapes output directory: " + relativePath);
        return target;
    }

    private static void validateParents(Path root, Path parent) {
        for (Path current = parent; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                throw new GenerationException("generated file parent is unsafe: " + current);
            }
            if (current.equals(root)) return;
        }
    }
}
