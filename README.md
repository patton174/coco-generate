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
- `list` - list built-in template routes and their implementation status;
- `init <directory>` - create a safe starter `coco-generate.yml` without
  overwriting an existing file.
- `plan <directory>` - render and print deterministic CRUD targets without writing;
- `generate <directory>` - apply that plan using `CREATE_NEW` only.

`crud` is executable. `admin-module`, `master-data`, `purchase`, `sales`,
`inventory`, and `finance` remain metadata-only product routes. Maven plugin
and IDE integration are not implemented.

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

For development, the same entry point can be invoked through Maven:

```bash
mvn -q compile exec:java '-Dexec.args=list'
```

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
| `coco.codegen.templateLocation` | Not supported; only the built-in `crud` templates are used |
| `coco.codegen.encoding` | Always UTF-8 |

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
