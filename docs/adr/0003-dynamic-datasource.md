# ADR 0003：动态数据源、独立 HikariCP 与单写主权

## 状态与日期

- **状态**：Accepted
- **日期**：2026-07-17
- **追溯**：宪章原则 VIII「可观测、可恢复与安全迁移」与「当前交付与工程门禁」多数据源条款；`specs/002-pdp-product/persistence-design.md` 第 2、3、9 节；`specs/002-pdp-product/plan.md`「存储」「P1 MySQL 持久化与数据库独立边界」「关键设计规则」；FR-139～FR-150、FR-171～FR-172、FR-174。

## 背景

PDP 部署需要受控的多数据源路由，覆盖在线主库、可选只读副本、迁移源、迁移目标，以及独立的 Flowable 工作流引擎数据源。在线业务主库承担写命令、审批、权限判定、审计、事件发布与读后写一致性查询；只读副本只承载显式允许最终一致的查询；迁移源与目标仅在受批准的迁移计划期间加载，使用独立会话工厂与本地事务边界；Flowable Process Engine 拥有独立 schema、账号、连接池与事务管理器，不参与普通业务动态路由。

必须满足以下不可协商约束：

- **严格路由**：未知数据源键失败，不回退到默认库。
- **独立 HikariCP**：每个数据源使用独立连接池，独立容量、超时、keepalive、健康检查与指标。
- **独立凭据与预算**：每个数据源独立账号、独立连接预算。
- **禁止 XA**：不使用跨数据库两阶段提交；迁移以批次、检查点、幂等与核对保证可恢复性。
- **事务绑定路由**：数据源在事务入口确定，事务开始后切换必须失败。
- **单写主权**：每个 PDP 部署只有一个业务写入主库。
- **隔离持久化上下文与本地事务边界**：迁移源、迁移目标与在线主库使用独立 `SqlSessionFactory`、Mapper 扫描包、本地事务管理器、账号与连接池。
- 宪章原则 VIII 与「当前交付与工程门禁」明确要求：多数据源和迁移连接 MUST 使用严格路由、独立凭据、连接预算、健康检查和指标；迁移源、迁移目标与在线主库必须隔离持久化上下文和本地事务边界，禁止跨库两阶段提交和事务内切换数据源。

## 决策

### 1. 采用 dynamic-datasource-spring-boot4-starter

采用 `dynamic-datasource-spring-boot4-starter 4.5.0`，版本由项目 BOM 锁定，并通过 Spring Boot 4.1 兼容性验证。`dynamic-datasource` 负责数据源注册、严格键校验与在线主/读路由。

数据源键定义如下：

| 数据源键 | 用途 | 写入 | 生命周期 |
|---|---|---:|---|
| `pdpPrimary` | 在线业务、审计、事件发布、后台任务事实 | 是 | 必需，应用启动时建立 |
| `pdpRead` | 明确允许最终一致的查询 | 否 | 可选，只能与主库同引擎且来自受管复制 |
| `migrationSource` | 历史 MySQL 或数据库切换源 | 否 | 迁移计划批准后加载，使用独立只读会话工厂，结束后卸载并撤权 |
| `migrationTarget` | 数据库切换目标的预装载和核对 | 仅迁移执行器 | 使用独立目标会话工厂，开放业务写入前不得作为 `pdpPrimary` |
| `workflowEngine` | Flowable Process Engine 运行表、作业和历史 | 仅 `workflow` 模块 | 必需；使用 MySQL 8.4，但独立 schema/账号/池/事务管理器，不加入普通业务动态路由 |

### 2. 严格路由与主库绑定

- `strict=true`；找不到数据源键时失败，不回退到默认库。
- `primary=pdpPrimary`；未声明路由的业务调用始终访问主库。

### 3. `@DS` 使用边界

- `@DS` 只允许出现在 `persistence` 与 `datamigration` 基础设施实现中。
- Controller、领域服务、领域对象禁止使用 `@DS`，也不得引用 `BaseMapper`、Wrapper、JDBC 类型或连接池类型。

### 4. 事务绑定路由

- 数据源在事务入口确定。
- 事务开始后尝试切换数据源必须失败。

### 5. 路由优先级与读后写一致性

- 写命令、审批、权限判定、审计、事件发布以及读后写一致性查询 MUST 使用 `pdpPrimary`。
- `pdpRead` 只承载显式标注可陈旧的报表或列表，响应必须能提供数据截止时间；P1 默认核心查询仍访问主库。

### 6. 独立 HikariCP 池与连接预算

- 每个数据源使用独立 HikariCP 池，独立容量、超时、keepalive 与指标。
- 所有应用副本、Flowable 异步执行器、后台执行器与迁移任务的连接池上限之和不得超过数据库可用连接的 70%，其余连接留给高可用、维护和应急。
- 池大小、超时、keepalive 与 `maxLifetime` 必须经容量压测冻结，不按并发用户数线性放大。

### 7. 迁移源/目标的隔离与本地事务

- `migrationSource` 与 `migrationTarget` 使用独立 `SqlSessionFactory`、Mapper 扫描包、本地事务管理器、账号和 HikariCP 池。
- 业务 Mapper 不能注入迁移数据源，迁移 Mapper 不能使用业务管理权限。
- 源事务只读；目标批次只在目标本地事务内提交并保存检查点。
- 跨源/目标不启用分布式事务，不使用 XA。
- 数据源路由上下文必须在 `finally` 中清理；线程池任务显式传递或重建上下文，禁止 ThreadLocal 泄漏。

### 8. Flowable 工作流引擎数据源隔离

- `workflowEngine` 由平台 `workflow` 基础模块显式配置，不通过 `@DS` 选择，不加入普通业务动态路由。
- 业务 Mapper 不得访问 Flowable 表，Flowable 引擎也不得直接更新 PDP 业务表。
- Flowable 与业务事务不使用 XA；业务提交、outbox、流程启动/推进和回写均使用幂等键、关联标识和可补偿事件桥接。

### 9. 动态增删数据源

- 动态增加、删除数据源只能由受审计的迁移控制面执行，不提供普通业务 API。
- 生产环境不支持通过修改连接串切换到空库、旧快照或未核对目标。

## 替代方案

| 方案 | 结论 | 理由 |
|---|---|---|
| (a) 单一数据源，不引入只读副本 | 拒绝 | 实现简单，但放弃读扩展路径，无法承载百万级记录下的报表与列表读负载，也无法隔离迁移读写 |
| (b) 基于 Spring `AbstractRoutingDataSource` 自研实现 | 拒绝 | 需重新实现严格键校验、独立池装配、健康检查、指标暴露与事务绑定路由等 `dynamic-datasource` 已具备的能力，重复造轮子且增大故障面 |
| (c) XA/分布式事务 | 拒绝 | 性能损耗大、运维复杂，且宪章明确禁止跨库两阶段提交；迁移以批次、检查点、幂等和核对保证可恢复性 |
| (d) 迁移与业务共用 `SqlSessionFactory` | 拒绝 | 违反持久化上下文与事务边界隔离要求，导致事务混淆、Mapper 越权与回滚范围失控 |

## 质量属性影响

- **性能**：通过 `pdpRead` 承载陈旧容忍报表与列表，缓解主库读压力；在线主库连接预算不被报表流量挤占。
- **可靠性**：每数据源独立连接池，单一数据源故障（池耗尽、账号失效、网络分区）不会直接拖垮其他数据源；迁移源/目标故障不影响在线业务。
- **可观测性**：按数据源独立暴露 active、idle、pending、timeout、acquire duration、usage duration 与连接创建失败指标，支持从用户影响定位到责任数据源。
- **安全**：每数据源独立账号与凭据；迁移源只读、迁移目标仅迁移执行器可写、Flowable 表与业务表账号隔离，缩小越权面。

## 数据与迁移影响

- **单写主权**：每个 PDP 部署只有一个业务写入主库 `pdpPrimary`，迁移目标在 Go 决策完成前不得作为业务主库。
- **迁移隔离**：`migrationSource`/`migrationTarget` 独立会话工厂、Mapper 包、本地事务与连接池，源只读、目标批次本地提交，不使用 XA；切换仍执行全量、增量、冻结、核对和主权转换，不通过把 `pdpPrimary` 路由到 `migrationTarget` 直接完成。
- **Flowable schema 隔离**：Flowable 表与 PDP 业务表使用独立 schema/表前缀、账号、池与事务管理器，互不直接读写。

## HikariCP 基线

每个数据源使用独立 HikariCP 池。HikariCP 版本由 Spring Boot BOM 管理，不由业务模块单独覆盖。

| 参数 | 基线 |
|---|---|
| `poolName` | 包含部署、数据源键和实例标识 |
| `transactionIsolation` | `TRANSACTION_READ_COMMITTED` |
| `connectionTimeout` | 在线池初始 3 秒；迁移池 10 秒，压测后冻结 |
| `validationTimeout` | 不超过 2 秒且小于 `connectionTimeout` |
| `maxLifetime` | 小于数据库、代理或网络连接上限至少 30 秒 |
| `keepaliveTime` | 小于 `maxLifetime`，初始 120 秒 |
| `minimumIdle` | 在线主库默认等于 `maximumPoolSize`；迁移池可较小 |
| `maximumPoolSize` | 由数据库连接预算、应用副本数和压测确定，不按并发用户数线性放大 |
| `readOnly` | `pdpRead`、`migrationSource` 为 true；其他为 false |
| `initializationFailTimeout` | `pdpPrimary` 与 `workflowEngine` 必须启动失败即退出；临时迁移源由迁移作业报告失败 |
| `leakDetectionThreshold` | 非生产或故障诊断时启用，生产默认关闭 |

监控与探活约定：

- 必须按数据源监控 active、idle、pending、timeout、acquire duration、usage duration 和连接创建失败。
- 使用 JDBC4 `isValid()`，不配置数据库专用探活 SQL。
- 操作系统和驱动必须启用 TCP keepalive。

## 路由守卫

引入 `DataSourceRoutingGuard`，在路由决策点强制执行以下规则：

- 拒绝未知数据源键（`strict=true`，不回退默认库）。
- 拒绝跨特权路由（业务 Mapper 不得访问迁移数据源或 Flowable 表；迁移 Mapper 不得使用业务管理权限）。
- 拒绝事务内切换数据源（数据源在事务入口确定，事务开始后切换必须失败）。

## 验证方式

- **`DynamicDataSourceRoutingTest`**：覆盖严格路由、只读回退与跨特权路由阻断。
- **`HikariPoolResilienceTest`**：覆盖连接池耗尽、连接泄漏检测与数据库故障后的恢复。
- **`MigrationDataSourceIsolationTest`**：覆盖迁移源/目标独立会话工厂、本地事务边界、源只读、目标批次本地提交与上下文清理。

## 复审条件

- 容量压测冻结池大小、超时与 keepalive 后复审。
- 只读副本拓扑变化时复审。
- 迁移新增源类型（如新增历史数据库引擎或源结构）时复审。
- 每季度随宪章与运行手册例行复核。
