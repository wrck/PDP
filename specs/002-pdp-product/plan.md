# PDP 企业级项目交付管理平台实施计划

**分支**：`002-pdp-product` | **日期**：2026-07-16 | **规格**：[spec.md](spec.md)

**输入**：L0 产品规格 `specs/002-pdp-product/spec.md`

## 摘要

PDP 首期交付“平台基础能力 + 标准实施领域包”的可上线闭环，覆盖工作空间治理、领域包深度定制、项目模板与生命周期、计划执行、交付件、统一审批、风险问题变更、多视图、权限审计、高可用及现有 MySQL 历史数据迁移。技术上采用可水平扩展的模块化单体：以稳定核心模型承载跨领域统一语义，以元数据运行时承载字段、对象、页面、状态、规则和动作定制，以受治理扩展接口承载专业算法或设备能力；模块间通过显式 API 和事务事件协作，为后续按容量或组织边界拆分服务保留路径。持久化核心不绑定数据库产品，同一制品通过配置选择 PostgreSQL 18 或 MySQL 8.4 LTS；MySQL 历史数据先进入隔离暂存区，经版本化映射、全量加 CDC、业务核对和冻结切换后进入当前部署选定的业务主库。

本计划只承诺 P1。P2/P3 仅保留兼容边界和演进接口，不在首期实现资源、成本、移动离线、六类行业包全集、AI 等完整能力。

## 技术背景

**语言与版本**：后端 Java 21 LTS；前端 TypeScript 5.x、Vue 3.5；数据库基线 PostgreSQL 18、MySQL 8.4 LTS；历史数据迁移支持 MySQL 5.7/8.x 源；契约 YAML/JSON

**主要依赖**：Spring Boot 4.1、Spring Modulith 2.1、`spring-modulith-events-jdbc`、Spring Security、MyBatis-Plus 3.5.17 Spring Boot 4 Starter、dynamic-datasource 4.5.0 Spring Boot 4 Starter、MyBatis、HikariCP、Liquibase、Vue 3.5、Vite 8、Vue Router、Pinia、Ant Design Vue、TanStack Vue Query、JSON Schema

**存储**：每个部署从 PostgreSQL 18 或 MySQL 8.4 LTS 中选择一个业务事实真源；dynamic-datasource 受控路由 `pdpPrimary`、可选 `pdpRead`、`migrationSource` 和 `migrationTarget`，每个数据源使用独立 HikariCP 池；现有 MySQL 作为迁移期只读源；S3 兼容对象存储保存附件与交付件文件；Redis 仅用于缓存、限流和短期协调，不作为业务真源

**测试**：JUnit 5、Spring Modulith 模块测试、Testcontainers 数据库矩阵（PostgreSQL 18/MySQL 8.4）、动态数据源路由与 HikariCP 故障测试、Redis 与对象存储容器、ArchUnit、Vitest、Vue Test Utils、Testing Library for Vue、Playwright、迁移核对、契约校验与 k6 性能测试

**目标平台**：Linux 容器；开发环境支持 Docker Compose；生产环境支持 Kubernetes 兼容平台、托管 PostgreSQL 或 MySQL 以及 S3 兼容对象存储

**项目类型**：企业级 Web 应用，单仓库、前后端分离、后端模块化单体

**性能目标**：1000 名并发活跃用户；95% 常用页面在 2 秒内呈现可用结果；百万级记录下 95% 常用搜索和报表在 3 秒内返回或先呈现渐进结果

**约束**：核心业务可用性不低于 99.95%；RTO 不超过 30 分钟、RPO 不超过 5 分钟；权限撤销及时生效；核心事务不得依赖搜索、报表、AI 或非关键外部系统；MySQL 历史迁移必须可追溯、可核对、可重跑，并具有明确切换与回退边界；业务与领域代码不得依赖数据库专有能力

**规模与范围**：P1 覆盖 14 个业务与运行模块、1 个公共持久化基础模块、1 个标准实施领域包、1 条真实或等价项目端到端试点、1 套 MySQL 历史迁移与切换方案；预留多工作空间、百万级对象、三层领域包继承和外部集成扩展

## 宪章检查

当前 `.specify/memory/constitution.md` 仍是未填写模板，没有可执行项目宪章条款。规划期间采用仓库指南和 L0 规格作为临时门禁：

| 门禁 | 规划前检查 | Phase 1 后复核 |
|---|---|---|
| 所有文档为中文、UTF-8 | 通过 | 通过 |
| 规划必须追溯 L0 的 P1 范围，不以子域替代完整产品 | 通过 | 通过，模块与契约均以 P1 闭环为边界 |
| 平台核心与领域定制分离，领域包不得绕过权限和审计 | 通过 | 通过，采用核心模型、元数据运行时、受治理扩展三层边界 |
| 关键行为可测试、可审计、可恢复 | 通过 | 通过，数据模型、契约和快速验证均含相应场景 |
| 不引入无必要的分布式复杂度 | 通过 | 通过，P1 采用模块化单体与事务事件日志 |
| 高可用、性能和安全指标必须进入设计与验证 | 通过 | 通过，已进入部署、数据、契约和验证方案 |
| 数据库不绑定且认证实现行为一致 | 通过 | 通过，采用持久化端口、双数据库适配器和发布测试矩阵 |

不存在未解释的门禁失败。

## 架构与模块分解

### 运行时边界

1. **Web 前端**：提供平台管理、领域设计、项目工作区和统一待办入口。
2. **PDP 应用**：承载同步命令、查询、权限决策和核心事务；保持无状态以支持多副本。
3. **后台任务执行器**：与 PDP 应用共享模块和数据库，但以独立运行配置处理导入导出、索引、通知、迁移和集成重试。
4. **关系数据库适配层**：按部署配置连接 PostgreSQL 或 MySQL，保存核心数据、领域元数据、运行实例、审计和事务事件日志；每个部署仅允许一个写入主库。
5. **对象存储**：保存文件内容；数据库保存文件元数据、版本、哈希和授权关系。
6. **Redis**：缓存可重建数据、限流和短租约；失效时核心事务降级为直接访问数据库。
7. **迁移暂存区**：保存 MySQL 原始快照、增量位点、标准化中间数据、问题隔离和核对结果；只能由迁移服务访问，不向在线业务开放。

### 业务模块

| 模块 | P1 职责 | 主要追溯 |
|---|---|---|
| `identity` | 企业身份映射、会话、用户停用 | FR-063～FR-069 |
| `workspace` | 工作空间、组织、成员、角色、跨空间授权 | FR-003～FR-006、FR-121～FR-124 |
| `domainconfig` | 领域包、对象/字段/页面/状态/规则、版本、发布和迁移 | FR-007～FR-020、FR-130～FR-134 |
| `template` | 项目模板及批准版本 | FR-021～FR-022 |
| `project` | 项目、阶段、父子项目、顶层生命周期 | FR-023～FR-028、FR-117～FR-120 |
| `planning` | 任务、检查项、里程碑、依赖、基线、进度 | FR-029～FR-037、FR-125～FR-129 |
| `deliverable` | 交付件、版本、发布、签核和归档清单 | FR-038～FR-042 |
| `approval` | 统一审批定义、实例、节点、委托与回写 | FR-043～FR-046 |
| `governance` | 风险、问题、变更、审计、数据保留 | FR-047～FR-052、FR-069～FR-071 |
| `experience` | 列表、看板、日历、时间线、保存视图、通知 | FR-053～FR-062 |
| `standarddelivery` | 标准实施领域包及从创建到归档的闭环 | FR-094、FR-135～FR-138 |
| `integration` | 企业身份、对象存储、事件订阅、重试与健康度基础 | FR-088～FR-093 |
| `operations` | 可用性、性能、后台作业、备份恢复、降级和运行观测 | FR-102～FR-111 |
| `datamigration` | MySQL 盘点、映射、全量/增量迁移、隔离、核对、切换与旧系统退役 | FR-139～FR-150 |
| `persistence` | 数据库能力检测、公共持久化契约、PostgreSQL/MySQL 方言、变更集和数据库切换支持 | FR-151～FR-162 |

模块只能依赖其他模块公开的应用服务、只读查询接口或领域事件；禁止跨模块直接访问内部表和实现类。CI 使用 Spring Modulith/ArchUnit 校验依赖方向和循环引用。

### 多数据库持久化边界

- 领域层和应用层只依赖仓储、查询、锁、分页、搜索投影和事务端口，不引用数据库驱动、方言类或专有 SQL。
- `persistence-common` 维护逻辑类型、Mapper 端口、公共 SQL 片段、结果映射、查询契约和数据库能力模型；`persistence-postgresql` 与 `persistence-mysql` 只实现差异化 DDL、索引、查询和运维检查。
- 同一应用制品通过 `pdp.database.type=postgresql|mysql` 与连接配置选择数据库，启动时验证产品、版本、schema、字符集、排序规则、时区、事务引擎和必要权限。
- MyBatis-Plus 只在基础设施层提供单表 CRUD、分页、乐观锁和安全拦截能力；业务模块不得直接暴露或依赖 `BaseMapper`、`IService`、`QueryWrapper` 等框架类型。
- 公共查询优先使用参数化的标准 SQL；复杂查询使用显式 Mapper XML。数据库专用语句通过 MyBatis `databaseId` 或独立适配器 Mapper 选择，禁止在业务服务中按数据库类型拼接 SQL。
- dynamic-datasource 设置 `primary=pdpPrimary`、`strict=true`；`@DS` 只允许出现在持久化和迁移基础设施实现，事务开始后禁止切换路由。
- `pdpRead` 仅用于明确允许最终一致的查询；审批、权限、写命令、读后写和事件发布始终访问 `pdpPrimary`。迁移源/目标使用独立 Mapper、事务管理器和连接池。
- 每个数据源使用独立 HikariCP 池；在线池总连接预算不超过数据库可用连接的 70%，池大小、超时、keepalive 和 maxLifetime 必须经容量压测冻结。
- PostgreSQL 专有 `jsonb`/GIN/全文检索和 MySQL JSON/生成列/FULLTEXT 只能作为适配器内优化；权限、状态、审计和业务正确性必须由公共模型保证。
- Liquibase 变更集分为 `common`、`postgresql` 和 `mysql`，专用变更使用 `dbms` 条件并接受两种数据库的升级路径测试。
- 生产环境不支持通过修改连接串进行热切换。数据库切换使用全量、增量、核对、冻结和切换流程，且任一时刻只有一个数据库接受业务写入。
- PDP 到 PDP 的跨数据库切换使用数据库无关的标准导出模型和事务内迁移日志：全量快照记录日志位点，后续变更按全局位点幂等追平；原生 WAL/binlog 只作为适配器级加速或遗留源接入方式。

### MySQL 历史数据迁移边界

- 异构迁移工具只负责可靠提取和传输，不直接决定 PDP 业务语义。
- 原始 MySQL 数据先进入隔离的迁移暂存区，再由版本化映射转换为 PDP 命令或受控批量装载格式。
- 核心对象必须经过必填、唯一、关系、状态、权限和审计校验；无法映射的数据进入隔离区，不得静默丢弃。
- 全量迁移后通过 MySQL binlog 或等价变更源捕获增量；正式切换采用“冻结旧系统写入—应用最终增量—核对—启用 PDP—旧系统只读”的单向切换。
- 不采用长期双写。PDP 开始接收新写入后，回切旧系统必须先有经过验证的反向同步；否则采用前向修复。
- 详细持久化契约见 [persistence-design.md](persistence-design.md)，技术比较见 [technology-comparison.md](technology-comparison.md)，迁移执行方案见 [mysql-migration.md](mysql-migration.md)。

### 领域定制分层

```text
平台稳定内核
  └─ 平台标准包
      └─ 行业领域包
          └─ 工作空间客户包
```

- 稳定内核维护工作空间归属、统一标识、核心字段、权限、审计、版本和保留动作。
- 声明式元数据支持核心对象扩展与全新业务对象，并可定义字段、关系、页面、视图、状态、规则、动作、权限和集成映射。
- 专业算法、设备协议或高计算逻辑通过签名扩展包和受限服务接口接入，禁止访问内部表或提升当前用户权限。
- 运行实例绑定已发布领域包版本快照；升级通过预检、映射、分批迁移、报告和回滚完成。

## 实施阶段

### 阶段 A0：技术验证与迁移盘点

- 使用 Vue 完成领域设计器、动态表单、看板、时间线、虚拟表格和字段级权限的技术原型，验证复杂组件选型与性能预算。
- 固化 Vue Composition API、SFC、Pinia、服务器状态、路由、组件分层和主题扩展规范，禁止在主应用引入第二套前端框架。
- 完成 MySQL 版本、结构、存储引擎、字符集、时区、对象规模、数据质量、存储过程、触发器和附件存储盘点。
- 产出源到目标映射目录、数据质量基线、全量耗时基线、增量方案和切换窗口评估。
- 使用同一数据集完成 PostgreSQL 与 MySQL 的 schema 升级、持久化契约、核心查询、并发控制、性能和恢复验证，冻结首批认证版本及能力矩阵。
- 使用脱敏生产等价数据分别验证“历史 MySQL → PostgreSQL 目标”和“历史 MySQL → MySQL 目标”的迁移路径，由部署环境选择目标库，不再以代码改造切换数据库。

### 阶段 A：工程与治理基线

- 建立后端模块骨架、前端壳、MyBatis-Plus 基础设施层、dynamic-datasource 严格路由、HikariCP 独立连接池、公共持久化端口、PostgreSQL/MySQL Mapper 适配器、Liquibase 数据库变更、统一错误模型、API 版本和可观测性。
- 接入 OIDC 企业身份，完成工作空间、成员、角色、数据范围和审计骨架。
- 建立 CI 质量门禁、容器化本地环境、备份恢复与性能基线。

### 阶段 B：平台配置内核

- 完成核心字段目录、领域包三层继承、版本差异、冲突检测和职责分离发布。
- 完成对象、字段、关系、表单、页面、视图、状态机、规则、动作和权限元模型。
- 完成沙箱预览、场景测试、运行时快照和迁移作业。

### 阶段 C：项目执行内核

- 完成模板创建项目、统一生命周期、父子项目和跨工作空间协作。
- 完成任务、检查项、里程碑、依赖、计划基线、可解释进度和多视图。
- 完成交付件版本、审批签核、风险问题变更和归档门禁。

### 阶段 D：标准实施领域包

- 配置工前准备、施工计划、实施方案、部署、验收和归档子阶段。
- 补充标准对象、必需交付件、审批、完成规则、默认视图和角色权限。
- 使用真实或等价试点项目完成端到端验收，验证配置不修改平台核心。

### 阶段 E：上线硬化

- 完成三副本部署、故障转移、限流、降级、事件重试、备份和季度恢复演练。
- 在 PostgreSQL 与 MySQL 上分别完成 1000 并发与百万级数据性能测试、权限穿透测试、审计覆盖检查和数据库恢复演练。
- 完成至少两次 MySQL 生产等价全量迁移和彩排切换，验证增量追平、核对、冻结、启用、回退点和旧系统只读保留。
- 完成 PostgreSQL → MySQL 与 MySQL → PostgreSQL 双向生产等价数据库切换演练，验证标准迁移包、增量追平、业务语义一致性和单写主权。
- 建立发布、回滚、MySQL 历史迁移、领域包变更和运营值守手册。

## 关键设计规则

- 所有写命令必须携带工作空间上下文；跨空间访问必须命中有效协作授权。
- 高风险写操作使用幂等键；更新使用版本号或 `ETag/If-Match` 防止静默覆盖。
- 核心状态与合法性校验在同一数据库事务完成；通知、搜索投影和外部协同通过事务事件日志异步执行。
- 查询以权限过滤后的读模型为准；搜索索引、缓存和报表不得扩大数据库授权结果。
- 核心字段使用规范化列；扩展字段使用版本化逻辑 JSON 与受控索引投影，分别映射 PostgreSQL `jsonb` 和 MySQL `JSON`；新业务对象使用通用实例和关系模型。
- UUIDv7 由应用生成；金额和权重使用明确精度定点数；时间统一为 UTC；业务键在应用层规范化，避免依赖数据库默认排序规则和大小写语义。
- 事务隔离显式配置并以乐观锁 `revision` 保证并发语义；不得依赖不同数据库的默认隔离级别或隐式锁行为。
- 核心列表使用签名 keyset cursor；排序必须包含 UUIDv7 唯一兜底键，MyBatis-Plus `Page/IPage` 不得作为外部分页契约。
- 自定义更新必须在 SQL 中校验并递增 `revision`；影响行数为 0 时按权限语义返回 404 或 409。
- UUID、JSON、Instant、稳定枚举和值对象使用统一 TypeHandler；关闭 MyBatis 二级缓存和懒加载，设置 `localCacheScope=STATEMENT`。
- 唯一性、权限、流程条件和游标排序投影必须同事务更新；全文搜索和报表投影可异步，但必须提供数据截止时间。
- 搜索词项由平台统一分析器生成，数据库全文检索只作为候选加速，不能直接决定权限、最终结果集合或稳定排序。
- 事件发布采用 Spring Modulith JDBC 仓储，schema 由 Liquibase 管理，生产关闭自动建表。
- 所有 Mapper 方法必须显式表达工作空间和数据权限边界；MyBatis-Plus 租户插件不得替代项目、对象、字段和跨工作空间授权校验。
- 禁止在 P1 同时引入 Hibernate/JPA 作为第二套业务持久化框架，避免双持久化上下文、事务边界和实体状态语义分裂。
- 附件访问使用短时签名地址，下载前再次校验对象与字段权限。
- 删除默认采用归档/处置流程；交付件、审批、签核、配置和审计在保留期内不可物理删除。
- 迁移数据必须保留 `source_system`、`source_table`、`source_key`、`migration_run_id` 和 `mapping_version`；在线接口不得向普通用户暴露内部迁移控制字段。

## 项目结构

### 本特性文档

```text
specs/002-pdp-product/
├── spec.md
├── plan.md
├── research.md
├── technology-comparison.md
├── persistence-design.md
├── mysql-migration.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── README.md
│   ├── openapi.yaml
│   ├── domain-package.schema.json
│   └── events.md
└── tasks.md                 # 由 /speckit-tasks 后续生成
```

### 预期源代码结构

```text
backend/
├── pom.xml
├── src/main/java/com/pdp/
│   ├── identity/
│   ├── workspace/
│   ├── domainconfig/
│   ├── template/
│   ├── project/
│   ├── planning/
│   ├── deliverable/
│   ├── approval/
│   ├── governance/
│   ├── experience/
│   ├── standarddelivery/
│   ├── integration/
│   ├── operations/
│   ├── datamigration/
│   ├── persistence/
│   │   ├── common/          # TypeHandler、游标、数据源、连接池和能力检测
│   │   ├── postgresql/      # PostgreSQL 专用公共适配
│   │   └── mysql/           # MySQL 专用公共适配
│   └── shared/
├── src/main/resources/db/changelog/
│   ├── common/
│   ├── postgresql/
│   └── mysql/
├── src/main/resources/mapper/
│   ├── common/
│   ├── postgresql/
│   └── mysql/
└── src/test/

frontend/
├── src/app/
│   ├── router/
│   ├── stores/
│   └── providers/
├── src/features/
├── src/entities/
├── src/widgets/
├── src/shared/
│   ├── api/
│   ├── components/
│   ├── composables/
│   ├── schema-runtime/
│   └── theme/
└── tests/

tests/
├── contract/
├── e2e/
├── performance/
├── security/
└── recovery/

deploy/
├── compose/
├── k8s/
└── observability/
```

**结构决策**：后端先保持单一可部署应用，但按业务能力建立强模块边界；后台任务以同一制品的独立运行配置部署。业务模块定义持久化端口，MyBatis-Plus、Mapper XML、结果映射和数据库实现集中在 `persistence`，禁止把框架类型或 PostgreSQL/MySQL 分支散落在领域模块。Vue 前端按业务特性组织，页面组合进入 `widgets`，可复用业务模型进入 `entities`，跨特性组件、Composable 和元数据渲染运行时只能进入 `shared`。Pinia Store 只保存跨页面客户端状态，不缓存可由 TanStack Vue Query 管理的服务器事实。部署、端到端、性能和恢复测试独立于应用目录。

## 复杂度跟踪

无宪章违规。模块化单体、每部署单一写入主库、两种认证数据库适配器和标准 HTTP/事件契约是满足当前范围的最小可行复杂度；不实施运行时跨数据库分片、无迁移热切换或双主写入。消息代理、独立搜索集群和微服务拆分均延后到容量数据或组织边界证明必要时。
