package io.github.coco.generate.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.template.TemplatePackException;
import io.github.coco.generate.test.TestProjectFixture;

class GenerationEngineCustomTemplateTest {

    @TempDir
    Path temporaryDirectory;

    private final GenerationEngine engine = new GenerationEngine();

    @Test
    void externalRootReplacesBuiltinAndSupportsPerResourceGroups() throws Exception {
        Path project = TestProjectFixture.createExternalProject(temporaryDirectory.resolve("project"));

        GenerationPlan plan = engine.plan(project);

        assertEquals(List.of(
                "com/example/catalog/domain/product/Product.java",
                "com/example/catalog/interfaces/rest/product/ProductController.java",
                "com/example/catalog/audit/AuditLogAudit.java"),
                plan.files().stream().map(GenerationPlan.PlannedFile::relativePath).toList());
        assertTrue(plan.templateRoot().startsWith("file:"));
        assertTrue(plan.templateRoot().endsWith("/.coco/templates"));
        assertEquals("company-crud", plan.defaultGroup());
        assertEquals("1.0.0", plan.packVersion());
        assertTrue(plan.files().stream().allMatch(file -> "java".equals(file.kind())));
        assertEquals(List.of("company-crud", "company-crud", "audit-crud"),
                plan.files().stream().map(GenerationPlan.PlannedFile::templateGroup).toList());
        assertFalse(plan.files().stream()
                .anyMatch(file -> file.relativePath().endsWith("ProductRepository.java")));

        String product = content(plan, "com/example/catalog/domain/product/Product.java");
        assertTrue(product.contains("@author yaml-team"));
        assertTrue(product.contains("java.util.List"));
        assertTrue(product.contains("java.util.Optional"));
        assertTrue(product.contains("team: platform"));
        assertTrue(product.contains("group company-crud pack 1.0.0 resource Product"));

        String audit = content(plan, "com/example/catalog/audit/AuditLogAudit.java");
        assertTrue(audit.contains("@author resource-team"));
        assertTrue(audit.contains("group audit-crud pack 2.0.0"));
    }

    @Test
    void cliVariablesOverrideYamlAndResourceVariables() throws Exception {
        Path project = TestProjectFixture.createExternalProject(temporaryDirectory.resolve("project"));

        GenerationPlan plan = engine.plan(project, new GenerationOptions(
                null, null, null, Map.of("author", "cli-team")));

        assertTrue(content(plan, "com/example/catalog/domain/product/Product.java")
                .contains("@author cli-team"));
        assertTrue(content(plan, "com/example/catalog/audit/AuditLogAudit.java")
                .contains("@author cli-team"));
    }

    @Test
    void cliTemplateGroupOverridesTheYamlDefault() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        GenerationPlan plan = engine.plan(project, new GenerationOptions(
                null, null, "alternate", Map.of()));

        assertEquals(List.of("com/example/simple/alternate/Widget.java"),
                plan.files().stream().map(GenerationPlan.PlannedFile::relativePath).toList());
        assertEquals("alternate", plan.defaultGroup());
        assertEquals("3.0.0", plan.packVersion());
        assertTrue(content(plan, "com/example/simple/alternate/Widget.java")
                .contains("root-team 3.0.0"));
    }

    @Test
    void cliTemplateRootOverridesTheYamlLocation() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));
        Path override = Files.createDirectory(temporaryDirectory.resolve("override-root"));
        Files.writeString(override.resolve("index.txt"), "simple\n");
        TestProjectFixture.writePack(override, "simple", """
                manifestVersion=2
                id=simple
                displayName=Simple
                description=Override pack.
                status=executable
                outputKind=readable-source
                ownership=business-project
                packVersion=9.0.0
                variable.count=1
                variable.0.name=author
                variable.0.type=string
                variable.0.required=true
                template.count=1
                template.0.source=templates/widget.java.ftl
                template.0.output=${crud.basePackagePath}/${_coco.templateGroup}/${crud.resourceName}.java
                template.0.kind=java
                """, Map.of(
                "templates/widget.java.ftl",
                "${vars.author} ${_coco.packVersion}"));

        GenerationPlan plan = engine.plan(project, new GenerationOptions(
                null, override.toString(), null, Map.of()));

        assertEquals("9.0.0", plan.packVersion());
        assertTrue(plan.templateRoot().endsWith("/override-root"));
        assertTrue(content(plan, "com/example/simple/simple/Widget.java")
                .contains("root-team 9.0.0"));
    }

    @Test
    void cliConfigFileOverridesTheDefaultConfiguration() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));
        Files.writeString(project.resolve("alt.yml"), """
                template:
                  location: ./.coco/templates
                  group: simple
                vars:
                  author: alt-team
                base-package: com.example.alt
                resources:
                  - name: Gadget
                    table: alt_gadget
                    id:
                      name: id
                      column: id
                      type: Long
                      strategy: AUTO
                    fields:
                      - name: label
                        column: label
                        type: String
                        required: true
                """);

        GenerationPlan plan = engine.plan(project, new GenerationOptions(
                Path.of("alt.yml"), null, null, Map.of()));

        assertEquals(List.of("com/example/alt/simple/Gadget.java"),
                plan.files().stream().map(GenerationPlan.PlannedFile::relativePath).toList());
        assertTrue(content(plan, "com/example/alt/simple/Gadget.java").contains("alt-team"));
    }

    @Test
    void rejectsComplexCliVariablesAndUnknownCliVariables() throws Exception {
        Path project = TestProjectFixture.createExternalProject(temporaryDirectory.resolve("project"));

        assertThrows(TemplatePackException.class, () -> engine.plan(
                project, new GenerationOptions(null, null, null, Map.of("imports", "java.util.List"))));
        assertThrows(TemplatePackException.class, () -> engine.plan(
                project, new GenerationOptions(null, null, null, Map.of("unknown", "value"))));
    }

    private static String content(GenerationPlan plan, String relativePath) {
        return plan.files().stream()
                .filter(file -> file.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow()
                .content();
    }
}
