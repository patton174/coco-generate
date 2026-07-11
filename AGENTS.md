# Coco Generate Agent Guide

## Project Positioning

Coco Generate is a development-time source generator and template platform for
Coco applications. It owns generator entry points, template-pack contracts,
typed variables, generation planning, and safe file writing.

It must not become a runtime dynamic CRUD engine. Generated output must be
readable source code that business teams can review, modify, and own after
generation.

## Repository Boundaries

- Generated files are business source code, not runtime metadata interpreted by
  Coco Generate.
- File writes are fail-safe by default. Existing files must not be overwritten
  unless a future explicit, reviewed overwrite policy authorizes the exact
  target.
- `coco-framework` must never depend on this repository. During migration this
  repository may temporarily adapt framework code-generation contracts in the
  forward direction.
- `coco-admin` consumes generated source code only. It must not embed the
  generator or require Coco Generate at runtime.
- Templates must not hide business transactions, domain models, or custom query
  behavior behind runtime magic.

## Development Rules

- Use Java 17 language and bytecode compatibility. Verification may run on JDK
  21.
- Keep CLI, catalog, generation engine, and file-writing boundaries explicit.
- Treat template manifests and project configuration as data. Do not execute
  scripts from template packs.
- Keep built-in output deterministic and free of credentials or environment
  secrets.
- Add compatibility tests before changing published manifest or configuration
  contracts.
- Do not claim a command or template route is implemented until it produces and
  verifies real source output.
- If `.codegraph/` exists, use CodeGraph before broad source searches. After
  source changes, run `codegraph sync .` when the CLI is available.

## Verification

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B -ntp verify
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar help
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list
```

Before reporting completion, check `git status -sb`. Do not commit generated
build output or the local CodeGraph database.

## Pull Request Workflow

- Work on a focused branch and merge through a pull request into `main`.
- Do not push directly to protected `main` and do not use force pushes.
- Require the stable `CI gate` context and resolve review conversations before merge.
- Use merge commits so generator contracts and template-pack changes remain visible.
- Keep generated examples, build output, local secrets, IDE metadata, and the CodeGraph database out of commits.
