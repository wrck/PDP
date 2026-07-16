# PDP P1 持久化与动态数据源设计

## 1. 设计目标

本设计落实宪章原则 V、FR-139～FR-150、FR-171～FR-172、FR-174，统一 P1 MySQL 8.4、MyBatis-Plus、动态数据源、
平台工作流数据源、连接池、游标分页、并发控制、类型映射、索引投影、搜索和事务事件语义。

核心边界：

- 每个 PDP 部署只有一个业务写入主库。
- 动态数据源用于受控访问业务主库、可选只读副本和迁移源/目标，不用于把上线切换伪装成运行时连接切换。
- 业务模块依赖仓储与查询端口，不依赖 `@DS`、`BaseMapper`、Wrapper、JDBC 类型或连接池类型。
- 不使用 XA/Seata。跨数据库迁移以批次、检查点、幂等和核对保证可恢复性。

## 2. 动态数据源拓扑

首个验证基线采用 `dynamic-datasource-spring-boot4-starter 4.5.0`，正式版本由项目 BOM 锁定并通过 Spring Boot 4.1 兼容测试。

| 数据源键 | 用途 | 写入 | 生命周期 |
|---|---|---:|---|
| `pdpPrimary` | 在线业务、审计、事件发布、后台任务事实 | 是 | 必需，应用启动时建立 |
| `pdpRead` | 明确允许最终一致的查询 | 否 | 可选，只能与主库同引擎且来自受管复制 |
| `migrationSource` | 历史 MySQL 源系统 | 否 | 迁移计划批准后加载，使用独立只读会话工厂，结束后卸载并撤权 |
| `migrationTarget` | PDP MySQL 目标的预装载和核对 | 仅迁移执行器 | 使用独立目标会话工厂，开放业务写入前不得作为 `pdpPrimary` |
| `workflowEngine` | Flowable Process Engine 运行表、作业和历史 | 仅 `workflow` 模块 | 必需；使用 MySQL 8.4，但独立 schema/账号/池/事务管理器，不加入普通业务动态路由 |

路由规则：

- `strict=true`，找不到数据源时失败，不回退到默认库。
- `primary=pdpPrimary`；未声明路由的业务调用始终访问主库。
- `@DS` 只允许用于 `persistence`、`datamigration` 基础设施实现；Controller、领域服务和领域对象禁止使用。
- 数据源在事务入口确定，事务开始后尝试切换必须失败。
- 写命令、审批、权限判定、审计、事件发布和读后写一致性查询必须访问 `pdpPrimary`。
- `pdpRead` 只承载显式标注可陈旧的报表或列表，响应必须能提供数据截止时间；P1 默认核心查询仍访问主库。
- 动态增加、删除数据源只能由受审计的迁移控制面执行，不提供普通业务 API。
- dynamic-datasource 负责数据源注册、严格键校验和在线主/读路由；迁移源与目标不与业务 Mapper 共用 `SqlSessionFactory` 或事务管理器。
- `workflowEngine` 由平台工作流基础模块显式配置，不允许通过 `@DS` 选择；业务 Mapper 不得访问
  Flowable 表，Flowable 引擎也不得直接更新 PDP 业务表。

## 3. HikariCP 连接池基线

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

所有应用副本、Flowable 异步执行器、后台执行器与迁移任务的连接池上限之和不得超过数据库可用连接的
70%，其余连接留给高可用、维护和应急。必须按数据源监控 active、idle、pending、timeout、acquire
duration、usage duration 和连接创建失败。使用 JDBC4 `isValid()`，不配置数据库专用探活 SQL；
操作系统和驱动必须启用 TCP keepalive。

## 4. 游标分页契约

外部 API 使用不透明、签名的 keyset cursor，不使用 offset 作为核心列表语义。MyBatis-Plus `Page/IPage` 仅允许用于后台小数据集或适配器内部，不得直接作为 API 请求或响应模型。

游标载荷至少包含：

- 游标版本和查询类型；
- 规范化排序字段值、NULL 标记和 UUIDv7 唯一兜底键；
- 正向或反向方向；
- 过滤条件摘要、工作空间/权限范围摘要；
- 签发时间和可选过期时间。

游标使用 Base64URL 编码和 HMAC 签名，不包含数据库类型、表名或原始 SQL。过滤条件、排序方式或权限范围改变时旧游标必须失效。所有可分页查询必须定义稳定排序，最终排序键为 `id`；P1 MySQL 适配器必须实现公共契约定义的 NULL 排序和复合 keyset 谓词。默认不执行总数查询。

## 5. 乐观锁和冲突语义

所有可修改业务对象使用整数 `revision`。更新和归档必须满足：

```text
SET revision = revision + 1
WHERE id = :id
  AND workspace_id = :workspaceId
  AND revision = :expectedRevision
  AND <当前授权范围>
```

- 影响行数为 1 表示成功；为 0 时重新执行最小权限检查后返回 404 或 409，禁止泄露无权对象存在性。
- HTTP 更新使用 `If-Match` 或请求体 revision，冲突统一为 `409 application/problem+json`。
- MyBatis-Plus 乐观锁插件只负责受支持的内置更新；所有自定义 Mapper SQL 必须显式递增和校验 revision。
- 批量更新逐项返回成功、冲突和无权结果，不允许以最后写入者覆盖。

## 6. TypeHandler 与逻辑类型

| 逻辑类型 | Java | P1 MySQL 映射 | 规则 |
|---|---|---|---|
| UUIDv7 | `UUID` | `BINARY(16)` | 使用 RFC 4122 网络字节序，不调用数据库生成函数 |
| `Instant` | `Instant` | `datetime(6)` | JDBC 会话 UTC，微秒精度，读取后仍为 Instant |
| `JsonDocument` | Jackson `JsonNode` | `JSON` | 使用统一 Jackson 配置；计算哈希时采用规范化 JSON |
| 稳定枚举键 | `String`/值对象 | `varchar` | 保存稳定键，禁止 ordinal |
| 金额/权重 | `BigDecimal` | `decimal(p,s)` | 精度、舍入和溢出由字段定义控制 |
| `ObjectRef`/`ActorRef` | 值对象 | 分列或规范化 JSON | 核心引用优先分列，不以数据库对象类型保存 |

TypeHandler 在 `persistence-common` 注册；数据库专用 JSON/UUID 处理位于对应适配器。复杂字段必须使用显式 XML `resultMap` 或已验证的 `autoResultMap`。NULL、空字符串、空 JSON 对象和空数组保持不同语义。MyBatis 二级缓存和懒加载关闭，`localCacheScope=STATEMENT`，未知列映射设为失败。

## 7. 动态字段投影一致性

投影按用途分级：

| 类型 | 更新方式 | 允许延迟 | 用途 |
|---|---|---:|---|
| 约束投影 | 与业务对象同事务 | 否 | 唯一性、权限、状态规则、流程条件 |
| 查询投影 | 与业务对象同事务 | 否 | 列表筛选、排序、游标字段 |
| 搜索/报表投影 | 事务事件异步 | 是 | 全文搜索、聚合报表、推荐 |

动态唯一字段生成版本化规范值和摘要，并在 `(workspace_id, object_type_key, field_key, normalized_hash)` 上建立唯一约束；冲突时回查规范值避免哈希碰撞。投影定义变更必须触发后台重建，在完成前不得把新投影用于约束或流程判断。

## 8. 数据库无关搜索语义

P1 使用平台统一分析器生成 `SearchDocument` 和 `SearchTermProjection`，统一 Unicode 规范化、大小写折叠、停用词、字段权重和词项版本。MySQL FULLTEXT 或专用索引只能加速候选检索：

- 权限过滤、结构化过滤和最终结果集合以平台投影契约为准。
- 同一分析器版本和数据集必须产生相同词项、匹配集合和稳定业务排序。
- 相关度相同使用业务时间和 UUIDv7 打破平局。
- 数据库原生相关度不得直接成为 API 稳定排序。
- 投影异步时响应提供 `indexedAt`；需要强一致的精确查询回查主库。

## 9. 迁移数据源与事务边界

历史 MySQL 迁移和上线切换使用独立 `SqlSessionFactory`、Mapper 扫描包、事务管理器和 HikariCP 池。业务 Mapper 不能注入迁移数据源，迁移 Mapper 不能使用业务管理权限。

- 源库只读；目标库只接受迁移执行器写入，直到 Go 决策完成。
- 每个批次在单一目标数据库事务内提交并保存检查点；跨源/目标不启用分布式事务。
- 读取源批次、转换、写入目标和记录结果使用幂等批次键恢复。
- 数据源路由上下文必须在 `finally` 中清理，线程池任务显式传递或重建上下文，禁止 ThreadLocal 泄漏。
- 上线切换仍执行全量、增量、冻结、核对和主权转换，不通过把 `pdpPrimary` 路由到 `migrationTarget` 直接完成。

## 10. Spring Modulith 事件发布

采用 `spring-modulith-events-jdbc`，不使用 JPA 事件仓储。事件发布记录与业务更新位于 `pdpPrimary` 的同一事务。

- Spring Modulith 事件表由 Liquibase 的公共与数据库专用变更集管理。
- 生产关闭框架自动 schema 初始化。
- `EventPublication` 至少记录 publication id、event type/version、listener id、aggregate ref、payload、publication date、status、completion attempts、last resubmission date、completion date 和错误摘要。
- 每个监听器拥有独立发布状态；失败、陈旧处理中记录可重提，消费者必须幂等。
- 事件 payload 使用稳定契约和版本，不直接序列化数据库实体或 Mapper Record。

## 11. 数据库默认配置

公共基线：

- JDBC 会话时区 UTC，事务隔离 `READ_COMMITTED`。
- 表、列和索引使用小写 `snake_case`；业务稳定键由应用执行版本化 Unicode 规范化。
- 精确唯一性依赖规范值或摘要，不依赖数据库默认大小写、重音或尾随空格行为。
- 业务 schema 只由 Liquibase 管理；应用账号无 DDL 权限。

MySQL 8.4：

- 所有核心表使用 InnoDB、`utf8mb4` 和批准的 `utf8mb4_0900_bin` 或等价确定性排序规则。
- `sql_mode` 至少包含 `STRICT_TRANS_TABLES`、`ONLY_FULL_GROUP_BY`、`NO_ZERO_DATE`、`NO_ZERO_IN_DATE`、`ERROR_FOR_DIVISION_BY_ZERO`、`NO_ENGINE_SUBSTITUTION`。
- 禁止零日期、隐式截断和非事务表；启动预检发现后拒绝运行。

## 12. 验证门禁

- MySQL 8.4 验证空库初始化、逐版本升级和失败恢复。
- 游标分页覆盖正反向、NULL、并发插入、权限变化和游标篡改。
- 自定义 Mapper 更新覆盖 revision 成功、冲突、越权与批量部分失败。
- TypeHandler 对 UUID 字节序、JSON、Instant、枚举、NULL 执行 MySQL 往返测试。
- 动态数据源覆盖严格路由、事务内切换拒绝、上下文清理和连接池耗尽。
- 搜索分析器和投影结果符合公共契约；MySQL 原生搜索优化关闭时仍正确。
- Spring Modulith JDBC 事件覆盖提交、回滚、监听失败、重启重提和幂等消费。
- 连接池参数通过容量压测冻结，并验证数据库故障、网络分区和连接恢复。
