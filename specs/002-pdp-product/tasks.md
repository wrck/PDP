# PDP 企业级项目交付管理平台实施任务

**输入**：`specs/002-pdp-product/` 下的产品规格、实施计划、研究决策、数据模型、持久化设计、迁移方案和接口契约  
**实施边界**：优先完成 P1 可上线闭环；P2、P3 任务保留为后续独立增量  
**测试要求**：每个用户故事必须先建立可失败的独立测试，再实现业务能力

## 任务格式

- `[P]`：可与同阶段其他标记任务并行，且修改不同文件。
- `[USn]`：对应 `spec.md` 中的用户故事编号。
- 每项任务均给出目标文件路径；新增文件可在执行任务时创建。

## 阶段 1：工程初始化

**目标**：建立后端、前端、部署和测试工程骨架。

- [ ] T001 创建 Maven Wrapper、Java 21 和 Spring Boot 4.1 聚合工程配置于 `backend/pom.xml`
- [ ] T002 创建后端应用入口和模块包结构于 `backend/src/main/java/com/pdp/PdpApplication.java`
- [ ] T003 [P] 创建 Vue 3.5、TypeScript、Vite 8 和 pnpm 工程配置于 `frontend/package.json`
- [ ] T004 [P] 创建 Vue 应用入口、路由和 Provider 骨架于 `frontend/src/app/main.ts`
- [ ] T005 [P] 配置前端 ESLint、Prettier、vue-tsc 和 Vitest 于 `frontend/eslint.config.js`
- [ ] T006 [P] 配置后端格式化、静态分析、JaCoCo、ArchUnit 和测试插件于 `backend/pom.xml`
- [ ] T007 [P] 创建 PostgreSQL、MySQL、Redis、对象存储和 OIDC 本地依赖编排于 `deploy/compose/compose.yml`
- [ ] T008 [P] 创建应用、后台执行器和数据库配置样例于 `deploy/compose/.env.example`
- [ ] T009 [P] 创建 Kubernetes 应用与后台执行器基础清单于 `deploy/k8s/base/kustomization.yaml`
- [ ] T010 [P] 创建 CI 双数据库构建、测试和契约校验流水线于 `.github/workflows/ci.yml`
- [ ] T011 [P] 创建契约、端到端、性能、安全和恢复测试目录说明于 `tests/README.md`
- [ ] T012 配置统一版本 BOM、依赖锁定和禁止 Hibernate/JPA 规则于 `backend/pom.xml`

**检查点**：空工程可以构建，前后端静态检查可运行，本地依赖可启动。

---

## 阶段 2：阻塞性基础设施

**目标**：完成所有用户故事共同依赖的持久化、安全、事件、文件、接口和可观测基础。

**关键要求**：本阶段完成前，不开始业务用户故事实现。

- [ ] T013 创建公共错误码、Problem Details 和异常映射于 `backend/src/main/java/com/pdp/shared/web/ProblemExceptionHandler.java`
- [ ] T014 [P] 创建 UUIDv7、ObjectRef、ActorRef、分页和 revision 公共值对象于 `backend/src/main/java/com/pdp/shared/model/CommonValueObjects.java`
- [ ] T015 [P] 创建工作空间请求上下文、追踪 ID 和幂等键过滤器于 `backend/src/main/java/com/pdp/shared/web/RequestContextFilter.java`
- [ ] T016 [P] 配置 OIDC 登录、会话和 API 安全链于 `backend/src/main/java/com/pdp/identity/infrastructure/SecurityConfiguration.java`
- [ ] T017 创建功能、数据范围、对象、字段和操作权限决策端口于 `backend/src/main/java/com/pdp/shared/security/AuthorizationService.java`
- [ ] T018 [P] 创建 OpenAPI 契约校验和接口兼容测试于 `tests/contract/openapi-contract.spec.ts`
- [ ] T019 [P] 创建领域包 JSON Schema 校验测试于 `tests/contract/domain-package-schema.spec.ts`
- [ ] T020 配置 MyBatis-Plus、阻止全表更新删除、乐观锁和数据库厂商识别于 `backend/src/main/java/com/pdp/persistence/common/MybatisConfiguration.java`
- [ ] T021 创建动态数据源属性、`pdpPrimary`、`pdpRead`、`migrationSource`、`migrationTarget` 严格路由于 `backend/src/main/java/com/pdp/persistence/common/DynamicDataSourceConfiguration.java`
- [ ] T022 创建事务开始后禁止切换及路由上下文清理拦截器于 `backend/src/main/java/com/pdp/persistence/common/DataSourceRoutingGuard.java`
- [ ] T023 创建每数据源独立 HikariCP 配置、70% 连接预算和指标绑定于 `backend/src/main/java/com/pdp/persistence/common/HikariPoolConfiguration.java`
- [ ] T024 [P] 创建动态路由、事务切换拒绝和上下文泄漏集成测试于 `backend/src/test/java/com/pdp/persistence/DataSourceRoutingTest.java`
- [ ] T025 [P] 创建连接池耗尽、数据库重启、超时和迁移限流测试于 `backend/src/test/java/com/pdp/persistence/HikariPoolFailureTest.java`
- [ ] T026 创建 PostgreSQL/MySQL 产品、版本、时区、排序规则、引擎和 schema 启动检查于 `backend/src/main/java/com/pdp/persistence/common/DatabaseCapabilityValidator.java`
- [ ] T027 创建 UUIDv7、Instant、JsonDocument、稳定枚举和引用 TypeHandler 于 `backend/src/main/java/com/pdp/persistence/common/typehandler/PdpTypeHandlers.java`
- [ ] T028 [P] 创建 PostgreSQL/MySQL TypeHandler 往返契约测试于 `backend/src/test/java/com/pdp/persistence/TypeHandlerContractTest.java`
- [ ] T029 创建签名 keyset cursor 编解码、HMAC 密钥轮换和查询绑定于 `backend/src/main/java/com/pdp/persistence/common/cursor/SignedCursorCodec.java`
- [ ] T030 [P] 创建正反向、NULL、并发写入、权限变化和游标篡改测试于 `backend/src/test/java/com/pdp/persistence/SignedCursorContractTest.java`
- [ ] T031 创建公共乐观锁仓储支持和 404/409 判定逻辑于 `backend/src/main/java/com/pdp/persistence/common/RevisionUpdateSupport.java`
- [ ] T032 [P] 创建自定义 Mapper revision、越权和批量部分冲突测试于 `backend/src/test/java/com/pdp/persistence/RevisionUpdateContractTest.java`
- [ ] T033 创建 Spring Modulith JDBC 事件发布、监听重提和清理配置于 `backend/src/main/java/com/pdp/integration/infrastructure/EventPublicationConfiguration.java`
- [ ] T034 [P] 创建事件提交、回滚、监听失败、重启重提和幂等测试于 `backend/src/test/java/com/pdp/integration/EventPublicationTest.java`
- [ ] T035 创建公共、PostgreSQL 和 MySQL Liquibase 根变更集于 `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- [ ] T036 [P] 创建数据库基础表、事件发布表和公共审计字段变更集于 `backend/src/main/resources/db/changelog/common/001-foundation.yaml`
- [ ] T037 [P] 创建 PostgreSQL 专用类型、索引和能力变更集于 `backend/src/main/resources/db/changelog/postgresql/001-foundation.yaml`
- [ ] T038 [P] 创建 MySQL 专用类型、索引和能力变更集于 `backend/src/main/resources/db/changelog/mysql/001-foundation.yaml`
- [ ] T039 创建数据库空库初始化、升级失败恢复和校验和测试于 `backend/src/test/java/com/pdp/persistence/LiquibaseMatrixTest.java`
- [ ] T040 创建 SearchDocument、SearchTermProjection 和统一分析器基础于 `backend/src/main/java/com/pdp/experience/search/SearchProjectionService.java`
- [ ] T041 [P] 创建双数据库搜索词项、匹配集合和稳定排序测试于 `backend/src/test/java/com/pdp/experience/SearchConsistencyTest.java`
- [ ] T042 创建动态字段约束投影、查询投影和异步投影重建端口于 `backend/src/main/java/com/pdp/domainconfig/runtime/ProjectionCoordinator.java`
- [ ] T043 创建对象存储上传、版本、哈希、短时下载地址和权限复核端口于 `backend/src/main/java/com/pdp/integration/file/FileStorageService.java`
- [ ] T044 [P] 创建 Redis 不可用时缓存旁路和限流降级配置于 `backend/src/main/java/com/pdp/operations/cache/CacheDegradationConfiguration.java`
- [ ] T045 [P] 创建 Micrometer 指标、结构化日志、追踪和健康检查配置于 `backend/src/main/java/com/pdp/operations/observability/ObservabilityConfiguration.java`
- [ ] T046 创建 Spring Modulith 与 ArchUnit 模块边界规则测试于 `backend/src/test/java/com/pdp/architecture/ModuleBoundaryTest.java`
- [ ] T047 创建前端 API 客户端、Problem Details、ETag 和 cursor 基础封装于 `frontend/src/shared/api/client.ts`
- [ ] T048 [P] 创建前端字段权限、只读原因和动态 schema 渲染基础于 `frontend/src/shared/schema-runtime/SchemaRenderer.vue`
- [ ] T049 [P] 创建前端主题令牌、国际化占位和无障碍基础样式于 `frontend/src/shared/theme/tokens.ts`
- [ ] T050 运行 PostgreSQL/MySQL 基础持久化矩阵并记录结果于 `specs/002-pdp-product/evidence/foundation-validation.md`

**检查点**：双数据库基础设施、动态数据源、连接池、游标、乐观锁、类型映射、事件和权限骨架全部通过。

---

## 阶段 3：用户故事 1—工作空间与组织治理（P1）

**目标**：建立工作空间、组织、成员、角色和跨工作空间限时授权。  
**独立测试**：两个工作空间的数据、搜索、附件和导出互相隔离，授权生效与撤销及时一致。

- [ ] T051 [P] [US1] 编写工作空间隔离、成员切换和协作授权集成测试于 `backend/src/test/java/com/pdp/workspace/WorkspaceIsolationTest.java`
- [ ] T052 [P] [US1] 编写工作空间管理和越权访问端到端测试于 `tests/e2e/workspace-governance.spec.ts`
- [ ] T053 [P] [US1] 创建 Workspace、OrganizationUnit、Membership、Role 和 CollaborationGrant 领域模型于 `backend/src/main/java/com/pdp/workspace/domain/WorkspaceModels.java`
- [ ] T054 [US1] 实现工作空间、成员、角色和协作授权应用服务于 `backend/src/main/java/com/pdp/workspace/application/WorkspaceApplicationService.java`
- [ ] T055 [US1] 实现工作空间仓储、权限条件和 revision Mapper 于 `backend/src/main/resources/mapper/common/WorkspaceMapper.xml`
- [ ] T056 [US1] 实现 `/workspaces` 与协作授权接口于 `backend/src/main/java/com/pdp/workspace/web/WorkspaceController.java`
- [ ] T057 [US1] 实现工作空间切换、组织成员和授权管理页面于 `frontend/src/features/workspace/WorkspaceManagementPage.vue`
- [ ] T058 [US1] 验证授权撤销后的 API、搜索、缓存和历史链接失效于 `tests/security/cross-workspace-revocation.spec.ts`

**检查点**：US1 可独立部署和演示。

---

## 阶段 4：用户故事 2—领域包与深度定制（P1）

**目标**：提供三层继承、声明式元数据、受治理扩展、测试、审核、发布和迁移。  
**独立测试**：网络设备割接样例包可创建新对象和流程，无效引用、冲突、循环和越权在发布前被拒绝。

- [ ] T059 [P] [US2] 编写领域包 schema、继承、冲突和发布职责分离测试于 `backend/src/test/java/com/pdp/domainconfig/DomainPackagePublishingTest.java`
- [ ] T060 [P] [US2] 编写领域设计器导入、预览、审核和发布端到端测试于 `tests/e2e/domain-package-designer.spec.ts`
- [ ] T061 [P] [US2] 创建 DomainPackage、Version、Definition、Override、Installation 和 RuntimeSnapshot 模型于 `backend/src/main/java/com/pdp/domainconfig/domain/DomainPackageModels.java`
- [ ] T062 [US2] 实现三层继承合并、核心字段冲突和状态机可达性校验于 `backend/src/main/java/com/pdp/domainconfig/application/PackageValidationService.java`
- [ ] T063 [US2] 实现场景测试、独立审核、签名发布和运行快照服务于 `backend/src/main/java/com/pdp/domainconfig/application/PackagePublishingService.java`
- [ ] T064 [US2] 实现存量实例迁移预览、分批、暂停、恢复和回滚于 `backend/src/main/java/com/pdp/domainconfig/application/PackageMigrationService.java`
- [ ] T065 [US2] 实现领域包校验、发布和迁移接口于 `backend/src/main/java/com/pdp/domainconfig/web/DomainPackageController.java`
- [ ] T066 [US2] 实现对象、字段、关系、页面、状态和规则设计器于 `frontend/src/features/domain-config/DomainPackageDesignerPage.vue`
- [ ] T067 [US2] 创建网络设备割接领域包契约样例于 `backend/src/test/resources/domain-packages/network-cutover-sample.json`
- [ ] T068 [US2] 验证约束/查询投影同步更新及搜索/报表投影异步重建于 `backend/src/test/java/com/pdp/domainconfig/ProjectionConsistencyTest.java`

**检查点**：US2 可在不修改平台核心的情况下表达复杂领域业务。

---

## 阶段 5：用户故事 3—项目模板与项目创建（P1）

**目标**：从空白或批准模板生成完整项目默认结构，并交付标准实施模板。  
**独立测试**：标准实施模板生成阶段、任务、里程碑、检查项、交付件和负责人规则准确率 100%。

- [ ] T069 [P] [US3] 编写模板版本、快照和项目实例化集成测试于 `backend/src/test/java/com/pdp/template/ProjectTemplateInstantiationTest.java`
- [ ] T070 [P] [US3] 编写标准实施模板创建项目端到端测试于 `tests/e2e/project-template-create.spec.ts`
- [ ] T071 [P] [US3] 创建 ProjectTemplate、TemplateVersion 和实例化规则模型于 `backend/src/main/java/com/pdp/template/domain/ProjectTemplateModels.java`
- [ ] T072 [US3] 实现模板批准、版本快照和幂等项目实例化服务于 `backend/src/main/java/com/pdp/template/application/ProjectTemplateService.java`
- [ ] T073 [US3] 实现项目模板和项目创建接口于 `backend/src/main/java/com/pdp/template/web/ProjectTemplateController.java`
- [ ] T074 [US3] 实现模板选择、参数录入和创建结果页面于 `frontend/src/features/project-create/ProjectCreatePage.vue`
- [ ] T075 [US3] 创建标准实施领域包、默认阶段和模板清单于 `backend/src/main/resources/domain-packages/standard-delivery/package.json`
- [ ] T076 [US3] 验证标准实施模板默认对象准确率和五分钟建计划目标于 `tests/e2e/standard-delivery-bootstrap.spec.ts`

**检查点**：可从标准实施模板创建可执行项目。

---

## 阶段 6：用户故事 4—项目生命周期与主子项目（P1）

**目标**：实现统一顶层生命周期、领域子阶段、主子项目和关闭门禁。  
**独立测试**：主项目与区域子项目分别执行，汇总、权限、阶段映射和关闭条件正确。

- [ ] T077 [P] [US4] 编写生命周期映射、非法倒退和主子项目关闭测试于 `backend/src/test/java/com/pdp/project/ProjectLifecycleTest.java`
- [ ] T078 [P] [US4] 编写阶段推进、回退和主子项目端到端测试于 `tests/e2e/project-lifecycle.spec.ts`
- [ ] T079 [P] [US4] 创建 Project、ProjectStage、ProjectMember 和生命周期模型于 `backend/src/main/java/com/pdp/project/domain/ProjectModels.java`
- [ ] T080 [US4] 实现统一生命周期、领域阶段映射和条件执行器于 `backend/src/main/java/com/pdp/project/application/ProjectLifecycleService.java`
- [ ] T081 [US4] 实现主子项目汇总、归档、恢复和作废服务于 `backend/src/main/java/com/pdp/project/application/ProjectHierarchyService.java`
- [ ] T082 [US4] 实现项目详情和状态转换接口于 `backend/src/main/java/com/pdp/project/web/ProjectController.java`
- [ ] T083 [US4] 实现项目概览、阶段轨迹和主子项目页面于 `frontend/src/features/project/ProjectOverviewPage.vue`
- [ ] T084 [US4] 配置标准实施工前、计划、方案、部署、验收和归档阶段映射于 `backend/src/main/resources/domain-packages/standard-delivery/lifecycle.json`

**检查点**：项目生命周期可独立验收且跨领域统计口径统一。

---

## 阶段 7：用户故事 5—任务、检查项与团队协作（P1）

**目标**：实现任务、子任务、检查项、评论、提及、关注、附件和活动。  
**独立测试**：强制检查项未完成时任务不能完成，协作事实和项目进度及时更新。

- [ ] T085 [P] [US5] 编写任务状态、父子任务和强制检查项集成测试于 `backend/src/test/java/com/pdp/planning/TaskWorkflowTest.java`
- [ ] T086 [P] [US5] 编写任务协作、附件和活动端到端测试于 `tests/e2e/task-collaboration.spec.ts`
- [ ] T087 [P] [US5] 创建 Task、ChecklistItem、Comment、Follow 和 Activity 模型于 `backend/src/main/java/com/pdp/planning/domain/TaskModels.java`
- [ ] T088 [US5] 实现任务分配、状态转换、完成门禁和父子汇总服务于 `backend/src/main/java/com/pdp/planning/application/TaskApplicationService.java`
- [ ] T089 [US5] 实现评论、提及、关注、附件和活动服务于 `backend/src/main/java/com/pdp/experience/application/CollaborationService.java`
- [ ] T090 [US5] 实现项目任务与任务状态转换接口于 `backend/src/main/java/com/pdp/planning/web/TaskController.java`
- [ ] T091 [US5] 实现任务详情、检查项和协作面板于 `frontend/src/features/task/TaskDetailPage.vue`
- [ ] T092 [US5] 验证任务并发更新的 ETag、revision 和 409 处理于 `backend/src/test/java/com/pdp/planning/TaskConcurrencyTest.java`

**检查点**：任务执行与协作闭环可独立运行。

---

## 阶段 8：用户故事 6—里程碑、依赖和计划基线（P1）

**目标**：实现里程碑、依赖、循环检测、计划基线和可解释进度。  
**独立测试**：依赖计划可保存基线，变更后可展示偏差、贡献和阻塞项。

- [ ] T093 [P] [US6] 编写依赖循环、日期冲突和基线比较集成测试于 `backend/src/test/java/com/pdp/planning/PlanBaselineTest.java`
- [ ] T094 [P] [US6] 编写里程碑、时间线和进度解释端到端测试于 `tests/e2e/plan-milestone-progress.spec.ts`
- [ ] T095 [P] [US6] 创建 Milestone、Dependency、PlanBaseline 和 ProgressSnapshot 模型于 `backend/src/main/java/com/pdp/planning/domain/PlanModels.java`
- [ ] T096 [US6] 实现依赖图校验、影响传播和基线批准服务于 `backend/src/main/java/com/pdp/planning/application/PlanningService.java`
- [ ] T097 [US6] 实现里程碑权重、必需产出和可解释进度计算于 `backend/src/main/java/com/pdp/planning/application/ProgressCalculationService.java`
- [ ] T098 [US6] 实现经审批的临时人工进度调整和到期恢复于 `backend/src/main/java/com/pdp/planning/application/ProgressOverrideService.java`
- [ ] T099 [US6] 实现基线、里程碑和进度接口于 `backend/src/main/java/com/pdp/planning/web/PlanningController.java`
- [ ] T100 [US6] 实现计划时间线、基线差异和进度解释页面于 `frontend/src/features/planning/ProjectPlanPage.vue`

**检查点**：项目进度可追溯至批准基线和成果证据。

---

## 阶段 9：用户故事 7—交付件全生命周期（P1）

**目标**：实现交付件类型、版本、发布、签核、权限和归档清单。  
**独立测试**：v1 退回后发布 v2，原版本不可覆盖，审批、签名和归档历史完整。

- [ ] T101 [P] [US7] 编写交付件版本不可覆盖、签核绑定版本和归档门禁测试于 `backend/src/test/java/com/pdp/deliverable/DeliverableLifecycleTest.java`
- [ ] T102 [P] [US7] 编写上传、退回、修订、发布和归档端到端测试于 `tests/e2e/deliverable-lifecycle.spec.ts`
- [ ] T103 [P] [US7] 创建 Deliverable、DeliverableVersion、Signature 和归档清单模型于 `backend/src/main/java/com/pdp/deliverable/domain/DeliverableModels.java`
- [ ] T104 [US7] 实现交付件创建、版本发布、命名和阶段约束服务于 `backend/src/main/java/com/pdp/deliverable/application/DeliverableService.java`
- [ ] T105 [US7] 实现内部/外部签核、签名证据和版本绑定服务于 `backend/src/main/java/com/pdp/deliverable/application/SignatureService.java`
- [ ] T106 [US7] 实现交付件与版本接口于 `backend/src/main/java/com/pdp/deliverable/web/DeliverableController.java`
- [ ] T107 [US7] 实现交付件列表、版本历史、预览和签核页面于 `frontend/src/features/deliverable/DeliverablePage.vue`
- [ ] T108 [US7] 配置标准实施必需交付件、模板和验收归档规则于 `backend/src/main/resources/domain-packages/standard-delivery/deliverables.json`

**检查点**：交付成果具备完整版本和证据链。

---

## 阶段 10：用户故事 8—统一审批中心（P1）

**目标**：为任意可审批对象提供节点、办理、委托、加签、超时和状态回写。  
**独立测试**：交付件和变更审批均可完成，审批人只看到有权字段。

- [ ] T109 [P] [US8] 编写审批动作、轮次、超时和字段权限集成测试于 `backend/src/test/java/com/pdp/approval/ApprovalWorkflowTest.java`
- [ ] T110 [P] [US8] 编写统一待办、审批办理和历史端到端测试于 `tests/e2e/approval-center.spec.ts`
- [ ] T111 [P] [US8] 创建 ApprovalDefinition、Instance、Step 和 Assignment 模型于 `backend/src/main/java/com/pdp/approval/domain/ApprovalModels.java`
- [ ] T112 [US8] 实现审批路由、通过、退回、拒绝、撤回、转交、委托和加签服务于 `backend/src/main/java/com/pdp/approval/application/ApprovalService.java`
- [ ] T113 [US8] 实现审批对象字段裁剪和 revision 条件状态回写于 `backend/src/main/java/com/pdp/approval/application/ApprovalBindingService.java`
- [ ] T114 [US8] 实现审批发起和办理接口于 `backend/src/main/java/com/pdp/approval/web/ApprovalController.java`
- [ ] T115 [US8] 实现统一待办、审批详情和历史页面于 `frontend/src/features/approval/ApprovalCenterPage.vue`
- [ ] T116 [US8] 配置标准实施方案与验收审批定义于 `backend/src/main/resources/domain-packages/standard-delivery/approvals.json`

**检查点**：审批能力与具体业务对象解耦并可独立验收。

---

## 阶段 11：用户故事 9—多视图项目工作区（P1）

**目标**：实现概览、列表、看板、日历、时间线、详情和保存视图。  
**独立测试**：任一视图更新后其他视图数据、状态和权限保持一致。

- [ ] T117 [P] [US9] 编写多视图统一查询、字段裁剪和 cursor 测试于 `backend/src/test/java/com/pdp/experience/ProjectWorkspaceQueryTest.java`
- [ ] T118 [P] [US9] 编写列表、看板、日历和时间线一致性端到端测试于 `tests/e2e/project-workspace-views.spec.ts`
- [ ] T119 [P] [US9] 创建 SavedView、ViewDefinition 和角色默认视图模型于 `backend/src/main/java/com/pdp/experience/domain/ViewModels.java`
- [ ] T120 [US9] 实现权限过滤的项目工作区读模型和 keyset 查询于 `backend/src/main/java/com/pdp/experience/application/ProjectWorkspaceQueryService.java`
- [ ] T121 [US9] 实现保存个人视图和发布角色默认视图服务于 `backend/src/main/java/com/pdp/experience/application/SavedViewService.java`
- [ ] T122 [US9] 实现工作区查询和保存视图接口于 `backend/src/main/java/com/pdp/experience/web/ProjectWorkspaceController.java`
- [ ] T123 [US9] 实现列表、看板、日历、时间线和详情组合页面于 `frontend/src/features/project-workspace/ProjectWorkspacePage.vue`
- [ ] T124 [US9] 验证千行虚拟表格、拖拽乐观更新和 409 回滚体验于 `frontend/tests/project-workspace-performance.spec.ts`

**检查点**：多视图共享同一事实和权限口径。

---

## 阶段 12：用户故事 10—风险、问题与变更控制（P1）

**目标**：实现风险、问题、变更、影响分析、审批应用和前后差异。  
**独立测试**：风险转问题并发起变更，批准后只应用获批差异且保留历史。

- [ ] T125 [P] [US10] 编写风险转问题、变更影响和批准应用集成测试于 `backend/src/test/java/com/pdp/governance/RiskIssueChangeTest.java`
- [ ] T126 [P] [US10] 编写风险矩阵、问题处理和变更审批端到端测试于 `tests/e2e/risk-issue-change.spec.ts`
- [ ] T127 [P] [US10] 创建 Risk、Issue、ChangeRequest 和 ChangeDiff 模型于 `backend/src/main/java/com/pdp/governance/domain/GovernanceModels.java`
- [ ] T128 [US10] 实现风险评级、矩阵汇总和风险转问题服务于 `backend/src/main/java/com/pdp/governance/application/RiskIssueService.java`
- [ ] T129 [US10] 实现变更影响、审批绑定和批准后条件应用服务于 `backend/src/main/java/com/pdp/governance/application/ChangeControlService.java`
- [ ] T130 [US10] 实现风险、问题和变更接口于 `backend/src/main/java/com/pdp/governance/web/GovernanceController.java`
- [ ] T131 [US10] 实现风险矩阵、问题列表和变更差异页面于 `frontend/src/features/governance/GovernancePage.vue`

**检查点**：项目偏差不会静默覆盖原计划。

---

## 阶段 13：用户故事 11—权限、审计与数据生命周期（P1）

**目标**：统一页面、搜索、报表、导出、附件、自动化和集成权限，建立审计与保留处置。  
**独立测试**：内部、代理商和客户权限均符合范围，关键操作审计覆盖率 100%。

- [ ] T132 [P] [US11] 编写组合权限、字段权限、停用和临时授权集成测试于 `backend/src/test/java/com/pdp/identity/AuthorizationMatrixTest.java`
- [ ] T133 [P] [US11] 编写附件、下载、导出、归档和法律保留安全测试于 `tests/security/data-protection.spec.ts`
- [ ] T134 [P] [US11] 创建 AuditEvent、RetentionPolicy、LegalHold 和 DisposalJob 模型于 `backend/src/main/java/com/pdp/governance/domain/DataGovernanceModels.java`
- [ ] T135 [US11] 实现统一授权策略、临时授权和责任重新分配于 `backend/src/main/java/com/pdp/identity/application/AuthorizationApplicationService.java`
- [ ] T136 [US11] 实现不可变审计记录、查询和受控导出服务于 `backend/src/main/java/com/pdp/governance/application/AuditService.java`
- [ ] T137 [US11] 实现分类、保留、归档、恢复、法律保留和处置服务于 `backend/src/main/java/com/pdp/governance/application/DataLifecycleService.java`
- [ ] T138 [US11] 实现权限管理、审计查询和保留策略接口于 `backend/src/main/java/com/pdp/governance/web/DataGovernanceController.java`
- [ ] T139 [US11] 实现角色权限矩阵、审计和数据生命周期页面于 `frontend/src/features/security/DataGovernancePage.vue`
- [ ] T140 [US11] 验证缓存、搜索投影、通知和导出的权限一致性于 `backend/src/test/java/com/pdp/identity/AuthorizationPropagationTest.java`

**检查点**：跨入口权限和审计语义一致。

---

## 阶段 14：用户故事 18—高可用与业务连续性（P1）

**目标**：实现无状态多副本、后台隔离、降级、恢复、容量和运行告警。  
**独立测试**：模拟核心与非核心故障，核心业务达到可用性、RTO 和 RPO 目标。

- [ ] T141 [P] [US18] 编写 Redis、搜索、报表、扩展和集成故障降级测试于 `backend/src/test/java/com/pdp/operations/DegradationTest.java`
- [ ] T142 [P] [US18] 编写应用副本故障、数据库恢复和对象恢复演练脚本于 `tests/recovery/p1-recovery.ps1`
- [ ] T143 [P] [US18] 创建后台作业、重试、暂停、取消和失败明细模型于 `backend/src/main/java/com/pdp/operations/domain/BackgroundJobModels.java`
- [ ] T144 [US18] 实现后台作业执行、租约、幂等、进度和故障隔离于 `backend/src/main/java/com/pdp/operations/application/BackgroundJobService.java`
- [ ] T145 [US18] 配置应用三副本、滚动升级、探针、预算和反亲和于 `deploy/k8s/overlays/production/pdp-deployment.yaml`
- [ ] T146 [US18] 配置核心 SLI、连接池、事件积压、迁移和降级告警于 `deploy/observability/pdp-alerts.yaml`
- [ ] T147 [US18] 编写备份、时间点恢复、对象版本恢复和季度演练手册于 `docs/runbooks/disaster-recovery.md`
- [ ] T148 [US18] 执行 1000 并发与百万级数据性能测试并记录基线于 `tests/performance/p1-core.js`

**检查点**：关键故障场景有自动告警、受控降级和已验证恢复路径。

---

## 阶段 15：用户故事 20—MySQL 历史数据迁移与上线切换（P1）

**目标**：实现源库盘点、映射、全量、CDC、隔离、核对、切换和退役。  
**独立测试**：使用脱敏生产等价数据完成至少两次全量迁移和彩排切换。

- [ ] T149 [P] [US20] 编写源数据类型、质量问题和映射预检测试于 `backend/src/test/java/com/pdp/datamigration/MigrationPreflightTest.java`
- [ ] T150 [P] [US20] 编写全量、增量、幂等重跑和断点恢复测试于 `backend/src/test/java/com/pdp/datamigration/MigrationExecutionTest.java`
- [ ] T151 [P] [US20] 编写核对、Go/No-Go、回退点和不可逆点端到端测试于 `tests/e2e/mysql-migration-cutover.spec.ts`
- [ ] T152 [P] [US20] 创建 MigrationProgram、SourceInventory、Mapping、Run、Batch、LegacyKeyMap 和 Issue 模型于 `backend/src/main/java/com/pdp/datamigration/domain/MigrationModels.java`
- [ ] T153 [US20] 实现 MySQL 结构、字符集、时区、引擎和数据质量盘点于 `backend/src/main/java/com/pdp/datamigration/application/SourceInventoryService.java`
- [ ] T154 [US20] 实现版本化映射、类型转换、问题隔离和血缘服务于 `backend/src/main/java/com/pdp/datamigration/application/MigrationMappingService.java`
- [ ] T155 [US20] 实现 `migrationSource` 读取、`migrationTarget` 幂等批次写入和检查点于 `backend/src/main/java/com/pdp/datamigration/application/MigrationExecutionService.java`
- [ ] T156 [US20] 实现 binlog/CDC 位点、顺序、删除和积压追平适配器于 `backend/src/main/java/com/pdp/datamigration/infrastructure/MySqlChangeCaptureAdapter.java`
- [ ] T157 [US20] 实现数量、关系、审批链、附件哈希和业务抽样核对于 `backend/src/main/java/com/pdp/datamigration/application/ReconciliationService.java`
- [ ] T158 [US20] 实现迁移计划、运行、核对和切换决策接口于 `backend/src/main/java/com/pdp/datamigration/web/DataMigrationController.java`
- [ ] T159 [US20] 实现迁移盘点、映射、问题、进度和切换控制台于 `frontend/src/features/data-migration/DataMigrationConsolePage.vue`
- [ ] T160 [US20] 完成两次生产等价彩排并归档证据于 `specs/002-pdp-product/evidence/mysql-migration-rehearsals.md`

**检查点**：历史数据迁移可追溯、可重放、可核对且不依赖分布式事务。

---

## 阶段 16：用户故事 21—多数据库部署与受控切换（P1）

**目标**：同一制品支持 PostgreSQL/MySQL，并完成双向受控数据库切换。  
**独立测试**：两种数据库运行相同 P1 契约、权限、业务、性能和恢复测试，双向切换核对通过。

- [ ] T161 [P] [US21] 编写 PostgreSQL/MySQL 仓储、查询、游标、锁和类型契约矩阵于 `backend/src/test/java/com/pdp/persistence/PersistenceContractMatrixTest.java`
- [ ] T162 [P] [US21] 编写双数据库核心 API、权限、状态和审计一致性测试于 `tests/contract/database-behavior-matrix.spec.ts`
- [ ] T163 [P] [US21] 编写 PostgreSQL 与 MySQL 启动预检失败用例于 `backend/src/test/java/com/pdp/persistence/DatabaseCapabilityFailureTest.java`
- [ ] T164 [US21] 实现 PostgreSQL 公共持久化适配器和专用查询于 `backend/src/main/java/com/pdp/persistence/postgresql/PostgresqlPersistenceAdapter.java`
- [ ] T165 [US21] 实现 MySQL 公共持久化适配器和专用查询于 `backend/src/main/java/com/pdp/persistence/mysql/MysqlPersistenceAdapter.java`
- [ ] T166 [US21] 实现数据库无关标准迁移包导出、导入和全局位点于 `backend/src/main/java/com/pdp/datamigration/application/DatabaseSwitchService.java`
- [ ] T167 [US21] 验证动态数据源不允许用 `migrationTarget` 直接替换 `pdpPrimary` 于 `backend/src/test/java/com/pdp/persistence/DatabaseOwnershipGuardTest.java`
- [ ] T168 [US21] 完成 PostgreSQL→MySQL 与 MySQL→PostgreSQL 切换演练于 `tests/recovery/database-switch-rehearsal.ps1`
- [ ] T169 [US21] 编写 PostgreSQL 与 MySQL 部署、备份、恢复和容量手册于 `docs/runbooks/database-operations.md`
- [ ] T170 [US21] 归档双数据库认证矩阵、执行计划和切换证据于 `specs/002-pdp-product/evidence/database-certification.md`

**检查点**：数据库产品不进入业务语义，同一时刻只有一个写入主库。

---

## 阶段 17：用户故事 12—项目组合、目标与驾驶舱（P2）

**目标**：提供项目组合、目标、异常优先驾驶舱和有权钻取。  
**独立测试**：组合指标与有权源数据一致，30 秒内定位异常项目。

- [ ] T171 [P] [US12] 编写组合权限、指标口径和明细钻取测试于 `backend/src/test/java/com/pdp/portfolio/PortfolioDashboardTest.java`
- [ ] T172 [P] [US12] 编写异常优先驾驶舱端到端测试于 `tests/e2e/portfolio-dashboard.spec.ts`
- [ ] T173 [P] [US12] 创建 Portfolio、Goal、MetricDefinition 和 Dashboard 模型于 `backend/src/main/java/com/pdp/portfolio/domain/PortfolioModels.java`
- [ ] T174 [US12] 实现组合筛选、指标截止时间和权限聚合服务于 `backend/src/main/java/com/pdp/portfolio/application/PortfolioQueryService.java`
- [ ] T175 [US12] 实现组合与驾驶舱接口于 `backend/src/main/java/com/pdp/portfolio/web/PortfolioController.java`
- [ ] T176 [US12] 实现异常优先驾驶舱、指标卡和钻取页面于 `frontend/src/features/portfolio/PortfolioDashboardPage.vue`

**检查点**：US12 可作为 P2 独立增量发布。

---

## 阶段 18：用户故事 13—资源、工时与成本（P2）

**目标**：管理技能、产能、分配、工时、预算和成本偏差。  
**独立测试**：制造资源冲突和成本超阈值后可定位到项目、任务和责任人。

- [ ] T177 [P] [US13] 编写产能冲突、工时和成本阈值集成测试于 `backend/src/test/java/com/pdp/resource/ResourceCostTest.java`
- [ ] T178 [P] [US13] 编写资源负荷和成本偏差端到端测试于 `tests/e2e/resource-cost.spec.ts`
- [ ] T179 [P] [US13] 创建 Skill、Capacity、Allocation、Worklog、Budget 和 CostEntry 模型于 `backend/src/main/java/com/pdp/resource/domain/ResourceCostModels.java`
- [ ] T180 [US13] 实现资源冲突检测、工时汇总和成本偏差服务于 `backend/src/main/java/com/pdp/resource/application/ResourceCostService.java`
- [ ] T181 [US13] 实现资源和成本接口于 `backend/src/main/java/com/pdp/resource/web/ResourceCostController.java`
- [ ] T182 [US13] 实现资源负荷、工时和成本分析页面于 `frontend/src/features/resource/ResourceCostPage.vue`

**检查点**：US13 可作为 P2 独立增量发布。

---

## 阶段 19：用户故事 14—自动化、通知、搜索与报表（P2）

**目标**：实现版本化自动化、统一通知、跨对象搜索和报表。  
**独立测试**：超期规则可触发风险和通知，重复事件无重复业务副作用，失败可重试。

- [ ] T183 [P] [US14] 编写自动化幂等、递归防护和失败隔离测试于 `backend/src/test/java/com/pdp/experience/AutomationRuleTest.java`
- [ ] T184 [P] [US14] 编写搜索权限、报表钻取和通知偏好端到端测试于 `tests/e2e/search-report-notification.spec.ts`
- [ ] T185 [P] [US14] 创建 AutomationRule、Notification、ReportDefinition 和 Subscription 模型于 `backend/src/main/java/com/pdp/experience/domain/AutomationModels.java`
- [ ] T186 [US14] 实现事件、条件、动作、执行身份和递归防护引擎于 `backend/src/main/java/com/pdp/experience/application/AutomationService.java`
- [ ] T187 [US14] 实现通知中心、渠道偏好、重试和去重于 `backend/src/main/java/com/pdp/experience/application/NotificationService.java`
- [ ] T188 [US14] 实现权限过滤搜索、报表、导出和订阅服务于 `backend/src/main/java/com/pdp/experience/application/SearchReportService.java`
- [ ] T189 [US14] 实现自动化、通知、搜索和报表接口于 `backend/src/main/java/com/pdp/experience/web/ExperienceController.java`
- [ ] T190 [US14] 实现通知中心、搜索结果和报表页面于 `frontend/src/features/experience/SearchReportPage.vue`

**检查点**：非关键异步能力失败不影响核心事务。

---

## 阶段 20：用户故事 15—外部参与者与移动现场作业（P2）

**目标**：提供代理商、客户和现场工程师角色入口及受控离线同步。  
**独立测试**：弱网采集照片和检查项后可安全同步，客户只看到授权进度和签核。

- [ ] T191 [P] [US15] 编写外部角色权限、离线冲突和最新状态门禁测试于 `backend/src/test/java/com/pdp/fieldwork/ExternalFieldworkTest.java`
- [ ] T192 [P] [US15] 编写弱网采集、恢复同步和客户签核端到端测试于 `tests/e2e/mobile-fieldwork.spec.ts`
- [ ] T193 [P] [US15] 创建 OfflineChangeSet、FieldCheckin 和 SyncConflict 模型于 `backend/src/main/java/com/pdp/fieldwork/domain/FieldworkModels.java`
- [ ] T194 [US15] 实现批准数据类型离线暂存、同步、去重和冲突处理于 `backend/src/main/java/com/pdp/fieldwork/application/OfflineSyncService.java`
- [ ] T195 [US15] 实现外部角色工作台和现场作业接口于 `backend/src/main/java/com/pdp/fieldwork/web/FieldworkController.java`
- [ ] T196 [US15] 实现响应式现场任务、检查、拍照、签到和同步页面于 `frontend/src/features/fieldwork/FieldworkPage.vue`
- [ ] T197 [US15] 验证审批、阶段推进和关闭操作强制在线确认最新权限于 `tests/security/offline-sensitive-actions.spec.ts`

**检查点**：US15 可作为 P2 独立增量发布。

---

## 阶段 21：用户故事 16—开放集成与企业协同（P2）

**目标**：提供授权 API、事件订阅、去重、乱序处理、重试和健康度。  
**独立测试**：外部系统正常、重复、乱序和不可用时均可追踪且不破坏核心事务。

- [ ] T198 [P] [US16] 编写外部请求去重、乱序、过期和冲突测试于 `backend/src/test/java/com/pdp/integration/ExternalIntegrationTest.java`
- [ ] T199 [P] [US16] 编写事件订阅、失败重试和人工补偿端到端测试于 `tests/e2e/integration-operations.spec.ts`
- [ ] T200 [P] [US16] 创建 IntegrationDefinition、DeliveryAttempt 和 HealthSnapshot 模型于 `backend/src/main/java/com/pdp/integration/domain/IntegrationModels.java`
- [ ] T201 [US16] 实现授权外部命令、幂等和冲突检测服务于 `backend/src/main/java/com/pdp/integration/application/ExternalCommandService.java`
- [ ] T202 [US16] 实现事件映射、签名、投递、重试、暂停和补偿服务于 `backend/src/main/java/com/pdp/integration/application/EventDeliveryService.java`
- [ ] T203 [US16] 实现集成配置和健康度接口于 `backend/src/main/java/com/pdp/integration/web/IntegrationController.java`
- [ ] T204 [US16] 实现集成目录、投递积压和人工补偿页面于 `frontend/src/features/integration/IntegrationOperationsPage.vue`

**检查点**：US16 可作为 P2 独立增量发布。

---

## 阶段 22：用户故事 17—行业业务领域包（P2）

**目标**：通过领域包交付售前、转包、割接、回访和维保等行业能力。  
**独立测试**：六类领域包共享平台权限、审批、审计、搜索、报表和集成，不修改核心。

- [ ] T205 [P] [US17] 编写六类领域包 schema、继承和公共能力契约测试于 `backend/src/test/java/com/pdp/standarddelivery/IndustryPackageContractTest.java`
- [ ] T206 [P] [US17] 编写六类样例领域包独立运行端到端测试于 `tests/e2e/industry-domain-packages.spec.ts`
- [ ] T207 [P] [US17] 创建售前测试领域包于 `backend/src/main/resources/domain-packages/presales-test/package.json`
- [ ] T208 [P] [US17] 创建转包代施领域包于 `backend/src/main/resources/domain-packages/subcontract-delivery/package.json`
- [ ] T209 [P] [US17] 创建割接领域包于 `backend/src/main/resources/domain-packages/network-cutover/package.json`
- [ ] T210 [P] [US17] 创建验收回访领域包于 `backend/src/main/resources/domain-packages/acceptance-followup/package.json`
- [ ] T211 [P] [US17] 创建维保领域包于 `backend/src/main/resources/domain-packages/maintenance-service/package.json`
- [ ] T212 [US17] 验证工作空间客户包覆盖仍保持上游追溯于 `backend/src/test/java/com/pdp/domainconfig/CustomerPackageOverrideTest.java`

**检查点**：US17 可作为 P2 独立增量发布。

---

## 阶段 23：用户故事 19—国际化、无障碍与 AI 辅助（P3）

**目标**：提供语言、时区、格式、键盘无障碍和受控 AI 建议。  
**独立测试**：切换语言时区后业务时间点不变，AI 不可用时核心流程仍可人工完成。

- [ ] T213 [P] [US19] 编写时区、日期和数字格式一致性测试于 `backend/src/test/java/com/pdp/experience/LocalizationTest.java`
- [ ] T214 [P] [US19] 编写键盘、焦点、非颜色状态和 AI 降级端到端测试于 `tests/e2e/accessibility-ai.spec.ts`
- [ ] T215 [US19] 实现用户语言、时区和格式偏好服务于 `backend/src/main/java/com/pdp/identity/application/UserPreferenceService.java`
- [ ] T216 [US19] 实现授权上下文裁剪、建议确认和 AI 故障降级端口于 `backend/src/main/java/com/pdp/experience/application/AiAssistanceService.java`
- [ ] T217 [US19] 完善前端国际化资源和时区格式化于 `frontend/src/shared/i18n/index.ts`
- [ ] T218 [US19] 完善核心页面键盘操作、焦点和状态表达于 `frontend/src/shared/components/AccessibleStatus.vue`

**检查点**：US19 可作为 P3 独立增量发布。

---

## 阶段 24：跨故事上线硬化

**目标**：完成 P1 集成验收、安全、性能、迁移和发布证据。

- [ ] T219 [P] 执行 OpenAPI、领域包 schema 和事件契约兼容检查于 `tests/contract/contract-regression.spec.ts`
- [ ] T220 [P] 执行 SQL 注入、非法排序、越权、全表更新删除和敏感字段泄漏测试于 `tests/security/persistence-security.spec.ts`
- [ ] T221 [P] 执行 PostgreSQL/MySQL 全量 P1 Playwright 回归于 `tests/e2e/p1-regression.spec.ts`
- [ ] T222 [P] 执行连接池容量、泄漏检测和数据库负载保护压测于 `tests/performance/connection-pool.js`
- [ ] T223 [P] 执行事件积压、搜索投影重建和通知补偿演练于 `tests/recovery/async-recovery.ps1`
- [ ] T224 完成真实或等价标准实施项目从创建到归档验收于 `specs/002-pdp-product/evidence/p1-pilot-acceptance.md`
- [ ] T225 更新 API、数据库、迁移、领域包和运行手册索引于 `docs/runbooks/README.md`
- [ ] T226 创建发布、回滚、数据库升级和 Go/No-Go 清单于 `docs/runbooks/release-cutover.md`
- [ ] T227 运行 `quickstart.md` 全部九类验证场景并记录结果于 `specs/002-pdp-product/evidence/quickstart-validation.md`
- [ ] T228 核对 FR-001～FR-162、SC-001～SC-035 与测试证据追踪矩阵于 `specs/002-pdp-product/evidence/requirements-traceability.md`
- [ ] T229 运行依赖漏洞、凭据、许可证和容器镜像扫描并记录例外于 `specs/002-pdp-product/evidence/security-release-gate.md`
- [ ] T230 完成 P1 上线评审并签署产品、业务、技术、安全和运维结论于 `specs/002-pdp-product/evidence/p1-release-decision.md`

---

## 依赖关系与执行顺序

### 阶段依赖

1. 阶段 1 无前置依赖。
2. 阶段 2 依赖阶段 1，并阻塞所有用户故事。
3. P1 业务主链建议按 US1 → US2 → US3 → US4 → US5/US6 → US7/US8 → US9/US10 → US11 执行。
4. US18 可在阶段 2 完成后并行推进，但最终恢复演练依赖 P1 核心业务可运行。
5. US20 依赖阶段 2、US1～US11 的目标模型与校验规则。
6. US21 的基础矩阵可在 Phase 2 后开始，完整切换演练依赖 US20 和 P1 核心闭环。
7. US12～US17 为 P2；依赖阶段 2，并按各自注明的公共业务能力集成。
8. US19 为 P3；可在阶段 2 后开发，但全页面无障碍验收依赖目标页面完成。
9. 阶段 24 依赖所有纳入本次发布的故事完成。

### 用户故事依赖图

```text
阶段 1 → 阶段 2
              ├─ US1 ─┬─ US3 ─ US4 ─┬─ US5 ─ US6 ─┐
              │       │             ├─ US7 ─ US8 ─┤
              │       │             └─ US9 ─ US10 ┤
              │       └───────────────────── US11 ┤
              ├─ US18 ────────────────────────────┤
              ├─ US20（依赖 P1 目标模型）──────────┤
              └─ US21（依赖 US20 与 P1 闭环）─────┘
                                                   ↓
                                           阶段 24 / P1 上线

阶段 2 → US12、US13、US14、US15、US16、US17（P2）
阶段 2 → US19（P3）
```

## 并行执行机会

| 用户故事 | 可优先并行的测试/模型任务 | 后续串行收敛 |
|---|---|---|
| US1 | T051、T052、T053 | T054～T058 |
| US2 | T059、T060、T061 | T062～T068 |
| US3 | T069、T070、T071 | T072～T076 |
| US4 | T077、T078、T079 | T080～T084 |
| US5 | T085、T086、T087 | T088～T092 |
| US6 | T093、T094、T095 | T096～T100 |
| US7 | T101、T102、T103 | T104～T108 |
| US8 | T109、T110、T111 | T112～T116 |
| US9 | T117、T118、T119 | T120～T124 |
| US10 | T125、T126、T127 | T128～T131 |
| US11 | T132、T133、T134 | T135～T140 |
| US18 | T141、T142、T143 | T144～T148 |
| US20 | T149～T152 | T153～T160 |
| US21 | T161～T163 | T164～T170 |
| US12 | T171～T173 | T174～T176 |
| US13 | T177～T179 | T180～T182 |
| US14 | T183～T185 | T186～T190 |
| US15 | T191～T193 | T194～T197 |
| US16 | T198～T200 | T201～T204 |
| US17 | T205～T211 | T212 |
| US19 | T213、T214 | T215～T218 |

## 实施策略

### 第一可演示增量

1. 完成阶段 1 和阶段 2。
2. 完成 US1 工作空间治理。
3. 独立验证工作空间隔离、授权和撤销。

该增量用于验证工程和安全底座，不等同于 P1 可上线产品。

### P1 可上线最小范围

1. 完成阶段 1、阶段 2。
2. 完成 US1～US11。
3. 完成 US18、US20、US21。
4. 完成 US3、US4、US7、US8 中的标准实施领域包任务。
5. 完成阶段 24 全部上线门禁。

### P2/P3 增量

- P2 按 US12～US17 分别独立排期、测试和发布。
- P3 的 US19 不得成为 P1 核心流程依赖。
- 每完成一个故事都运行其独立测试及受影响的双数据库回归，不等待所有后续故事完成。

## 执行约束

- 测试任务先于对应实现任务执行，并先确认测试能够失败。
- 标记 `[P]` 的任务仅在不修改同一文件且无未完成依赖时并行。
- 领域层和应用层不得引用 MyBatis-Plus、数据源、数据库方言或 Mapper 实现。
- 所有数据库写入必须包含工作空间、权限范围和 revision 条件。
- 所有核心列表必须使用签名 keyset cursor；不得以 offset/page 作为外部契约。
- 动态数据源只能在基础设施和迁移边界使用；事务开始后不得切换。
- 每个数据源使用独立 HikariCP 池，迁移任务不得耗尽在线连接预算。
- PostgreSQL 和 MySQL 必须运行相同业务契约和权限测试。
- 每个任务或逻辑任务组完成后提交单一范围变更，并更新对应验证证据。
