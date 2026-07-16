# PDP P1 业务闭环与核心状态机验证矩阵

**特性目录**：`specs/002-pdp-product`

**创建日期**：2026-07-17

**规格层级**：L0 产品规格配套矩阵（实现前门禁与故事验收输入）

**主要追溯**：FR-166、FR-167、FR-168、FR-169、FR-020、FR-068、FR-087、FR-117～FR-120、FR-124、FR-128、FR-129、FR-149、FR-160、FR-161、FR-174

**输入**：`data-model.md` 第 2.1 节、第 3～9 节；`spec.md` 第 3.1 节、FR/SC 列表；`plan.md` “业务闭环与领域设计”与“关键设计规则”

## 1. 目的

本矩阵是 P1 业务闭环与核心状态机验证的单一输入源，定义每个核心对象状态机的：

1. 允许的 `from_state + action_key + to_state` 迁移路径；
2. 前置条件（业务规则、依赖、不变量）；
3. 所需权限（工作空间、对象、字段、操作、数据范围）；
4. 并发与乐观锁语义（`expected_revision`、幂等键、版本冲突处理）；
5. 失败原因分类（稳定原因、阻断项、下一步建议，不泄露无权字段）；
6. 验证证据与测试文件路径（契约测试、MySQL 契约测试、evidence 文件）。

用途：

- **实现前门禁**：阶段 A0 必须冻结本矩阵，并通过 `/speckit-analyze` 校验存在 CRITICAL/HIGH 为 0 才能进入阶段 A。
- **故事验收**：每个 US 的状态机契约测试和 evidence 任务必须引用本矩阵对应行作为期望。
- **高风险操作目录**：本矩阵第 4 节是 `evidence/p1-high-risk-operations.md` 的输入。
- **服务等级档案**：本矩阵定义的失败原因分类与 SLI/SLO 失败模式对应。

## 2. 通用状态迁移规则（来自 `data-model.md` 2.1）

### 2.1 状态模型声明要求

所有状态模型必须显式声明以下字段，缺一不可：

| 声明项 | 内容 |
|---|---|
| `from_state + action_key + to_state` | 允许的迁移路径；未声明的迁移一律拒绝 |
| 前置条件 | 业务规则、不变量、依赖、对象版本、保留/法律约束、迁移门禁 |
| 所需权限 | 工作空间、对象类型、字段、操作和数据范围权限的组合 |
| 失败原因分类 | 至少覆盖本节 2.4 列出的通用失败原因 |

### 2.2 执行与并发语义

- 状态迁移必须以 `expected_revision` 执行；SQL 必须在 `WHERE revision = expected_revision` 条件下更新并 `revision = revision + 1`。
- 影响行数为 0 时必须区分三类：无权（按 404 语义返回，不泄露字段）、不存在（404）、版本冲突（409，返回当前版本与建议重新生成预览）。
- 高风险操作必须先生成未过期的 `OperationImpactPreview`，用户明确确认后才能执行；执行时引用预览标识，并校验所有 `target_refs` 的 `current_revisions` 与预览一致，否则必须重新生成预览，不得静默覆盖。
- 跨聚合的写操作（如审批通过回写业务对象、变更批准更新关联对象、项目关闭触发子项目检查）必须在同一本地事务提交，并通过 outbox 事件触发 Flowable 编排与异步投影；禁止使用 XA。
- 所有状态迁移必须保存 `StateTransitionContext`（含 `object_ref`、`from_state`、`to_state`、`action_key`、`actor_ref`、`permission`、`expected_revision`、`rule_version`、`reason`、`correlation_id`）或可由 `AuditEvent` 完整还原。

### 2.3 权限校验语义

- 写命令必须携带工作空间上下文；跨工作空间访问必须命中有效 `CollaborationGrant` 且在有效期内。
- 默认拒绝；显式拒绝优先于允许；无权与不存在不得通过响应差异泄露（统一返回 404 + 稳定原因）。
- 权限撤销后：新请求立即使用最新授权；本地缓存 5 秒失效；搜索/报表投影 30 秒内移除无权结果；实时会话 30 秒刷新或断开；活动会话与刷新凭据 1 分钟内撤销。
- 审批页面展示的数据必须再次经过当前审批人的对象与字段权限；候选人不得作为最终权限来源。

### 2.4 通用失败原因分类

| 失败原因分类 | 稳定代码 | 触发场景 |
|---|---|---|
| 无权 | `UNAUTHORIZED` | 缺少工作空间/对象/字段/操作/数据范围权限，或协作授权已到期/撤销 |
| 前置条件未满足 | `PRECONDITION_FAILED` | 必需任务、检查项、交付件、审批、子项目门禁未满足 |
| 非法状态 | `ILLEGAL_STATE` | 当前状态不在允许的 `from_state` 列表中，或领域子阶段无法映射顶层生命周期 |
| 版本冲突 | `VERSION_CONFLICT` | `expected_revision` 不匹配，对象已被并发修改 |
| 依赖冲突 | `DEPENDENCY_CONFLICT` | 依赖未完成、循环依赖、父项目未关闭、依赖图成环 |
| 保留/法律约束 | `RETENTION_HOLD` | 对象处于有效 `LegalHold` 或保留期内，禁止物理删除或处置 |
| 迁移门禁未通过 | `MIGRATION_GATE_FAILED` | 领域包发布前校验失败、数据库切换 Go/No-Go 未通过、迁移核对失败 |
| 不可逆点已越过 | `IRREVERSIBLE_POINT_PASSED` | 已越过回退点，仅允许前向修复或已验证的反向同步 |

### 2.5 失败响应与审计要求

失败响应和审计必须包含：

- `correlation_id`：贯穿请求、状态迁移、审计和事件；
- 目标对象 `ObjectRef`（`object_type_key`、`object_id`、`workspace_id`）；
- 当前状态与当前版本；
- 稳定原因（来自 2.4 分类）和稳定原因详情（不包含无权字段名）；
- 阻断项列表（`blocking_items`，仅展示有权限查看的项）；
- 下一步建议（重新生成预览、重新审批、联系责任人等）。

不得泄露：无权字段名、内部迁移控制字段、其他工作空间对象引用、连接凭据、密钥引用。

### 2.6 领域包扩展约束

- 领域包可以扩展状态和动作（增加领域子状态、领域动作），但发布时必须证明：状态可达、存在责任人、具有终态或补偿路径，并唯一映射到适用的平台顶层状态。
- 无法映射、映射冲突或会导致顶层生命周期倒退的领域阶段配置必须在发布前被拒绝（受控回退场景除外）。
- 领域扩展不得改变平台核心身份、工作空间归属、基础权限、审计、版本、保留或单写主权。

## 3. 核心对象状态机矩阵

### 3.1 `UserAccount`（身份生命周期）

**模块**：`identity` ｜ **状态机名**：`user_account_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `INVITED → ACTIVE` via `activate` | OIDC 主体匹配、邮箱未冲突、工作空间成员关系已建立 | 平台管理员或系统自动激活 | `expected_revision`；同一外部主体激活幂等 | `UNAUTHORIZED`、`VERSION_CONFLICT`、`PRECONDITION_FAILED` | `tests/backend/contract/identity/IdentityLifecycleDatabaseContractTest.java`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → SUSPENDED` via `suspend` | 记录原因、操作者；活动会话与刷新凭据须在 1 分钟内撤销 | 平台管理员 + 安全审计人复核 | `expected_revision`；幂等键 `(user_id, action)` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `SUSPENDED → ACTIVE` via `reactivate` | 原因已解决、未完成责任已重新分配或保留 | 平台管理员 + 安全审计人 | `expected_revision`；会话须重新认证 | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| `SUSPENDED → DISABLED` via `disable` | 保留期内、审计已归档、未完成责任已重新分配 | 平台管理员 + 业务审批 | `expected_revision`；不可逆点已越过须记录 | `RETENTION_HOLD`、`IRREVERSIBLE_POINT_PASSED` | 同上 |

**状态列表**：`INVITED`、`ACTIVE`、`SUSPENDED`、`DISABLED`

**不变量**：停用后会话和授权缓存必须失效；未完成责任允许重新分配；DISABLED 在保留期内不可物理删除。

### 3.2 `UserSession`（会话生命周期）

**模块**：`identity` ｜ **状态机名**：`user_session_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `ACTIVE → EXPIRED` via `expire` | `expires_at` 已过；系统定时或请求时检测 | 系统执行身份 | `authorization_version` 校验；幂等 | 无 | `tests/backend/contract/identity/IdentityLifecycleDatabaseContractTest.java`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → REVOKED` via `revoke` | 用户停用、权限撤销、高风险操作再认证失效；记录 `revocation_reason` | 平台管理员、系统或本人 | 1 分钟 SLA；幂等键 `(session_id, reason)` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`ACTIVE`、`EXPIRED`、`REVOKED`

**不变量**：用户停用或权限撤销后，活动会话与刷新凭据必须在 1 分钟内撤销；新请求立即使用最新授权。

### 3.3 `Workspace`（工作空间生命周期）

**模块**：`workspace` ｜ **状态机名**：`workspace_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → ACTIVE` via `activate` | 至少一名 owner、默认数据分类策略已绑定、`code` 全局唯一 | 平台管理员 | `expected_revision`；`code` 唯一约束 | `UNAUTHORIZED`、`VERSION_CONFLICT`、`PRECONDITION_FAILED` | `tests/contracts/us1-workspace-governance.spec.ts`、`tests/backend/contract/workspace/WorkspaceGovernanceDatabaseContractTest.java`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → SUSPENDED` via `suspend` | 记录原因；活动项目允许保留只读访问 | 平台管理员 + 安全审计人 | `expected_revision`；缓存 5 秒失效 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `SUSPENDED → ACTIVE` via `reactivate` | 原因已解决 | 平台管理员 + 安全审计人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| `ACTIVE/SUSPENDED → ARCHIVED` via `archive` | 无活动项目、未完成迁移、有效跨空间授权已撤销或转移 | 平台管理员 + 业务审批；生成 `OperationImpactPreview` | `expected_revision`；不可逆点须记录；预览版本一致 | `PRECONDITION_FAILED`（存在活动项目/迁移/授权）、`VERSION_CONFLICT`、`IRREVERSIBLE_POINT_PASSED` | 同上 |

**状态列表**：`DRAFT`、`ACTIVE`、`SUSPENDED`、`ARCHIVED`

**不变量**：存在活动项目时不得直接归档；归档前须列出阻断对象并记录操作者、原因和对象版本。

### 3.4 `OrganizationUnit`（组织树）

**模块**：`workspace` ｜ **状态机名**：`organization_unit_lifecycle`（树形结构 + 状态）

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建 `ACTIVE` via `create` | 同一工作空间内 `code` 唯一；`parent_id` 不形成环；`path` 由父级拼接 | 工作空间管理员 | 父子关系变更使用 `expected_revision` 双边校验；环检测在事务内完成 | `DEPENDENCY_CONFLICT`（成环）、`VERSION_CONFLICT`、`PRECONDITION_FAILED`（`code` 冲突） | `tests/backend/contract/workspace/WorkspaceGovernanceDatabaseContractTest.java`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → ARCHIVED` via `archive` | 无活动成员、无活动子组织、无活动项目归属 | 工作空间管理员 | 父子双边 `expected_revision`；级联检查子组织 | `PRECONDITION_FAILED`、`DEPENDENCY_CONFLICT` | 同上 |
| 移动 `parent_id` via `move` | 新父级在同一工作空间、不成环、不形成禁止的所有权循环 | 工作空间管理员 | 双边 `expected_revision`；环检测 | `DEPENDENCY_CONFLICT`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`ACTIVE`、`ARCHIVED`（隐含 `DRAFT` 仅在树形结构未启用时使用）

**不变量**：同一工作空间内 `code` 唯一；父子关系不得成环；`path` 物化用于树查询但不作为唯一事实源。

### 3.5 `WorkspaceMembership`（成员关系）

**模块**：`workspace` ｜ **状态机名**：`workspace_membership_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建 `ACTIVE` via `add` | 用户为 `ACTIVE`；外部成员须有 `valid_until`；不重复加入 | 工作空间管理员 | `expected_revision`；`(workspace_id, user_id)` 唯一约束 | `UNAUTHORIZED`、`VERSION_CONFLICT`、`PRECONDITION_FAILED` | `tests/contracts/us1-workspace-governance.spec.ts`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → SUSPENDED` via `suspend` | 记录原因 | 工作空间管理员 | `expected_revision`；缓存 5 秒失效 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `ACTIVE → REVOKED` via `revoke` | 记录原因；未完成责任允许重新分配 | 工作空间管理员 | `expected_revision`；授权缓存立即失效 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 到期 `ACTIVE → EXPIRED` via `expire` | `valid_until` 已过；外部成员强制到期 | 系统定时 | 幂等键 `(membership_id, expire_at)` | 无 | 同上 |

**状态列表**：`ACTIVE`、`SUSPENDED`、`REVOKED`、`EXPIRED`

**不变量**：有效期外不得用于授权；外部成员必须有明确到期时间。

### 3.6 跨工作空间协作授权（`CollaborationGrant`）

**模块**：`workspace` ｜ **状态机名**：`collaboration_grant_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → ACTIVE` via `grant` | `target_ref` 可解析到有权对象；`allowed_actions` 不含改变主工作空间、保留责任、模板归属、平台安全策略的能力；`valid_until` 已设置 | 主工作空间管理员 + 协作工作空间管理员确认 | `expected_revision`；幂等键 `(owner_workspace_id, collaborator_workspace_id, target_ref, role_id)` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us1-workspace-governance.spec.ts`、`evidence/us1-workspace-governance.md` |
| `ACTIVE → REVOKED` via `revoke` | 记录原因；历史链接与缓存立即失效 | 主工作空间管理员 | `expected_revision`；授权缓存 5 秒失效 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 到期 `ACTIVE → EXPIRED` via `expire` | `valid_until` 已过 | 系统定时 | 幂等 | 无 | 同上 |

**状态列表**：`DRAFT`、`ACTIVE`、`EXPIRED`、`REVOKED`

**不变量**：不得授予改变主工作空间、数据保留责任、核心模板归属和平台安全策略的能力；撤销后历史链接和缓存立即失效；FR-124 时限：本地缓存 5 秒，结束后历史链接不得继续访问。

### 3.7 `DomainPackage` 与 `DomainPackageVersion`（领域包版本生命周期）

**模块**：`domainconfig` ｜ **状态机名**：`domain_package_version_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → VALIDATING` via `validate` | `content_hash` 已生成；`manifest` 完整 | 领域设计人 | `expected_revision`；幂等键 `(version_id, content_hash)` | `UNAUTHORIZED`、`VERSION_CONFLICT` | `tests/contracts/us2-domain-package.spec.ts`、`tests/backend/contract/domainconfig/DomainPackageDatabaseContractTest.java`、`evidence/us2-domain-package.md` |
| `VALIDATING → REJECTED` via `reject_validation` | 校验失败；保留全部失败项 | 系统自动 | 幂等 | `MIGRATION_GATE_FAILED`（结构/引用/状态/规则/权限/迁移/回滚校验失败） | 同上 |
| `REJECTED → DRAFT` via `revise` | 设计人重新编辑 | 领域设计人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `VALIDATING → REVIEW_PENDING` via `submit_for_review` | 校验通过 | 领域设计人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| `REVIEW_PENDING → PUBLISHED` via `publish` | 设计人与发布审核人不同；已通过全部发布校验；继承层次不超三层、不成环、客户包只继承行业或平台标准包 | 发布审核人；生成 `OperationImpactPreview` | `expected_revision`；预览版本一致；不可逆点须记录 | `MIGRATION_GATE_FAILED`、`IRREVERSIBLE_POINT_PASSED`、`UNAUTHORIZED` | 同上 |
| `PUBLISHED → DEPRECATED` via `deprecate` | 已有替代版本或退役计划；运行实例固定快照不变 | 发布审核人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| `DEPRECATED → RETIRED` via `retire` | 无运行实例引用或全部已迁移；保留期内不可物理删除 | 发布审核人 + 平台管理员 | `expected_revision`；不可逆点须记录 | `RETENTION_HOLD`、`PRECONDITION_FAILED`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| `PUBLISHED → PUBLISHED(rollback)` via `rollback` | 出现严重问题；存在可回退的基线版本；生成预览 | 发布审核人 + 平台管理员；`OperationImpactPreview` | `expected_revision`；幂等键；不可逆点检查 | `MIGRATION_GATE_FAILED`、`IRREVERSIBLE_POINT_PASSED`、`VERSION_CONFLICT` | 同上 |
| 存量实例迁移 `MigrationJob` 状态：`DRAFT → PREVIEWING → READY → RUNNING → COMPLETED`，异常 `PAUSED/FAILED/ROLLING_BACK/ROLLED_BACK` via `migrate` | 预览确认、分批、断点续传、回滚检查点 | 领域设计人 + 发布审核人 | 迁移作业 `expected_revision`；批次 `attempts` 限流 | `MIGRATION_GATE_FAILED`、`DEPENDENCY_CONFLICT`、`VERSION_CONFLICT` | `tests/contracts/us2-domain-package.spec.ts`、`evidence/us2-domain-package.md` |

**状态列表**：`DRAFT`、`VALIDATING`、`REJECTED`、`REVIEW_PENDING`、`PUBLISHED`、`DEPRECATED`、`RETIRED`；`MigrationJob`：`DRAFT`、`PREVIEWING`、`READY`、`RUNNING`、`COMPLETED`、`PAUSED`、`FAILED`、`ROLLING_BACK`、`ROLLED_BACK`

**不变量**：设计人与发布审核人不得相同；已发布内容不可修改；发布前必须完成结构、引用、状态、规则、权限、迁移和回滚校验；运行实例默认固定快照，升级不得直接改变历史事实。

### 3.8 `Project`（项目顶层生命周期）

**模块**：`project` ｜ **状态机名**：`project_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `PRE_PLANNING → PLANNING` via `start_planning` | 项目编号唯一、模板版本已绑定、`runtime_snapshot_id` 已生成 | 项目经理 + 工作空间成员 | `expected_revision`；`(workspace_id, project_no)` 唯一 | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us4-project-lifecycle.spec.ts`、`tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java`、`evidence/us4-project-lifecycle.md` |
| `PLANNING → EXECUTING` via `start_executing` | 计划基线已批准或允许无基线启动；必需阶段已配置 | 项目经理 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `EXECUTING → ACCEPTING` via `start_accepting` | 关键交付件已提交、必需阶段退出条件满足 | 项目经理 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `ACCEPTING → SERVICING` via `start_servicing` | 验收交付件已签核、关键审批已通过 | 项目经理 + 业务验收人 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `SERVICING → CLOSED` via `close` | 必需任务、交付件、问题、审批和子项目门禁全部满足；生成 `OperationImpactPreview` | 项目经理 + 业务审批；`OperationImpactPreview` | `expected_revision`；预览版本一致；不可逆点须记录 | `PRECONDITION_FAILED`、`VERSION_CONFLICT`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 非终态 → `CANCELLED` via `cancel` | 记录原因、操作者；保留审计与历史 | 项目经理 + 业务审批 | `expected_revision`；不可逆点须记录 | `UNAUTHORIZED`、`VERSION_CONFLICT`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 受控回退 `EXECUTING → PLANNING`（或其他回退） via `controlled_rollback` | 记录原因和审批；不导致顶层生命周期非法倒退（除受控回退） | 项目经理 + 业务审批；`OperationImpactPreview` | `expected_revision`；预览版本一致；幂等键 | `MIGRATION_GATE_FAILED`（领域阶段映射冲突）、`IRREVERSIBLE_POINT_PASSED`、`VERSION_CONFLICT` | 同上 |
| 复制/恢复 `CLOSED → PRE_PLANNING`（新实例） via `copy`/`restore` | 不删除历史和审计；新项目编号 | 项目经理 + 工作空间管理员 | 新对象 `revision` 初始化 | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| 归档 `CLOSED → ARCHIVED`（项目归档状态） via `archive` | 完整交付件清单已生成、缺失和版本异常已识别 | 项目经理 + 工作空间管理员 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`PRE_PLANNING`、`PLANNING`、`EXECUTING`、`ACCEPTING`、`SERVICING`、`CLOSED`、`CANCELLED`、`ARCHIVED`（隐含 `PAUSED`）

**不变量**：项目仅有一个主工作空间；父子项目不得成环；关闭前必须满足必需任务、交付件、问题、审批和子项目门禁；顶层状态与领域子阶段唯一映射。

### 3.9 `ProjectStage`（阶段生命周期）

**模块**：`project` ｜ **状态机名**：`project_stage_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `NOT_STARTED → READY` via `mark_ready` | 进入条件 `entry_rule` 满足 | 项目经理 | `expected_revision`；`rule_version` 一致 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us4-project-lifecycle.spec.ts`、`tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java`、`evidence/us4-project-lifecycle.md` |
| `READY → IN_PROGRESS` via `start` | 进入条件已满足、负责人已分配 | 阶段负责人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS → COMPLETED` via `complete` | 退出条件 `exit_rule` 满足、`required_outputs` 全部交付 | 阶段负责人 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS → BLOCKED` via `block` | 记录 `blocked_reason` | 阶段负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `BLOCKED → IN_PROGRESS`（或前一可执行状态） via `unblock` | 阻塞原因已解除 | 阶段负责人 | `expected_revision`；须回到记录的前一可执行状态 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS → PAUSED` via `pause` | 记录原因 | 项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `PAUSED → IN_PROGRESS` via `resume` | 恢复条件已满足 | 项目经理 | `expected_revision`；须回到前一可执行状态 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS/READY → CANCELLED` via `cancel` | 记录原因 | 项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`NOT_STARTED`、`READY`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`、`PAUSED`、`CANCELLED`

**不变量**：同一领域子阶段唯一映射一个顶层生命周期；未满足退出条件不得推进；解除阻塞或恢复必须回到记录的前一可执行状态。

### 3.10 父子项目（层级与权限汇总）

**模块**：`project` ｜ **状态机名**：`project_hierarchy`（跨聚合约束）

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建父子关系 via `link_parent` | 父子不成环、同一主工作空间或具备跨空间协作授权 | 项目经理 + 工作空间管理员 | 双边 `expected_revision`；环检测在事务内 | `DEPENDENCY_CONFLICT`（成环）、`VERSION_CONFLICT`、`UNAUTHORIZED` | `tests/contracts/us4-project-lifecycle.spec.ts`、`evidence/us4-project-lifecycle.md` |
| 子项目关闭门禁 via `check_close_gates` | 所有子项目 `CLOSED` 或 `CANCELLED`；父项目关闭前子项目门禁满足 | 项目经理 | 双边 `expected_revision` | `PRECONDITION_FAILED`、`DEPENDENCY_CONFLICT` | 同上 |
| 权限汇总 via `aggregate_permissions` | 子项目权限不扩大父项目权限；跨空间子项目须有有效协作授权 | 系统权限服务 | 读后写一致性，权限缓存 5 秒 | `UNAUTHORIZED` | 同上 |

**状态列表**：依赖 `Project` 状态机；本机为层级约束。

**不变量**：父子项目不得成环；子项目权限不得扩大父项目权限；父项目关闭前所有子项目须满足关闭门禁。

### 3.11 `Task`（任务生命周期）

**模块**：`planning` ｜ **状态机名**：`task_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → READY` via `mark_ready` | 负责人已分配、依赖满足（如配置）、对象版本有效 | 任务负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us5-task-collaboration.spec.ts`、`tests/backend/contract/planning/TaskCollaborationDatabaseContractTest.java`、`evidence/us5-task-collaboration.md` |
| `READY → IN_PROGRESS` via `start` | 负责人有效、权限通过 | 任务负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS → BLOCKED` via `block` | 记录 `blocked_reason` | 任务负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `BLOCKED → IN_PROGRESS`（或前一可执行状态） via `unblock` | 阻塞原因已解除 | 任务负责人 | `expected_revision`；须回到前一可执行状态 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `IN_PROGRESS → COMPLETED` via `complete` | 必需检查项全部完成、依赖任务已完成、对象版本有效 | 任务负责人 | `expected_revision`；检查项门禁同事务 | `PRECONDITION_FAILED`（必需检查项未完成、依赖未完成）、`VERSION_CONFLICT` | 同上 |
| 任意非终态 → `CANCELLED` via `cancel` | 记录原因 | 任务负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `COMPLETED → ARCHIVED` via `archive` | 项目或阶段已归档 | 项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 父子任务关联 via `link_subtask` | 父子不成环、依赖图不形成循环 | 任务负责人或项目经理 | 双边 `expected_revision`；环检测 | `DEPENDENCY_CONFLICT`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`DRAFT`、`READY`、`IN_PROGRESS`、`BLOCKED`、`COMPLETED`、`CANCELLED`、`ARCHIVED`

**不变量**：必需检查项未完成时任务不得完成；解除阻塞回到前一可执行状态；父子任务与依赖图不得成环。

### 3.12 `ChecklistItem`（检查项生命周期）

**模块**：`planning` ｜ **状态机名**：`checklist_item_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `UNCHECKED → CHECKED` via `check` | 任务非 `COMPLETED/CANCELLED/ARCHIVED`；`required` 项须有 `evidence_ref`（如配置） | 任务负责人或参与人 | `expected_revision`；与任务完成门禁同事务 | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us5-task-collaboration.spec.ts`、`evidence/us5-task-collaboration.md` |
| `CHECKED → UNCHECKED` via `uncheck` | 任务非 `COMPLETED/CANCELLED/ARCHIVED`；记录操作者 | 任务负责人或参与人 | `expected_revision`；级联重算任务完成状态 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`UNCHECKED`、`CHECKED`（等价于 `未完成 → 完成`）

**不变量**：必需检查项未完成时任务不得完成；检查项状态变更必须与任务完成门禁在同一事务。

### 3.13 `Milestone`（里程碑生命周期）

**模块**：`planning` ｜ **状态机名**：`milestone_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `PLANNED → AT_RISK` via `mark_at_risk` | 进度滞后或阻塞项预警 | 项目经理或里程碑负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | `tests/contracts/us6-plan-baseline.spec.ts`、`tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java`、`evidence/us6-plan-baseline.md` |
| `PLANNED/AT_RISK → ACHIEVED` via `achieve` | 必需产出规则满足、`completion_rule` 通过 | 里程碑负责人 | `expected_revision`；权重归一校验 | `PRECONDITION_FAILED`（必需产出未满足）、`VERSION_CONFLICT` | 同上 |
| `PLANNED/AT_RISK → MISSED` via `miss` | 计划时间已过且未达成 | 项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 任意 → `CANCELLED` via `cancel` | 记录原因 | 项目经理 | `expected_revision`；权重须重新归一 | `PRECONDITION_FAILED`（权重归一失败）、`VERSION_CONFLICT` | 同上 |

**状态列表**：`PLANNED`、`AT_RISK`、`ACHIEVED`、`MISSED`、`CANCELLED`

**不变量**：项目有效里程碑权重总和为 100%；只有满足必需产出规则时才能进入 `ACHIEVED`；未满足必需产出不得完成。

### 3.14 `Dependency`（依赖关系）

**模块**：`planning` ｜ **状态机名**：`dependency_lifecycle`（关系约束）

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建依赖 via `create` | `predecessor_ref` 与 `successor_ref` 不相同、不形成循环、日期不冲突（考虑 `lag_minutes`） | 项目经理或任务负责人 | 双边 `expected_revision`；环检测在事务内；拓扑排序校验 | `DEPENDENCY_CONFLICT`（成环/日期冲突）、`VERSION_CONFLICT`、`UNAUTHORIZED` | `tests/contracts/us6-plan-baseline.spec.ts`、`tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java`、`evidence/us6-plan-baseline.md` |
| 删除依赖 via `delete` | 不影响已 `COMPLETED` 任务的事实 | 项目经理或任务负责人 | 双边 `expected_revision` | `RETENTION_HOLD`、`VERSION_CONFLICT` | 同上 |

**状态列表**：依赖关系无独立状态，由 `predecessor_ref` 与 `successor_ref` 任务状态隐含。

**不变量**：依赖图不得成环；考虑 `lag_minutes` 后日期不得冲突；删除依赖不改变已 `COMPLETED` 任务的事实。

### 3.15 `PlanBaseline`（计划基线生命周期）

**模块**：`planning` ｜ **状态机名**：`plan_baseline_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → APPROVAL_PENDING` via `submit` | 快照完整、里程碑权重归一、依赖图无环 | 项目经理 | `expected_revision`；`approval_id` 关联 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us6-plan-baseline.spec.ts`、`tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java`、`evidence/us6-plan-baseline.md` |
| `APPROVAL_PENDING → APPROVED` via `approve` | 关联审批通过、业务对象版本匹配 | 审批人 | `expected_revision`；审批回写条件更新 | `PRECONDITION_FAILED`（审批未通过或对象版本不匹配）、`VERSION_CONFLICT` | 同上 |
| `APPROVED → SUPERSEDED` via `supersede` | 新基线已批准；生成 `OperationImpactPreview` | 项目经理 + 审批；`OperationImpactPreview` | `expected_revision`；预览版本一致；不可逆点须记录；不删除原基线 | `IRREVERSIBLE_POINT_PASSED`、`VERSION_CONFLICT` | 同上 |
| 创建 `ProgressSnapshot` via `snapshot` | 基线 `APPROVED`、里程碑贡献与阻塞项可计算 | 系统或项目经理 | `baseline_id` 关联；幂等键 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| 人工调整 `ProgressOverride` via `override` | 经审批；记录原值、调整值、原因、期限、审批人 | 项目经理 + 审批；`OperationImpactPreview` | `expected_revision`；不改变底层事实 | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 基线比较 via `compare` | 两基线均 `APPROVED` 或 `SUPERSEDED`；偏差可计算 | 项目经理 | 读一致性 | `PRECONDITION_FAILED` | 同上 |

**状态列表**：`DRAFT`、`APPROVAL_PENDING`、`APPROVED`、`SUPERSEDED`

**不变量**：已批准基线不得原位覆盖；替换必须形成新版本或差异记录；手工调整不改变底层事实。

### 3.16 `Deliverable` 与 `DeliverableVersion`（交付件生命周期）

**模块**：`deliverable` ｜ **状态机名**：`deliverable_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → REVIEWING` via `submit` | 内容版本已创建、必填元数据满足、阶段约束满足 | 交付件负责人 | `expected_revision`；`current_version_id` 关联 | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us7-deliverable.spec.ts`、`tests/backend/contract/deliverable/DeliverableDatabaseContractTest.java`、`evidence/us7-deliverable.md` |
| `REVIEWING → APPROVED` via `approve` | 审批通过、签核绑定确切版本 | 审批人 + 签核人 | `expected_revision`；签核 `signature_type` 一致 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `REVIEWING → REJECTED` via `reject` | 记录原因 | 审批人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `REJECTED → DRAFT` via `revise` | 创建新版本 `DeliverableVersion`；不覆盖原版本 | 交付件负责人 | `expected_revision`；`version_no` 递增 | `RETENTION_HOLD`（已发布版本不可覆盖）、`VERSION_CONFLICT` | 同上 |
| `APPROVED → PUBLISHED` via `publish` | 签核完成、阶段权限有效；生成 `OperationImpactPreview` | 交付件负责人 + 审批；`OperationImpactPreview` | `expected_revision`；预览版本一致；不可逆点须记录；已发布版本不可覆盖 | `IRREVERSIBLE_POINT_PASSED`、`RETENTION_HOLD`、`VERSION_CONFLICT` | 同上 |
| `PUBLISHED → ARCHIVED` via `archive` | 项目归档或保留期处置；不删除已发布版本 | 项目经理 + 工作空间管理员 | `expected_revision` | `RETENTION_HOLD`、`VERSION_CONFLICT` | 同上 |
| 创建 `Signature` via `sign` | 签核人身份快照、内容哈希、签核版本绑定 | 签核人 | `expected_revision`；幂等键 `(deliverable_version_id, signer_actor)` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`DRAFT`、`REVIEWING`、`APPROVED`、`REJECTED`、`PUBLISHED`、`ARCHIVED`

**不变量**：已发布版本不可覆盖；修订必须创建新版本；签核必须绑定确切版本；保留期内不可物理删除。

### 3.17 `ApprovalDefinition` / `ApprovalInstance` / `ApprovalStep`（审批生命周期）

**模块**：`approval` ｜ **状态机名**：`approval_instance_lifecycle` + `approval_step_lifecycle`

**审批实例**：

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → PENDING` via `submit` | 定义版本已发布、业务对象可解析、申请人有权发起 | 申请人 | `expected_revision`；幂等键 `(business_object_ref, definition_version_id, applicant_id)` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | `tests/contracts/us8-approval.spec.ts`、`tests/backend/contract/approval/ApprovalDatabaseContractTest.java`、`evidence/us8-approval.md` |
| `PENDING → APPROVED` via `approve` | 所有必需节点通过、业务对象版本匹配回写条件 | 终审节点办理人 | `expected_revision`；状态回写使用对象 `revision` 条件更新 | `PRECONDITION_FAILED`（对象版本不匹配或节点未完成）、`VERSION_CONFLICT` | 同上 |
| `PENDING → REJECTED` via `reject` | 终审节点拒绝；记录意见 | 终审节点办理人 | `expected_revision`；不回写业务状态为通过 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `PENDING → WITHDRAWN` via `withdraw` | 仅申请人可撤回；记录原因 | 申请人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `PENDING → CANCELLED` via `cancel` | 业务对象已取消或迁移；记录原因 | 系统或项目经理 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |

**审批节点**：

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `PENDING → APPROVED/REJECTED` via `handle` | 当前办理人有权、对象与字段权限再次校验、业务对象版本匹配 | 当前节点办理人 | `expected_revision`；幂等键 `(step_id, assignee, action)` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| 退回 `PENDING → PENDING`（上一节点） via `return` | 记录原因；生成新轮次 | 当前节点办理人 | `expected_revision`；保留原轮次 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 重提 `PENDING → PENDING`（下一节点） via `resubmit` | 退回问题已解决 | 申请人或退回节点办理人 | `expected_revision`；幂等键 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| 转交 `assignee` 变更 via `transfer` | 新办理人在权限范围内 | 当前节点办理人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 委托 via `delegate` | 委托关系在权限范围内；委托人不替代最终权限 | 当前节点办理人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 加签 via `add_counter_signer` | 加签人在权限范围内 | 当前节点办理人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 抄送 via `cc` | 抄送人有权查看；不形成办理节点 | 当前节点办理人 | `expected_revision` | `UNAUTHORIZED` | 同上 |
| 终止 `PENDING → CANCELLED` via `terminate` | 记录原因；不可逆点须记录 | 系统或管理员 | `expected_revision`；幂等键 | `IRREVERSIBLE_POINT_PASSED`、`VERSION_CONFLICT` | 同上 |
| 终态预览 via `preview_terminal` | 业务对象版本未变化；预览未过期 | 申请人或办理人 | 读一致性；预览 `expires_at` | `VERSION_CONFLICT`（对象版本变化）、`PRECONDITION_FAILED` | 同上 |

**状态列表**：实例 `DRAFT`、`PENDING`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`CANCELLED`；节点 `PENDING`、`APPROVED`、`REJECTED`、`DELEGATED`、`TRANSFERRED`、`COUNTER_SIGNED`、`CC`、`CANCELLED`

**不变量**：审批展示的数据必须再次经过当前审批人的对象与字段权限；状态回写要求业务对象版本匹配；候选人不得作为最终权限来源；不回写业务状态为通过时不得改变业务对象。

### 3.18 `Risk`（风险生命周期）

**模块**：`governance` ｜ **状态机名**：`risk_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建 `OPEN` via `create` | 责任人已分配、概率/影响/等级已评估 | 风险负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | `tests/contracts/us10-governance-control.spec.ts`、`tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java`、`evidence/us10-governance-control.md` |
| `OPEN → MONITORING` via `monitor` | 触发条件监测中 | 风险负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `OPEN/MONITORING → MATERIALIZED` via `materialize` | 转为问题；保留双向关联 | 风险负责人 | `expected_revision`；幂等键 `(risk_id, materialize_action)` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `OPEN/MONITORING → CLOSED` via `close` | 关闭原因已记录、措施已落实 | 风险负责人 + 项目经理 | `expected_revision` | `PRECONDITION_FAILED`（原因缺失）、`VERSION_CONFLICT` | 同上 |
| 任意非终态 → `CANCELLED` via `cancel` | 记录原因 | 风险负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `CLOSED/CANCELLED → OPEN` via `reopen` | 记录重开原因、操作者、前后状态 | 风险负责人 + 项目经理 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`OPEN`、`MONITORING`、`MATERIALIZED`、`CLOSED`、`CANCELLED`

**不变量**：转为问题时保留双向关联；重开、取消、关闭必须保存原因、操作者和前后状态。

### 3.19 `Issue`（问题生命周期）

**模块**：`governance` ｜ **状态机名**：`issue_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 创建 `OPEN` via `create` | 来源、类型、严重程度、负责人已分配 | 问题负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | `tests/contracts/us10-governance-control.spec.ts`、`tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java`、`evidence/us10-governance-control.md` |
| `OPEN → ANALYZING` via `analyze` | 关联对象已识别 | 问题负责人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `ANALYZING → TREATING` via `treat` | 方案已制定 | 问题负责人 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `TREATING → RESOLVED` via `resolve` | 关闭结论已记录 | 问题负责人 + 项目经理 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `RESOLVED → CLOSED` via `close` | 关闭结论已复核 | 项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `CLOSED → REOPENED` via `reopen` | 记录重开原因、操作者、前后状态 | 问题负责人 + 项目经理 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| 任意非终态 → `CANCELLED` via `cancel` | 记录原因 | 问题负责人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |

**状态列表**：`OPEN`、`ANALYZING`、`TREATING`、`RESOLVED`、`CLOSED`、`REOPENED`、`CANCELLED`

**不变量**：重开、取消、关闭必须保存原因、操作者和前后状态。

### 3.20 `ChangeRequest`（变更生命周期）

**模块**：`governance` ｜ **状态机名**：`change_request_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → ANALYZING` via `analyze` | 范围/工期/资源/成本/风险/交付影响已记录 | 变更发起人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | `tests/contracts/us10-governance-control.spec.ts`、`tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java`、`evidence/us10-governance-control.md` |
| `ANALYZING → APPROVAL_PENDING` via `submit` | 差异快照已生成、影响预览已确认 | 变更发起人 | `expected_revision`；`OperationImpactPreview` 关联 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| 预览确认 via `preview_confirm` | 影响预览未过期、对象版本未变化 | 变更发起人 + 受影响对象责任人 | 预览版本一致校验 | `VERSION_CONFLICT`（对象版本变化）、`PRECONDITION_FAILED` | 同上 |
| `APPROVAL_PENDING → APPROVED` via `approve` | 关联审批通过、应用对象版本匹配 | 审批人 | `expected_revision`；审批联动 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `APPROVAL_PENDING → REJECTED` via `reject` | 审批拒绝 | 审批人 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| 任意非终态 → `CANCELLED` via `cancel` | 记录原因 | 变更发起人或项目经理 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `APPROVED → APPLYING` via `apply` | 关联对象版本匹配；幂等键 | 变更发起人 + 应用执行身份 | `expected_revision`；幂等键 `(change_request_id, apply_attempt)` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `APPLYING → APPLIED` via `complete` | 关联对象在同一受控操作中更新、前后差异保留 | 系统或变更发起人 | `expected_revision`；同事务更新关联对象 | `DEPENDENCY_CONFLICT`、`VERSION_CONFLICT` | 同上 |
| `APPLYING → APPLY_FAILED` via `fail` | 应用异常；保留失败明细 | 系统 | `expected_revision`；仅允许幂等重试、回滚或人工补偿 | `MIGRATION_GATE_FAILED`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 失败补偿 `APPLY_FAILED → APPLYING` via `retry`/`rollback`/`manual_compensate` | 幂等键防重复；记录补偿结果 | 变更发起人 + 应用执行身份 | `expected_revision`；幂等键 | `VERSION_CONFLICT`、`IRREVERSIBLE_POINT_PASSED` | 同上 |

**状态列表**：`DRAFT`、`ANALYZING`、`APPROVAL_PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`、`APPLYING`、`APPLIED`、`APPLY_FAILED`

**不变量**：批准变更必须在同一受控操作中更新关联对象并保留前后差异；应用异常只能幂等重试、回滚或人工补偿。

### 3.21 审计/保留/处置（`AuditEvent` / `RetentionPolicy` / `LegalHold` / `DispositionRequest`）

**模块**：`governance` ｜ **状态机名**：`audit_retention_disposition_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `AuditEvent` 只追加写入 via `record` | 事件发生在业务事务内；敏感值脱敏或摘要 | 系统执行身份 | 只追加；摘要链/等价防篡改校验 | `RETENTION_HOLD`（不可修改） | `tests/backend/contract/security/AuthorizationAuditDatabaseContractTest.java`、`tests/contracts/us11-audit-lifecycle.spec.ts`、`evidence/us11-security-lifecycle.md` |
| `LegalHold` `DRAFT → ACTIVE` via `activate` | 法律或审计依据已记录、批准人已确认 | 法务/审计 + 平台管理员 | `expected_revision`；优先于普通保留与处置 | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `LegalHold` `ACTIVE → RELEASED` via `release` | 解除时间已记录、批准人已确认 | 法务/审计 + 平台管理员 | `expected_revision` | `UNAUTHORIZED`、`VERSION_CONFLICT` | 同上 |
| `DispositionRequest` `DRAFT → PENDING_APPROVAL` via `submit` | 目标范围已识别、计划动作已定义、证据摘要已生成 | 工作空间管理员 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | 同上 |
| `DispositionRequest` `PENDING_APPROVAL → APPROVED` via `approve` | 审批通过、不可逆点预览已确认 | 审批人 + 平台管理员；`OperationImpactPreview` | `expected_revision`；预览版本一致 | `PRECONDITION_FAILED`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 处置预览 via `preview` | 生成可核对清单、不可逆点已标记 | 工作空间管理员 | 读一致性；预览 `expires_at` | `RETENTION_HOLD`（法律保留优先）、`VERSION_CONFLICT` | 同上 |
| `DispositionRequest` `APPROVED → EXECUTING` via `execute` | 预览未过期、对象版本未变化 | 工作空间管理员 + 执行身份 | `expected_revision`；幂等键 | `VERSION_CONFLICT`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| `DispositionRequest` `EXECUTING → COMPLETED` via `complete` | 执行结果已记录、保留批准及执行证据 | 系统 | `expected_revision` | `MIGRATION_GATE_FAILED`、`VERSION_CONFLICT` | 同上 |
| 物理删除 via `dispose` | 保留期已过、无有效 `LegalHold`、处置已批准 | 平台管理员 | 不可逆点须记录 | `RETENTION_HOLD`、`IRREVERSIBLE_POINT_PASSED` | 同上 |

**状态列表**：`AuditEvent`（只追加）；`LegalHold`：`DRAFT`、`ACTIVE`、`RELEASED`；`DispositionRequest`：`DRAFT`、`PENDING_APPROVAL`、`APPROVED`、`EXECUTING`、`COMPLETED`

**不变量**：审计只追加、不可由普通业务接口修改；有效法律保留优先于普通保留与处置规则；交付件、审批、签核、配置和审计在保留期内不得物理删除；所有处置必须先生成可核对清单并保留批准及执行证据。

### 3.22 导出（`BackgroundJob` 子类型 + 处置）

**模块**：`operations` / 跨模块 ｜ **状态机名**：`export_job_lifecycle`

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| 申请 `QUEUED` via `request` | 范围、字段、格式已定义；幂等键 | 有导出权限的用户 | 幂等键 `(job_type, scope, requested_by, request_hash)` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | `evidence/us14-search-notification.md`、`evidence/p1-high-risk-operations.md` |
| `QUEUED → RUNNING` via `start` | 资源预算可用、检查点已初始化 | 系统执行身份 | `expected_revision`；检查点 | `MIGRATION_GATE_FAILED`、`VERSION_CONFLICT` | 同上 |
| `RUNNING → COMPLETED` via `complete` | 进度 100%、`result_file_id` 已生成 | 系统 | `expected_revision`；幂等 | `VERSION_CONFLICT` | 同上 |
| `RUNNING → PAUSED`/`FAILED`/`CANCELLED` via `pause`/`fail`/`cancel` | 记录原因、失败明细 | 系统或申请人 | `expected_revision`；保留检查点 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| 下载 via `download` | 启动、生成下载地址、下载时均复核权限；签名地址 ≤ 5 分钟 | 申请人 | 签名地址时效；权限复核 | `UNAUTHORIZED`、`PRECONDITION_FAILED`（地址过期） | 同上 |
| 过期清理 via `cleanup` | 保留期已过、`result_file_id` 已归档或处置 | 系统 | 不可逆点须记录 | `RETENTION_HOLD`、`IRREVERSIBLE_POINT_PASSED` | 同上 |

**状态列表**：`QUEUED`、`RUNNING`、`PAUSED`、`FAILED`、`CANCELLED`、`COMPLETED`

**不变量**：导出在启动、生成下载地址和下载时均必须复核权限；附件签名地址有效期不得超过 5 分钟且实际下载前必须复核权限；过期清理须遵守保留期与法律保留。

### 3.23 迁移任务/批次/切换（`MigrationProgram` / `MigrationRun` / `MigrationBatch` / `MigrationMapping` / `MigrationChange` / `CutoverDecision`）

**模块**：`datamigration` ｜ **状态机名**：`migration_program_lifecycle` + `migration_run_lifecycle` + `migration_change_lifecycle`

**`MigrationProgram`**：

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `DRAFT → INVENTORY` via `start_inventory` | 源系统已注册、凭据密钥引用有效 | 迁移负责人 | `expected_revision` | `UNAUTHORIZED`、`PRECONDITION_FAILED` | `evidence/us20-mysql-migration.md`、`evidence/us20-migration-rehearsal.md` |
| `INVENTORY → MAPPING` via `complete_inventory` | 盘点完整、风险级别已标记 | 迁移负责人 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `MAPPING → REHEARSING` via `approve_mapping` | 映射版本已批准、不迁移内容已 `EXCLUDE` 并记录原因 | 迁移负责人 + 业务审批 | `expected_revision`；映射版本不可修改 | `MIGRATION_GATE_FAILED`、`VERSION_CONFLICT` | 同上 |
| `REHEARSING → READY` via `complete_rehearsal` | 至少两次生产等价彩排、阻断问题清零、回退演练完成 | 迁移负责人 + DBA + 业务签字 | `expected_revision`；不可逆点预检 | `MIGRATION_GATE_FAILED`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| `READY → CUTTING_OVER` via `cutover` | Go/No-Go 通过、源数据库写入冻结、最终增量已应用、核对通过 | 迁移负责人 + DBA + 业务签字；`OperationImpactPreview` + `CutoverDecision` | `expected_revision`；预览版本一致；幂等键；不可逆点须记录 | `MIGRATION_GATE_FAILED`（核对失败/双写/回退点越界）、`IRREVERSIBLE_POINT_PASSED`、`VERSION_CONFLICT` | 同上 |
| `CUTTING_OVER → STABILIZING` via `stabilize` | 目标数据库写入已开放、旧系统只读 | 迁移负责人 + DBA | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `STABILIZING → COMPLETED` via `complete` | 业务签字、审计证据归档、旧系统下线处置已批准 | 迁移负责人 + 业务签字 + 法务/审计 | `expected_revision`；不可逆点须记录 | `RETENTION_HOLD`、`IRREVERSIBLE_POINT_PASSED` | 同上 |
| 任意非终态 → `PAUSED`/`CANCELLED` via `pause`/`cancel` | 记录原因；未越过回退点时可回退 | 迁移负责人 | `expected_revision` | `IRREVERSIBLE_POINT_PASSED`（越界后禁止直接取消） | 同上 |
| 回退 `CUTTING_OVER → ROLLING_BACK`（或前序状态） via `rollback` | 未越过回退点或已验证反向同步；记录 `CutoverDecision` | 迁移负责人 + DBA + 业务签字 | `expected_revision`；幂等键；不可逆点检查 | `IRREVERSIBLE_POINT_PASSED`、`MIGRATION_GATE_FAILED` | 同上 |

**`MigrationRun`**（`run_type` 含 `PROFILE`、`DRY_RUN`、`FULL_LOAD`、`CDC`、`RECONCILIATION`、`CUTOVER`、`ROLLBACK_REHEARSAL`）：

| 关键迁移 (from→to via action_key) | 前置条件 | 所需权限 | 并发/乐观锁 | 失败原因 | 验证证据/测试 |
|---|---|---|---|---|---|
| `QUEUED → RUNNING` via `start` | 映射版本已批准、源快照已记录 | 迁移执行身份 | `expected_revision`；批次断点续传 | `MIGRATION_GATE_FAILED`、`VERSION_CONFLICT` | 同上 |
| `RUNNING → VALIDATING` via `validate` | 装载完成、校验规则已执行 | 迁移执行身份 | `expected_revision` | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |
| `VALIDATING → COMPLETED` via `complete` | 核对通过、报告已生成 | 迁移执行身份 | `expected_revision`；相同源快照+映射版本+批次键重复执行不得创建重复目标业务对象 | `MIGRATION_GATE_FAILED`（核对失败）、`VERSION_CONFLICT` | 同上 |
| `RUNNING/VALIDATING → PAUSED`/`FAILED`/`CANCELLED` via `pause`/`fail`/`cancel` | 记录失败明细、保留检查点 | 迁移执行身份或迁移负责人 | `expected_revision`；批次 `attempts` 限流 | `PRECONDITION_FAILED`、`VERSION_CONFLICT` | 同上 |

**`MigrationBatch`**：状态机同 `MigrationRun` 子集（`QUEUED → RUNNING → COMPLETED`，允许 `PAUSED/FAILED/CANCELLED`）；支持断点续传和独立重跑，完成后记录源/目标计数和校验摘要。

**`MigrationMapping`**：`DRAFT → REVIEW_PENDING → APPROVED → SUPERSEDED`；已用于正式运行的映射版本不可修改。

**`MigrationChange`**：`CAPTURED → TRANSFORMED → APPLIED`，异常 `QUARANTINED/FAILED/IGNORED`；`source_position + source_transaction_id + source_object + source_key + operation` 保证幂等，同一源对象按提交顺序应用。

**`CutoverDecision`**（`decision_type`：`GO_NO_GO`、`OPEN_TARGET_WRITES`、`ROLLBACK`、`FORWARD_FIX`、`DECOMMISSION_SOURCE`）：开放目标数据库写入必须记录所有门禁结果；开放后如无反向同步能力，不得批准直接恢复源数据库写入。

**状态列表**：`MigrationProgram`：`DRAFT`、`INVENTORY`、`MAPPING`、`REHEARSING`、`READY`、`CUTTING_OVER`、`STABILIZING`、`COMPLETED`、`PAUSED`、`CANCELLED`、`ROLLING_BACK`；`MigrationRun`：`QUEUED`、`RUNNING`、`VALIDATING`、`COMPLETED`、`PAUSED`、`FAILED`、`CANCELLED`

**不变量**：进入 `READY` 前必须完成映射审批、至少两次生产等价彩排、阻断问题清零和回退演练；正式切换时必须先冻结源数据库写入并应用最终增量，再开放目标数据库写入；禁止无治理的双主写入；PDP 开始产生新业务写入后，除非已验证反向同步，否则不得直接恢复旧系统为写入主系统。

## 4. 并发与失败场景验证矩阵

下表针对 P1 高风险迁移，列出并发冲突场景、期望失败原因和补偿路径。所有场景的失败响应必须满足第 2.5 节要求。

| 高风险操作 | 并发冲突场景 | 期望失败原因 | 期望 HTTP 状态 | 补偿路径 | 验证证据/测试 |
|---|---|---|---|---|---|
| 项目受控回退（`controlled_rollback`） | 回退预览生成后，关联阶段或任务被并发推进，`current_revisions` 不一致 | `VERSION_CONFLICT` + `PRECONDITION_FAILED` | 409 | 重新生成 `OperationImpactPreview`，重新提交回退审批 | `tests/contracts/us4-project-lifecycle.spec.ts`、`evidence/us4-project-lifecycle.md`、`evidence/p1-high-risk-operations.md` |
| 项目受控回退 | 顶层生命周期倒退导致领域阶段映射冲突 | `MIGRATION_GATE_FAILED` | 409 | 修正领域阶段映射或改用允许的回退路径 | 同上 |
| 项目关闭（`close`） | 关闭预览生成后，子项目或交付件被并发修改 | `VERSION_CONFLICT` | 409 | 重新生成预览，重新校验关闭门禁 | 同上 |
| 项目关闭 | 必需任务、交付件、问题、审批或子项目门禁未满足 | `PRECONDITION_FAILED` + `blocking_items` | 409 | 完成 blocking_items 后重新提交 | 同上 |
| 基线替换（`supersede`） | 已批准基线被并发修改或新基线审批未通过 | `VERSION_CONFLICT` + `PRECONDITION_FAILED` | 409 | 重新审批新基线，重新生成替换预览 | `tests/contracts/us6-plan-baseline.spec.ts`、`evidence/us6-plan-baseline.md` |
| 基线替换 | 越过不可逆点后尝试回退原基线 | `IRREVERSIBLE_POINT_PASSED` | 409 | 仅允许创建新基线前向修复 | 同上 |
| 人工进度调整（`ProgressOverride`） | 调整审批通过后底层任务事实被并发修改 | `VERSION_CONFLICT` | 409 | 重新计算原值，重新提交调整审批 | 同上 |
| 人工进度调整 | 未经审批尝试调整 | `UNAUTHORIZED` + `PRECONDITION_FAILED` | 403/409 | 提交审批后重试 | 同上 |
| 交付件发布（`publish`） | 发布预览生成后，签核人或内容版本被并发变更 | `VERSION_CONFLICT` | 409 | 重新生成发布预览，重新签核 | `tests/contracts/us7-deliverable.spec.ts`、`evidence/us7-deliverable.md` |
| 交付件发布 | 尝试覆盖已发布版本 | `RETENTION_HOLD` + `IRREVERSIBLE_POINT_PASSED` | 409 | 创建新版本修订，原版本保留 | 同上 |
| 交付件发布 | 签核绑定版本与发布版本不一致 | `PRECONDITION_FAILED` | 409 | 重新签核确切版本 | 同上 |
| 审批终态（`approve`/`reject`） | 状态回写时业务对象版本不匹配 | `VERSION_CONFLICT` + `PRECONDITION_FAILED` | 409 | 重新读取业务对象，重新校验回写条件；不静默覆盖 | `tests/contracts/us8-approval.spec.ts`、`tests/backend/contract/approval/ApprovalDatabaseContractTest.java`、`evidence/us8-approval.md` |
| 审批终态 | 同一节点并发办理（双签冲突） | `VERSION_CONFLICT` | 409 | 幂等键拒绝重复办理；保留首个有效办理 | 同上 |
| 审批终态 | 终态预览生成后对象版本变化 | `VERSION_CONFLICT` | 409 | 重新生成终态预览 | 同上 |
| 审批节点退回/重提 | 退回后并发重提导致轮次冲突 | `VERSION_CONFLICT` | 409 | 幂等键拒绝重复轮次；保留原轮次 | 同上 |
| 变更应用（`apply`） | 应用时关联对象版本与预览不一致 | `VERSION_CONFLICT` | 409 | 重新生成影响预览，重新提交应用 | `tests/contracts/us10-governance-control.spec.ts`、`evidence/us10-governance-control.md` |
| 变更应用 | 应用过程中部分对象更新失败 | `MIGRATION_GATE_FAILED` → `APPLY_FAILED` | 409 | 仅允许幂等重试、回滚或人工补偿；保留失败明细 | 同上 |
| 变更应用 | 越过不可逆点后尝试整体回滚 | `IRREVERSIBLE_POINT_PASSED` | 409 | 前向修复或已验证的反向同步 | 同上 |
| 处置执行（`DispositionRequest.execute`） | 处置预览生成后对象被并发修改或法律保留被激活 | `VERSION_CONFLICT` + `RETENTION_HOLD` | 409 | 重新生成处置预览；法律保留优先 | `tests/backend/contract/security/AuthorizationAuditDatabaseContractTest.java`、`evidence/us11-security-lifecycle.md` |
| 处置执行 | 尝试物理删除保留期内对象 | `RETENTION_HOLD` + `IRREVERSIBLE_POINT_PASSED` | 409 | 拒绝；保留期过后重新审批 | 同上 |
| 导出下载（`download`） | 签名地址过期或权限撤销 | `UNAUTHORIZED` + `PRECONDITION_FAILED` | 403/409 | 重新生成签名地址（≤ 5 分钟）；权限撤销 30 秒内生效 | `evidence/us14-search-notification.md`、`evidence/p1-high-risk-operations.md` |
| 迁移切换（`cutover`） | Go/No-Go 未通过、核对失败、增量未追平 | `MIGRATION_GATE_FAILED` | 409 | 阻断切换；继续增量追平与核对 | `evidence/us20-mysql-migration.md`、`evidence/us20-migration-rehearsal.md` |
| 迁移切换 | 检测到双主写入或回退点越界 | `IRREVERSIBLE_POINT_PASSED` + `MIGRATION_GATE_FAILED` | 409 | 立即阻断；仅允许已验证反向同步或前向修复 | 同上 |
| 迁移切换 | 切换预览生成后源数据被并发写入（冻结失败） | `VERSION_CONFLICT` + `MIGRATION_GATE_FAILED` | 409 | 重新冻结源数据库写入，重新应用最终增量 | 同上 |
| 迁移回退（`rollback`） | 越过不可逆点后尝试回退到旧系统 | `IRREVERSIBLE_POINT_PASSED` | 409 | 前向修复；记录 `CutoverDecision` | 同上 |
| 迁移批次重跑 | 相同源快照+映射版本+批次键重复执行 | `VERSION_CONFLICT`（幂等拒绝） | 409 | 幂等键拒绝创建重复目标业务对象；返回原批次结果 | 同上 |
| 领域包发布（`publish`） | 发布校验失败或继承层次成环 | `MIGRATION_GATE_FAILED` | 409 | 保持原生产版本；返回全部失败校验项 | `tests/contracts/us2-domain-package.spec.ts`、`evidence/us2-domain-package.md` |
| 领域包回滚（`rollback`） | 无可回退基线版本或运行实例未迁移 | `IRREVERSIBLE_POINT_PASSED` + `PRECONDITION_FAILED` | 409 | 完成实例迁移后重新回滚；或前向修复 | 同上 |
| 用户停用（`suspend`/`disable`） | 停用时未完成责任未重新分配 | `PRECONDITION_FAILED` | 409 | 重新分配责任后重试 | `tests/backend/contract/identity/IdentityLifecycleDatabaseContractTest.java`、`evidence/us1-workspace-governance.md` |
| 用户停用 | 并发停用同一用户 | `VERSION_CONFLICT` | 409 | 幂等键拒绝重复停用 | 同上 |
| 工作空间归档（`archive`） | 存在活动项目或有效跨空间授权 | `PRECONDITION_FAILED` + `blocking_items` | 409 | 完成 blocking_items 后重新生成预览 | `tests/contracts/us1-workspace-governance.spec.ts`、`evidence/us1-workspace-governance.md` |

## 5. 证据要求

### 5.1 证据文件命名约定

每个状态机关联的证据文件按 `tasks.md` 中的 evidence 任务命名，统一存放于 `specs/002-pdp-product/evidence/`。每个 evidence 文件必须包含：状态机覆盖矩阵、正常迁移、失败场景、补偿路径、日志/指标追踪和签字记录。

### 5.2 状态机 ↔ 测试 ↔ 证据映射

| 状态机 | 契约测试 | MySQL 契约测试 | Evidence 文件 |
|---|---|---|---|
| `user_account_lifecycle` / `user_session_lifecycle` | （由 US1 契约覆盖） | `tests/backend/contract/identity/IdentityLifecycleDatabaseContractTest.java` | `evidence/us1-workspace-governance.md` |
| `workspace_lifecycle` / `organization_unit_lifecycle` / `workspace_membership_lifecycle` / `collaboration_grant_lifecycle` | `tests/contracts/us1-workspace-governance.spec.ts` | `tests/backend/contract/workspace/WorkspaceGovernanceDatabaseContractTest.java` | `evidence/us1-workspace-governance.md` |
| `domain_package_version_lifecycle` / `MigrationJob`（领域包实例迁移） | `tests/contracts/us2-domain-package.spec.ts` | `tests/backend/contract/domainconfig/DomainPackageDatabaseContractTest.java` | `evidence/us2-domain-package.md` |
| 项目模板版本状态 | `tests/contracts/us3-project-template.spec.ts` | `tests/backend/contract/template/ProjectTemplateDatabaseContractTest.java` | `evidence/us3-project-template.md` |
| `project_lifecycle` / `project_stage_lifecycle` / `project_hierarchy` | `tests/contracts/us4-project-lifecycle.spec.ts` | `tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java` | `evidence/us4-project-lifecycle.md` |
| `task_lifecycle` / `checklist_item_lifecycle` | `tests/contracts/us5-task-collaboration.spec.ts` | `tests/backend/contract/planning/TaskCollaborationDatabaseContractTest.java` | `evidence/us5-task-collaboration.md` |
| `milestone_lifecycle` / `dependency_lifecycle` / `plan_baseline_lifecycle` | `tests/contracts/us6-plan-baseline.spec.ts` | `tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java` | `evidence/us6-plan-baseline.md` |
| `deliverable_lifecycle` / `Signature` | `tests/contracts/us7-deliverable.spec.ts` | `tests/backend/contract/deliverable/DeliverableDatabaseContractTest.java` | `evidence/us7-deliverable.md` |
| `approval_instance_lifecycle` / `approval_step_lifecycle` | `tests/contracts/us8-approval.spec.ts` | `tests/backend/contract/approval/ApprovalDatabaseContractTest.java` | `evidence/us8-approval.md` |
| 多视图与基础统计（无独立状态机，引用 `task_lifecycle` 等） | `tests/contracts/us9-project-workspace.spec.ts` | `tests/backend/contract/experience/SavedViewDatabaseContractTest.java` | `evidence/us9-project-workspace.md` |
| `risk_lifecycle` / `issue_lifecycle` / `change_request_lifecycle` | `tests/contracts/us10-governance-control.spec.ts` | `tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java` | `evidence/us10-governance-control.md` |
| `audit_retention_disposition_lifecycle` / 授权优先级 | `tests/contracts/us11-authorization.spec.ts`、`tests/contracts/us11-audit-lifecycle.spec.ts` | `tests/backend/contract/security/AuthorizationAuditDatabaseContractTest.java` | `evidence/us11-security-lifecycle.md` |
| `export_job_lifecycle` / 通知与搜索投影 | `tests/contracts/us14-search-notification.spec.ts` | （由 US14 后端测试覆盖） | `evidence/us14-search-notification.md` |
| `migration_program_lifecycle` / `migration_run_lifecycle` / `migration_batch` / `migration_mapping` / `migration_change` / `CutoverDecision` | （由 US20 契约覆盖） | （由 US20 后端测试覆盖） | `evidence/us20-mysql-migration.md`、`evidence/us20-migration-rehearsal.md` |
| 平台工作流实例/任务/事件迁移（`WorkflowInstanceRef` / `WorkflowTaskRef` / `WorkflowIncident`） | （由 `workflow` 模块覆盖） | `tests/backend/contract/workflow/FlowableSchemaMySqlMatrixTest.java` | `evidence/platform-workflow-acceptance.md` |
| 高风险操作目录（覆盖第 4 节全部场景） | 端到端抽查 | 端到端抽查 | `evidence/p1-high-risk-operations.md` |
| MySQL 持久化与跨库切换禁用契约 | （由 `persistence` 模块覆盖） | `tests/backend/contract/persistence/PersistenceProviderExtensionContractTest.java`、`tests/backend/architecture/PersistenceBoundaryTest.java`、`tests/backend/contract/persistence/LiquibaseMySqlMatrixTest.java` | `evidence/mysql-contract-matrix.md` |
| 业务连续性与服务等级档案 | （由 `operations` 模块覆盖） | （由 `operations` 模块覆盖） | `evidence/us18-business-continuity.md`、`evidence/p1-service-level-coverage.md` |

### 5.3 证据内容最低要求

每个 evidence 文件必须包含以下章节，缺一不可：

1. **状态机覆盖矩阵**：列出本 US 涉及的所有状态机、状态列表、关键迁移和验证结果。
2. **正常迁移验证**：每个 `from_state + action_key + to_state` 至少一个用例，附 `correlation_id`。
3. **失败场景验证**：覆盖本矩阵第 2.4 节适用的失败原因分类，每类至少一个用例，附稳定原因和下一步建议。
4. **并发与乐观锁验证**：覆盖 `expected_revision` 冲突场景，证明不静默覆盖。
5. **补偿路径验证**：高风险操作必须验证预览、确认、审计和撤销/回退/人工补偿路径。
6. **日志与指标追踪**：每个迁移必须可由 `correlation_id` 串联审计、事件和指标。
7. **签字记录**：产品、领域/业务验收、架构、质量、运维及适用安全责任人签字。

### 5.4 实现前门禁

- 阶段 A0 必须冻结本矩阵，并通过实现前 `/speckit-analyze`，要求 CRITICAL/HIGH 为 0（`evidence/analysis-pre-implementation.md`）。
- 任何状态机变更必须先更新本矩阵、契约（`contracts/openapi.yaml`、`contracts/events.md`、`contracts/domain-package.schema.json`）和契约测试，再实现 Controller、消费者或领域包运行时。
- 阶段 E 重新执行只读 `/speckit-analyze`（`evidence/analysis-pre-release.md`），CRITICAL/HIGH 为 0 后方可最终 DoD 签字（`evidence/p1-acceptance.md`）。

### 5.5 P1 边界声明

- 本矩阵“数据库切换”相关状态机（`MigrationProgram` 中认证数据库之间的切换）在 P1 仅实现状态语义、高风险操作分类和治理扩展契约，不提供认证数据库之间的可执行切换入口；P1 可执行路径仅包括历史 MySQL 旧系统到 PDP MySQL 的上线切换。
- P2/P3 状态机（项目集、项目组合、售前测试、转包代施、割接、回访、维保、AI、移动离线、高级自动化、外部通知渠道等）不在本矩阵范围，需在独立子规格或 backlog 中定义并追溯上位需求。
