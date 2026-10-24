# Coco Generate

English | [简体中文](README_CN.md)

Coco Generate is the development-time source generator and template-pack
platform for the Coco ecosystem. It is intentionally separate from
[Coco Framework](https://github.com/patton174/coco-framework) and produces
readable source code that applications such as
[Coco Admin](https://github.com/patton174/coco-admin) can review, change, and
own.

## Current Status

The CLI provides a runnable CRUD source-generation route:

- `help` - show the available commands;
- `list [--template-root <path>] [<project-directory>]` - list built-in or
  external template routes and their implementation status;
- `init <directory>` - create a safe starter `coco-generate.yml` without
  overwriting an existing file;
- `plan [options] <directory>` - render and print deterministic targets without
  writing;
- `generate [options] <directory>` - apply that plan using `CREATE_NEW` only.

`crud` is executable. `admin-module`, `master-data`, `purchase`, `sales`,
`inventory`, and `finance` remain metadata-only product routes. Maven plugin
and IDE integration are not implemented.

All built-in routes now use the v2 `manifest.properties` contract. A project
can replace the built-in template root with an external v2 template pack, select
a template group per resource, and pass typed variables. The output root remains
`src/main/java`; existing files are never overwritten.

## Run The CLI

Requirements: Maven 3.9+ and JDK 17 or newer. CI verifies with JDK 21 while the
compiled bytecode targets Java 17.

```bash
mvn -B -ntp verify
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar help
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar init ./example
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar plan ./example
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar generate ./example
```

Optional `plan`/`generate` arguments:

```bash
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar plan \
  --config ./example/alt.yml \
  --template-root ./example/.coco/templates \
  --template company-crud \
  --var author=platform-team \
  ./example
```

- `--config` is resolved against the project directory when relative.
- `--template-root` overrides `template.location`.
- `--template` overrides `template.group`.
- `--var name=value` is repeatable and accepts scalar values only; complex
  values belong in YAML `vars`.

For development, the same entry point can be invoked through Maven:

```bash
mvn -q compile exec:java '-Dexec.args=list'
```

## External Template Packs (Phase 1)

An external template root has the same layout as the bundled templates:

```text
.coco/templates/
  index.txt
  company-crud/
    manifest.properties
    templates/
      domain.java.ftl
      controller.java.ftl
```

`index.txt` lists one group id per line (`#` starts a comment). Each group has a
v2 `manifest.properties`:

```properties
manifestVersion=2
id=company-crud
displayName=Company CRUD
description=Company-standard CRUD source layout.
status=executable
outputKind=readable-source
ownership=business-project
packVersion=1.0.0

variable.count=2
variable.0.name=author
variable.0.type=string
variable.0.required=true
variable.1.name=apiVersion
variable.1.type=enum
variable.1.values=v1,v2
variable.1.default=v1

template.count=2
template.0.source=templates/domain.java.ftl
template.0.output=${crud.basePackagePath}/domain/${crud.resourcePackage}/${crud.resourceName}.java
template.0.kind=java
template.1.source=templates/controller.java.ftl
template.1.output=${crud.basePackagePath}/interfaces/rest/${crud.resourcePackage}/${crud.resourceName}Controller.java
template.1.kind=java
```

Project configuration:

```yaml
template:
  location: ./.coco/templates   # builtin | classpath:... | file:... | ./relative | /absolute
  group: company-crud
  encoding: UTF-8

vars:
  author: platform-team
  apiVersion: v1

base-package: com.example.catalog
resources:
  - name: Product
    table: catalog_product
    template: company-crud    # optional per-resource override
    vars:
      aggregate: Catalog
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

Templates can read four model roots:

- `${_coco.templateGroup}`, `${_coco.templateLocation}`,
  `${_coco.packVersion}`, `${_coco.outputRoot}`;
- `${vars.*}` - merged custom variables;
- `${crud.*}` - the existing CRUD model, so bundled templates need no changes;
- `${resource.name}`, `${resource.table}`, `${resource.apiPath}`,
  `${resource.template}`, `${resource.vars}`.

Variable declarations support `string`, `boolean`, `integer`, `enum`, `any`,
`list<T>`, `map<T>`, and `object`, nested up to five levels. Enum lists are not
supported in Phase 1. Precedence is manifest default < root `vars` < resource
`vars` < CLI `--var`. Unknown keys are rejected when a pack declares variables;
`required=true` variables must resolve to a value.

External template roots **replace** the built-in root; there is no overlay.
Template files are data: no scripts are executed, `?new` and `?api` are
disabled, template paths cannot escape the template root, and generated paths
cannot escape `<project>/src/main/java`. Templates must not contain credentials,
tokens, or private keys.

The full contract is in
[docs/specs/2026-10-24-phase-1-extensible-template-design.md](docs/specs/2026-10-24-phase-1-extensible-template-design.md).

## Product Boundary

Coco Generate runs during development and writes ordinary source files. It is
not a runtime dynamic CRUD system, and generated code is not owned by the
generator after creation. Existing files are protected by default.

The detailed product and migration contract is documented in
[docs/specs/2026-07-11-coco-generate-product-boundary.md](docs/specs/2026-07-11-coco-generate-product-boundary.md).
The CLI reads `coco-generate.yml`; a project containing only the legacy
`coco-codegen.yml` is accepted during the compatibility window. Both files in
one project are rejected to avoid ambiguous generation. Output is written below
`src/main/java`; existing files, unsafe paths, and symbolic-link escapes fail
closed.

## Migrating From `coco:generate`

Coco Framework 3.0.0 removes the `coco-feature-codegen` module, the
`CocoFeature.CODEGEN` flag, and the `coco-maven-plugin` `coco:generate` goal.
This repository is the only supported entry point for CRUD source generation.
The bundled `crud` templates are byte-for-byte identical to the ones the
framework shipped in 2.x, so generated sources keep the same semantics.

| Legacy `coco:generate` parameter | Coco Generate equivalent |
| --- | --- |
| `coco.codegen.spec` (default `coco-codegen.yml`) | `coco-generate.yml` in the project directory; the legacy file name is still read and the YAML structure is unchanged |
| `coco.codegen.outputDirectory` (default `src/main/java`) | Always `<project>/src/main/java` |
| `coco.codegen.dryRun=true` | `plan <directory>`, which prints the plan without writing |
| `coco.codegen.overwrite=true` | No equivalent. Only `CREATE_NEW` is allowed and existing files are reported as conflicts |
| `coco.codegen.templateLocation` | `template.location` with a v2 external template pack; an external root replaces the built-in root |
| `coco.codegen.templateGroup` | `template.group`, optionally overridden per resource with `template` |
| `coco.codegen.encoding` | `template.encoding` controls template file reading; output is always UTF-8 |

Projects upgrading to framework 3.0.0 must also drop `codegen` from
`coco.features.disabled`, `coco.features.enabled`, and `@CocoFeatures`; a
leftover entry fails the build with a message pointing here.

## Related Repositories

- [coco-framework](https://github.com/patton174/coco-framework) - the runtime
  Web framework, which no longer contains a generator and must not depend on
  this repository.
- [coco-admin](https://github.com/patton174/coco-admin) - an application that
  may consume generated source, without a runtime dependency on this tool.

## License

Licensed under the [Apache License 2.0](LICENSE).
