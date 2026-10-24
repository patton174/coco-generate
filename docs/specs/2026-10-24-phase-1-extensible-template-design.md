# Phase 1：可扩展模板改造方案

- 日期：2026-10-24
- 状态：设计稿，关键决策已确认；未修改任何 Java 代码或模板
- 范围：外部模板包、自定义变量（完整类型系统）、模板组选择、CLI 参数
- 不在本期：模块骨架、多输出根、覆盖/增量策略、SPI、Maven 插件

## 1. 背景

当前 `coco-generate` 只有一条硬编码的 `crud` 路线：

- 模板根固定为 classpath `/META-INF/coco-generate/templates/crud/templates`
- 执行清单固定为 `crud/generation-manifest.properties`（Phase 1 迁移到 v2 `manifest.properties`）
- 输出根固定为 `<project>/src/main/java`
- 输出文件固定为 10 个，包段和后缀写死
- YAML key 白名单封闭，无法传入自定义变量
- `TemplateCatalog` 只服务于 `list`，`GenerationEngine` 不读 catalog
- 写入策略固定 `CREATE_NEW`

结果是：**能用，但不能按团队规范定制 Java 模板，也不能复用模板包。**

Phase 1 只解决“模板来源、模板组、变量、输出路径”四件事，并把内置 `crud` 统一迁移到 v2 清单。模块骨架和再生成策略放到后续阶段。

## 2. 目标

1. 项目可以指向一个外部模板目录（classpath 或本地文件系统）。
2. 项目可以选择模板组，资源可以覆盖模板组。
3. 模板可以声明自己的输出文件列表和输出路径。
4. 模板可以读取项目级、资源级自定义变量。
5. 内置 `crud` 迁移到 v2 清单，但模板内容与生成结果保持字节级兼容，现有配置零改动。
6. 保留现有安全模型：路径校验、全量预检、`CREATE_NEW`、失败回滚。
7. 不引入运行时依赖，不执行模板脚本。
8. 外部模板根完全替换内置根，不做“内置 + 外部覆盖”的叠加。
9. `vars` 使用完整类型系统，支持标量、枚举、列表、Map、对象和 `any`。

## 3. 非目标

- 不生成 Maven 模块骨架（`pom.xml`、`src/main/resources`、`src/test/java`、Flyway、README）。Phase 2。
- 不支持多个输出根。Phase 1 仍然只写 `src/main/java`。
- 不支持覆盖、跳过、增量、diff。Phase 1 仍然只 `CREATE_NEW`。Phase 3。
- 不引入 `ServiceLoader` / 插件 SPI。模板包是数据，不是代码。Phase 3。
- 不支持 Maven 插件、IDE 插件。
- 不支持任意 YAML schema；Phase 1 仍然围绕 CRUD 资源模型。完整类型系统只作用于 `vars`，不改变资源模型。
- 不支持模板根叠加（内置 + 外部覆盖）；`template.location` 是替换语义。Phase 2 再考虑。
- 不支持模板包签名、信任库、远程仓库。

## 4. 用户契约

### 4.1 YAML 配置

新增根级 `template` 和 `vars`，新增资源级 `template` 和 `vars`：

```yaml
template:
  location: builtin            # builtin | classpath:... | file:... | ./relative
  group: crud                  # 默认模板组
  encoding: UTF-8              # 模板文件编码，默认 UTF-8

vars:
  author: platform-team
  apiVersion: v1
  # 复杂值（list/map/object）也可写在这里，但必须先在模板包 manifest 中声明类型。

base-package: com.example.catalog

resources:
  - name: Product
    table: catalog_product
    template: company-crud     # 可选，覆盖根级 group
    vars:
      aggregate: Catalog
      softDelete: true
    api-path: /products
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

规则：

- `template`、`vars` 都是可选的；不写时行为与现在完全一致。
- `template.location` 默认 `builtin`。
- `template.group` 默认 `crud`。
- `template.encoding` 只影响模板文件读取；输出仍固定 UTF-8。
- `vars` 的 key 必须是合法标识符；不允许覆盖保留名。
- 资源级 `vars` 覆盖根级同名变量。
- 资源级 `template` 覆盖根级 `template.group`。
- `template.location` 是替换语义：只要不是 `builtin`，就完全不读内置模板。
- `vars` 的值按模板包声明的类型校验；完整类型规则见 4.5。
- 未知 key 仍然报错，避免拼写错误被静默忽略。

### 4.2 模板包目录

外部模板包与内置结构保持一致：

```text
<template-root>/
  index.txt
  <group>/
    manifest.properties
    templates/
      domain.java.ftl
      controller.java.ftl
      ...
```

- `index.txt`：每行一个 group id，`#` 开头为注释。必填，用于 `list`。
- `<group>/manifest.properties`：v2 唯一清单，同时提供 catalog 元数据和执行模板列表；内置 `crud` 与外部包使用同一格式。
- `templates/`：FreeMarker 模板文件，路径相对于 group 目录。
- group id 规则：`[a-z][a-z0-9-]{1,63}`。
- 模板文件路径必须相对、规范化，不能包含 `..`、绝对路径、盘符、NUL。
- 模板根会先规范化；文件模板根必须是普通目录，不能通过符号链接逃逸。

`template.location` 支持：

| 值 | 含义 |
| --- | --- |
| `builtin` | 内置 classpath 模板根 |
| `classpath:/path` | classpath 下的模板根 |
| `file:/abs/path` | 文件系统绝对路径 |
| `./relative` | 相对于项目目录的路径 |
| `/abs/path` | 文件系统绝对路径 |

拒绝 `http:`、`https:`、`jar:` 等其他 scheme。

`template.location` 是替换语义：选择一个根后，只从该根加载 `index.txt`、`manifest.properties` 和 `templates/`，不会回退到内置根。

### 4.3 manifest v2

所有模板包（含内置 `crud`）统一使用 `manifestVersion=2`：

```properties
manifestVersion=2
id=company-crud
displayName=Company CRUD
description=Company-standard CRUD source layout.
status=executable
outputKind=readable-source
ownership=business-project
packVersion=1.0.0

# 变量声明，可选。不声明时允许自由变量。
variable.count=2
variable.0.name=author
variable.0.type=string
variable.0.required=true
variable.0.description=Code author for the generated header.
variable.1.name=apiVersion
variable.1.type=enum
variable.1.values=v1,v2
variable.1.default=v1

# 输出文件列表
template.count=2
template.0.source=templates/domain.java.ftl
template.0.output=${crud.basePackagePath}/domain/${crud.resourcePackage}/${crud.resourceName}.java
template.0.kind=java
template.1.source=templates/controller.java.ftl
template.1.output=${crud.basePackagePath}/interfaces/rest/${crud.resourcePackage}/${crud.resourceName}Controller.java
template.1.kind=java
```

校验规则：

- `manifestVersion` 只接受 `2`。Phase 1 不读取 v1 清单；内置 `crud` 已迁移到 v2。
- `id` 必须等于 group 目录名。
- `status` 必须是 `executable` 才能用于 `plan/generate`；`metadata-only` 直接报错。
- `outputKind` Phase 1 只接受 `readable-source`。
- `ownership` Phase 1 只接受 `business-project`。
- `packVersion` 任意非空字符串；会显示在计划里。
- `template.count` 必须为正，最大 100。
- `variable.count` 最大 50。
- `template.N.source` 必须是 group 内相对路径。
- `template.N.output` 是相对输出根的路径，渲染后交给现有 `SafePaths` 校验。
- `template.N.kind` 可选：`java`、`resource`、`test`、`config`、`other`。Phase 1 仅作为元数据，不影响输出根。
- 清单拒绝未知 key。
- 不再提供 v1 兼容分支。当前没有公开的 v1 外部模板包；如后续发现，按附录 C 的规则迁移。

### 4.4 模板可用模型

每个资源渲染时提供以下模型：

```text
_coco:
  templateGroup       # 当前使用的 group
  templateLocation    # builtin / classpath:... / file:...
  packVersion         # 模板包版本
  outputRoot          # Phase 1 固定 src/main/java 的绝对路径

vars:                 # 合并后的自定义变量
  author: ...
  apiVersion: ...

crud:                 # 现有 CocoCrudSpec 模型，保持兼容
  basePackage
  basePackagePath
  resourceName
  resourceVariable
  resourcePackage
  tableName
  apiPath
  id
  fields
  allFields
  typeImports
  ...

resource:             # 原始资源声明
  name
  table
  apiPath
  template
  vars
```

现有内置模板继续使用 `${crud.*}`，不需要修改。自定义模板可以使用：

```ftl
// ${vars.author} - ${_coco.templateGroup} ${_coco.packVersion}
package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage};

public class ${crud.resourceName}Controller {
    private final ${crud.resourceName}ApplicationService service;
    ...
}
```

变量访问规则：

- 简单变量：`${vars.author}`。
- 带连字符的变量不建议使用；变量名限制为 Java 标识符形态 `[A-Za-z_][A-Za-z0-9_]*`。
- 列表/Map/对象变量按声明类型校验后传入；模板可用 FreeMarker 的 `<#list>` / hash 访问。
- `object` 是自由形态 Map，Phase 1 不做嵌套 schema 校验；需要强类型嵌套时用 `map<...>` 或 `list<...>` 组合。
- 保留名不能出现在 `vars` 中：`_coco`、`crud`、`resource`、`vars`、`templateGroup`、`targetPackage`、`basePackage`、`basePackagePath`、`resourceName`、`resourceVariable`、`resourcePackage`、`tableName`、`apiPath`。

### 4.5 变量声明与优先级

变量合并优先级从低到高：

```text
manifest 默认值
  < 根级 vars
  < 资源级 vars
  < CLI --var
```

声明类型（完整类型系统）：

```text
type    := scalar | list | map | object
scalar  := string | boolean | integer | enum | any
list    := "list<" type ">"
map     := "map<" type ">"        # key 固定为 string
object  := "object"                # 自由形态 Map，Phase 1 不做嵌套 schema
```

| type | 说明 | 示例值 |
| --- | --- | --- |
| `string` | 字符串 | `platform-team` |
| `boolean` | 布尔 | `true` |
| `integer` | 整数（Java `long` 范围） | `42` |
| `enum` | 枚举，必须提供 `values` | `v1` |
| `any` | 原样传递，不校验 | 任意 YAML 值 |
| `list<T>` | 同类型列表，元素递归校验 | `[catalog, product]` |
| `map<T>` | key 为 string、value 同类型的 Map | `{team: platform}` |
| `object` | 自由形态 Map，值为任意 YAML 映射 | `{team: platform}` |

约束：

- 类型最多嵌套 5 层；超过报错。
- `list` 的元素类型和 `map` 的值类型可以是任意上述类型，包括嵌套的 `list` / `map`。
- `map` 的 key 固定为字符串；不支持非字符串 key。
- `enum` 只允许作为顶层标量；`list<enum>` 在 Phase 1 不支持。需要枚举列表时用 `list<string>` 并在模板侧约束。
- `object` 不做嵌套 schema 校验；Phase 1 只要求值是 YAML 映射。
- `any` 是显式逃逸口，声明后不做递归校验。
- manifest 的 `default` 只支持 `string`、`boolean`、`integer`、`enum` 标量；`list` / `map` / `object` 的默认值写在 YAML `vars` 中。
- CLI `--var` 只接受标量字符串，再按声明类型解析；复杂值必须写在 YAML `vars` 中。
- 类型解析失败、元素类型不匹配、Map value 类型不匹配、嵌套超限 → 统一报错，不静默转换。

复杂变量声明示例：

```properties
variable.count=3
variable.0.name=author
variable.0.type=string
variable.0.required=true
variable.1.name=imports
variable.1.type=list<string>
variable.2.name=annotations
variable.2.type=map<string>
```

对应 YAML：

```yaml
vars:
  author: platform-team
  imports:
    - java.util.List
    - java.util.Optional
  annotations:
    lombok: "true"
```

规则：

- 如果 `variable.count > 0`，`vars` 中未声明的 key 报错。
- 如果 `variable.count` 缺失或为 0，允许自由变量。
- `required=true` 且合并后没有值 → 报错。
- 类型不匹配 → 报错；`list` / `map` 递归校验每个元素。
- `enum` 值不在 `values` 中 → 报错。
- 变量值不允许从环境变量隐式补全。
- 变量中不得写入 token、password、private key 等凭据；文档明确禁止。

### 4.6 CLI

现有命令保持兼容：

```text
coco-generate help
coco-generate list
coco-generate init <directory>
coco-generate plan <project-directory>
coco-generate generate <project-directory>
```

Phase 1 增加可选参数：

```text
coco-generate plan     [--config <file>] [--template-root <path>] [--template <group>] [--var name=value]... <project-directory>
coco-generate generate [--config <file>] [--template-root <path>] [--template <group>] [--var name=value]... <project-directory>
coco-generate list     [--template-root <path>] [<project-directory>]
```

- `--config` 相对路径按项目目录解析。
- `--template-root` 覆盖 `template.location`。
- `--template` 覆盖根级 `template.group`。
- `--var` 可重复；只支持标量值（`string` / `boolean` / `integer` / `enum`），按声明类型解析；`list` / `map` / `object` 必须写在 YAML `vars` 中。
- `list` 不带参数时只列内置路线；带项目目录时同时列外部模板包。
- 现有 `plan <dir>` / `generate <dir>` 行为不变。
- 不增加 `--force`、`--overwrite`、`--output`。这些属于后续阶段。

计划输出增加模板来源信息：

```text
Template root: file:/path/to/project/.coco/templates
Default group: company-crud (packVersion 1.0.0)
Output: /path/to/project/src/main/java
CREATE_NEW com/example/catalog/domain/product/Product.java <sha256> [company-crud]
...
```

退出码保持：

| 退出码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 2 | 用法错误、YAML 校验失败、变量校验失败、模板包校验失败 |
| 3 | 输出文件冲突 |
| 4 | IO 错误：配置缺失、模板不可读、写入失败 |

## 5. 内部设计

### 5.1 包结构

新增 `io.github.coco.generate.template`：

```text
template/
  TemplateLocation              # builtin / classpath / filesystem
  TemplateResourceRoot          # 读模板字节
  ClasspathTemplateResourceRoot
  FileSystemTemplateResourceRoot
  TemplatePackManifest          # v2 清单
  TemplateDefinition
  TemplateVariable
  TemplateVariableType
  TemplateTypeParser            # 解析 list<T> / map<T> / object
  VariableTypeValidator         # 递归校验
  TemplatePackLoader
  TemplatePack                  # manifest + resource root + FreeMarker 配置
  TemplateRenderer              # 渲染输出路径和模板内容
  RenderedTemplateFile
  VariableResolver
  TemplatePackException
```

新增 `io.github.coco.generate.core`：

```text
core/
  SafePaths                     # 从 engine 包移出，公开给 template 使用
  GenerationException           # 从 engine 包移出
```

这样 `template` 不依赖 `engine`，避免包循环：

```text
cli -> engine -> template
cli -> catalog -> template
template -> core
engine -> core
```

### 5.2 关键接口（示意，不是实现）

```java
public interface TemplateResourceRoot {
    byte[] read(String relativePath) throws IOException;
    boolean exists(String relativePath);
    String describe();
}

public record TemplatePackManifest(
        int manifestVersion,
        String id,
        String displayName,
        String description,
        String status,
        String outputKind,
        String ownership,
        String packVersion,
        List<TemplateVariable> variables,
        List<TemplateDefinition> templates) {
}

public record TemplateDefinition(String source, String output, String kind) {
}

public record TemplateVariable(
        String name,
        TemplateVariableType type,
        boolean required,
        Object defaultValue,
        List<String> enumValues,
        String description) {
}

public sealed interface TemplateVariableType {
    record Scalar(ScalarType type) implements TemplateVariableType {
    }

    record ListOf(TemplateVariableType element) implements TemplateVariableType {
    }

    record MapOf(TemplateVariableType value) implements TemplateVariableType {
    }

    record ObjectType() implements TemplateVariableType {
    }
}

public enum ScalarType {
    STRING, BOOLEAN, INTEGER, ENUM, ANY
}

public final class TemplatePackLoader {
    public TemplatePack load(TemplateResourceRoot root, String group);
    public List<String> listGroups(TemplateResourceRoot root);
}

public final class TemplateRenderer {
    public List<RenderedTemplateFile> render(TemplatePack pack, Map<String, Object> model);
}

public record RenderedTemplateFile(
        String relativePath,
        String content,
        String templateGroup,
        String kind) {
}
```

### 5.3 数据流

```text
coco-generate.yml
  │ CrudYamlParser
  ▼
CrudYamlSpec（新增 template / vars）
  │ 解析 template.location / group / vars
  ▼
TemplateResourceRoot
  │ TemplatePackLoader
  ▼
TemplatePack（manifest + FreeMarker 配置）
  │
  ├─ 对每个 resource：
  │    CocoCrudSpec -> crud 模型
  │    VariableResolver -> 合并 vars
  │    VariableTypeValidator -> 递归校验
  │    TemplateModelFactory -> {_coco, vars, crud, resource}
  │    TemplateRenderer -> RenderedTemplateFile
  ▼
GenerationEngine
  │ SafePaths 校验输出路径 + SHA-256
  ▼
GenerationPlan
  │ GenerationPlanApplier（保持现有 CREATE_NEW / 预检 / 回滚）
  ▼
目标工程
```

### 5.4 与现有代码的衔接

| 现有类 | 改动 |
| --- | --- |
| `CrudYamlSpec` | 增加 `TemplateSpec`、根级/资源级 `vars`（值可为完整类型） |
| `CrudYamlParser` | 增加 `template`、`vars` key；校验 group、编码、保留名 |
| `CocoCrudSpec` | 暴露 `templateModel()` 给模型工厂；模型内容不变 |
| `GenerationEngine` | 移除硬编码 `TEMPLATE_ROOT` / `"crud"`；读取 v2 `manifest.properties` 并加载 `TemplatePack`；输出根仍固定 |
| `GenerationPlan` | 增加模板来源、group、packVersion 元数据；`PlannedFile` 增加 group/kind |
| `GenerationPlanApplier` | 不改；继续 `CREATE_NEW` |
| `GenerationRequest` | 保留；CRUD 模型仍通过它暴露，或改为内部模型 |
| `CocoGenerateCli` | 增加可选参数；`list` 支持项目目录 |
| `TemplateCatalog` | 改为读取 v2 `manifest.properties`，与执行清单共用解析；增加外部模板包列表能力 |
| `ProjectInitializer` | 可选：初始配置增加 `template`/`vars` 注释 |
| `SafePaths` / `GenerationException` | 移到 `core` 包；行为不变 |
| `TemplateManifest` | 保留为 catalog 投影；由 v2 `TemplatePackManifest` 映射，内置与外部一致 |

### 5.5 内置 `crud` 统一到 v2

Phase 1 直接把内置 `crud` 迁移到 v2，不再维护适配器：

- `crud/manifest.properties` 升级为唯一的 v2 清单，包含：
  - 原有 catalog 元数据：`id`、`displayName`、`description`、`status`、`outputKind`、`ownership`；
  - 新增 `manifestVersion=2`、`packVersion=builtin-1`、`variable.count=0`；
  - 10 个 `template.N.source/output` 条目，`source` 统一写成 `templates/...`。
- 删除 `crud/generation-manifest.properties`；`GenerationEngine` 改为读取 `crud/manifest.properties`。
- `TemplateCatalog` 与 `TemplatePackLoader` 共用同一个 v2 解析器，避免 catalog 和执行清单两套格式。
- 10 个 `.ftl` 文件不改；golden 输出必须字节级一致。
- `index.txt` 不变。

这样 Phase 1 只有一种清单格式，外部包和内置包走同一条加载路径。代价是内置资源的清单文件需要一次性迁移，以及 `TemplateCatalogTest` 需要按 v2 断言。

### 5.6 安全

Phase 1 的外部模板是本地/公司受控文件，但必须保留以下防线：

1. 模板路径：
   - 只允许相对路径。
   - 拒绝 `..`、绝对路径、盘符、NUL、Windows 设备名。
   - 每个模板路径解析后必须仍在模板根内。
   - 文件模板根必须是普通目录；模板文件不能通过符号链接逃出根目录。
2. FreeMarker：
   - `setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER)`，禁用 `?new`。
   - `setAPIBuiltinEnabled(false)`，禁用 `?api`。
   - `RETHROW_HANDLER`，不吞模板异常。
   - 不启用本地化查找。
   - 模型只包含 Map/List/标量，不暴露文件系统、ClassLoader、Runtime。
3. 输出路径：
   - 继续使用 `SafePaths.normalize`。
   - 继续使用 `SafeFileSystem.verifyProjectTo`。
   - 继续全量预检、`CREATE_NEW`、失败回滚。
4. 资源上限：
   - 单个模板文件最大 1 MiB。
   - 单个模板包最多 100 个模板、50 个变量。
   - 变量类型嵌套深度最大 5。
   - 单个 `list` / `map` / `object` 最多 10,000 个直接子元素。
   - 输出文件数量沿用模板数量上限。
5. 信任模型：
   - Phase 1 不支持不可信模板包。
   - FreeMarker 模板仍可能通过循环消耗 CPU；不能在沙箱中运行。
   - 文档明确：只加载项目仓库内或公司受控的模板包。
   - 不执行脚本、不访问网络、不读取环境变量、不写入凭据。

### 5.7 确定性

- 资源顺序保持 YAML 顺序。
- 变量合并顺序固定：manifest 默认值 → 根级 → 资源级 → CLI。
- 模板内的 `vars` Map 使用 `LinkedHashMap`，保持插入顺序。
- 输出路径渲染后统一用 `SafePaths` 规范化。
- 每个文件计算 SHA-256 写入计划。
- 同一模板包、同一 YAML、同一变量 → 相同输出。

## 6. 文件改动清单

### 新增

```text
src/main/java/io/github/coco/generate/core/SafePaths.java
src/main/java/io/github/coco/generate/core/GenerationException.java
src/main/java/io/github/coco/generate/template/TemplateLocation.java
src/main/java/io/github/coco/generate/template/TemplateResourceRoot.java
src/main/java/io/github/coco/generate/template/ClasspathTemplateResourceRoot.java
src/main/java/io/github/coco/generate/template/FileSystemTemplateResourceRoot.java
src/main/java/io/github/coco/generate/template/TemplatePackManifest.java
src/main/java/io/github/coco/generate/template/TemplateDefinition.java
src/main/java/io/github/coco/generate/template/TemplateVariable.java
src/main/java/io/github/coco/generate/template/TemplateVariableType.java
src/main/java/io/github/coco/generate/template/TemplateTypeParser.java
src/main/java/io/github/coco/generate/template/VariableTypeValidator.java
src/main/java/io/github/coco/generate/template/TemplatePackLoader.java
src/main/java/io/github/coco/generate/template/TemplatePack.java
src/main/java/io/github/coco/generate/template/TemplateRenderer.java
src/main/java/io/github/coco/generate/template/RenderedTemplateFile.java
src/main/java/io/github/coco/generate/template/VariableResolver.java
src/main/java/io/github/coco/generate/template/TemplatePackException.java
```

### 修改

```text
src/main/java/io/github/coco/generate/config/CrudYamlSpec.java
src/main/java/io/github/coco/generate/config/CrudYamlParser.java
src/main/java/io/github/coco/generate/crud/CocoCrudSpec.java
src/main/java/io/github/coco/generate/engine/GenerationEngine.java
src/main/java/io/github/coco/generate/engine/GenerationPlan.java
src/main/java/io/github/coco/generate/cli/CocoGenerateCli.java
src/main/java/io/github/coco/generate/catalog/TemplateCatalog.java
src/main/java/io/github/coco/generate/catalog/TemplateManifest.java
src/main/java/io/github/coco/generate/init/ProjectInitializer.java   # 可选
src/main/resources/META-INF/coco-generate/templates/crud/manifest.properties
README.md
README_CN.md
AGENTS.md
```

### 删除

```text
src/main/resources/META-INF/coco-generate/templates/crud/generation-manifest.properties
```

### 测试新增

```text
src/test/java/io/github/coco/generate/template/TemplatePackLoaderTest.java
src/test/java/io/github/coco/generate/template/TemplateRendererTest.java
src/test/java/io/github/coco/generate/template/VariableResolverTest.java
src/test/java/io/github/coco/generate/template/TemplateTypeParserTest.java
src/test/java/io/github/coco/generate/template/VariableTypeValidatorTest.java
src/test/java/io/github/coco/generate/template/TemplateResourceRootTest.java
src/test/java/io/github/coco/generate/engine/GenerationEngineCustomTemplateTest.java
src/test/java/io/github/coco/generate/cli/CocoGenerateCliOptionsTest.java
src/test/resources/templates/company-crud/index.txt
src/test/resources/templates/company-crud/manifest.properties
src/test/resources/templates/company-crud/templates/*.ftl
```

## 7. 测试计划

### 单元

- `TemplateLocation`：builtin / classpath / file / relative / 拒绝 http。
- `TemplateResourceRoot`：正常读取、缺失文件、路径逃逸、符号链接逃逸。
- `TemplatePackManifest`：v2、必填字段、未知 key、数量上限、status；v1 清单被拒绝。
- `VariableResolver`：优先级、required、标量、`list<T>`、`map<T>`、`object`、`any`、嵌套、深度上限、未知变量、保留名。
- `TemplateTypeParser` / `VariableTypeValidator`：合法类型、非法类型、嵌套超限、元素/value 类型不匹配。
- `TemplateRenderer`：输出路径渲染、模板渲染、重复输出、模板异常、`?new`/`?api` 被阻止。

### 集成

- 外部模板包：`plan/generate` 生成 2 个自定义文件。
- 资源级 `template` 覆盖根级 group。
- 根级/资源级/CLI 变量合并。
- 复杂变量（`list<string>`、`map<string>`、`list<map<string>>`）端到端渲染。
- 缺少必填变量、枚举非法、未知变量。
- 模板路径逃逸、输出路径逃逸、符号链接。
- 目标文件冲突时整批失败，不写任何文件。
- 第二次 `generate` 仍然退出码 3。

### 回归

- 内置 `crud` golden fixture 字节级一致。
- 内置 `crud` 的 `manifest.properties` 为 v2，且 `generation-manifest.properties` 已删除。
- `TemplateCatalog` 与 `GenerationEngine` 读取同一份 v2 清单，元数据一致。
- 现有 `CocoGenerateCliTest`、`GenerationEngineTest`、`ProjectInitializerTest`、`TemplateCatalogTest` 全部通过。
- `coco-codegen.yml` 兼容读取。
- `plan <dir>` / `generate <dir>` 旧调用方式不变。
- `list` 无参数输出不变。

### 安全

- `?new` 模板报错。
- `?api` 模板报错。
- `../` 模板路径报错。
- 输出路径 `../../` 报错。
- 模板根外符号链接报错。

## 8. 实施步骤与工作量

| 步骤 | 内容 | 预估 |
| --- | --- | --- |
| 1 | `core` 包迁移（`SafePaths`、`GenerationException`），保持行为不变 | 0.5 天 |
| 2 | YAML 增加 `template` / `vars`，parser 校验 group、编码、保留名 | 1 天 |
| 3 | `TemplateResourceRoot` + classpath/file 实现 + 路径安全 | 1 天 |
| 4 | v2 清单统一、内置 `crud` 迁移、`TemplateCatalog` 共用解析 | 1.5 天 |
| 5 | `TemplatePack` / `TemplateRenderer` 替换硬编码渲染 | 1 天 |
| 6 | 完整变量类型系统：类型解析、递归校验、模型合并 | 1.5–2 天 |
| 7 | 资源级 group、计划元数据、CLI 参数、`list` 扩展 | 1 天 |
| 8 | 安全加固、测试、文档、示例模板包 | 1 天 |
| 合计 | | 约 7–9 个工程日 |

建议按以下顺序合并 PR：

1. `core` 迁移 + 无行为变化（golden 通过）
2. v2 清单统一 + 内置 `crud` 迁移 + `TemplateCatalog` 共用解析
3. 模板资源根 + 外部模板包加载
4. 渲染器替换 + 外部模板包端到端
5. 完整变量类型系统
6. 资源级 group + CLI + 安全加固 + 文档

## 9. 验收标准

满足以下条件即可认为 Phase 1 完成：

- [ ] 不写任何 `template` / `vars` 的旧配置，生成结果与现在字节级一致。
- [ ] 内置 `crud` 的 `manifest.properties` 为 v2，`generation-manifest.properties` 已删除，golden fixture 不变。
- [ ] `TemplateCatalog` 与 `GenerationEngine` 读取同一份 v2 清单。
- [ ] 在项目中创建 `.coco/templates/company-crud/`，配置 `template.location` 和 `template.group` 后，`plan` 能看到该模板包定义的文件，`generate` 能写出这些文件。
- [ ] 外部 `template.location` 完全替换内置根，不会混入内置模板。
- [ ] 资源级 `template` 可以切换模板组。
- [ ] 模板可以读取 `${vars.*}`、`${_coco.*}`、`${crud.*}`、`${resource.*}`。
- [ ] `vars` 支持 `string` / `boolean` / `integer` / `enum` / `any` / `list<T>` / `map<T>` / `object`，并递归校验。
- [ ] 变量优先级、required、enum、unknown、类型不匹配、嵌套超限校验按本文档工作。
- [ ] CLI `--var` 对标量生效；复杂类型明确报错并提示写入 YAML。
- [ ] 模板路径无法逃逸模板根；输出路径无法逃逸项目目录。
- [ ] `?new` / `?api` 被禁用。
- [ ] `mvn -B -ntp verify` 全绿。
- [ ] README / AGENTS 有外部模板包的使用说明和最小示例。

## 10. 已确认决策

以下决策已在 2026-10-24 确认，后续实现按此执行：

1. **外部模板根：替换。**
   - `template.location` 指向哪个根，就只从该根加载；`builtin` 是默认根。
   - 不做“内置 + 外部覆盖”的叠加；叠加留到 Phase 2。
   - 影响：`TemplateResourceRoot` 只需要一个活动根，不需要 overlay 链。

2. **内置 `crud`：本期统一 v2。**
   - `crud/manifest.properties` 升级为唯一 v2 清单；删除 `crud/generation-manifest.properties`。
   - `TemplateCatalog` 与 `GenerationEngine` 共用 v2 解析器。
   - 10 个 `.ftl` 和 golden 输出保持不变。
   - 影响：多约 1–1.5 天迁移和测试；Phase 1 只有一种清单格式。

3. **`vars`：完整类型系统。**
   - 支持 `string`、`boolean`、`integer`、`enum`、`any`、`list<T>`、`map<T>`、`object`。
   - 递归校验，类型嵌套最大 5 层；`map` key 固定为 string；`object` 不做嵌套 schema。
   - `enum` 仅顶层；`list<enum>` 不在 Phase 1。
   - CLI `--var` 只支持标量；复杂值写 YAML。
   - 影响：多约 1.5–2 天类型解析、校验和测试。

4. **CLI 参数：本期包含。**
   - `--config`、`--template-root`、`--template`、`--var`。
   - 不接受 `--force` / `--overwrite` / `--output`；这些留到后续阶段。

5. **资源级模板组：本期包含。**
   - 资源级 `template` 覆盖根级 `template.group`。
   - 一个项目可以同时使用多个模板组；每个资源独立加载对应 group。
   - 影响：`TemplatePackLoader` 需要按 group 缓存/加载；计划中每个文件记录来源 group。

**与初版设计稿的差异：**

- 初版建议“内置 `crud` 用适配器”，现改为“统一 v2”；工作量 +1–1.5 天。
- 初版建议“`vars` 只做标量 + `any`”，现改为“完整类型系统”；工作量 +1.5–2 天。
- 初版估算 5–6 天，修订为 7–9 天。

## 附录 A：最小外部模板包示例

目录：

```text
.coco/templates/
  index.txt
  company-crud/
    manifest.properties
    templates/
      domain.java.ftl
      controller.java.ftl
```

`index.txt`：

```text
company-crud
```

`manifest.properties`：

```properties
manifestVersion=2
id=company-crud
displayName=Company CRUD
description=Company-standard CRUD layout.
status=executable
outputKind=readable-source
ownership=business-project
packVersion=1.0.0

variable.count=1
variable.0.name=author
variable.0.type=string
variable.0.required=true

template.count=2
template.0.source=templates/domain.java.ftl
template.0.output=${crud.basePackagePath}/domain/${crud.resourcePackage}/${crud.resourceName}.java
template.0.kind=java
template.1.source=templates/controller.java.ftl
template.1.output=${crud.basePackagePath}/interfaces/rest/${crud.resourcePackage}/${crud.resourceName}Controller.java
template.1.kind=java
```

`templates/domain.java.ftl`：

```ftl
package ${crud.basePackage}.domain.${crud.resourcePackage};

/**
 * ${crud.resourceName} domain model.
 * @author ${vars.author}
 */
public record ${crud.resourceName}(
<#list crud.allFields as field>
        ${field.javaType} ${field.name}<#if field_has_next>,</#if>
</#list>
) {
}
```

`coco-generate.yml`：

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

运行：

```bash
coco-generate plan /path/to/project
coco-generate generate /path/to/project
```

## 附录 B：与 2.x 旧 codegen 的对应关系

| 2.x 旧能力 | Phase 1 对应 |
| --- | --- |
| `CocoCodeGenerator` 可替换 SPI | Phase 1 不做；模板包是数据，不是代码 |
| `templateLocation` 支持 classpath/file/普通路径 | `template.location` |
| 任意 `templateGroup` | `template.group` + 资源级 `template` |
| `CocoCodegenRequest.attributes` | 根级/资源级 `vars` + CLI `--var` |
| `template.N.source/output` | v2 清单的 `template.N.source/output` |
| `overwrite` / `dryRun` | Phase 1 只保留 `plan`（dry-run）；`overwrite` 放 Phase 3 |
| `encoding` 可配 | `template.encoding`；输出仍 UTF-8 |
| `outputDirectory` 可配 | 仍固定 `src/main/java`；Phase 2 再放开 |

Phase 1 的目标不是把 2.x 的 Spring 运行时模型搬回来，而是把**与运行时无关的模板扩展能力**搬到独立 CLI。

Phase 1 只接受 v2 清单；如果从旧格式迁移，按附录 C。

## 附录 C：v1 清单迁移到 v2

如果存在 Phase 1 之前的 v1 外部模板包，按以下方式迁移：

| v1 | v2 |
| --- | --- |
| `group=...` | `id=...`，且必须与目录名一致 |
| 无元数据 | 补 `displayName`、`description`、`status=executable`、`outputKind=readable-source`、`ownership=business-project` |
| 无版本 | 补 `packVersion` |
| 无变量 | 可选补 `variable.count` |
| `template.N.source=foo.ftl`（相对 `templates/`） | `template.N.source=templates/foo.ftl`（相对 group 根） |
| `template.N.output=...` | 不变 |

迁移后把文件命名为 `<group>/manifest.properties`，删除旧的 `generation-manifest.properties`。Phase 1 不提供自动迁移命令；当前也没有公开的 v1 外部模板包，默认按“无历史包袱”处理。
