package io.github.coco.generate.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.coco.generate.init.ProjectInitializer;

class CocoGenerateCliTest {

    @TempDir
    Path temporaryDirectory;

    private final CocoGenerateCli cli = new CocoGenerateCli();

    @Test
    void helpReportsImplementedCommands() {
        CommandResult result = invoke("help");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("help"));
        assertTrue(result.output().contains("list"));
        assertTrue(result.output().contains("init <directory>"));
        assertTrue(result.output().contains("plan <directory>"));
        assertTrue(result.output().contains("generate <directory>"));
    }

    @Test
    void listDistinguishesExecutableAndMetadataOnlyRoutes() {
        CommandResult result = invoke("list");

        assertEquals(0, result.exitCode());
        for (String route : new String[] {
                "crud", "admin-module", "master-data", "purchase", "sales", "inventory", "finance"
        }) {
            assertTrue(result.output().contains(route), () -> "Missing route: " + route);
        }
        assertTrue(result.output().contains("crud"));
        assertTrue(result.output().contains("[executable]"));
        assertTrue(result.output().contains("[metadata-only]"));
    }

    @Test
    void initCreatesOnceAndThenReturnsAConflict() {
        Path project = temporaryDirectory.resolve("application");

        CommandResult created = invoke("init", project.toString());
        CommandResult conflict = invoke("init", project.toString());

        assertEquals(0, created.exitCode());
        assertTrue(Files.isRegularFile(project.resolve(ProjectInitializer.CONFIG_FILE_NAME)));
        assertEquals(CocoGenerateCli.EXIT_CONFLICT, conflict.exitCode());
        assertTrue(conflict.error().contains("Refusing to overwrite"));
    }

    @Test
    void generationRequiresProjectDirectory() {
        CommandResult result = invoke("generate");

        assertEquals(CocoGenerateCli.EXIT_USAGE, result.exitCode());
        assertTrue(result.error().contains("Usage: coco-generate generate"));
    }

    @Test
    void missingConfigurationReturnsIoExitCode() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("missing-config"));

        CommandResult result = invoke("generate", project.toString());

        assertEquals(CocoGenerateCli.EXIT_IO, result.exitCode());
        assertTrue(result.error().contains("missing coco-generate.yml"));
    }

    @Test
    void existingGeneratedSourcesReturnConflictExitCode() {
        Path project = temporaryDirectory.resolve("generated");
        assertEquals(0, invoke("init", project.toString()).exitCode());
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
