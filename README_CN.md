# Coco Generate

[English](README.md) | 简体中文

Coco Generate 是 Coco 生态中的开发期源码生成器和模板包平台。它与
[Coco Framework](https://github.com/patton174/coco-framework) 保持独立，输出的是可读、
可修改并最终由业务项目接管的源码；[Coco Admin](https://github.com/patton174/coco-admin)
等应用只消费生成结果，不在运行时依赖生成器。

## 当前能力

本仓库提供可运行的 CRUD 源码生成入口，CLI 支持：

- `help`：显示可用命令；
- `list [--template-root <path>] [<project-directory>]`：列出内置或外部模板路线及其实现状态；
- `init <directory>`：安全创建初始 `coco-generate.yml`，目标文件已存在时拒绝覆盖；
- `plan [options] <directory>`：只渲染并打印确定性目标，不写入文件；
- `generate [options] <directory>`：以 `CREATE_NEW` 策略应用同一计划。

`crud` 已可执行；`admin-module`、`master-data`、`purchase`、`sales`、`inventory` 和
`finance` 仍是 metadata-only 路线。Maven 插件和 IDE 集成尚未实现；未实现路线不会创建空
Java 模块伪装成交付。

所有内置路线已统一使用 v2 `manifest.properties`。项目可以选择外部 v2 模板包、按资源切换
模板组并传入带类型的变量。输出根仍固定为 `src/main/java`，已有文件不会被覆盖。

## 运行 CLI

需要 Maven 3.9+ 与 JDK 17 或更高版本。CI 使用 JDK 21 验证，产物保持 Java 17 字节码兼容。

```bash
mvn -B -ntp verify
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar help
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar init ./example
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar plan ./example
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar generate ./example
```

`plan` / `generate` 可选参数：

```bash
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar plan \
  --config ./example/alt.yml \
  --template-root ./example/.coco/templates \
  --template company-crud \
  --var author=platform-team \
  ./example
```

- `--config` 相对路径按项目目录解析；
- `--template-root` 覆盖 `template.location`；
- `--template` 覆盖 `template.group`；
- `--var name=value` 可重复，只接受标量值；复杂值写在 YAML `vars` 中。

开发时也可以通过 Maven 调用同一个入口：

```bash
mvn -q compile exec:java '-Dexec.args=list'
```

## 外部模板包（Phase 1）

外部模板根与内置模板结构一致：

```text
.coco/templates/
  index.txt
  company-crud/
    manifest.properties
    templates/
      domain.java.ftl
      controller.java.ftl
```

`index.txt` 每行一个 group id，`#` 开头为注释。每个 group 使用 v2 清单：

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

项目配置：

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
    template: company-crud    # 可选，资源级覆盖
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

模板可以读取四个模型根：

- `${_coco.templateGroup}`、`${_coco.templateLocation}`、`${_coco.packVersion}`、
  `${_coco.outputRoot}`；
- `${vars.*}`：合并后的自定义变量；
- `${crud.*}`：现有 CRUD 模型，内置模板无需修改；
- `${resource.name}`、`${resource.table}`、`${resource.apiPath}`、
  `${resource.template}`、`${resource.vars}`。

变量类型支持 `string`、`boolean`、`integer`、`enum`、`any`、`list<T>`、`map<T>` 和
`object`，最多嵌套 5 层；Phase 1 不支持 `list<enum>`。优先级为清单默认值 < 根级
`vars` < 资源级 `vars` < CLI `--var`。模板包声明变量后，未声明的 key 会报错；
`required=true` 的变量必须有值。

外部模板根是**替换**语义，不会与内置根叠加。模板文件是数据：不执行脚本，禁用 `?new` /
`?api`，模板路径不能逃逸模板根，输出路径不能逃逸 `<project>/src/main/java`。模板中不得
写入凭据、token 或私钥。

完整契约见
[docs/specs/2026-10-24-phase-1-extensible-template-design.md](docs/specs/2026-10-24-phase-1-extensible-template-design.md)。

## 产品边界

Coco Generate 只在开发期运行并写出普通源码。它不能演变为运行时动态 CRUD 系统；源码
生成后由业务团队评审、修改和维护。默认写入策略必须保护已有文件。

详细边界与迁移方案见
[docs/specs/2026-07-11-coco-generate-product-boundary.md](docs/specs/2026-07-11-coco-generate-product-boundary.md)。
CLI 优先读取 `coco-generate.yml`；兼容窗口内仅存在旧 `coco-codegen.yml` 的项目仍可读取，
两个文件同时存在则失败关闭。输出根为 `src/main/java`，已有文件、不安全路径与符号链接逃逸均
会拒绝。

## 从 `coco:generate` 迁移

Coco Framework 3.0.0 已移除 `coco-feature-codegen` 模块、`CocoFeature.CODEGEN` 功能标识和
`coco-maven-plugin` 的 `coco:generate` goal，CRUD 源码生成由本仓库唯一承担。内置 `crud`
模板与框架 2.x 内置模板逐字节一致，生成源码语义不变。

| 旧 `coco:generate` 参数 | Coco Generate 对应方式 |
| --- | --- |
| `coco.codegen.spec`（默认 `coco-codegen.yml`） | 项目目录下的 `coco-generate.yml`；旧文件名仍可识别，YAML 结构不变 |
| `coco.codegen.outputDirectory`（默认 `src/main/java`） | 固定写入 `<项目目录>/src/main/java` |
| `coco.codegen.dryRun=true` | `plan <directory>`：只打印计划，不写文件 |
| `coco.codegen.overwrite=true` | 无对应。只允许 `CREATE_NEW`，已有文件一律作为冲突报告 |
| `coco.codegen.templateLocation` | `template.location`，配合 v2 外部模板包；外部根替换内置根 |
| `coco.codegen.templateGroup` | `template.group`，可用资源级 `template` 覆盖 |
| `coco.codegen.encoding` | `template.encoding` 控制模板文件读取；输出始终为 UTF-8 |

升级到框架 3.0.0 的项目还需要从 `coco.features.disabled`、`coco.features.enabled` 和
`@CocoFeatures` 中删除 `codegen`；残留条目会让构建失败并给出指向本仓库的提示。

## 相关仓库

- [coco-framework](https://github.com/patton174/coco-framework)：运行时 Web 框架，自 3.0.0 起
  不再包含生成器，且不得反向依赖本仓库。
- [coco-admin](https://github.com/patton174/coco-admin)：可以接收生成源码，但不得嵌入生成器
  或形成运行时依赖。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
