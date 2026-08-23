package io.github.coco.generate.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import io.github.coco.generate.catalog.TemplateCatalog;
import io.github.coco.generate.catalog.TemplateManifest;
import io.github.coco.generate.init.ProjectInitializer;
import io.github.coco.generate.init.ProjectInitializer.ConfigurationExistsException;
import io.github.coco.generate.engine.GenerationEngine;
import io.github.coco.generate.engine.GenerationException;
import io.github.coco.generate.engine.GenerationPlan;
import io.github.coco.generate.engine.GenerationPlanApplier;

/** Command-line entry point for the Coco Generate development tool. */
public final class CocoGenerateCli {

    static final int EXIT_USAGE = 2;

    static final int EXIT_CONFLICT = 3;

    static final int EXIT_IO = 4;

    private final TemplateCatalog catalog;

    private final ProjectInitializer initializer;

    private final GenerationEngine engine;

    public CocoGenerateCli() {
        this(TemplateCatalog.builtIn(), new ProjectInitializer(), new GenerationEngine());
    }

    CocoGenerateCli(TemplateCatalog catalog, ProjectInitializer initializer, GenerationEngine engine) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.initializer = Objects.requireNonNull(initializer, "initializer must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * Starts the CLI process.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        int exitCode = new CocoGenerateCli().run(args, out, err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Executes one command without terminating the current JVM.
     *
     * @param args command-line arguments
     * @param out standard output destination
     * @param err standard error destination
     * @return process-style exit code
     */
    public int run(String[] args, PrintWriter out, PrintWriter err) {
        String[] safeArgs = args == null ? new String[0] : Arrays.copyOf(args, args.length);
        Objects.requireNonNull(out, "out must not be null");
        Objects.requireNonNull(err, "err must not be null");
        if (safeArgs.length == 0 || isHelp(safeArgs[0])) {
            printHelp(out);
            return 0;
        }
        return switch (safeArgs[0]) {
            case "list" -> list(safeArgs, out, err);
            case "init" -> init(safeArgs, out, err);
            case "plan" -> plan(safeArgs, out, err);
            case "generate" -> generate(safeArgs, out, err);
            default -> usageError("Unknown command: " + safeArgs[0], err);
        };
    }

    private int list(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length != 1) {
            return usageError("list does not accept arguments", err);
        }
        out.println("Built-in template routes:");
        for (TemplateManifest template : catalog.templates()) {
            out.printf("  %-14s %s [%s]%n", template.id(), template.description(), template.status());
        }
        return 0;
    }

    private int plan(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length != 2) return usageError("Usage: coco-generate plan <project-directory>", err);
        try {
            printPlan(engine.plan(Path.of(args[1])), out);
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println("Unable to plan Coco Generate sources: " + ex.getMessage());
            return EXIT_USAGE;
        } catch (IOException | GenerationException ex) {
            err.println("Unable to plan Coco Generate sources: " + ex.getMessage());
            return EXIT_IO;
        }
    }

    private int generate(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length != 2) return usageError("Usage: coco-generate generate <project-directory>", err);
        try {
            GenerationPlan plan = engine.plan(Path.of(args[1]));
            new GenerationPlanApplier().apply(plan);
            printPlan(plan, out);
            out.println("Generated " + plan.files().size() + " files.");
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_USAGE;
        } catch (IOException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_IO;
        } catch (GenerationException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_CONFLICT;
        }
    }

    private static void printPlan(GenerationPlan plan, PrintWriter out) {
        out.println("Output: " + plan.outputDirectory());
        for (GenerationPlan.PlannedFile file : plan.files()) {
            out.println(file.action() + " " + file.relativePath() + " " + file.sha256());
        }
        out.println("Planned " + plan.files().size() + " files.");
    }

    private int init(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length != 2) {
            return usageError("Usage: coco-generate init <directory>", err);
        }
        try {
            Path configuration = initializer.initialize(Path.of(args[1]));
            out.println("Created " + configuration);
            return 0;
        }
        catch (InvalidPathException ex) {
            err.println("Invalid initialization directory: " + ex.getInput());
            return EXIT_USAGE;
        }
        catch (ConfigurationExistsException ex) {
            err.println(ex.getMessage());
            return EXIT_CONFLICT;
        }
        catch (IOException ex) {
            err.println("Unable to initialize Coco Generate: " + ex.getMessage());
            return EXIT_IO;
        }
    }

    private static boolean isHelp(String command) {
        return "help".equals(command) || "--help".equals(command) || "-h".equals(command);
    }

    private static int usageError(String message, PrintWriter err) {
        err.println(message);
        err.println("Run 'coco-generate help' for available commands.");
        return EXIT_USAGE;
    }

    private static void printHelp(PrintWriter out) {
        out.println("Coco Generate - development-time source generator foundation");
        out.println();
        out.println("Usage: coco-generate <command>");
        out.println();
        out.println("Commands:");
        out.println("  help                 Show this help text");
        out.println("  list                 List built-in template routes and their status");
        out.println("  init <directory>     Create a protected coco-generate.yml");
        out.println("  plan <directory>     Print the CRUD source plan without writing files");
        out.println("  generate <directory> Apply the CRUD source plan with CREATE_NEW writes");
    }
}
