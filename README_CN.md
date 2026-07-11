# Coco Generate

[English](README.md) | 简体中文

Coco Generate 是 Coco 生态中的开发期源码生成器和模板包平台。它与
[Coco Framework](https://github.com/patton174/coco-framework) 保持独立，输出的是可读、
可修改并最终由业务项目接管的源码；[Coco Admin](https://github.com/patton174/coco-admin)
等应用只消费生成结果，不在运行时依赖生成器。

## 当前能力

本仓库目前是可运行的初始基座，CLI 已支持：

- `help`：显示可用命令；
- `list`：列出内置模板路线及其元数据；
- `init <directory>`：安全创建初始 `coco-generate.yml`，目标文件已存在时拒绝覆盖。

当前 catalog 仅包含 `crud`、`admin-module`、`master-data`、`purchase`、`sales`、
`inventory` 和 `finance` 的元数据。源码生成、Maven 插件和 IDE 集成尚未实现；这些路线
不是空 Java 模块，也不代表对应业务模板已经交付。

## 运行 CLI

需要 Maven 3.9+ 与 JDK 17 或更高版本。CI 使用 JDK 21 验证，产物保持 Java 17 字节码兼容。

```bash
mvn -B -ntp verify
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar help
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar list
java -jar target/coco-generate-0.1.0-SNAPSHOT.jar init ./example
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

## 相关仓库

- [coco-framework](https://github.com/patton174/coco-framework)：运行时 Web 框架，也是迁移期
  `coco-feature-codegen` 契约的来源；它不得反向依赖本仓库。
- [coco-admin](https://github.com/patton174/coco-admin)：可以接收生成源码，但不得嵌入生成器
  或形成运行时依赖。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
