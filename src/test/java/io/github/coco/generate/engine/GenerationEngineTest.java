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
        assertFalse(Files.exists(project.resolve("src/main/java")));
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
    void rejectsAmbiguousCurrentAndLegacyConfigurations() throws Exception {
        Path project = copyFixture("ambiguous");
        Files.copy(project.resolve("coco-generate.yml"), project.resolve("coco-codegen.yml"));

        IOException exception = assertThrows(IOException.class, () -> engine.plan(project));

        assertTrue(exception.getMessage().contains("both coco-generate.yml and coco-codegen.yml exist"));
    }

    @Test
    void rejectsUnsafeOutputPathFormsBeforeFilesystemWrites() {
        for (String unsafe : List.of("../escape.java", "/absolute.java", "C:/absolute.java", "NUL.java")) {
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
}
