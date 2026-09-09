# Coco Generate

[English](README.md) | 简体中文

Coco Generate 是 Coco 生态中的开发期源码生成器和模板包平台。它与
[Coco Framework](https://github.com/patton174/coco-framework) 保持独立，输出的是可读、
可修改并最终由业务项目接管的源码；[Coco Admin](https://github.com/patton174/coco-admin)
等应用只消费生成结果，不在运行时依赖生成器。

## 当前能力

本仓库提供可运行的 CRUD 源码生成入口，CLI 支持：

- `help`：显示可用命令；
- `list`：列出内置模板路线及其实现状态；
- `init <directory>`：安全创建初始 `coco-generate.yml`，目标文件已存在时拒绝覆盖。
- `plan <directory>`：只渲染并打印确定性 CRUD 目标，不写入文件；
- `generate <directory>`：以 `CREATE_NEW` 策略应用同一计划。

`crud` 已可执行；`admin-module`、`master-data`、`purchase`、`sales`、`inventory` 和
`finance` 仍是 metadata-only 路线。Maven 插件和 IDE 集成尚未实现；未实现路线不会创建空
Java 模块伪装成交付。

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

开发时也可以通过 Maven 调用同一个入口：

```bash
mvn -q compile exec:java '-Dexec.args=list'
```

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
| `coco.codegen.templateLocation` | 暂不支持外部模板根，只使用内置 `crud` 模板 |
| `coco.codegen.encoding` | 固定 UTF-8 |

升级到框架 3.0.0 的项目还需要从 `coco.features.disabled`、`coco.features.enabled` 和
`@CocoFeatures` 中删除 `codegen`；残留条目会让构建失败并给出指向本仓库的提示。

## 相关仓库

- [coco-framework](https://github.com/patton174/coco-framework)：运行时 Web 框架，自 3.0.0 起
  不再包含生成器，且不得反向依赖本仓库。
- [coco-admin](https://github.com/patton174/coco-admin)：可以接收生成源码，但不得嵌入生成器
  或形成运行时依赖。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
