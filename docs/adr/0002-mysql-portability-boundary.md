# ADR 0002：P1 MySQL 持久化与数据库独立边界

## 1. 状态与日期

- **状态**：Accepted
- **日期**：2026-07-17
- **追溯**：宪章原则 V「契约优先、兼容演进与数据库独立」与「当前交付与工程门禁」数据库独立条款；`specs/002-pdp-product/persistence-design.md`（全文，尤其第 1、6、11、12 节）；`specs/002-pdp-product/plan.md`「存储」「P1 MySQL 持久化与数据库独立边界」；`specs/002-pdp-product/technology-comparison.md` 第 1 节结论摘要与第 4 节 PostgreSQL 与 MySQL 详细对比；`specs/002-pdp-product/data-model.md` 第 1.1 节数据库无关逻辑类型；FR-139～FR-150、FR-171～FR-172、FR-174。

## 2. 背景

PDP P1 必须在 **MySQL 8.4 LTS** 上认证唯一一个业务写入主库，但宪章原则 V 与「当前交付与工程门禁」要求领域层、应用层、领域包和外部契约 MUST 与数据库产品、ORM/Mapper 框架和持久化记录解耦：认证数据库之间必须保持相同业务语义，专有优化只能位于适配器边界并具有等价实现或可验证降级路径。因此 P1 需要在「只交付并认证一种数据库」与「保持数据库无关的长期演进能力」之间建立明确边界。

P1 面临的具体张力：

- **唯一认证数据库**：每个 PDP 部署只能有一个业务写入主库；P1 只实现并认证 MySQL 8.4 LTS，与既有运维体系及历史数据更接近。
- **数据库独立不可破**：领域层与应用层不得依赖数据库驱动、方言类、专有 SQL 或持久化记录类型；外部契约保持数据库无关。
- **需要受治理的扩展点**：为 P2 引入第二种数据库（PostgreSQL 为首选候选）预留适配器注册、能力画像与切换治理类型，避免后期返工或静默扩大 P1 范围。
- **不可伪装切换**：动态数据源只能用于受控访问主库、只读副本和迁移源/目标，不得把上线切换伪装成运行时连接切换；P1 不支持通过修改连接字符串切换数据库或形成双主。

因此 P1 需要定义：适配器注册机制、能力画像、部署事实、启动校验、单写主权和切换治理扩展点——但 **不交付第二种数据库实现，也不交付可执行的跨库切换**。PostgreSQL 适配器、认证矩阵与 MySQL↔PostgreSQL 受控切换整体下移 P2。

## 3. 决策

### 3.1 端口与依赖边界

- 领域层与应用层只依赖仓储、查询、锁、分页、搜索投影和事务端口；不引用数据库驱动、方言类、专有 SQL 或持久化记录类型。
- 业务模块不依赖 `@DS`、`BaseMapper`、`IService`、`QueryWrapper`、JDBC 类型或连接池类型；这些框架类型只能出现在基础设施适配器边界。

### 3.2 `public-persistence` 模块职责

`public-persistence` 模块维护与数据库无关的公共资产：

- 逻辑类型与 TypeHandler 注册（UUIDv7、Instant、JsonDocument、稳定枚举键、Decimal、ObjectRef/ActorRef 等）；
- Mapper 端口、公共 SQL 片段、显式 `resultMap` 与查询契约；
- `PersistenceProvider`、`DatabaseCapabilityProfile`、`DatabaseDeploymentProfile` 抽象；
- 数据库切换治理扩展类型（P1 预注册但不启用执行）。

注册表（registry）在启动时选择唯一启用的已认证适配器；P1 唯一启用的适配器是 MySQL。

### 3.3 `persistence-mysql` 与 `persistence-postgresql` 边界

- `persistence-mysql` 只实现 MySQL 差异化 DDL、索引、查询和运维检查；MySQL 专用语句通过 MyBatis `databaseId` 或独立适配器 Mapper 选择，禁止在业务服务中按数据库类型拼接 SQL。
- `persistence-postgresql` **不作为 P1 制品**；其适配器、DDL、查询和契约测试整体下移 P2。

### 3.4 P1 启动校验

P1 应用制品通过明确的 MySQL 连接配置启动，并在启动时快速失败（fail-fast）校验：

- 产品必须为 MySQL；
- 版本必须为认证的 8.4 LTS；
- schema、字符集（`utf8mb4` 与批准的 `utf8mb4_0900_bin` 或等价确定性排序规则）、时区（JDBC 会话 UTC）、事务引擎（核心表 InnoDB）和必要权限；
- `sql_mode` 至少包含 `STRICT_TRANS_TABLES`、`ONLY_FULL_GROUP_BY`、`NO_ZERO_DATE`、`NO_ZERO_IN_DATE`、`ERROR_FOR_DIVISION_BY_ZERO`、`NO_ENGINE_SUBSTITUTION`；禁止零日期、隐式截断和非事务表。

启动预检发现不满足时拒绝运行。

### 3.5 MyBatis-Plus 使用边界

- MyBatis-Plus 只在基础设施层提供单表 CRUD、分页、乐观锁和安全拦截能力（如阻止全表更新删除插件）；
- 业务模块 MUST NOT 直接暴露或依赖 `BaseMapper`、`IService`、`QueryWrapper` 等框架类型；
- 简单单表操作使用 MyBatis-Plus；复杂查询使用参数化的标准 SQL 或显式 Mapper XML；
- 公共查询优先使用参数化的标准 SQL，复杂查询使用显式 Mapper XML；
- MyBatis-Plus 乐观锁插件只负责受支持的内置更新；所有自定义 Mapper SQL 必须显式递增并校验 `revision`；
- MyBatis 二级缓存和懒加载关闭，`localCacheScope=STATEMENT`，未知列映射设为失败；
- 框架版本由项目 BOM 锁定；升级 MyBatis-Plus、MyBatis 或 SQL 解析插件必须运行完整 MySQL 回归。

### 3.6 Liquibase 变更集分层

- Liquibase 变更集分为 `common` 与 `mysql` 目录；
- MySQL 专用变更使用 `dbms=mysql` 条件；
- MySQL 专用变更接受 MySQL 空库初始化、逐版本升级和失败恢复测试；
- 业务 schema 只由 Liquibase 管理；应用账号无 DDL 权限；生产关闭框架自动 schema 初始化。

### 3.7 模拟适配器验证

P1 使用模拟适配器（mock adapter）验证：

- 适配器注册流程；
- 能力拒绝（capability rejection，不匹配时拒绝激活）；
- 唯一激活（同一时刻只有一个已认证适配器启用）；
- 单写主权（single-write sovereignty）；
- 跨库切换被禁用并返回稳定禁用原因。

P1 **不**通过模拟适配器连接或认证第二种生产数据库。

## 4. 替代方案

### 4.1 （a）JPA/Hibernate 作为第二套持久化框架

- **方案**：在 MyBatis-Plus 之外同时引入 Hibernate/JPA，覆盖稳定写聚合。
- **拒绝理由**：会形成双持久化上下文与事务边界分裂；Hibernate 一级缓存、脏检查与 MyBatis 显式 SQL 在同一聚合或同一事务中混用会产生状态语义冲突；PDP 的主要风险集中在复杂权限、动态查询、批量迁移和执行计划，不在实体 CRUD，引入第二套框架不解决核心风险却显著扩大治理面。
- **结论**：`technology-comparison.md` 第 3.3 节已明确 Hibernate 不进入业务持久化栈；后续若稳定写聚合复杂度显著高于 SQL 查询复杂度，可通过 ADR 评估，但不得在单个聚合或同一事务中混用两套实体状态模型。

### 4.2 （b）P1 同时认证 MySQL 与 PostgreSQL

- **方案**：在 P1 即交付并认证 MySQL 与 PostgreSQL 两套适配器、契约矩阵和受控切换。
- **拒绝理由**：范围与风险超出 P1 可上线目标；当前没有容量、隔离、合规或组织边界证据要求 P1 必须同时支持两种数据库；MySQL 与既有运维体系及历史数据更接近，优先用于 P1；PostgreSQL 在 `jsonb`、复杂索引和高级 SQL 上更灵活，作为 P2 首个候选适配器即可。
- **结论**：PostgreSQL 适配器、认证矩阵与 MySQL↔PostgreSQL 受控切换整体下移 P2。

### 4.3 （c）在领域层嵌入 SQL

- **方案**：领域层直接编写 SQL 或引用数据库方言类，跳过端口与适配器边界。
- **拒绝理由**：直接违反宪章原则 V「契约优先、兼容演进与数据库独立」与「当前交付与工程门禁」中「领域层、应用层、领域包和外部契约 MUST 保持数据库无关」的不可协商条款；一旦领域层耦合数据库方言，后续切换或并存认证将不可行。
- **结论**：领域层与应用层只依赖仓储/查询/锁/分页/搜索投影/事务端口；专有 SQL 与方言类只能位于适配器边界。

## 5. 质量属性影响

- **可移植性（Portability）**：P2 PostgreSQL 路径通过端口、逻辑类型与适配器边界保留；新增数据库只需新增适配器实现并通过认证矩阵，不修改领域层、应用层或外部契约。
- **可测试性（Testability）**：每种认证数据库提供独立契约测试矩阵；P1 持久化故事必须同时交付公共 Liquibase、仓储端口、Mapper/适配器和 MySQL 契约测试。
- **可维护性（Maintainability）**：适配器边界清晰，公共语句与数据库专用语句分层；MySQL 专用优化不污染领域与应用层，关闭优化后仍需通过正确性契约。
- **性能（Performance）**：MySQL 专用优化（如生成列、函数索引、多值索引、FULLTEXT）只允许位于适配器内部；权限、状态、审计和业务正确性必须由公共模型保证。

## 6. 数据与迁移影响

逻辑类型到 P1 MySQL 物理映射表（来自 `data-model.md` 第 1.1 节与 `persistence-design.md` 第 6 节）：

| 逻辑类型 | Java | P1 MySQL 8.4 映射 | 统一规则 |
|---|---|---|---|
| `UUIDv7` | `UUID` | `BINARY(16)` | 应用生成和解析，使用 RFC 4122 网络字节序，不调用数据库生成函数或私有重排格式 |
| `Boolean` | `Boolean` | `boolean`/`tinyint(1)` | 持久化层只接受真/假，不接受任意整数 |
| `Instant` | `Instant` | UTC `datetime(6)` | JDBC 会话固定 UTC，微秒精度，业务时区单独保存 |
| `Decimal` | `BigDecimal` | `decimal(p,s)` | 精度、舍入和溢出规则由领域模型定义 |
| `JsonDocument` | Jackson `JsonNode` | `JSON` | 关键筛选、排序和统计字段进入类型化索引投影，不依赖专有 JSON 查询保证正确性；哈希、签名或比较先生成规范化 JSON |
| `Text` | `String` | `varchar`/`longtext` | 长度和 UTF-8 规则统一，禁止依赖数据库隐式截断 |
| `BinaryRef` | 对象存储引用 | 对象存储引用 | 大文件不进入业务主库 |
| 稳定枚举键 | `String`/值对象 | `varchar` | 保存稳定键，禁止保存 ordinal |
| `ObjectRef`/`ActorRef` | 值对象 | 分列或规范化 JSON | 核心引用优先分列，不以数据库对象类型保存 |

切换治理类型在 P1 仅预注册但不执行；运行时无跨库迁移热切换。

每个 P1 持久化用户故事必须同时交付：业务模块仓储端口、公共 Liquibase 变更集、公共 Mapper XML 或适配器、必要的 MySQL 专用索引以及 MySQL 仓储契约测试；不能只创建领域模型和应用服务。

## 7. 适配器注册与能力画像

`public-persistence` 定义以下抽象，由各数据库适配器实现并通过注册表登记：

- **`PersistenceProvider`**：适配器注册表入口，描述适配器标识、版本与激活状态；注册表在启动时选择唯一启用的已认证适配器。
- **`DatabaseCapabilityProfile`**：能力画像，至少包含数据库产品、认证版本、字符集、排序规则、时区、事务引擎与可用特性集合（如生成列、函数索引、多值索引、FULLTEXT）；不匹配的适配器在启动时被拒绝激活。
- **`DatabaseDeploymentProfile`**：部署画像，描述单写主库与可选只读副本（与主库同引擎且来自受管复制）；明确单写主权与读副本语义。

启动校验在能力画像与部署画像不匹配时 fail-fast，拒绝应用启动。

## 8. 单写主权与切换治理扩展点

P1 切换语义只覆盖历史 MySQL 旧系统到 PDP MySQL 的受控上线切换（详见历史数据迁移边界与 ADR 0003）。认证数据库之间的跨库切换整体下移 P2，但 P1 在 `public-persistence` 预注册「已认证数据库切换」操作类型，返回稳定的禁用原因。

P1 边界：

- 每个 PDP 部署只有一个业务写入主库；
- 写命令、审批、权限判定、审计、事件发布与读后写一致性查询始终访问 `pdpPrimary`；
- 生产环境不支持通过修改连接串切换到空库、旧快照或未核对目标；
- 不提供运行时无迁移的热切换；
- 不形成双主。

P2 才交付：

- 第二种数据库实现（PostgreSQL 适配器）；
- 跨库迁移执行器与受控切换流程；
- 外部切换 API。

## 9. 验证方式

- **`PersistenceBoundaryTest`（架构测试）**：使用 ArchUnit 校验领域层与应用层不依赖数据库驱动、方言类、`BaseMapper`、`IService`、`QueryWrapper`、JDBC 类型或连接池类型；`@DS` 只允许出现在 `persistence` 与 `datamigration` 基础设施实现。
- **`PersistenceProviderExtensionContractTest`（模拟适配器契约测试）**：使用模拟适配器验证注册、能力拒绝、唯一激活、单写主权与跨库切换禁用返回稳定原因。
- **MySQL 8.4 契约矩阵**：覆盖空库初始化、逐版本升级与失败恢复（回退边界）；每个 Mapper 方法在 MySQL 8.4 上运行契约测试并检查执行计划和索引命中。
- **启动校验测试**：覆盖产品/版本/字符集/排序规则/时区/引擎/权限/`sql_mode` 不匹配时 fail-fast。
- **TypeHandler 往返测试**：对 UUID 字节序、JSON、Instant、枚举、NULL 与空语义执行 MySQL 往返测试。
- **游标分页与乐观锁测试**：覆盖正反向游标、NULL 排序、并发插入、权限变化、游标篡改；自定义 Mapper 更新覆盖 `revision` 成功、冲突、越权与批量部分失败。

## 10. 第二种数据库与跨库迁移的 P2 下移

明确下移 P2 的事项：

- PostgreSQL 适配器实现（DDL、索引、查询、TypeHandler、运维检查）；
- PostgreSQL 认证矩阵（空库初始化、逐版本升级、失败恢复、契约测试）；
- MySQL↔PostgreSQL 受控切换流程（全量、增量、冻结、核对、主权转换）；
- 跨库迁移执行器（独立 `SqlSessionFactory`、Mapper 扫描包、本地事务管理器、连接池、检查点与幂等恢复）；
- 外部切换 API 与运行时切换治理执行能力。

P1 只预注册上述扩展点并返回稳定禁用原因，不交付可执行的跨库切换。

## 11. 复审条件

本 ADR 在以下任一条件触发时必须专项复审，并记录继续有效、修订或例外结论及责任人：

- P2 PostgreSQL 适配器工作启动；
- 出现第二种数据库的认证需求或客户/合规要求；
- 单写主权、动态数据源或迁移边界发生实质变化；
- 宪章原则 V 或「当前交付与工程门禁」数据库独立条款修订；
- 季度例行复核（至少每季度一次）。

复审必须重新评估：端口边界是否仍被架构测试守护、能力画像是否覆盖新增数据库特性、切换治理扩展点是否仍满足 P2 演进需要、以及 P1 模拟适配器验证是否仍能反映真实适配器契约。
