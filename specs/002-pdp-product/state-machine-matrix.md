# P1 核心状态机验证矩阵

## 通用约束

所有迁移必须校验工作空间、当前状态、动作权限、`expectedRevision`、业务前置条件和依赖不变量。失败返回稳定原因、当前状态/版本和下一步建议，并写入关联审计。高风险迁移还必须校验未过期影响预览。

| 对象 | 关键迁移 | 前置条件与权限 | 并发/失败原因 | 闭环证据 |
|---|---|---|---|---|
| Workspace | DRAFT→ACTIVE→SUSPENDED→ARCHIVED | 负责人权限；无活动项目方可归档 | 版本冲突、活动项目、无权 | US1 状态机/API/E2E |
| CollaborationGrant | DRAFT→ACTIVE→EXPIRED/REVOKED | 主工作空间授权；范围和期限有效 | 越权范围、已撤销、版本冲突 | 撤权时限与审计 |
| DomainPackageVersion | DRAFT→VALIDATING→REVIEW_PENDING→PUBLISHED | 结构、权限、迁移和职责分离通过 | 冲突、不可达状态、同人审核 | US2 发布证据 |
| MigrationJob | DRAFT→PREVIEWING→READY→RUNNING→COMPLETED | 预览和映射批准 | 门禁失败、暂停、回滚冲突 | 领域包迁移报告 |
| Project | PRE_PLANNING→PLANNING→EXECUTING→ACCEPTING→SERVICING→CLOSED | 阶段、任务、交付件、审批和子项目门禁 | 必需产出缺失、非法倒退 | US4/标准试点 |
| ProjectStage | NOT_STARTED→READY→IN_PROGRESS→COMPLETED | 进入/退出条件和负责人权限 | 阻塞、暂停、未满足产出 | 阶段状态机测试 |
| Task | DRAFT→READY→IN_PROGRESS→BLOCKED→COMPLETED | 必需检查项完成 | revision 冲突、检查项缺失 | US5 测试 |
| Milestone | PLANNED→AT_RISK→ACHIEVED/MISSED | 完成规则和必需产出满足 | 依赖冲突、证据缺失 | US6 进度证据 |
| PlanBaseline | DRAFT→APPROVAL_PENDING→APPROVED→SUPERSEDED | 审批通过、权重归一 | 循环依赖、版本冲突 | 基线差异测试 |
| Deliverable | DRAFT→REVIEWING→APPROVED→PUBLISHED→ARCHIVED | 内容哈希、版本与签核有效 | 覆盖已发布版本、无权 | US7 证据 |
| ApprovalInstance | DRAFT→PENDING→APPROVED/REJECTED/WITHDRAWN/CANCELLED | 当前办理人实时有权 | 重复动作、业务对象版本冲突 | US8/工作流恢复 |
| Risk/Issue/Change | 各自开放→处理中→关闭/取消 | 负责人和关闭结论 | 无原因重开、影响未处理 | US10 证据 |
| BackgroundJob | QUEUED→RUNNING→PAUSED→COMPLETED/FAILED/CANCELLED | 资源预算和幂等键有效 | 租约冲突、重试耗尽 | 作业恢复测试 |
| MigrationProgram/CUTOVER | DRAFT→…→READY→CUTTING_OVER→STABILIZING→COMPLETED | 映射、CDC、核对、冻结、Go 决策 | 未追平、核对失败、越过不可逆点 | US20 彩排 |
| DatabaseSwitch | PLANNED→REHEARSING→READY→FREEZING→SWITCHING→VALIDATING→STABILIZING→COMPLETED | `MYSQL→MYSQL` 获批、源目标不同、核对通过 | 未认证组合、双主、schema/位点冲突 | US21 双向演练 |
| WorkflowInstanceRef | STARTING→ACTIVE→SUSPENDED→COMPLETED/TERMINATED/INCIDENT | 定义已部署、业务引用和幂等键有效 | 重复关联、无权管理、引擎故障 | SC-046 |

状态机测试按允许迁移、非法迁移、权限拒绝、版本冲突、依赖冲突和恢复路径建立参数化用例；允许迁移通过率与非法迁移拦截率目标均为 100%。

