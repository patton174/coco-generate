package io.github.coco.generate.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.test.TestProjectFixture;

class CocoGenerateCliOptionsTest {

    @TempDir
    Path temporaryDirectory;

    private final CocoGenerateCli cli = new CocoGenerateCli();

    @Test
    void generateHonorsTemplateAndVariableOverrides() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        CommandResult result = invoke(
                "generate", "--template", "alternate", "--var", "author=cli-team",
                project.toString());

        assertEquals(0, result.exitCode(), result.error());
        Path generated = project.resolve(
                "src/main/java/com/example/simple/alternate/Widget.java");
        assertTrue(Files.isRegularFile(generated));
        String content = Files.readString(generated);
        assertTrue(content.contains("cli-team"));
        assertTrue(content.contains("3.0.0"));
        assertTrue(result.output().contains("Default group: alternate (packVersion 3.0.0)"));
        assertTrue(result.output().contains("[alternate]"));
    }

    @Test
    void planHonorsConfigOverride() throws Exception {
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

        CommandResult result = invoke("plan", "--config", "alt.yml", project.toString());

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("com/example/alt/simple/Gadget.java"));
        assertTrue(result.output().contains("Template root: file:"));
    }

    @Test
    void listSupportsExternalRootsAndProjectConfigurations() throws Exception {
        Path project = TestProjectFixture.createExternalProject(temporaryDirectory.resolve("project"));

        CommandResult byRoot = invoke(
                "list", "--template-root", TestProjectFixture.templateRoot(project).toString());
        CommandResult byProject = invoke("list", project.toString());

        assertEquals(0, byRoot.exitCode(), byRoot.error());
        assertTrue(byRoot.output().contains("Template root: file:"));
        assertTrue(byRoot.output().contains("company-crud"));
        assertTrue(byRoot.output().contains("audit-crud"));
        assertEquals(0, byProject.exitCode(), byProject.error());
        assertTrue(byProject.output().contains("company-crud"));
        assertTrue(byProject.output().contains("audit-crud"));
    }

    @Test
    void rejectsComplexCliVariablesWithUsageExitCode() throws Exception {
        Path project = TestProjectFixture.createExternalProject(temporaryDirectory.resolve("project"));

        CommandResult result = invoke("plan", "--var", "imports=java.util.List", project.toString());

        assertEquals(CocoGenerateCli.EXIT_USAGE, result.exitCode());
        assertTrue(result.error().contains("use YAML"));
    }

    @Test
    void rejectsMalformedOrUnknownOptions() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        CommandResult malformedVariable = invoke("plan", "--var", "author", project.toString());
        CommandResult unknownOption = invoke("plan", "--bogus", project.toString());

        assertEquals(CocoGenerateCli.EXIT_USAGE, malformedVariable.exitCode());
        assertTrue(malformedVariable.error().contains("name=value"));
        assertEquals(CocoGenerateCli.EXIT_USAGE, unknownOption.exitCode());
        assertTrue(unknownOption.error().contains("Unknown option"));
    }

    @Test
    void missingGroupOrConfigUsesIoExitCode() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        CommandResult missingGroup = invoke("plan", "--template", "missing", project.toString());
        CommandResult missingConfig = invoke("plan", "--config", "missing.yml", project.toString());

        assertEquals(CocoGenerateCli.EXIT_IO, missingGroup.exitCode());
        assertEquals(CocoGenerateCli.EXIT_IO, missingConfig.exitCode());
    }

    @Test
    void invalidGroupNameUsesUsageExitCode() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));

        CommandResult result = invoke("plan", "--template", "Not_A_Group", project.toString());

        assertEquals(CocoGenerateCli.EXIT_USAGE, result.exitCode());
    }

    @Test
    void existingGeneratedFilesStillReturnConflictExitCode() throws Exception {
        Path project = TestProjectFixture.createSimpleProject(temporaryDirectory.resolve("project"));
        assertEquals(0, invoke("generate", project.toString()).exitCode());

        CommandResult result = invoke("generate", project.toString());

        assertEquals(CocoGenerateCli.EXIT_CONFLICT, result.exitCode());
        assertTrue(result.error().contains("collision"));
    }

    private CommandResult invoke(String... args) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        int exitCode = cli.run(args, new PrintWriter(output, true), new PrintWriter(error, true));
        return new CommandResult(exitCode, output.toString(), error.toString());
    }

    private record CommandResult(int exitCode, String output, String error) {
    }
}
