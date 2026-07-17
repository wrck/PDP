# PDP 产品平台实施任务

**输入**：`specs/002-pdp-product/` 下的产品规格、实施计划、研究、数据模型、持久化设计、迁移方案和契约。
**执行范围**：本文件只包含 P1 可执行任务；P2/P3 仅维护在 `backlog-p2-p3.md`。
**完成原则**：先契约、状态机和失败测试，再实现；任何持久化故事必须同时交付公共变更集、仓储端口、MySQL Mapper/适配器和 MySQL 契约测试；公共契约位于 `public-persistence`，MySQL 专有 DDL、索引、SQL、方言、类型映射和适配器实现位于 `persistence-mysql`；领域层、应用层和外部契约不得依赖 MySQL 专有实现；每个故事同时交付权限、审计、可观测和闭环证据。

## 阶段 0：实现前治理门禁

- [X] T001 关闭规格质量清单并确认 P1 规格状态、业务闭环、状态机和成功标准已批准，文件：`specs/002-pdp-product/checklists/requirements.md`、`specs/002-pdp-product/spec.md`
- [X] T002 [P] 创建模块化单体架构 ADR，记录边界、替代方案、质量属性和拆分触发条件，文件：`docs/adr/0001-modular-monolith.md`
- [X] T003 [P] 创建 P1 MySQL 持久化与数据库独立边界 ADR，定义适配器注册、能力画像、部署事实、启动校验、单写主权和统一数据库切换执行器，并记录第二种数据库产品适配器、跨产品组合及 PostgreSQL 下移 P2，文件：`docs/adr/0002-mysql-portability-boundary.md`
- [X] T004 [P] 创建动态数据源、独立 HikariCP 和单写主权 ADR，文件：`docs/adr/0003-dynamic-datasource.md`
- [X] T005 [P] 创建 MySQL 历史迁移、上线切换和本地事务边界 ADR，文件：`docs/adr/0004-data-migration.md`
- [X] T006 [P] 创建平台工作流与 Flowable 边界 ADR，记录平台基础能力定位、BPMN 标准、四类稳定端口、事实权威、事务、MySQL schema、升级和退出条件，文件：`docs/adr/0005-platform-workflow-flowable.md`
- [X] T007 建立 ISO 21500 系列、PMI Lexicon、BPMN 与 PDP 扩展的受控术语目录和术语偏离审批机制，文件：`specs/002-pdp-product/terminology.md`、`docs/governance/terminology-governance.md`
- [X] T008 创建平台威胁模型与数据分类目录，覆盖信任边界、字段、附件、签名、导出、迁移、凭据和审计，文件：`docs/security/threat-model.md`、`docs/security/data-classification.md`
- [X] T009 创建传输与存储保护、密钥引用、轮换、职责分离和审计防篡改基线，文件：`docs/security/security-baseline.md`
- [X] T010 创建 P1 业务闭环与核心状态机验证矩阵，定义前置条件、权限、并发、失败原因和证据，文件：`specs/002-pdp-product/state-machine-matrix.md`
- [X] T011 创建关键能力服务等级档案，定义请求类别、时限、SLI/SLO、容量、失败模式、告警、责任人和运行手册，文件：`docs/service-levels/p1-service-catalog.md`
- [X] T012 建立 P1 用户故事、FR、SC、契约和任务的基础追踪矩阵，校验 OpenAPI、事件、领域包 Schema 和迁移报告 Schema 完整一致，并登记消费者、版本、兼容影响及弃用窗口，文件：`specs/002-pdp-product/traceability.md`、`specs/002-pdp-product/contracts/coverage.md`、`specs/002-pdp-product/contracts/migration-report.schema.json`
- [X] T013 创建高风险操作目录和统一预览、确认、不可逆点及补偿交互规范，纳入 P1 `MYSQL→MYSQL` 数据库切换类型、禁用原因、回退语义和 P2 产品组合扩展条件，文件：`docs/ux/high-risk-operations.md`
- [X] T014 执行实现前 `/speckit-analyze`，由评审人归档结果并要求 CRITICAL/HIGH 为 0，文件：`specs/002-pdp-product/evidence/analysis-pre-implementation.md`

## 阶段 1：工程初始化

- [X] T015 创建 Java 21、Spring Boot 4.1、Maven 多模块父工程和版本锁定，文件：`pom.xml`、`.mvn/wrapper/`
- [X] T016 创建 API 应用、业务模块和持久化适配器模块骨架，文件：`apps/api/pom.xml`、`modules/*/pom.xml`
- [X] T017 创建根 pnpm 命令，统一前端、契约、端到端和性能测试入口，文件：`package.json`
- [X] T018 创建 pnpm 工作区并纳入 Web 与测试包，文件：`pnpm-workspace.yaml`
- [X] T019 创建 Vue 3.5、TypeScript、Vite 前端工程，文件：`apps/web/package.json`、`apps/web/vite.config.ts`
- [X] T020 创建 Vue 应用入口、路由和状态管理骨架，文件：`apps/web/src/main.ts`、`apps/web/src/router/index.ts`、`apps/web/src/stores/index.ts`
- [X] T021 [P] 配置 ESLint、Prettier、Stylelint 和 EditorConfig，文件：`eslint.config.js`、`.prettierrc.json`、`.stylelintrc.json`、`.editorconfig`
- [X] T022 创建独立测试工作区，文件：`tests/package.json`、`tests/tsconfig.json`
- [X] T023 [P] 配置 Playwright 端到端测试，文件：`tests/playwright.config.ts`
- [X] T024 [P] 配置 OpenAPI、JSON Schema 和事件契约校验，文件：`tests/scripts/validate-contracts.mjs`、`tests/.spectral.yaml`
- [X] T025 创建 MySQL 8.4 持久化、升级、权限和集成测试持续集成矩阵，文件：`.github/workflows/ci.yml`
- [X] T026 [P] 创建本地 MySQL 8.4、历史 MySQL 源库、Redis、对象存储和可观测组件编排，文件：`infra/compose/compose.yaml`
- [X] T027 [P] 创建无凭据的配置示例与说明，文件：`.env.example`、`docs/configuration.md`
- [X] T028 [P] 创建 Kubernetes 探针、滚动升级和 PodDisruptionBudget 基础清单，文件：`infra/k8s/base/`
- [X] T029 创建后端单元、集成、架构、恢复和数据库契约测试目录，文件：`tests/backend/`
- [X] T030 配置依赖治理，显式使用 MyBatis-Plus 并阻止 Hibernate/JPA 进入运行时；建立技术债记录机制，文件：`pom.xml`、`tests/backend/architecture/DependencyPolicyTest.java`、`docs/technical-debt.md`
- [X] T031 在项目 BOM 中锁定经验证的 Flowable 8.0.x，并仅引入 `flowable-spring-boot-starter-process`，阻止全引擎/REST/IDM/CMMN/DMN/JPA starter，文件：`pom.xml`、`tests/backend/architecture/WorkflowDependencyPolicyTest.java`

## 阶段 2：公共平台基础

### 身份、权限和审计基础

- [X] T032 创建统一错误码、异常和 `application/problem+json` 响应，文件：`modules/shared-kernel/src/main/java/com/pdp/shared/error/`
- [X] T033 [P] 创建工作空间、操作者、链路、幂等键和审计上下文值对象，文件：`modules/shared-kernel/src/main/java/com/pdp/shared/context/`
- [X] T034 创建请求上下文过滤器并强制校验工作空间边界，文件：`apps/api/src/main/java/com/pdp/api/security/RequestContextFilter.java`
- [X] T035 创建用户账户、外部身份、用户会话领域模型及仓储端口，文件：`modules/identity/src/main/java/com/pdp/identity/domain/`、`modules/identity/src/main/java/com/pdp/identity/port/`
- [X] T036 创建用户账户、身份映射和会话公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/001-identity.xml`
- [X] T037 创建身份 MySQL Mapper、XML 和数据库适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/identity/`、`modules/persistence-mysql/src/main/resources/mapper/identity/`
- [X] T038 创建 OIDC 登录、回调、用户同步和外部身份绑定适配器，文件：`modules/identity/src/main/java/com/pdp/identity/infrastructure/oidc/`
- [X] T039 创建用户启用、停用、离职、会话和刷新凭据撤销服务，文件：`modules/identity/src/main/java/com/pdp/identity/application/IdentityLifecycleService.java`
- [X] T040 创建身份生命周期 MySQL 契约测试，文件：`tests/backend/contract/identity/IdentityLifecycleDatabaseContractTest.java`
- [X] T041 创建统一认证、授权决策和资源范围校验服务，文件：`modules/identity/src/main/java/com/pdp/identity/application/AuthorizationService.java`
- [X] T042 创建权限撤销时效基线测试，文件：`tests/backend/security/PermissionRevocationSlaTest.java`

### MySQL 持久化、动态数据源和连接池

- [X] T043 配置 MySQL 适配器的 MyBatis-Plus、分页、乐观锁和审计字段填充，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/config/MybatisPlusConfig.java`
- [X] T044 配置 MySQL 动态数据源，在线业务仅允许 `pdpPrimary` 和 `pdpRead`，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/config/DynamicDataSourceConfig.java`
- [X] T045 创建 MySQL 数据源严格路由守卫，拒绝未知键、越权路由和事务内切换，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/routing/DataSourceRoutingGuard.java`
- [X] T046 配置各数据源独立 HikariCP 容量、超时、存活检测和指标，文件：`apps/api/src/main/resources/application-datasource.yml`
- [X] T047 创建动态路由、只读降级和越权拦截测试，文件：`tests/backend/integration/datasource/DynamicDataSourceRoutingTest.java`
- [X] T048 创建连接池耗尽、连接泄漏和数据库故障恢复测试，文件：`tests/backend/integration/datasource/HikariPoolResilienceTest.java`
- [X] T049 创建历史 MySQL 源库专用 DataSource、SqlSessionFactory 和 Mapper 扫描，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/config/LegacySourceMybatisConfig.java`
- [X] T050 创建迁移目标库专用 DataSource、SqlSessionFactory 和 Mapper 扫描，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/config/PdpTargetMybatisConfig.java`
- [X] T051 创建源库只读事务管理器、目标库本地事务管理器和批次边界，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/config/MigrationTransactionConfig.java`
- [X] T052 创建迁移源/目标连接、Mapper、事务和凭据隔离测试，文件：`tests/backend/integration/datamigration/MigrationDataSourceIsolationTest.java`
- [X] T053 创建公共持久化适配器注册、数据库能力画像、部署事实和切换组合契约，文件：`modules/public-persistence/src/main/java/com/pdp/persistence/provider/`

### 数据访问、事件和投影基础

- [X] T054 [P] 创建 MySQL UUID、JSON、枚举、时间和值对象 TypeHandler，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/typehandler/`
- [X] T055 创建 TypeHandler MySQL 往返与公共逻辑语义测试，文件：`tests/backend/contract/persistence/TypeHandlerDatabaseContractTest.java`
- [X] T056 创建签名 keyset cursor 与稳定排序组件，文件：`modules/shared-kernel/src/main/java/com/pdp/shared/page/`
- [X] T057 创建游标分页 MySQL 契约测试，文件：`tests/backend/contract/persistence/CursorPaginationDatabaseContractTest.java`
- [X] T058 创建 `revision`、ETag 和乐观并发冲突组件，文件：`modules/shared-kernel/src/main/java/com/pdp/shared/concurrency/`
- [X] T059 创建并发更新、重试和冲突呈现测试，文件：`tests/backend/integration/concurrency/OptimisticConcurrencyTest.java`
- [X] T060 创建领域事件、Outbox、幂等消费和死信基础设施，文件：`modules/integration/src/main/java/com/pdp/integration/event/`
- [X] T061 创建重复、乱序、失败重放和死信恢复测试，文件：`tests/backend/integration/event/EventDeliverySemanticsTest.java`
- [X] T062 创建 Liquibase 根变更集及 common/mysql 目录规则，文件：`modules/public-persistence/src/main/resources/db/changelog/db.changelog-master.xml`
- [X] T063 创建公共审计摘要链、Outbox、幂等和后台作业表，文件：`modules/public-persistence/src/main/resources/db/changelog/common/002-platform-foundation.xml`
- [X] T064 [P] 创建 MySQL 8.4 专用 DDL、索引和约束，文件：`modules/persistence-mysql/src/main/resources/db/changelog/mysql/001-platform-indexes.xml`
- [X] T065 创建空库、升级库和回滚演练的 Liquibase MySQL 测试，文件：`tests/backend/contract/persistence/LiquibaseMySqlMatrixTest.java`
- [X] T066 创建实现公共持久化提供方扩展契约的 MySQL 8.4 仓储和 SQL 方言适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/`
- [X] T067 创建仓储端口禁止泄漏 MyBatis、MySQL 驱动和数据库专有类型的架构测试，并使用模拟适配器验证注册、唯一激活、未知能力拒绝和边界兼容，文件：`tests/backend/architecture/PersistenceBoundaryTest.java`、`tests/backend/contract/persistence/PersistenceProviderExtensionContractTest.java`
- [X] T068 创建权限过滤的搜索投影端口、文档和统一分析器，文件：`modules/experience/src/main/java/com/pdp/experience/search/`
- [X] T069 创建搜索投影 30 秒可见和撤权过滤测试，文件：`tests/backend/integration/search/SearchProjectionConsistencyTest.java`
- [X] T070 创建后台作业协调器以及批量导入、导出、归档、统计、投影重建、断点恢复、进度和失败明细能力，文件：`modules/operations/src/main/java/com/pdp/operations/job/BackgroundJobCoordinator.java`、`modules/operations/src/main/java/com/pdp/operations/projection/ProjectionRebuildJob.java`
- [X] T071 创建批量作业暂停、取消、检查点恢复、失败明细和资源预算测试，文件：`tests/backend/integration/job/BackgroundJobLifecycleTest.java`
- [X] T072 创建对象存储、短时签名 URL、病毒扫描和隔离适配器，文件：`modules/experience/src/main/java/com/pdp/experience/storage/`
- [X] T073 创建 Redis 缓存降级、失效和防击穿组件，文件：`modules/operations/src/main/java/com/pdp/operations/cache/`
- [X] T074 创建日志、指标、链路追踪及 FR-165 可用性 SLI 采集，文件：`modules/operations/src/main/java/com/pdp/operations/observability/`
- [X] T075 创建高风险操作影响预览、版本确认、不可逆点和补偿端口及公共组件；注册 P1 `MYSQL→MYSQL` 数据库切换操作，并对未认证产品、版本或组合返回稳定禁用原因，文件：`modules/shared-kernel/src/main/java/com/pdp/shared/operation/`、`apps/web/src/components/high-risk-operation/`
- [X] T076 创建影响预览过期、并发版本变化、确认和补偿通用测试，文件：`tests/backend/integration/operation/HighRiskOperationTest.java`、`tests/e2e/high-risk-operation.spec.ts`

### 平台工作流基础能力

- [X] T077 先完成平台工作流定义、部署、实例诊断和管理动作的 OpenAPI/事件契约及失败测试，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`tests/contracts/platform-workflow.spec.ts`
- [X] T078 创建平台 `workflow` 模块以及定义、运行、人工任务和管理四类公开端口，文件：`modules/workflow/pom.xml`、`modules/workflow/src/main/java/com/pdp/workflow/port/`
- [X] T079 配置 Flowable Process Engine 独立 schema/表前缀、数据库账号、HikariCP 池和本地事务管理器，关闭生产自动建表/升级和内置 REST/IDM，文件：`modules/workflow/src/main/java/com/pdp/workflow/infrastructure/flowable/FlowableEngineConfig.java`、`apps/api/src/main/resources/application-workflow.yml`
- [X] T080 创建 Flowable MySQL 版本化初始化与升级脚本清单，并验证空库、上一版本升级和不支持版本快速失败，文件：`modules/workflow/src/main/resources/db/flowable/mysql/`、`tests/backend/contract/workflow/FlowableSchemaMySqlMatrixTest.java`
- [X] T081 创建平台流程定义、部署、实例引用和异常记录公共变更集及仓储端口，文件：`modules/public-persistence/src/main/resources/db/changelog/common/005-workflow-registry.xml`、`modules/workflow/src/main/java/com/pdp/workflow/domain/`
- [X] T082 实现 BPMN 2.0.2 校验、稳定流程键、业务版本、内容哈希、领域包关联和受控部署服务，文件：`modules/workflow/src/main/java/com/pdp/workflow/application/WorkflowDefinitionService.java`
- [X] T083 实现基于 Outbox 的流程启动、推进、消息关联和结果事件桥接，禁止 XA 并确保幂等，文件：`modules/workflow/src/main/java/com/pdp/workflow/application/WorkflowRuntimeService.java`、`modules/workflow/src/main/java/com/pdp/workflow/infrastructure/event/`
- [X] T084 实现平台人工任务查询、候选人投影、领取和办理端口，并在每次查询与办理时复核 PDP 当前权限，文件：`modules/workflow/src/main/java/com/pdp/workflow/application/WorkflowTaskService.java`
- [X] T085 配置 Flowable 异步执行器独立线程池、队列、连接预算、重试、死信、指标和告警，文件：`modules/workflow/src/main/java/com/pdp/workflow/infrastructure/flowable/WorkflowAsyncExecutorConfig.java`
- [X] T086 实现流程实例受控迁移、暂停、恢复、终止和人工补偿管理服务，文件：`modules/workflow/src/main/java/com/pdp/workflow/application/WorkflowAdministrationService.java`
- [X] T087 创建 BPMN 部署、实例固定版本、定时器、并行网关、消息关联、重试、死信、权限复核和引擎恢复集成测试，文件：`tests/backend/integration/workflow/PlatformWorkflowFoundationTest.java`
- [X] T088 创建业务模块不得依赖 Flowable API/表结构、不得直接查询引擎表且不得暴露 Flowable REST 的架构测试，文件：`tests/backend/architecture/WorkflowBoundaryTest.java`
- [X] T089 实现平台工作流定义、实例诊断和受控管理动作控制器，文件：`apps/api/src/main/java/com/pdp/api/workflow/WorkflowController.java`
- [X] T090 实现平台流程定义、部署、实例、incident/dead-letter 和迁移管理页面，文件：`apps/web/src/views/admin/workflow/`

### 前端、契约和架构基础

- [X] T091 创建 Spring Modulith/ArchUnit 模块边界测试，文件：`tests/backend/architecture/ModuleBoundaryTest.java`
- [X] T092 创建前端 API 客户端、错误处理、请求追踪和权限指令，文件：`apps/web/src/api/`、`apps/web/src/directives/permission.ts`
- [X] T093 [P] 创建 JSON Schema 表单、表格和详情渲染基础组件，文件：`apps/web/src/components/schema/`
- [X] T094 [P] 创建主题令牌、响应式布局和统一交互反馈，文件：`apps/web/src/styles/`、`apps/web/src/layouts/`
- [X] T095 创建 OpenAPI、Schema、事件样例与实现基线一致性测试，文件：`tests/contracts/contract-baseline.spec.ts`
- [ ] T096 执行基础阶段门禁并记录平台工作流、MySQL 持久化边界、权限和迁移隔离证据，文件：`specs/002-pdp-product/evidence/foundation-validation.md`

## 阶段 3：US1 工作空间与组织治理（P1）

- [X] T097 [US1] 先更新工作空间治理 API、状态迁移和兼容契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/coverage.md`
- [X] T098 [P] [US1] 编写工作空间、成员和跨空间授权状态机及 API 契约测试，文件：`tests/contracts/us1-workspace-governance.spec.ts`
- [ ] T099 [P] [US1] 编写双工作空间隔离和成员撤权端到端测试，文件：`tests/e2e/us1-workspace-governance.spec.ts`
- [X] T100 [US1] 创建工作空间、组织、成员、角色、数据范围和协作授权公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/010-workspace.xml`
- [X] T101 [US1] 创建工作空间和组织领域模型，文件：`modules/workspace/src/main/java/com/pdp/workspace/domain/`
- [X] T102 [US1] 创建工作空间、组织、成员和授权仓储端口，文件：`modules/workspace/src/main/java/com/pdp/workspace/port/`
- [X] T103 [US1] 创建工作空间治理 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/workspace/`、`modules/persistence-mysql/src/main/resources/mapper/workspace/`
- [X] T104 [US1] 创建唯一性、层级、数据范围和游标分页 MySQL 契约测试，文件：`tests/backend/contract/workspace/WorkspaceGovernanceDatabaseContractTest.java`
- [X] T105 [US1] 实现工作空间、组织、成员、角色和数据范围管理服务，文件：`modules/workspace/src/main/java/com/pdp/workspace/application/WorkspaceGovernanceService.java`
- [X] T106 [US1] 实现跨工作空间授权、到期和撤销服务，文件：`modules/workspace/src/main/java/com/pdp/workspace/application/CollaborationGrantService.java`
- [X] T107 [US1] 实现工作空间治理控制器，文件：`apps/api/src/main/java/com/pdp/api/workspace/WorkspaceController.java`
- [X] T108 [US1] 实现工作空间选择、组织、成员、角色和授权页面，文件：`apps/web/src/views/workspace/`
- [X] T109 [US1] 验证查询、搜索、导出和附件跨空间隔离，文件：`tests/backend/security/WorkspaceIsolationSecurityTest.java`
- [X] T110 [US1] 记录 US1 独立验收、状态机、撤权、日志指标追踪和 MySQL 闭环证据，文件：`specs/002-pdp-product/evidence/us1-workspace-governance.md`

## 阶段 4：US2 领域包与深度定制（P1）

- [X] T111 [US2] 先更新领域包 API、Schema、状态机、影响预览和扩展兼容契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/domain-package.schema.json`、`specs/002-pdp-product/contracts/coverage.md`
- [X] T112 [P] [US2] 编写领域包草稿、校验、审核、发布、退役、回滚和迁移状态机 API 契约测试，文件：`tests/contracts/us2-domain-package.spec.ts`
- [ ] T113 [P] [US2] 编写对象、字段、关系、页面、状态、规则和权限设计端到端测试，文件：`tests/e2e/us2-domain-package.spec.ts`
- [X] T114 [US2] 创建核心字段目录、领域包、版本、对象、字段、关系、页面、状态、规则、动作和迁移计划公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/020-domain-package.xml`
- [X] T115 [US2] 创建领域包和版本聚合模型，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/domain/packageversion/`
- [X] T116 [US2] 创建统一核心字段目录以及动态对象、字段、关系和页面元模型，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/domain/metamodel/`
- [X] T117 [US2] 创建状态、规则、动作、权限和受治理扩展模型，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/domain/behavior/`
- [X] T118 [US2] 创建领域包、元模型、扩展和迁移仓储端口，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/port/`
- [X] T119 [US2] 创建领域包 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/domainconfig/`、`modules/persistence-mysql/src/main/resources/mapper/domainconfig/`
- [X] T120 [US2] 创建 JSON、版本、唯一性、继承、核心字段冲突和分页 MySQL 契约测试，文件：`tests/backend/contract/domainconfig/DomainPackageDatabaseContractTest.java`
- [X] T121 [US2] 实现核心字段复用、顶层生命周期映射、元模型、规则引用、权限和兼容性校验服务，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/application/DomainPackageValidationService.java`
- [X] T122 [US2] 实现三层继承、差异、冲突检测和版本快照服务，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/application/DomainPackageCompositionService.java`
- [X] T123 [US2] 实现职责分离的测试、审核、发布、冻结和回滚服务，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/application/DomainPackageLifecycleService.java`
- [X] T124 [US2] 使用高风险操作框架实现升级影响预览、分批迁移、失败隔离和回滚服务，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/application/DomainPackageMigrationService.java`
- [X] T125 [US2] 实现领域包对平台流程定义的版本化绑定、变量映射、启动条件、权限声明和迁移校验，不允许领域包嵌入 Flowable 专有 API，文件：`modules/domainconfig/src/main/java/com/pdp/domainconfig/application/WorkflowBindingService.java`
- [X] T126 [US2] 实现领域包控制器和设计器，文件：`apps/api/src/main/java/com/pdp/api/domainconfig/DomainPackageController.java`、`apps/web/src/views/domain-package/`
- [ ] T127 [US2] 创建网络设备割接示例包并记录状态机、日志指标追踪、独立审核、影响预览和发布闭环证据，文件：`tests/fixtures/domain-package/network-cutover-package.json`、`specs/002-pdp-product/evidence/us2-domain-package.md`

## 阶段 5：US3 项目模板与项目创建（P1）

- [X] T128 [US3] 先更新项目模板、实例化和项目创建 API 契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/coverage.md`
- [X] T129 [P] [US3] 编写项目模板版本状态、原子实例化和从模板创建项目 API 契约测试，文件：`tests/contracts/us3-project-template.spec.ts`
- [ ] T130 [P] [US3] 编写模板维护、创建向导和版本快照端到端测试，文件：`tests/e2e/us3-project-template.spec.ts`
- [ ] T131 [US3] 创建项目模板、模板版本、模板组件和实例化记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/030-project-template.xml`
- [ ] T132 [US3] 创建项目模板、版本和实例化计划领域模型及仓储端口，文件：`modules/template/src/main/java/com/pdp/template/domain/`、`modules/template/src/main/java/com/pdp/template/port/`
- [ ] T133 [US3] 创建项目模板 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/template/`、`modules/persistence-mysql/src/main/resources/mapper/template/`
- [ ] T134 [US3] 创建版本快照、组件顺序和实例化幂等 MySQL 契约测试，文件：`tests/backend/contract/template/ProjectTemplateDatabaseContractTest.java`
- [ ] T135 [US3] 实现项目模板编辑、发布、冻结和版本比较服务，文件：`modules/template/src/main/java/com/pdp/template/application/ProjectTemplateService.java`
- [ ] T136 [US3] 实现项目、阶段、任务、里程碑、检查项、交付件和审批计划原子实例化服务，文件：`modules/template/src/main/java/com/pdp/template/application/ProjectInstantiationService.java`
- [ ] T137 [US3] 实现项目模板和创建项目控制器，文件：`apps/api/src/main/java/com/pdp/api/template/ProjectTemplateController.java`
- [ ] T138 [US3] 实现模板维护、预览和项目创建向导，文件：`apps/web/src/views/project-template/`
- [ ] T139 [US3] 验证新项目使用新版本且存量项目保持原版本快照，文件：`tests/backend/integration/template/ProjectTemplateSnapshotTest.java`
- [ ] T140 [US3] 记录 US3 默认计划准确性、日志指标追踪、幂等和独立闭环证据，文件：`specs/002-pdp-product/evidence/us3-project-template.md`

## 阶段 6：US4 项目生命周期与主子项目（P1）

- [ ] T141 [US4] 先更新项目、阶段状态机、影响预览和主子项目 API 契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T142 [P] [US4] 编写项目与阶段状态机、受控回退、关闭和主子项目 API 契约测试，文件：`tests/contracts/us4-project-lifecycle.spec.ts`
- [ ] T143 [P] [US4] 编写主子项目执行、汇总和关闭门禁端到端测试，文件：`tests/e2e/us4-project-lifecycle.spec.ts`
- [ ] T144 [US4] 创建项目、项目成员、阶段实例、父子关系和生命周期记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/040-project-lifecycle.xml`
- [ ] T145 [US4] 创建项目、阶段实例和父子项目领域模型，文件：`modules/project/src/main/java/com/pdp/project/domain/`
- [ ] T146 [US4] 创建项目、阶段和层级查询仓储端口，文件：`modules/project/src/main/java/com/pdp/project/port/`
- [ ] T147 [US4] 创建项目生命周期 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/project/`、`modules/persistence-mysql/src/main/resources/mapper/project/`
- [ ] T148 [US4] 创建层级完整性、阶段并发和汇总 MySQL 契约测试，文件：`tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java`
- [ ] T149 [US4] 使用高风险操作框架实现目标、范围、成员、统一顶层生命周期、受控回退、关闭、归档、恢复和作废服务，文件：`modules/project/src/main/java/com/pdp/project/application/ProjectLifecycleService.java`
- [ ] T150 [US4] 实现父子项目权限汇总和关闭条件服务，文件：`modules/project/src/main/java/com/pdp/project/application/ProjectHierarchyService.java`
- [ ] T151 [US4] 实现项目生命周期控制器，文件：`apps/api/src/main/java/com/pdp/api/project/ProjectLifecycleController.java`
- [ ] T152 [US4] 实现项目概览、阶段推进和主子项目页面，文件：`apps/web/src/views/project/`
- [ ] T153 [US4] 记录项目/阶段状态机、影响预览、日志指标追踪、关闭门禁和权限闭环证据，文件：`specs/002-pdp-product/evidence/us4-project-lifecycle.md`

## 阶段 7：US5 任务、检查项与团队协作（P1）

- [ ] T154 [US5] 先更新任务协作、任务状态机 API 和事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T155 [P] [US5] 编写任务状态机、子任务、检查项、指派、评论和附件 API 契约测试，文件：`tests/contracts/us5-task-collaboration.spec.ts`
- [ ] T156 [P] [US5] 编写父子任务、强制检查项和协作端到端测试，文件：`tests/e2e/us5-task-collaboration.spec.ts`
- [ ] T157 [US5] 创建任务、检查项、指派、评论、关注和活动记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/050-task-collaboration.xml`
- [ ] T158 [US5] 创建任务、检查项和活动领域模型，文件：`modules/planning/src/main/java/com/pdp/planning/domain/task/`
- [ ] T159 [US5] 创建任务、检查项和协作仓储端口，文件：`modules/planning/src/main/java/com/pdp/planning/port/task/`
- [ ] T160 [US5] 创建任务协作 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/task/`、`modules/persistence-mysql/src/main/resources/mapper/task/`
- [ ] T161 [US5] 创建层级、完成门禁、乐观锁和分页 MySQL 契约测试，文件：`tests/backend/contract/planning/TaskCollaborationDatabaseContractTest.java`
- [ ] T162 [US5] 实现任务分解、指派、检查项门禁、状态和进度服务，文件：`modules/planning/src/main/java/com/pdp/planning/application/TaskService.java`
- [ ] T163 [US5] 实现评论、提及、关注、附件关联和活动记录服务，文件：`modules/experience/src/main/java/com/pdp/experience/collaboration/TaskCollaborationService.java`
- [ ] T164 [US5] 实现任务控制器，文件：`apps/api/src/main/java/com/pdp/api/planning/TaskController.java`
- [ ] T165 [US5] 实现任务树、详情、检查项和协作页面，文件：`apps/web/src/views/task/`
- [ ] T166 [US5] 创建重复提交、高并发更新和进度汇总测试，文件：`tests/backend/integration/planning/TaskConcurrencyTest.java`
- [ ] T167 [US5] 记录 US5 状态机、完成门禁、活动审计、日志指标追踪和独立闭环证据，文件：`specs/002-pdp-product/evidence/us5-task-collaboration.md`

## 阶段 8：US6 里程碑、依赖和计划基线（P1）

- [ ] T168 [US6] 先更新计划、基线、影响预览和状态事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T169 [P] [US6] 编写里程碑和基线状态机、依赖、影响预览和偏差 API 契约测试，文件：`tests/contracts/us6-plan-baseline.spec.ts`
- [ ] T170 [P] [US6] 编写依赖计划、循环拒绝、建基线和偏差端到端测试，文件：`tests/e2e/us6-plan-baseline.spec.ts`
- [ ] T171 [US6] 创建里程碑、依赖、基线和快照公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/060-plan-baseline.xml`
- [ ] T172 [US6] 创建里程碑、依赖和基线领域模型及仓储端口，文件：`modules/planning/src/main/java/com/pdp/planning/domain/plan/`、`modules/planning/src/main/java/com/pdp/planning/port/plan/`
- [ ] T173 [US6] 创建计划基线 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/plan/`、`modules/persistence-mysql/src/main/resources/mapper/plan/`
- [ ] T174 [US6] 创建循环检测、快照一致性和日期计算 MySQL 契约测试，文件：`tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java`
- [ ] T175 [US6] 实现里程碑、依赖闭环检测和受影响事项服务，文件：`modules/planning/src/main/java/com/pdp/planning/application/DependencyPlanningService.java`
- [ ] T176 [US6] 使用高风险操作框架实现基线创建/替换、比较、里程碑权重、必需产出、可解释进度和经审批人工调整服务，文件：`modules/planning/src/main/java/com/pdp/planning/application/BaselineService.java`、`modules/planning/src/main/java/com/pdp/planning/application/ProgressCalculationService.java`
- [ ] T177 [US6] 实现计划基线控制器，文件：`apps/api/src/main/java/com/pdp/api/planning/PlanBaselineController.java`
- [ ] T178 [US6] 实现里程碑、依赖、基线差异和可解释进度页面，文件：`apps/web/src/views/plan/`
- [ ] T179 [US6] 创建跨时区、并发建基线、权重归一、必需产出和人工调整测试，文件：`tests/backend/integration/planning/PlanBaselineConcurrencyTest.java`
- [ ] T180 [US6] 记录 US6 状态机、影响预览、循环路径、日志指标追踪、偏差和独立闭环证据，文件：`specs/002-pdp-product/evidence/us6-plan-baseline.md`

## 阶段 9：US7 交付件全生命周期（P1）

- [ ] T181 [US7] 先更新交付件、附件、签核、发布预览和事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T182 [P] [US7] 编写交付件状态机、创建、提交、审核、签核、发布预览和归档 API 契约测试，文件：`tests/contracts/us7-deliverable.spec.ts`
- [ ] T183 [P] [US7] 编写 v1 退回、v2 发布、签名和归档端到端测试，文件：`tests/e2e/us7-deliverable.spec.ts`
- [ ] T184 [US7] 创建交付件、版本、附件、审核、签核和发布记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/070-deliverable.xml`
- [ ] T185 [US7] 创建交付件、版本、命名到期约束、内部/外部审核和签核领域模型，文件：`modules/deliverable/src/main/java/com/pdp/deliverable/domain/`
- [ ] T186 [US7] 创建交付件、版本和发布仓储端口，文件：`modules/deliverable/src/main/java/com/pdp/deliverable/port/`
- [ ] T187 [US7] 创建交付件 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/deliverable/`、`modules/persistence-mysql/src/main/resources/mapper/deliverable/`
- [ ] T188 [US7] 创建版本序列、签核不可变性和分页 MySQL 契约测试，文件：`tests/backend/contract/deliverable/DeliverableDatabaseContractTest.java`
- [ ] T189 [US7] 使用高风险操作框架实现模板、创建、退回、签核、修订、发布影响预览、引用和归档服务，文件：`modules/deliverable/src/main/java/com/pdp/deliverable/application/DeliverableService.java`
- [ ] T190 [US7] 实现附件签名 URL、五分钟有效期、再鉴权、扫描和隔离，文件：`modules/experience/src/main/java/com/pdp/experience/storage/AttachmentAccessService.java`
- [ ] T191 [US7] 实现阶段必需交付件完成门禁，文件：`modules/deliverable/src/main/java/com/pdp/deliverable/application/DeliverableGateService.java`
- [ ] T192 [US7] 实现交付件控制器和全生命周期页面，文件：`apps/api/src/main/java/com/pdp/api/deliverable/DeliverableController.java`、`apps/web/src/views/deliverable/`
- [ ] T193 [US7] 创建越权下载、签名过期、病毒命中和原版本不可覆盖测试，文件：`tests/backend/security/DeliverableSecurityTest.java`
- [ ] T194 [US7] 记录 US7 状态机、版本、签核、发布预览、日志指标追踪、归档和独立闭环证据，文件：`specs/002-pdp-product/evidence/us7-deliverable.md`

## 阶段 10：US8 统一审批中心（P1，复用平台工作流）

- [ ] T195 [US8] 先更新审批状态机、最小字段、平台工作流绑定、终态预览和事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T196 [P] [US8] 编写审批状态机、定义、实例、节点动作、终态预览、退回和重提 API 契约测试，文件：`tests/contracts/us8-approval.spec.ts`
- [ ] T197 [P] [US8] 编写交付件与变更审批、超时和状态回写端到端测试，文件：`tests/e2e/us8-approval.spec.ts`
- [ ] T198 [US8] 创建审批定义、实例、轮次、节点、动作和意见公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/080-approval.xml`
- [ ] T199 [US8] 创建审批定义、实例、轮次和节点领域模型，文件：`modules/approval/src/main/java/com/pdp/approval/domain/`
- [ ] T200 [US8] 创建审批定义、实例和待办仓储端口，文件：`modules/approval/src/main/java/com/pdp/approval/port/`
- [ ] T201 [US8] 创建审批 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/approval/`、`modules/persistence-mysql/src/main/resources/mapper/approval/`
- [ ] T202 [US8] 创建节点顺序、轮次保留、幂等动作和并发审批 MySQL 契约测试，文件：`tests/backend/contract/approval/ApprovalDatabaseContractTest.java`
- [ ] T203 [US8] 使用平台工作流运行/任务端口和高风险操作框架实现审批定义校验、发起、终态影响预览、通过、拒绝、撤回、退回、重提、转交、委托、加签、抄送和终止服务，文件：`modules/approval/src/main/java/com/pdp/approval/application/ApprovalService.java`
- [ ] T204 [US8] 实现审批最小字段披露和办理时权限复核，文件：`modules/approval/src/main/java/com/pdp/approval/application/ApprovalAuthorizationService.java`
- [ ] T205 [US8] 使用平台工作流定时器与事件桥接实现审批超时提醒、失败补偿和业务状态回写，文件：`modules/approval/src/main/java/com/pdp/approval/application/ApprovalWorkflowCoordinator.java`
- [ ] T206 [US8] 实现审批控制器和统一审批中心页面，文件：`apps/api/src/main/java/com/pdp/api/approval/ApprovalController.java`、`apps/web/src/views/approval/`
- [ ] T207 [US8] 创建重复点击、并发审批、退回重提和撤权测试，文件：`tests/backend/integration/approval/ApprovalResilienceTest.java`
- [ ] T208 [US8] 创建 Flowable 暂停、异步作业重试、重复回调、流程版本固定和恢复后无重复审批结论测试，文件：`tests/backend/integration/approval/ApprovalWorkflowRecoveryTest.java`
- [ ] T209 [US8] 记录 US8 状态机、节点、终态预览、敏感字段、日志指标追踪和状态回写闭环证据，文件：`specs/002-pdp-product/evidence/us8-approval.md`

## 阶段 11：US9 多视图项目工作区（P1）

- [ ] T210 [US9] 先更新项目工作区、保存视图和基础统计契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T211 [P] [US9] 编写概览、列表、看板、日历、时间线和保存视图 API 契约测试，文件：`tests/contracts/us9-project-workspace.spec.ts`
- [ ] T212 [P] [US9] 编写跨视图修改一致性和个人视图恢复端到端测试，文件：`tests/e2e/us9-project-workspace.spec.ts`
- [ ] T213 [US9] 创建个人保存视图、布局、筛选和排序公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/090-saved-view.xml`
- [ ] T214 [US9] 创建保存视图领域模型和多视图查询端口，文件：`modules/experience/src/main/java/com/pdp/experience/domain/view/`、`modules/experience/src/main/java/com/pdp/experience/port/view/`
- [ ] T215 [US9] 创建保存视图 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/savedview/`、`modules/persistence-mysql/src/main/resources/mapper/savedview/`
- [ ] T216 [US9] 创建视图所有权、布局 JSON 和稳定排序 MySQL 契约测试，文件：`tests/backend/contract/experience/SavedViewDatabaseContractTest.java`
- [ ] T217 [US9] 实现权限一致的概览、统计钻取、列表、看板状态/WIP 约束、日历和时间线服务，文件：`modules/experience/src/main/java/com/pdp/experience/application/ProjectWorkspaceQueryService.java`
- [ ] T218 [US9] 实现个人保存视图以及角色默认视图的发布、恢复、复制和删除服务，文件：`modules/experience/src/main/java/com/pdp/experience/application/SavedViewService.java`
- [ ] T219 [US9] 实现多视图查询与保存视图控制器，文件：`apps/api/src/main/java/com/pdp/api/experience/ProjectWorkspaceController.java`
- [ ] T220 [US9] 实现概览、列表、看板、日历、时间线和活动页，文件：`apps/web/src/views/project-workspace/`
- [ ] T221 [US9] 创建视图间数据一致、同权限和并发编辑测试，文件：`tests/backend/integration/experience/MultiViewConsistencyTest.java`
- [ ] T222 [US9] 记录 US9 多视图、基础统计、日志指标追踪和独立闭环证据，文件：`specs/002-pdp-product/evidence/us9-project-workspace.md`

## 阶段 12：US10 风险、问题与变更控制（P1）

- [ ] T223 [US10] 先更新风险、问题、变更状态机、影响预览 API 和事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T224 [P] [US10] 编写风险、问题和变更状态机、重开/取消/关闭原因、影响预览 API 契约测试，文件：`tests/contracts/us10-governance-control.spec.ts`
- [ ] T225 [P] [US10] 编写风险转问题、发起变更、审批和应用基线端到端测试，文件：`tests/e2e/us10-governance-control.spec.ts`
- [ ] T226 [US10] 创建风险、问题、措施和双向关联公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/100-risk-issue.xml`
- [ ] T227 [US10] 创建变更请求、影响项、决策和执行记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/101-change-request.xml`
- [ ] T228 [US10] 创建风险、问题和措施领域模型及仓储端口，文件：`modules/governance/src/main/java/com/pdp/governance/domain/risk/`、`modules/governance/src/main/java/com/pdp/governance/port/risk/`
- [ ] T229 [US10] 创建变更请求、影响分析和决策领域模型及仓储端口，文件：`modules/governance/src/main/java/com/pdp/governance/domain/change/`、`modules/governance/src/main/java/com/pdp/governance/port/change/`
- [ ] T230 [US10] 创建风险问题 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/risk/`、`modules/persistence-mysql/src/main/resources/mapper/risk/`
- [ ] T231 [US10] 创建变更请求 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/change/`、`modules/persistence-mysql/src/main/resources/mapper/change/`
- [ ] T232 [US10] 创建评分、双向关联、影响项和并发决策 MySQL 契约测试，文件：`tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java`
- [ ] T233 [US10] 实现风险评估、风险转问题、措施跟踪以及重开、取消、解决和关闭原因校验服务，文件：`modules/governance/src/main/java/com/pdp/governance/application/RiskIssueService.java`
- [ ] T234 [US10] 使用高风险操作框架实现影响分析、预览确认、审批联动和批准内容应用服务，文件：`modules/governance/src/main/java/com/pdp/governance/application/ChangeRequestService.java`
- [ ] T235 [US10] 实现变更应用幂等执行、基线差异保留和失败补偿，文件：`modules/governance/src/main/java/com/pdp/governance/application/ChangeExecutionService.java`
- [ ] T236 [US10] 实现风险问题和变更控制器，文件：`apps/api/src/main/java/com/pdp/api/governance/`
- [ ] T237 [US10] 实现风险矩阵、问题台账、变更申请和影响差异页面，文件：`apps/web/src/views/governance/`
- [ ] T238 [US10] 创建重复转换、部分失败、回滚和越权决策测试，文件：`tests/backend/integration/governance/GovernanceControlResilienceTest.java`
- [ ] T239 [US10] 记录 US10 状态机、原因、影响预览、日志指标追踪、双向关联和独立闭环证据，文件：`specs/002-pdp-product/evidence/us10-governance-control.md`

## 阶段 13：US11 权限、审计与数据生命周期（P1）

- [ ] T240 [US11] 先更新权限、审计、导出、保留、处置预览和生命周期事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T241 [P] [US11] 编写角色、数据范围、对象字段操作权限和临时授权 API 契约测试，文件：`tests/contracts/us11-authorization.spec.ts`
- [ ] T242 [P] [US11] 编写审计查询、导出、归档和处置 API 契约测试，文件：`tests/contracts/us11-audit-lifecycle.spec.ts`
- [ ] T243 [P] [US11] 编写代理商隔离、导出再鉴权和法律保留端到端测试，文件：`tests/e2e/us11-security-lifecycle.spec.ts`
- [ ] T244 [US11] 创建权限策略、角色绑定、数据范围、字段权限和临时授权公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/110-authorization.xml`
- [ ] T245 [US11] 创建审计事件、导出申请、保留策略、法律保留和处置记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/111-audit-lifecycle.xml`
- [ ] T246 [US11] 创建授权策略和临时授权领域模型及仓储端口，文件：`modules/identity/src/main/java/com/pdp/identity/domain/authorization/`、`modules/identity/src/main/java/com/pdp/identity/port/authorization/`
- [ ] T247 [US11] 创建审计、导出、保留和处置领域模型及仓储端口，文件：`modules/governance/src/main/java/com/pdp/governance/domain/audit/`、`modules/governance/src/main/java/com/pdp/governance/port/audit/`
- [ ] T248 [US11] 创建授权 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/authorization/`、`modules/persistence-mysql/src/main/resources/mapper/authorization/`
- [ ] T249 [US11] 创建审计生命周期 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/audit/`、`modules/persistence-mysql/src/main/resources/mapper/audit/`
- [ ] T250 [US11] 创建授权优先级、审计摘要链防篡改和保留/处置状态机 MySQL 契约测试，文件：`tests/backend/contract/security/AuthorizationAuditDatabaseContractTest.java`
- [ ] T251 [US11] 实现功能、数据、对象、字段、操作和临时授权服务，文件：`modules/identity/src/main/java/com/pdp/identity/application/PolicyAdministrationService.java`
- [ ] T252 [US11] 实现只追加审计写入、摘要链校验、脱敏、检索和证据关联服务，文件：`modules/governance/src/main/java/com/pdp/governance/application/AuditService.java`
- [ ] T253 [US11] 使用数据分类和高风险操作框架实现保留、归档、法律保留、处置预览、批准处置和不可删除约束服务，文件：`modules/governance/src/main/java/com/pdp/governance/application/DataLifecycleService.java`
- [ ] T254 [US11] 实现导出申请、执行、下载三阶段再鉴权和过期清理，文件：`modules/governance/src/main/java/com/pdp/governance/application/AuditExportService.java`
- [ ] T255 [US11] 实现权限管理、审计检索、导出和数据处置控制器，文件：`apps/api/src/main/java/com/pdp/api/security/`、`apps/api/src/main/java/com/pdp/api/audit/`
- [ ] T256 [US11] 实现权限、审计、导出、保留和处置页面，文件：`apps/web/src/views/admin/security/`、`apps/web/src/views/audit/`
- [ ] T257 [US11] 记录代理商隔离、撤权、审计防篡改、处置预览、日志指标追踪和生命周期闭环证据，文件：`specs/002-pdp-product/evidence/us11-security-lifecycle.md`

## 阶段 14：US14 基础搜索与站内通知（P1）

- [ ] T258 [US14] 先更新搜索、通知、撤权时限和事件消费者契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T259 [P] [US14] 编写权限搜索、通知列表、已读和重提 API 契约测试，文件：`tests/contracts/us14-search-notification.spec.ts`
- [ ] T260 [P] [US14] 编写跨项目搜索、结果跳转和站内通知端到端测试，文件：`tests/e2e/us14-search-notification.spec.ts`
- [ ] T261 [US14] 创建搜索投影游标、站内通知和已读状态公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/140-search-notification.xml`
- [ ] T262 [US14] 创建站内通知模型、搜索查询端口和通知仓储端口，文件：`modules/experience/src/main/java/com/pdp/experience/domain/notification/`、`modules/experience/src/main/java/com/pdp/experience/port/`
- [ ] T263 [US14] 创建通知 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/notification/`、`modules/persistence-mysql/src/main/resources/mapper/notification/`
- [ ] T264 [US14] 创建通知幂等、排序和权限搜索 MySQL 契约测试，文件：`tests/backend/contract/experience/SearchNotificationDatabaseContractTest.java`
- [ ] T265 [US14] 实现权限裁剪搜索、稳定分页、统一分析和结果高亮服务，文件：`modules/experience/src/main/java/com/pdp/experience/search/GlobalSearchService.java`
- [ ] T266 [US14] 实现站内通知生成、查询、已读、重提和清理服务，文件：`modules/experience/src/main/java/com/pdp/experience/notification/InAppNotificationService.java`
- [ ] T267 [US14] 实现搜索与通知控制器，文件：`apps/api/src/main/java/com/pdp/api/experience/SearchNotificationController.java`
- [ ] T268 [US14] 实现全局搜索、结果页和通知中心，文件：`apps/web/src/views/search/`、`apps/web/src/views/notification/`
- [ ] T269 [US14] 创建 30 秒可见、打开时复核、撤权移除和重复事件测试，文件：`tests/backend/integration/experience/SearchNotificationConsistencyTest.java`
- [ ] T270 [US14] 记录 US14 权限、时效、投影 SLI、日志指标追踪和独立闭环证据，文件：`specs/002-pdp-product/evidence/us14-search-notification.md`

## 阶段 15：US18 高可用与业务连续性（P1）

- [ ] T271 [US18] 先更新运维、服务等级、降级、恢复和可用性报告 API/事件契约，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T272 [P] [US18] 编写健康检查、降级状态、恢复操作和维护窗口 API 契约测试，文件：`tests/contracts/us18-business-continuity.spec.ts`
- [ ] T273 [P] [US18] 编写非核心故障降级和核心故障恢复端到端测试，文件：`tests/e2e/us18-business-continuity.spec.ts`
- [ ] T274 [US18] 创建服务等级档案、告警、恢复操作、维护窗口和可用性日汇总公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/180-operations.xml`
- [ ] T275 [US18] 创建服务等级档案、告警、恢复操作、维护窗口和可用性汇总模型及仓储端口，文件：`modules/operations/src/main/java/com/pdp/operations/domain/`、`modules/operations/src/main/java/com/pdp/operations/port/`
- [ ] T276 [US18] 创建运维 MySQL Mapper、XML 和适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/operations/`、`modules/persistence-mysql/src/main/resources/mapper/operations/`
- [ ] T277 [US18] 创建作业锁、告警幂等和可用性汇总 MySQL 契约测试，文件：`tests/backend/contract/operations/OperationsDatabaseContractTest.java`
- [ ] T278 [US18] 实现服务等级档案、健康检查、依赖诊断、后台作业查询、安全重试和核心故障 5 分钟内值守通知服务，文件：`modules/operations/src/main/java/com/pdp/operations/application/OperationsService.java`
- [ ] T279 [US18] 实现数据库、缓存、对象存储、搜索和外部集成降级恢复策略，文件：`modules/operations/src/main/java/com/pdp/operations/resilience/`
- [ ] T280 [US18] 实现 FR-165 可用性计算、排除项审批和月报生成，文件：`modules/operations/src/main/java/com/pdp/operations/application/AvailabilityReportService.java`
- [ ] T281 [US18] 实现运维控制器和运维页面，文件：`apps/api/src/main/java/com/pdp/api/operations/OperationsController.java`、`apps/web/src/views/admin/operations/`
- [ ] T282 [US18] 创建单实例、节点、数据库、缓存、搜索和外部依赖故障演练，文件：`tests/backend/resilience/PlatformRecoveryTest.java`
- [ ] T283 [US18] 验证 RTO 30 分钟、RPO 5 分钟和非核心故障不破坏核心事务，文件：`tests/backend/resilience/BusinessContinuityObjectiveTest.java`
- [ ] T284 [US18] 验证割接保障窗口无计划停机、滚动发布和核心故障 5 分钟内通知，文件：`tests/backend/resilience/CutoverProtectionWindowTest.java`
- [ ] T285 [US18] 记录 US18 服务等级档案、告警、降级、恢复、日志指标追踪和可用性闭环证据，文件：`specs/002-pdp-product/evidence/us18-business-continuity.md`

## 阶段 16：US20 MySQL 历史数据迁移与上线切换（P1）

- [ ] T286 [US20] 先更新迁移 API、事件和迁移报告 Schema，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/migration-report.schema.json`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T287 [P] [US20] 编写迁移评估、批次、校验、切换和回退 API 契约测试，文件：`tests/contracts/us20-mysql-migration.spec.ts`
- [ ] T288 [P] [US20] 编写迁移演练、差异查看、断点续跑和切换端到端测试，文件：`tests/e2e/us20-mysql-migration.spec.ts`
- [ ] T289 [US20] 创建迁移任务、批次、游标、映射、差异和切换记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/200-migration-control.xml`
- [ ] T290 [US20] 创建迁移任务、批次、差异和切换领域模型，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/domain/`
- [ ] T291 [US20] 创建迁移控制、来源追踪和核对仓储端口，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/port/`
- [ ] T292 [US20] 创建历史源库只读 Mapper 与 SQL，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/source/`、`modules/datamigration/src/main/resources/mapper/source/`
- [ ] T293 [US20] 创建目标库写入 Mapper、XML 和控制表适配器，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/target/`、`modules/datamigration/src/main/resources/mapper/target/`
- [ ] T294 [US20] 创建 PDP MySQL 目标迁移控制表契约测试，文件：`tests/backend/contract/datamigration/MigrationControlDatabaseContractTest.java`
- [ ] T295 [US20] 实现源库结构、字符集、时区、主键、枚举和数据质量评估，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/MigrationAssessmentService.java`
- [ ] T296 [US20] 实现字段、标识、状态、组织、权限和附件映射服务，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/MigrationMappingService.java`
- [ ] T297 [US20] 实现分批读取、幂等写入、断点续跑、限速和问题隔离执行器，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/MigrationExecutor.java`
- [ ] T298 [US20] 实现总量、主键、引用、金额、时间、权限和附件核对服务，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/MigrationReconciliationService.java`
- [ ] T299 [US20] 使用高风险操作框架实现增量追平、影响预览、写入冻结、切换门禁、回退点和前向修复编排，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/MigrationCutoverService.java`
- [ ] T300 [US20] 实现迁移控制器和迁移管理页面，文件：`apps/api/src/main/java/com/pdp/api/datamigration/MigrationController.java`、`apps/web/src/views/admin/migration/`
- [ ] T301 [US20] 创建乱码、非法日期、重复键、孤儿关系、源中断和目标回滚测试，文件：`tests/backend/integration/datamigration/MySqlHistoryMigrationResilienceTest.java`
- [ ] T302 [US20] 完成至少两轮全量和一轮增量切换彩排，文件：`specs/002-pdp-product/evidence/us20-migration-rehearsal.md`
- [ ] T303 [US20] 验证迁移对象可追溯到源系统、表、主键、批次和映射版本，文件：`tests/backend/integration/datamigration/MigrationLineageTest.java`
- [ ] T304 [US20] 记录 US20 状态机、检查点、切换预览、日志指标追踪、回退和业务签字闭环证据，文件：`specs/002-pdp-product/evidence/us20-mysql-migration.md`

## 阶段 17：US21 统一数据库部署与受控切换（P1 认证 MYSQL→MYSQL）

- [ ] T305 [US21] 校准受管数据库部署列表、当前主部署、切换创建、运行和审计 OpenAPI/事件契约，保持 P1 `MYSQL→MYSQL` 枚举并保留 P2 产品扩展兼容性，文件：`specs/002-pdp-product/contracts/openapi.yaml`、`specs/002-pdp-product/contracts/events.md`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T306 [P] [US21] 编写数据库部署查询、切换创建、状态推进、冲突和回退 API 契约测试，文件：`tests/contracts/us21-database-switch.spec.ts`
- [ ] T307 [P] [US21] 编写两个独立 MySQL 部署之间的全量、增量、冻结、主权切换和回退端到端测试，文件：`tests/e2e/us21-mysql-database-switch.spec.ts`
- [ ] T308 [US21] 创建数据库部署、切换能力组合、切换计划、运行、核对和主权记录公共变更集，文件：`modules/public-persistence/src/main/resources/db/changelog/common/210-database-switch.xml`
- [ ] T309 [US21] 创建数据库部署、能力组合和切换聚合及确定性状态机，文件：`modules/operations/src/main/java/com/pdp/operations/domain/databaseswitch/`
- [ ] T310 [US21] 创建数据库部署、切换计划、运行、核对和审计仓储端口，文件：`modules/operations/src/main/java/com/pdp/operations/port/databaseswitch/`
- [ ] T311 [US21] 创建 MySQL 数据库部署、能力组合和切换控制 Mapper、XML 及适配器，文件：`modules/persistence-mysql/src/main/java/com/pdp/mysql/databaseswitch/`、`modules/persistence-mysql/src/main/resources/mapper/databaseswitch/`
- [ ] T312 [US21] 创建数据库部署标识、能力画像、唯一写入主权、状态恢复和乐观并发 MySQL 契约测试，文件：`tests/backend/contract/databaseswitch/DatabaseSwitchDatabaseContractTest.java`
- [ ] T313 [US21] 实现受管数据库部署登记、能力探测、认证组合校验和当前主部署查询服务，文件：`modules/operations/src/main/java/com/pdp/operations/application/DatabaseDeploymentService.java`
- [ ] T314 [US21] 实现统一数据库切换计划、预检、演练、暂停、取消和回退点服务，文件：`modules/operations/src/main/java/com/pdp/operations/application/DatabaseSwitchPlanningService.java`
- [ ] T315 [US21] 实现 MySQL 源目标全量装载、增量捕获、断点续跑和幂等重放执行器，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/DatabaseSwitchExecutor.java`
- [ ] T316 [US21] 实现数量、关系、审批链、附件引用、schema、位点和业务抽样核对服务，文件：`modules/datamigration/src/main/java/com/pdp/datamigration/application/DatabaseSwitchReconciliationService.java`
- [ ] T317 [US21] 使用高风险操作框架实现冻结、最终增量、单写主权切换、稳定观察、回退和前向修复编排，文件：`modules/operations/src/main/java/com/pdp/operations/application/DatabaseWriteAuthorityService.java`
- [ ] T318 [US21] 实现数据库部署查询和切换控制器，文件：`apps/api/src/main/java/com/pdp/api/operations/DatabaseSwitchController.java`
- [ ] T319 [US21] 实现数据库部署、能力、切换计划、进度、核对、门禁和回退管理页面，文件：`apps/web/src/views/admin/database-switch/`
- [ ] T320 [US21] 创建未认证产品/版本/组合、源目标相同、schema 不兼容、增量落后、核对失败、冻结冲突和双主写入拦截测试，文件：`tests/backend/integration/databaseswitch/DatabaseSwitchSafetyTest.java`
- [ ] T321 [US21] 完成两个独立 MySQL 8.4 生产等价部署之间的双向切换和回退演练，文件：`specs/002-pdp-product/evidence/us21-mysql-switch-rehearsal.md`
- [ ] T322 [US21] 记录 US21 状态机、部署身份、能力组合、核对、单写主权、审计、回退和 P2 扩展兼容证据，文件：`specs/002-pdp-product/evidence/us21-database-switch.md`

## 阶段 18：标准实施领域包与 P1 闭环

- [ ] T323 更新标准实施包对象、状态、规则、权限和迁移 Schema 契约，文件：`specs/002-pdp-product/contracts/domain-package.schema.json`、`specs/002-pdp-product/contracts/coverage.md`
- [ ] T324 编写标准实施包 Schema、发布和版本快照契约测试，文件：`tests/contracts/standard-delivery-package.spec.ts`
- [ ] T325 定义标准实施领域包的工前准备、施工计划、实施方案、部署、验收和归档结构，文件：`modules/standarddelivery/src/main/resources/domain-packages/standard-delivery.json`
- [ ] T326 配置标准实施包对象、字段、关系、角色和权限，文件：`modules/standarddelivery/src/main/resources/domain-packages/standard-delivery-model.json`
- [ ] T327 配置标准实施包阶段、状态、规则、动作、退出条件及平台流程绑定，文件：`modules/standarddelivery/src/main/resources/domain-packages/standard-delivery-workflow.json`、`modules/standarddelivery/src/main/resources/processes/standard-delivery.bpmn20.xml`
- [ ] T328 配置标准任务、里程碑、检查项、交付件和审批模板，文件：`modules/standarddelivery/src/main/resources/domain-packages/standard-delivery-template.json`
- [ ] T329 编写从创建项目到验收归档的完整端到端测试，文件：`tests/e2e/standard-delivery-p1-journey.spec.ts`
- [ ] T330 创建不少于 20 项代表性领域需求配置样本，文件：`tests/fixtures/domain-package/representative-requirements.json`
- [ ] T331 验证至少 80% 样本无需修改平台核心代码即可满足，文件：`specs/002-pdp-product/evidence/sc-012-template-configurability.md`
- [ ] T332 按规格最低规模使用真实或生产等价标准实施项目完成从创建到归档试点，覆盖 3 类角色、主子项目、阶段、任务、里程碑、交付件、审批、治理对象、历史数据及失败/并发场景，文件：`specs/002-pdp-product/evidence/standard-delivery-pilot.md`
- [ ] T333 验证标准实施包升级不绕过权限、审计、版本和迁移门禁，文件：`tests/backend/integration/standarddelivery/StandardDeliveryUpgradeTest.java`
- [ ] T334 记录标准实施项目 15 个 P1 闭环、服务等级和高风险操作综合证据，文件：`specs/002-pdp-product/evidence/standard-delivery-p1-closure.md`

## 阶段 19：横向质量门禁与发布准备

- [ ] T335 [P] 执行 OpenAPI、JSON Schema、事件样例和实现路由的双向差异检查，文件：`tests/contracts/implementation-contract-diff.spec.ts`
- [ ] T336 [P] 执行 MySQL 8.4 全量数据库契约、空库安装和升级矩阵，验证 `MYSQL→MYSQL` 切换通过且未认证数据库产品、版本和组合被拒绝，文件：`specs/002-pdp-product/evidence/mysql-contract-matrix.md`
- [ ] T337 [P] 执行静态分析、依赖漏洞、许可证和密钥扫描，文件：`.github/workflows/security.yml`
- [ ] T338 复核并更新平台威胁模型、数据分类和遗留风险处置状态，并执行跨工作空间越权、对象字段授权、导出再鉴权、附件签名及审计防篡改安全测试，文件：`docs/security/threat-model.md`、`docs/security/data-classification.md`、`tests/backend/security/PlatformAuthorizationSecurityTest.java`
- [ ] T339 执行所有权限撤销路径 SLA 测试并证明 100% 达标，文件：`specs/002-pdp-product/evidence/sc-036-permission-revocation.md`
- [ ] T340 按规格性能档案在 MySQL 8.4 生产等价拓扑使用百万级数据和冻结事务集执行 10 分钟预热、30 分钟测量及 1000 并发混合负载，覆盖核心读写、搜索、审批、导出和历史迁移，文件：`tests/performance/p1-platform.k6.js`
- [ ] T341 按 FR-165 分别校验核心业务与其他在线能力的月度可用性、适用交互时限、排除项审批和报告证据完整性，文件：`specs/002-pdp-product/evidence/sc-016-sc-037-availability.md`
- [ ] T342 校验 P1 服务等级档案对所有关键能力的请求类别、SLI/SLO、容量、告警、负责人和运行手册覆盖率为 100%，文件：`specs/002-pdp-product/evidence/p1-service-level-coverage.md`
- [ ] T343 执行备份恢复、对象存储恢复和灾难恢复演练，并建立至少每季度重复验证机制，文件：`specs/002-pdp-product/evidence/disaster-recovery.md`
- [ ] T344 执行高风险操作目录端到端抽查，验证影响预览、版本确认、不可逆点、审计及补偿路径覆盖率为 100%，文件：`specs/002-pdp-product/evidence/p1-high-risk-operations.md`
- [ ] T345 执行平台工作流 MySQL 初始化/升级、流程迁移、引擎故障、异步积压、死信重放、备份恢复和无重复业务结果门禁，文件：`specs/002-pdp-product/evidence/platform-workflow-acceptance.md`
- [ ] T346 执行不少于 20 名目标用户的可用性测试，验证至少 90% 独立完成核心任务，并测量 5 分钟内建立 10 个任务、2 个里程碑和依赖计划，文件：`specs/002-pdp-product/evidence/sc-003-sc-019-usability-study.md`
- [ ] T347 更新阶段 0 建立的 P1 追踪矩阵，补齐代码、自动化测试、运行手册和验收证据并关闭全部断链，文件：`specs/002-pdp-product/traceability.md`
- [ ] T348 完成生产配置、密钥轮换、连接池容量和动态数据源清单审查，文件：`docs/production-readiness.md`
- [ ] T349 完成 MySQL 历史迁移、上线切换和业务连续性运行手册，文件：`docs/runbooks/data-cutover.md`
- [ ] T350 完成权限撤销、事件积压、死信、投影重建、导出、人工补偿和降级恢复运行手册，并提供无责复盘模板，文件：`docs/runbooks/platform-operations.md`、`docs/runbooks/postmortem-template.md`
- [ ] T351 完成平台工作流定义发布、实例迁移、异步执行器、事件关联、incident/dead-letter、schema 升级和引擎恢复运行手册，文件：`docs/runbooks/platform-workflow.md`
- [ ] T352 完成灰度发布、数据库变更顺序、兼容窗口和回滚清单，文件：`docs/runbooks/release.md`
- [ ] T353 归档 MySQL 8.4 数据与事务集的 P50/P95/P99、吞吐、失败率、资源和连接池等待结果，确认全部 SC-018 阈值达标，文件：`specs/002-pdp-product/evidence/performance-acceptance.md`
- [ ] T354 冻结 SC-020 上线前人工汇总耗时、重复录入不一致数量、样本、数据来源、统计口径、责任人和三个月复测计划，文件：`specs/002-pdp-product/evidence/sc-020-benefit-baseline.md`
- [ ] T355 更新需求质量清单并关闭全部未决标记，文件：`specs/002-pdp-product/checklists/requirements.md`
- [ ] T356 复核 P2/P3 仅存在于 backlog 或独立子规格，文件：`specs/002-pdp-product/backlog-p2-p3.md`
- [ ] T357 重新执行只读 `/speckit-analyze`，由评审人将输出归档至 `specs/002-pdp-product/evidence/analysis-pre-release.md`，并要求 CRITICAL/HIGH 为 0 后方可验收
- [ ] T358 按 DoD 执行全量 P1 独立验收，确认失败场景具有可理解反馈，并取得产品、领域/业务验收、架构、质量、运维及适用安全责任人签字，文件：`specs/002-pdp-product/evidence/p1-acceptance.md`

## 阶段 20：上线后价值验证

- [ ] T359 上线满三个月后按冻结口径复测 SC-020，验证人工汇总时间降低至少 60%、重复录入不一致问题降低至少 80%；未达标时形成整改和复测计划，文件：`specs/002-pdp-product/evidence/sc-020-three-month-outcome.md`

## 依赖顺序

1. 阶段 0 的 ADR、统一术语、威胁模型、状态机、服务等级、基础追踪矩阵、完整契约和实现前一致性分析全部通过后，才能进入阶段 1。
2. 阶段 1 完成后才能进入阶段 2；阶段 2 的平台工作流基础能力、持久化、权限、事件和架构测试是全部 P1 用户故事的共同门禁。
3. US1 提供工作空间和权限上下文；US2 复用平台工作流并提供领域定制运行时。
4. US3 依赖 US1、US2；US4 至 US11 依赖 US3 已提供项目上下文。
5. US4、US5、US6、US7、US8、US9、US10 可按模块并行，但跨模块协作只能使用公开端口或事件。
6. US11 的授权与审计骨架从基础阶段开始接入，完整治理能力随各故事同步验证。
7. US14 依赖 US3 至 US11 提供可索引事件和统一权限语义。
8. US18 从基础阶段持续接入，最终恢复演练依赖全部核心模块具备健康和降级接口。
9. US20 依赖目标领域模型、迁移双会话工厂和至少一种认证目标数据库稳定。
10. US21 依赖公共持久化契约、MySQL 适配器、高风险操作框架、后台迁移能力和 US18 运行保障；P1 必须完成 `MYSQL→MYSQL` 双向切换演练。
11. 标准实施领域包依赖平台工作流基础能力以及 US2 至 US11、US14，必须通过完整项目闭环。
12. 阶段 19 在全部 P1 故事和标准实施包完成后执行；最终一致性分析必须先于 DoD 签字，P2/P3 不阻塞 P1 发布。
13. 阶段 20 在 P1 上线满三个月后执行，不阻塞首次上线，但不得从产品成效验收中删除或以改变基线替代。

## 每个用户故事的完成定义

- 契约和可失败测试先于 Controller、消费者和前端实现。
- 持久化故事同时具备公共 Liquibase、仓储端口、Mapper/适配器和 MySQL 契约测试。
- 通过状态迁移、前置条件、工作空间隔离、对象字段授权、乐观并发、审计和失败恢复验证。
- 适用的高风险操作必须具备影响预览、版本确认、不可逆点、审计和补偿路径。
- 使用流程编排的故事必须只依赖平台工作流公开端口，并验证流程版本固定、实时授权、幂等、引擎故障、
  incident/dead-letter、恢复和无重复业务结果。
- 关键能力必须关联服务等级档案，并以日志、指标、追踪、告警和运行手册证明可运行性。
- 可独立演示、独立验收，并在 `evidence/` 留下覆盖正常、失败、补偿和恢复闭环的可复核证据。
- 未满足完成定义时，不得仅因“代码已编写”标记完成。

## 非本期范围

项目组合、资源成本、高级自动化与报表、多渠道订阅、外业、通用企业集成和六类行业包全集属于 P2；
国际化、全部无障碍能力和 AI 增强属于最低优先级 P3 可选范围，见 `specs/002-pdp-product/backlog-p2-p3.md`。
这些能力必须建立下位规格并完成上位需求追踪后，才能生成新的可执行任务。
