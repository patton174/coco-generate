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

## Template Pack Contract

Phase 1 accepts only v2 template packs. Bundled routes and external roots use
the same loader:

```text
<template-root>/
  index.txt
  <group>/
    manifest.properties
    templates/
      *.ftl
```

- `index.txt` lists one group per line; `#` starts a comment.
- `manifestVersion` must be `2`; v1 manifests are rejected, not adapted.
- `manifest.properties` must declare `template.count`; executable packs need at
  least one template, metadata-only packs use `template.count=0`.
- Template sources are relative to the group directory and must start with
  `templates/`.
- An external `template.location` replaces the built-in root; there is no
  overlay.
- Variables support `string`, `boolean`, `integer`, `enum`, `any`, `list<T>`,
  `map<T>`, and `object`, nested at most five levels. `enum` is top-level only.
- Precedence is manifest default < root `vars` < resource `vars` < CLI `--var`.
  When a pack declares variables, undeclared keys are rejected.
- Templates read `_coco`, `vars`, `crud`, and `resource`. The bundled CRUD
  templates continue to use `${crud.*}` and must remain byte-compatible.
- Templates are data. `?new` and `?api` stay disabled, template paths must stay
  inside the template root, output paths must stay inside the project, and
  generated files remain `CREATE_NEW` under `src/main/java`.

Minimal external manifest:

```properties
manifestVersion=2
id=company-crud
displayName=Company CRUD
description=Company-standard CRUD source layout.
status=executable
outputKind=readable-source
ownership=business-project
packVersion=1.0.0
variable.count=1
variable.0.name=author
variable.0.type=string
variable.0.required=true
template.count=1
template.0.source=templates/domain.java.ftl
template.0.output=${crud.basePackagePath}/domain/${crud.resourcePackage}/${crud.resourceName}.java
template.0.kind=java
```

Project configuration:

```yaml
template:
  location: ./.coco/templates
  group: company-crud
vars:
  author: platform-team
base-package: com.example.catalog
resources:
  - name: Product
    table: catalog_product
    id:
      name: id
      column: id
      type: Long
      strategy: AUTO
    fields:
      - name: sku
        column: sku
        type: String
        required: true
```

## Verification

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B -ntp verify
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar help
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list --template-root .\.coco\templates
```

Before reporting completion, check `git status -sb`. Do not commit generated
build output or the local CodeGraph database.

## Pull Request Workflow

- Work on a focused branch and merge through a pull request into `main`.
- Do not push directly to protected `main` and do not use force pushes.
- Require the stable `CI gate` context and resolve review conversations before merge.
- Use merge commits so generator contracts and template-pack changes remain visible.
- Keep generated examples, build output, local secrets, IDE metadata, and the CodeGraph database out of commits.
