package io.github.coco.generate.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.coco.generate.catalog.TemplateCatalog;
import io.github.coco.generate.catalog.TemplateManifest;
import io.github.coco.generate.core.GenerationException;
import io.github.coco.generate.engine.GenerationEngine;
import io.github.coco.generate.engine.GenerationOptions;
import io.github.coco.generate.engine.GenerationPlan;
import io.github.coco.generate.engine.GenerationPlanApplier;
import io.github.coco.generate.init.ProjectInitializer;
import io.github.coco.generate.init.ProjectInitializer.ConfigurationExistsException;
import io.github.coco.generate.template.TemplateLocation;
import io.github.coco.generate.template.TemplatePackException;

/** Command-line entry point for the Coco Generate development tool. */
public final class CocoGenerateCli {

    static final int EXIT_USAGE = 2;

    static final int EXIT_CONFLICT = 3;

    static final int EXIT_IO = 4;

    private static final Set<String> PLAN_OPTIONS = Set.of(
            "--config", "--template-root", "--template", "--var");

    private static final Set<String> LIST_OPTIONS = Set.of("--template-root");

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
        ParsedArguments parsed;
        try {
            parsed = parseArguments(args, LIST_OPTIONS);
        }
        catch (UsageException ex) {
            return usageError(ex.getMessage(), err);
        }
        if (parsed.positionals().size() > 1) {
            return usageError(
                    "Usage: coco-generate list [--template-root <path>] [<project-directory>]", err);
        }

        out.println("Built-in template routes:");
        printRoutes(catalog, out);
        if (parsed.templateRoot() == null && parsed.positionals().isEmpty()) {
            return 0;
        }

        try {
            TemplateLocation location;
            if (parsed.templateRoot() != null) {
                Path base = parsed.positionals().isEmpty()
                        ? Path.of("").toAbsolutePath()
                        : Path.of(parsed.positionals().get(0));
                location = TemplateLocation.parse(parsed.templateRoot(), base);
            }
            else {
                location = engine.templateLocation(Path.of(parsed.positionals().get(0)));
            }
            if ("builtin".equals(location.description())) {
                return 0;
            }
            TemplateCatalog external = TemplateCatalog.from(location.resources());
            out.println();
            out.println("Template root: " + location.description());
            out.println("Template routes:");
            printRoutes(external, out);
            return 0;
        }
        catch (InvalidPathException ex) {
            err.println("Invalid template or project path: " + ex.getInput());
            return EXIT_USAGE;
        }
        catch (TemplatePackException ex) {
            return templateFailure("Unable to list template routes: ", ex, err);
        }
        catch (IOException ex) {
            err.println("Unable to list template routes: " + ex.getMessage());
            return EXIT_IO;
        }
    }

    private int plan(String[] args, PrintWriter out, PrintWriter err) {
        ParsedArguments parsed;
        try {
            parsed = parseArguments(args, PLAN_OPTIONS);
        }
        catch (UsageException ex) {
            return usageError(ex.getMessage(), err);
        }
        if (parsed.positionals().size() != 1) {
            return usageError(
                    "Usage: coco-generate plan [options] <project-directory>", err);
        }
        try {
            GenerationPlan plan = engine.plan(
                    Path.of(parsed.positionals().get(0)), parsed.options());
            printPlan(plan, out);
            return 0;
        }
        catch (InvalidPathException ex) {
            err.println("Invalid project path: " + ex.getInput());
            return EXIT_USAGE;
        }
        catch (IllegalArgumentException ex) {
            err.println("Unable to plan Coco Generate sources: " + ex.getMessage());
            return EXIT_USAGE;
        }
        catch (TemplatePackException ex) {
            return templateFailure("Unable to plan Coco Generate sources: ", ex, err);
        }
        catch (GenerationException ex) {
            err.println("Unable to plan Coco Generate sources: " + ex.getMessage());
            return EXIT_CONFLICT;
        }
        catch (IOException ex) {
            err.println("Unable to plan Coco Generate sources: " + ex.getMessage());
            return EXIT_IO;
        }
    }

    private int generate(String[] args, PrintWriter out, PrintWriter err) {
        ParsedArguments parsed;
        try {
            parsed = parseArguments(args, PLAN_OPTIONS);
        }
        catch (UsageException ex) {
            return usageError(ex.getMessage(), err);
        }
        if (parsed.positionals().size() != 1) {
            return usageError(
                    "Usage: coco-generate generate [options] <project-directory>", err);
        }
        try {
            GenerationPlan plan = engine.plan(
                    Path.of(parsed.positionals().get(0)), parsed.options());
            new GenerationPlanApplier().apply(plan);
            printPlan(plan, out);
            out.println("Generated " + plan.files().size() + " files.");
            return 0;
        }
        catch (InvalidPathException ex) {
            err.println("Invalid project path: " + ex.getInput());
            return EXIT_USAGE;
        }
        catch (IllegalArgumentException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_USAGE;
        }
        catch (TemplatePackException ex) {
            return templateFailure("Unable to generate Coco sources: ", ex, err);
        }
        catch (GenerationException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_CONFLICT;
        }
        catch (IOException ex) {
            err.println("Unable to generate Coco sources: " + ex.getMessage());
            return EXIT_IO;
        }
    }

    private static int templateFailure(
            String operation, TemplatePackException exception, PrintWriter err) {
        err.println(operation + exception.getMessage());
        return exception.getCause() instanceof IOException ? EXIT_IO : EXIT_USAGE;
    }

    private static void printRoutes(TemplateCatalog catalog, PrintWriter out) {
        for (TemplateManifest template : catalog.templates()) {
            out.printf("  %-14s %s [%s]%n", template.id(), template.description(), template.status());
        }
    }

    private static void printPlan(GenerationPlan plan, PrintWriter out) {
        if (plan.templateRoot() != null) {
            out.println("Template root: " + plan.templateRoot());
        }
        if (plan.defaultGroup() != null) {
            out.print("Default group: " + plan.defaultGroup());
            if (plan.packVersion() != null) {
                out.print(" (packVersion " + plan.packVersion() + ")");
            }
            out.println();
        }
        out.println("Output: " + plan.outputDirectory());
        for (GenerationPlan.PlannedFile file : plan.files()) {
            StringBuilder line = new StringBuilder();
            line.append(file.action()).append(' ')
                    .append(file.relativePath()).append(' ')
                    .append(file.sha256());
            if (file.templateGroup() != null) {
                line.append(" [").append(file.templateGroup()).append(']');
            }
            out.println(line);
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

    private static ParsedArguments parseArguments(String[] args, Set<String> allowedOptions) {
        Path configFile = null;
        String templateRoot = null;
        String templateGroup = null;
        Map<String, String> variables = new LinkedHashMap<>();
        List<String> positionals = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                positionals.add(argument);
                continue;
            }
            if (!allowedOptions.contains(argument)) {
                throw new UsageException("Unknown option: " + argument);
            }
            switch (argument) {
                case "--config" -> {
                    String value = optionValue(args, ++index, argument);
                    try {
                        configFile = Path.of(value);
                    }
                    catch (InvalidPathException ex) {
                        throw new UsageException("Invalid --config path: " + value);
                    }
                }
                case "--template-root" -> templateRoot = optionValue(args, ++index, argument);
                case "--template" -> templateGroup = optionValue(args, ++index, argument);
                case "--var" -> {
                    String value = optionValue(args, ++index, argument);
                    int separator = value.indexOf('=');
                    String name = separator < 0 ? "" : value.substring(0, separator).trim();
                    if (separator <= 0 || name.isEmpty()) {
                        throw new UsageException(
                                "--var must use the form name=value: " + value);
                    }
                    variables.put(name, value.substring(separator + 1));
                }
                default -> throw new UsageException("Unknown option: " + argument);
            }
        }
        return new ParsedArguments(configFile, templateRoot, templateGroup, variables, positionals);
    }

    private static String optionValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new UsageException("Missing value for " + option);
        }
        String value = args[index];
        if (value.isBlank() || value.startsWith("--")) {
            throw new UsageException("Missing value for " + option);
        }
        return value;
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
        out.println("  list <directory>     Also list template routes from the project template root");
        out.println("  init <directory>     Create a protected coco-generate.yml");
        out.println("  plan <directory>     Print the source plan without writing files");
        out.println("  generate <directory> Apply the source plan with CREATE_NEW writes");
        out.println();
        out.println("Options for plan and generate:");
        out.println("  --config <file>       Configuration file; relative paths use the project directory");
        out.println("  --template-root <p>   Override template.location");
        out.println("  --template <group>    Override template.group");
        out.println("  --var name=value      Set a scalar template variable; repeatable");
        out.println();
        out.println("Options for list:");
        out.println("  --template-root <p>   List routes from this template root");
    }

    private record ParsedArguments(
            Path configFile,
            String templateRoot,
            String templateGroup,
            Map<String, String> variables,
            List<String> positionals) {

        private ParsedArguments {
            variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
            positionals = List.copyOf(positionals);
        }

        private GenerationOptions options() {
            return new GenerationOptions(configFile, templateRoot, templateGroup, variables);
        }
    }

    private static final class UsageException extends RuntimeException {

        private UsageException(String message) {
            super(message);
        }
    }
}
