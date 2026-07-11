# Coco Generate 产品边界与迁移规格

日期：2026-07-11

## 1. 定位

Coco Generate 是开发期源码生成器和模板平台，负责把显式配置、模板包和变量转换为可读的
业务源码。生成结果进入业务仓库后由业务团队接管，可以继续修改、重构和测试。

本产品不承担以下职责：

- 不在应用运行时动态暴露 Entity 或 CRUD Controller；
- 不通过数据库元数据驱动不可见的运行时业务行为；
- 不替业务项目决定领域模型、事务边界、权限模型或自定义查询；
- 不让 `coco-framework` 或 `coco-admin` 在运行时依赖生成器。

依赖方向必须保持为：迁移期 `coco-generate -> coco-feature-codegen`，以及产物层面的
`coco-admin -> generated source`。禁止出现 `coco-framework -> coco-generate`。

## 2. 入口模型

### 2.1 CLI

CLI 是独立、可脚本化的参考入口。初始基座只提供：

- `help`：展示真实可用命令；
- `list`：读取 classpath catalog 并列出模板路线元数据；
- `init <directory>`：使用安全默认值创建 `coco-generate.yml`。

未来的 `generate` 必须建立在稳定的应用服务接口上，并在实现模板渲染、计划预览、安全写入
与验证之后才能公开。当前版本不得把 `list` 中的 metadata-only 路线描述为可生成能力。

### 2.2 Maven 入口

未来独立的 `coco-generate-maven-plugin` 只做 Maven 参数适配、生命周期接入和错误映射，核心
生成逻辑由同一应用服务提供。默认 goal 必须显式调用，不得在普通编译中悄悄修改源码。

旧的 `coco-maven-plugin:coco:generate` 在兼容窗口内继续由 `coco-framework` 自己维护；它
不能通过新增依赖反向调用本仓库。

### 2.3 IDE 入口

IDE 集成只负责收集项目上下文、展示生成计划和调用同一应用服务。IDE 插件不得重新实现模板
引擎，不得绕过路径与覆盖保护，也不得把本地凭据写入项目配置。

## 3. 模板包

模板包采用显式资源结构：

```text
META-INF/coco-generate/templates/
  index.txt
  <template-id>/
    manifest.properties
    templates/                 # 路线实现后才允许出现
```

`index.txt` 给出确定性加载顺序。每个 manifest 至少包含：

- `manifestVersion`：manifest 协议版本；
- `id`：稳定的小写 kebab-case 路线标识；
- `displayName` 与 `description`：面向开发者的真实说明；
- `status`：`metadata-only` 或未来定义的可执行状态；
- `outputKind`：输出类型，初始固定为 `readable-source`；
- `ownership`：生成后所有权，固定为 `business-project`。

初始 catalog 包含 `crud`、`admin-module`、`master-data`、`purchase`、`sales`、
`inventory`、`finance`。目前只交付 manifest，不创建空 Java 模块来伪装生成能力。

外部模板包必须通过同一 manifest 校验。模板内容视为数据，默认禁止执行脚本、插件类、构建
命令或网络请求。未来若引入扩展代码，必须使用独立签名、来源信任和进程隔离方案，不得复用
普通模板包权限。

## 4. 变量模型

变量必须是有声明的结构化数据，而不是模板任意读取的环境映射。未来 manifest 为每个变量
声明名称、类型、是否必填、默认值、约束和说明。基础类型至少考虑：

- `string`、`boolean`、`integer`；
- `enum`；
- 受约束的 Java package、class name 与相对路径；
- 上述类型的列表和对象组合。

值的优先级固定为：显式 CLI/Maven/IDE 参数 > 项目配置 > 模板包默认值。合并后先完成类型
校验和 canonicalization，再计算生成计划。不得从环境变量隐式补全模板变量，不得把 token、
password、private key 等凭据写入 `coco-generate.yml`、生成计划、日志或源码。

## 5. 安全写入

写入分为 plan 与 apply 两个阶段。plan 包含每个目标相对路径、内容摘要、动作和冲突原因，且
不修改文件。apply 必须满足：

1. 输出根目录先转为规范绝对路径；所有目标必须是根目录内的规范相对路径。
2. 拒绝绝对模板路径、`..` 逃逸、NUL、设备名和指向根目录外的符号链接。
3. 默认只允许 `CREATE_NEW`；已有文件一律失败，并列出冲突。
4. 未来的覆盖能力必须由显式策略和逐文件 allowlist 控制，不允许全局静默覆盖。
5. 覆盖写入先写同目录临时文件、校验摘要，再原子移动；失败时不得留下半文件。
6. dry-run 与实际 apply 必须消费同一不可变计划，并在 apply 前重新检查目标状态。
7. 输出内容、日志和错误不得包含凭据。

当前 `init` 已实现上述边界的最小子集：只在指定目录创建固定名称的无凭据配置文件，使用
`CREATE_NEW` 防止检查与写入之间的覆盖竞态；目录参数规范化，已有配置时失败。

## 6. 分阶段迁移

### 阶段 0：冻结兼容基线

- 为 `coco-feature-codegen` 和现有 `coco:generate` 建立 golden fixture。
- 记录 1.0.2 的公开请求模型、模板变量、输出路径、覆盖语义和错误行为。
- 当前 framework 实现继续工作，不在这一步改变依赖方向。

### 阶段 1：前向过渡适配

- 本仓可以在独立 adapter 模块中临时依赖
  `io.github.patton174:coco-feature-codegen:1.0.2`。
- adapter 必须隐藏在本仓定义的 `GenerationEngine` 边界后，CLI、Maven 和 IDE 入口不得直接
  使用 framework 类型。
- 通过双跑 fixture 比较旧 goal 与新 CLI 的计划、路径和内容，差异必须显式记录。

### 阶段 2：模板与引擎独立

- 把通用变量、模板 manifest、渲染与安全写入能力迁入本仓自己的包名和版本契约。
- 用本仓实现替换 adapter，移除对 `coco-feature-codegen` 的依赖。
- `coco-framework` 保留冻结的兼容实现和必要修复，不新增对本仓的依赖。

### 阶段 3：入口迁移

- 发布独立 CLI 与 `coco-generate-maven-plugin`。
- 文档把新项目推荐为新应用入口，同时明确旧 `coco:generate` 的支持窗口。
- 现有用户可以继续使用旧 goal；迁移工具把旧参数转换为新配置，并输出可审查 diff。

### 阶段 4：兼容收敛

- 在已公告的主版本边界后，framework 可以停止增强旧生成器，但保留对应版本的可构建源码。
- 删除旧能力必须遵循 framework 自己的兼容政策，不能通过远程下载或运行时回调本仓实现。
- `coco-admin` 始终只提交和编译生成后的普通源码。

## 7. 兼容与验收

迁移阶段至少维护以下验收项：

- 旧 fixture 在声明兼容的输入上生成相同路径与语义等价源码；
- 非兼容变化有版本化迁移说明，不静默改变覆盖行为；
- CLI、Maven 和 IDE 对同一 canonical 请求生成相同计划；
- 重复执行默认不覆盖业务修改；
- 生成源码可以脱离 Coco Generate 单独编译和维护；
- framework、admin 与 generate 的依赖图满足本规格定义的单向边界。
