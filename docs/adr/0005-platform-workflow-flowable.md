# ADR-0005：平台工作流基础能力与 Flowable 边界

## 1. 状态与日期

- **状态**：Accepted
- **日期**：2026-07-17
- **决策人**：架构负责人
- **追溯**：`specs/002-pdp-product/spec.md`（FR-009、FR-017、FR-043～FR-046、FR-174）、`specs/002-pdp-product/plan.md`、`specs/002-pdp-product/technology-comparison.md`、`specs/002-pdp-product/persistence-design.md`、`.specify/memory/constitution.md`（原则 III、IV 与“当前交付与工程门禁”P1 工作流条款）

## 2. 背景

PDP 需要建设一项平台级 BPMN 2.0.2 工作流基础能力，供 `approval`、`project`、`deliverable`、`governance` 以及领域包运行时复用，统一承载审批路由、项目交付编排、阶段等待、超时、消息关联、补偿和异常诊断等编排职责。

宪章原则 III（统一领域语言与确定性状态）要求：业务状态 MUST 由可测试的领域状态机定义；工作流、自动化或审批引擎负责协调，不得成为核心业务事实的唯一存储。因此 PDP 的领域聚合（审批、项目、交付件）和审批聚合必须是业务事实与状态决策的权威来源，Flowable 只承担编排协调职责。

为达成该边界，PDP 需要：

1. 一组稳定的平台端口，使业务模块绝不依赖 Flowable 的 API、实体、表名或异常类型。
2. 将 Flowable 严格限定为 Process Engine，不引入 REST starter、IDM、CMMN、DMN 或 JPA/Hibernate 集成，避免边界泄漏与重复身份/授权模型。
3. Flowable 引擎表与 PDP 业务表使用独立 schema/表前缀、数据库账号、HikariCP 连接池和事务管理器；不使用 XA；生产关闭自动建表与自动升级，DDL 进入版本化发布认证矩阵。
4. 异步执行器、重试、死信、补偿和诊断能力平台化，且任何重试不得重复形成审批结论或业务状态变化。

本 ADR 是 `plan.md` 中“平台工作流基础能力与 Flowable 边界”章节和 `technology-comparison.md` 第 5 节“Flowable 工作流引擎选型与边界”的最终架构决策固化，并对应 `persistence-design.md` 中 `workflowEngine` 数据源拓扑。

## 3. 决策

1. **采用 Flowable 8.0.x**，版本由项目 BOM 锁定，并经过 Spring Boot 4.1 / Java 21 / MySQL 8.4 组合验证。仅引入 `flowable-spring-boot-starter-process`。
2. **不引入** Flowable 全引擎 starter、REST starter、IDM、CMMN、DMN 或 JPA/Hibernate 集成。PDP 的身份、授权、审批待办和业务查询继续使用自身模型，不依赖引擎内置身份数据。
3. **`workflow` 是平台公共基础模块**，对外暴露 4 类稳定端口：`WorkflowDefinitionPort`、`WorkflowRuntimePort`、`WorkflowTaskPort`、`WorkflowAdministrationPort`。业务模块 MUST NOT 依赖 Flowable 的 API、实体、表名或异常类型。
4. **BPMN 定义使用稳定 process key、语义化业务版本、内容哈希以及领域包版本关联**。已启动实例固定为启动时定义版本；流程迁移必须支持预览、分批、可暂停并保留证据。
5. **独立持久化边界**：Flowable 表与 PDP 业务表使用独立 schema/表前缀、数据库账号、HikariCP 池和事务管理器；不使用 XA。业务事实先在本地事务提交并登记 outbox，再由幂等编排消费者启动或推进流程实例。
6. **生产关闭 Flowable 自动建表与自动升级**；引擎 DDL/升级脚本版本化管理，并在 MySQL 8.4 上执行空库初始化、跨版本升级、回退边界和备份恢复测试。
7. **Flowable 用户任务只保存最小关联**（`approval_instance_id`、`business_object_ref`）。平台待办由统一工作流任务端口投影，候选人和办理权限由 PDP 实时计算并复核，绝不依赖引擎内置身份数据作授权。
8. **异步执行器使用独立线程池、队列、连接预算和指标**；重试耗尽形成可检索的 incident/dead-letter，支持安全重放与人工补偿，MUST NOT 产生重复的审批结论或业务状态变化。配置包含重试策略、死信、指标与告警。

## 4. 平台基础能力定位

`workflow` 是平台公共基础能力，定位与 `persistence` 一致，而不是业务模块。`approval`、`project`、`deliverable`、`governance` 以及领域包运行时均为上层消费者，只能通过 4 类稳定端口复用流程定义、实例、任务、定时、消息、重试、补偿和诊断能力。

业务模块与 `workflow` 的协作约束：

- 业务模块只能依赖 `workflow` 暴露的应用服务或稳定端口，禁止跨模块直接访问 Flowable 表、内部实现类或引擎对象。
- CI 通过 Spring Modulith / ArchUnit 校验依赖方向与循环引用；业务模块对 `org.flowable.*` 包、Flowable 表名或异常类型的依赖必须在编译期失败。
- `workflow` 模块的事件桥接位于模块边界内部，业务模块通过 Spring Modulith 事件或显式 API 与之协作。

## 5. BPMN 标准

- 业务流程符号统一采用 OMG BPMN 2.0.2，统一表达审批路由、并行、等待、超时和补偿。
- 流程定义使用稳定 process key、语义化业务版本、内容哈希和领域包版本关联，避免以数据库自增版本号作为业务标识。
- 已启动流程实例固定为启动时的定义版本；流程变量只保存编排所需的稳定标识和非敏感快照，不保存权威业务对象、完整附件、凭据或最终权限结论。
- 流程版本迁移走 `WorkflowAdministrationPort`，必须支持预览、分批、可暂停、回退边界与证据保留；迁移前后状态变化进入审计与可观测链路。

## 6. 四类稳定端口

| 端口 | 职责 |
|---|---|
| `WorkflowDefinitionPort` | BPMN 定义部署、校验、版本管理、内容哈希、领域包版本关联与查询；对外不暴露 Flowable `RepositoryService` 类型。 |
| `WorkflowRuntimePort` | 流程实例启动、推进、消息关联、定时器协调与结果桥接；实例启动固定定义版本；只接收稳定编排标识，不接收权威业务对象。 |
| `WorkflowTaskPort` | 用户任务查询、认领、办理、委派与回写；平台待办由该端口投影，候选人和办理权限由 PDP 实时计算并复核，办理前再次校验当前授权，绝不依赖引擎内置身份数据作授权。 |
| `WorkflowAdministrationPort` | 流程迁移、挂起、恢复、终止与人工补偿；迁移必须预览、分批、可暂停并保留证据；补偿操作不得重复形成审批结论或业务状态变化。 |

所有端口的输入输出对象均为 PDP 自有稳定契约，不携带 Flowable 实体、任务对象或异常类型。端口实现内部完成 Flowable API 调用、异常翻译、关联标识映射和幂等控制。

## 7. 事实权威

PDP 领域聚合（`approval`、`project`、`deliverable` 等）是业务事实与状态决策的权威来源，拥有业务不变量、状态机、权限与审计。Flowable 只负责编排路由、等待、超时、消息关联与补偿，不保存业务事实。

落地约束：

- 业务状态 MUST NEVER 仅存储在 Flowable 流程变量或任务表中；领域状态机和审批聚合是唯一事实源。
- 业务提交、outbox 登记与流程启动/推进通过幂等键、关联标识和可补偿事件桥接串联；任何重试不得生成重复审批动作或业务结果。
- 流程实例和人工任务不得成为项目、任务、交付件、审批、权限或审计事实的唯一存储（对应宪章“当前交付与工程门禁”）。
- 流程完成或异常终止时，结果通过 `WorkflowRuntimePort` 结果桥接回写领域聚合，由聚合决定最终业务状态变化。

## 8. 事务与 MySQL schema

- Flowable 引擎表使用独立 schema/表前缀、数据库账号、HikariCP 连接池和事务管理器，对应 `persistence-design.md` 中的 `workflowEngine` 数据源键。
- `workflowEngine` 不加入普通业务动态路由，禁止通过 `@DS` 选择，业务 Mapper 不得访问 Flowable 表，Flowable 引擎也不得直接更新 PDP 业务表。
- Flowable 与业务事务不使用 XA、Seata 或跨库两阶段提交；业务事实先在 `pdpPrimary` 本地事务提交并登记 outbox，再由幂等编排消费者启动或推进流程。
- 异步执行器使用独立线程池、连接预算与指标；其连接池上限计入“所有副本池上限之和不超过数据库可用连接 70%”的总预算。
- 引擎 DDL/升级脚本版本化管理，由 Liquibase `dbms=mysql` 变更集管理，生产关闭 Flowable 自动 schema 初始化与升级。
- 会话时区固定 UTC，事务隔离 `READ_COMMITTED`；MySQL 8.4 启动预检覆盖字符集、排序规则、引擎、`sql_mode` 与必要权限，发现违规即拒绝运行。

## 9. 升级与退出条件

- Flowable 版本升级必须重新执行验证矩阵：空库初始化、跨版本升级、回退边界、备份恢复以及代表性审批/超时/退回/加签/补偿流程回归。
- 引擎 DDL/升级脚本变更随 PDP 版本进入 MySQL 8.4 发布认证矩阵，必须从空库与上一支持版本两条路径完成验证。
- **退出条件**：若未来以平台原生工作流引擎替换 Flowable，业务模块由于只依赖 4 类稳定端口，可在端口实现层完成替换，不影响业务模型、契约与领域聚合。替换必须重新执行本 ADR 全部验证门禁并形成新 ADR。
- 启用 Flowable REST、IDM、CMMN、DMN 或 JPA/Hibernate 集成属于破坏本 ADR 边界的重大变更，必须提交新 ADR 并补充兼容、迁移、任务重排与验收影响。

## 10. 替代方案

| 方案 | 评价 | 结论 |
|---|---|---|
| (a) 自研工作流引擎 | 依赖少、领域控制直接；但定时、并行网关、补偿、可视化诊断、版本迁移与 BPMN 合规成本高，且与 OMG BPMN 2.0.2 标准对齐风险大。 | **拒绝**。 |
| (b) Camunda | 成熟 BPMN 平台，社区与文档丰富；但在 P1 目标栈（Spring Boot 4.1 + Java 21 + MySQL 8.4）下的组合验证与 Process Engine-only 嵌入 starter 路径不如 Flowable 直接。 | **考虑后放弃**，选择 Flowable。 |
| (c) 嵌入 Flowable REST/IDM | API 完整、部署解耦；但会引入边界泄漏、重复身份/授权模型与外部契约面，违反“PDP 自有身份/授权/审批待办/业务查询”边界。 | **拒绝**。 |
| (d) Flowable 共享业务数据源 | 减少数据源数量与配置复杂度；但违反独立 schema/账号/池/事务管理器边界，破坏事实权威与可独立演进能力。 | **拒绝**。 |

## 11. 验证方式

- **`PlatformWorkflowFoundationTest`**：覆盖 BPMN 定义部署、实例固定版本启动、定时器、并行网关、消息关联、异步执行器重试、死信、办理时 PDP 权限实时复核以及引擎恢复。
- **`WorkflowBoundaryTest`**（ArchUnit 架构测试）：业务模块不得依赖 Flowable API、实体、表名或异常类型；不得直接查询引擎表；不得暴露 Flowable REST 端点；`@DS` 不得用于选择 `workflowEngine`。
- **`FlowableSchemaMySqlMatrixTest`**：在 MySQL 8.4 上完成 Flowable schema 空库初始化、跨版本升级、回退边界与备份恢复验证，并随 PDP 发布认证矩阵执行。
- 阶段 A0 必须使用代表性审批、超时、退回、加签和补偿流程验证 Flowable 8.0.x Process Engine、Spring Boot 4.1、独立数据源/连接池、MySQL DDL 与流程版本迁移，冻结 BPMN 建模约束和引擎升级边界。
- 审批、项目交付和标准实施领域包在阶段 C/D 必须复用平台工作流能力编排路由、等待、超时和补偿，并验证领域聚合仍为业务决定与状态变化的唯一权威。

## 12. 复审条件

本 ADR 在以下任一条件触发时复审：

- Flowable 版本升级（包括补丁版本变更引发 BOM 重新锁定）。
- 新增工作流消费者模块或新增稳定端口。
- 异步执行器线程池、队列、连接预算或重试/死信策略发生容量级变化。
- 启用此前被本 ADR 拒绝的 Flowable REST/IDM/CMMN/DMN/JPA 能力或更换底层工作流引擎。
- 季度例行复核（与宪章季度复核同步），确认边界、事实权威与验证矩阵仍然有效。

复审结论必须记录“继续有效 / 修订 / 替换”以及责任人，并按宪章变更治理规则传播到 `plan.md`、`persistence-design.md`、`technology-comparison.md`、契约与任务。
