package io.github.coco.generate.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Assumptions;

class GenerationEngineTest {
    @TempDir
    Path temporaryDirectory;

    private final GenerationEngine engine = new GenerationEngine();

    @Test
    void goldenCrudFixtureProducesLegacyFileSetAndSourceSemantics() throws Exception {
        Path project = copyFixture("golden");

        GenerationPlan plan = engine.plan(project);

        assertEquals(List.of(
                "com/example/catalog/domain/product/Product.java",
                "com/example/catalog/domain/product/ProductRepository.java",
                "com/example/catalog/application/product/ProductApplicationService.java",
                "com/example/catalog/infrastructure/product/ProductEntity.java",
                "com/example/catalog/infrastructure/product/ProductMapper.java",
                "com/example/catalog/infrastructure/product/MybatisPlusProductRepository.java",
                "com/example/catalog/interfaces/rest/product/ProductController.java",
                "com/example/catalog/interfaces/rest/product/dto/CreateProductRequest.java",
                "com/example/catalog/interfaces/rest/product/dto/UpdateProductRequest.java",
                "com/example/catalog/interfaces/rest/product/dto/ProductResponse.java"),
                plan.files().stream().map(GenerationPlan.PlannedFile::relativePath).toList());
        assertTrue(plan.files().stream().anyMatch(file -> file.content().contains("@RequestMapping(\"/products\")")));
        assertTrue(plan.files().stream().anyMatch(file -> file.content().contains("public record Product(")));
        for (GenerationPlan.PlannedFile file : plan.files()) {
            assertEquals(resource("/golden/crud/expected/" + file.relativePath()), file.content(), file.relativePath());
        }
        assertFalse(Files.exists(project.resolve("src/main/java")));
    }

    @Test
    void legacyConfigurationNameRemainsExecutableWhenItIsTheOnlyConfiguration() throws Exception {
        Path project = copyFixture("legacy");
        Files.move(project.resolve("coco-generate.yml"), project.resolve("coco-codegen.yml"));

        assertEquals(10, engine.plan(project).files().size());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsWindowsJunctionBetweenProjectAndOutputRoot() throws Exception {
        Path project = copyFixture("junction-project");
        Path external = Files.createDirectory(temporaryDirectory.resolve("junction-external"));
        Process process = new ProcessBuilder("cmd", "/c", "mklink /J \"" + project.resolve("src")
                + "\" \"" + external + "\"").start();
        assertEquals(0, process.waitFor(), "mklink /J must be available on Windows");
        try {
            assertThrows(GenerationException.class, () -> engine.plan(project));
            assertFalse(Files.exists(external.resolve("main/java/com/example/catalog/domain/product/Product.java")));
        } finally {
            new ProcessBuilder("cmd", "/c", "rmdir \"" + project.resolve("src") + "\"").start().waitFor();
        }
    }

    @Test
    void rejectsSymbolicLinkBetweenProjectAndOutputRoot() throws Exception {
        Path project = copyFixture("symbolic-link-project");
        Path external = Files.createDirectory(temporaryDirectory.resolve("symbolic-link-external"));
        try {
            Files.createSymbolicLink(project.resolve("src"), external);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            Assumptions.abort("symbolic links are unavailable on this test host: " + ex.getMessage());
        }

        assertThrows(GenerationException.class, () -> engine.plan(project));
        assertFalse(Files.exists(external.resolve("main/java/com/example/catalog/domain/product/Product.java")));
    }

    @Test
    void applyWritesOnlyAfterEntireBatchPassesAndSecondRunFailsWithoutWriting() throws Exception {
        Path project = copyFixture("apply");
        GenerationPlan plan = engine.plan(project);
        GenerationPlanApplier applier = new GenerationPlanApplier();

        assertEquals(10, applier.apply(plan).size());
        Path existing = plan.outputDirectory().resolve(plan.files().get(0).relativePath());
        String original = Files.readString(existing, StandardCharsets.UTF_8);
        GenerationException exception = assertThrows(GenerationException.class, () -> applier.apply(plan));

        assertTrue(exception.getMessage().contains("collision"));
        assertEquals(original, Files.readString(existing, StandardCharsets.UTF_8));
    }

    @Test
    void removesPreviouslyCreatedFilesAndNewDirectoriesWhenNthWriteFails() {
        Path output = temporaryDirectory.resolve("atomic-output");
        GenerationPlan plan = new GenerationPlan(temporaryDirectory, output, List.of(
                new GenerationPlan.PlannedFile("first/One.java", "1", "CREATE_NEW", "one"),
                new GenerationPlan.PlannedFile("second/Two.java", "2", "CREATE_NEW", "two")));
        GenerationPlanApplier applier = new GenerationPlanApplier((target, content) -> {
            if (target.getFileName().toString().equals("Two.java")) {
                throw new IOException("injected second write failure");
            }
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        });

        IOException failure = assertThrows(IOException.class, () -> applier.apply(plan));

        assertEquals("injected second write failure", failure.getMessage());
        assertFalse(Files.exists(output.resolve("first/One.java")));
        assertFalse(Files.exists(output.resolve("second/Two.java")));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsAmbiguousCurrentAndLegacyConfigurations() throws Exception {
        Path project = copyFixture("ambiguous");
        Files.copy(project.resolve("coco-generate.yml"), project.resolve("coco-codegen.yml"));

        IOException exception = assertThrows(IOException.class, () -> engine.plan(project));

        assertTrue(exception.getMessage().contains("both coco-generate.yml and coco-codegen.yml exist"));
    }

    @Test
    void rejectsUnsafeOutputPathFormsBeforeFilesystemWrites() {
        for (String unsafe : List.of("../escape.java", "/absolute.java", "C:/absolute.java", "NUL.java",
                "NUL ", "NUL.", "COM1 .txt", "regular.")) {
            assertThrows(GenerationException.class, () -> SafePaths.normalize(unsafe));
        }
    }

    private Path copyFixture(String name) throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve(name));
        try (var source = GenerationEngineTest.class.getResourceAsStream("/golden/crud/coco-generate.yml")) {
            Files.copy(source, project.resolve("coco-generate.yml"));
        }
        return project;
    }

    private static String resource(String name) throws IOException {
        try (var input = GenerationEngineTest.class.getResourceAsStream(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
