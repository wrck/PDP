# PDP P1 追踪矩阵

## 1. 目的

本矩阵建立 P1 用户故事、功能需求（FR）、成功指标（SC）、契约和任务的基础追踪关系，并校验
OpenAPI、事件、领域包 Schema 和迁移报告 Schema 完整一致；同时登记消费者、版本、兼容影响及弃用
窗口。本矩阵在实现前建立基线（对应 `tasks.md` T012），发布阶段（对应 `tasks.md` T329）仅补齐
代码、自动化测试、运行手册和验收证据并关闭全部断链。

- 本矩阵是 P1 追踪与契约一致性的单一来源；`contracts/coverage.md` 是契约覆盖清单，两者互补。
- 所有 Controller、消费者、领域扩展或 Web 调用在实现前必须先在本矩阵及对应契约文件登记稳定
  操作、消费者、版本和兼容验证；实际路由、事件样例、Schema 与本矩阵不一致时发布门禁失败。
- 当前阶段为“实现前基线”：契约与规格已冻结，代码、测试、运行手册和验收证据统一标注为
  “待实现”，由发布阶段 T329 关闭。

## 2. P1 用户故事清单

P1 共 14 个用户故事，优先级均为 P1，对应 `tasks.md` 阶段 3–16。

| 故事 | 名称 | 阶段 | 主要角色 | 闭环证据路径 |
|---|---|---|---|---|
| US1 | 工作空间与组织治理 | 阶段 3 | 平台管理员 | `evidence/us1-workspace-governance.md` |
| US2 | 领域包与深度定制 | 阶段 4 | 领域管理员、独立发布者 | `evidence/us2-domain-package.md` |
| US3 | 项目模板与项目创建 | 阶段 5 | 管理员、项目经理 | `evidence/us3-project-template.md` |
| US4 | 项目生命周期与主子项目 | 阶段 6 | 项目经理 | `evidence/us4-project-lifecycle.md` |
| US5 | 任务、检查项与团队协作 | 阶段 7 | 项目成员 | `evidence/us5-task-collaboration.md` |
| US6 | 里程碑、依赖和计划基线 | 阶段 8 | 项目经理 | `evidence/us6-plan-baseline.md` |
| US7 | 交付件全生命周期 | 阶段 9 | 项目团队 | `evidence/us7-deliverable.md` |
| US8 | 统一审批中心 | 阶段 10 | 审批人 | `evidence/us8-approval.md` |
| US9 | 多视图项目工作区 | 阶段 11 | 各角色用户 | `evidence/us9-project-workspace.md` |
| US10 | 风险、问题与变更控制 | 阶段 12 | 项目团队 | `evidence/us10-governance-control.md` |
| US11 | 权限、审计与数据生命周期 | 阶段 13 | 管理员、审计人员、数据负责人 | `evidence/us11-security-lifecycle.md` |
| US14 | 基础搜索与站内通知 | 阶段 14 | 项目成员 | `evidence/us14-search-notification.md` |
| US18 | 高可用与业务连续性 | 阶段 15 | 平台运维、上线值守 | `evidence/us18-business-continuity.md` |
| US20 | MySQL 历史数据迁移与上线切换 | 阶段 16 | 数据负责人、上线团队 | `evidence/us20-mysql-migration.md`、`evidence/us20-migration-rehearsal.md` |

> 闭环证据文件当前均标注为“待实现”，由各故事末尾任务（如 T110、T127…T304）在实现阶段生成。

## 3. FR → 故事 追踪表

以下 FR 编号取自 `spec.md`（FR-001～FR-174）。标注“平台基础（跨故事）”的 FR 不归属单一故事，
作为多个 P1 故事的公共前提。

### 3.1 平台/L0 基础（跨故事）

| FR | 简述 | 归属 |
|---|---|---|
| FR-001 | 本产品规格作为所有 PDP 子规格上位范围和优先级来源 | L0 平台基础 |
| FR-002 | 子规格声明层级、上位规格、引用需求和不在范围内容 | L0 平台基础 |
| FR-166 | 每个 P1 业务能力明确角色、触发、状态、规则、输出、补偿、责任人和闭环证据位置 | 平台基础（跨故事） |
| FR-167 | 核心对象使用确定性状态机；每个迁移定义前置条件、权限、并发语义、结果和稳定失败原因 | 平台基础（跨故事） |
| FR-168 | 高风险操作提供影响预览、确认、审计及撤销/回退/补偿 | 平台基础（跨故事，含 US2/US6/US7/US8/US10/US11/US20） |
| FR-169 | 每项 P1 关键能力具有服务等级档案 | 平台基础（US18 主责） |
| FR-171 | P1 完成威胁建模、数据分类、传输存储保护、密钥凭据、职责分离、跨空间隔离和审计防篡改评审 | 平台基础（跨故事） |
| FR-172 | P1 外部 HTTP、事件和领域包接口具有完整版本化契约、消费者、兼容影响、弃用窗口和自动化兼容校验 | 平台基础（契约治理） |
| FR-173 | 维护版本化核心术语目录 | 平台基础（跨故事） |
| FR-174 | 工作流作为 P1 公共基础能力，提供版本化 BPMN 定义、部署、关联、人工任务、定时器、重试、补偿和迁移 | 平台基础（US8 主消费） |
| FR-135 | P1 首个可上线版本同时交付平台基础能力和标准实施领域包 | 平台基础（阶段 17） |
| FR-136 | P1 标准实施领域包闭环覆盖创建到归档主线 | 平台基础（阶段 17） |
| FR-137 | P1 上线验收使用真实或等价试点项目完成端到端验证 | 平台基础（阶段 17/18） |

### 3.2 按故事分组

| 故事 | FR 编号 | 范围说明 |
|---|---|---|
| US1 | FR-003、FR-004、FR-005、FR-006、FR-121、FR-122、FR-123、FR-124 | 工作空间创建启停归档、独立成员角色配置、多空间切换、显式授权可撤销、主工作空间归属、协作授权范围与撤销时效 |
| US2 | FR-007、FR-008、FR-009、FR-010、FR-011、FR-012、FR-013、FR-014、FR-015、FR-016、FR-017、FR-018、FR-019、FR-020、FR-130、FR-131、FR-132、FR-133、FR-134 | 领域包版本化组合发布迁移停用、扩展核心对象、声明式配置、三层继承、冲突检测、职责分离、发布校验、实例版本快照、存量迁移、核心字段目录与冲突防护 |
| US3 | FR-021、FR-022、FR-023（编号/名称/目标/范围/客户/合同/负责人/成员/优先级/健康度/时间） | 从空白或模板创建项目、模板含默认阶段任务里程碑交付件审批、项目主数据维护 |
| US4 | FR-023（项目主数据）、FR-024、FR-025、FR-026、FR-027、FR-028、FR-117、FR-118、FR-119、FR-120 | 父子层级与权限汇总、阶段生命周期与进出条件、推进回退暂停关闭受控、关闭前提、复制归档不作废历史、统一顶层生命周期与领域子阶段映射 |
| US5 | FR-029、FR-030、FR-031、FR-032、FR-033、FR-034、FR-037 | 任务子任务生命周期、负责人参与人优先级时间工作量评论附件、强制检查项门禁、里程碑、依赖循环与日期冲突、评论提及关注附件活动时间线 |
| US6 | FR-035、FR-036、FR-125、FR-126、FR-127、FR-128、FR-129 | 计划基线创建批准比较历史、统一进度汇总规则、里程碑权重归一与必需产出、未满足产出不计完成、进度可解释可追溯、经审批人工调整 |
| US7 | FR-038、FR-039、FR-040、FR-041、FR-042 | 交付件独立对象管理、已发布不可覆盖、模板命名到期约束、内外部签核、归档完整清单与缺失识别 |
| US8 | FR-043、FR-044、FR-045、FR-046 | 统一审批中心、通过退回拒绝撤回转交委托加签抄送、节点办理人时间意见附件动作回写、审批页不展示无权数据 |
| US9 | FR-053、FR-054、FR-055、FR-056、FR-057 | 概览列表看板日历时间线详情视图、一致数据权限状态、看板状态映射拖动WIP阻塞、时间线展示阶段任务里程碑依赖基线延期、个人视图与角色默认视图 |
| US10 | FR-047、FR-048、FR-049、FR-050、FR-051、FR-052 | 风险概率影响等级措施触发、矩阵汇总与转问题、问题来源类型严重程度方案结论、变更范围工期资源成本风险交付影响、批准变更更新并留差异、重开取消关闭记录原因 |
| US11 | FR-063、FR-064、FR-065、FR-066、FR-067、FR-068、FR-069、FR-070、FR-071、FR-164 | 功能数据范围对象字段操作权限组合、数据范围多维、临时授权范围期限审批撤销、敏感字段附件签名定位凭据成本独立授权审计、一致权限、撤权时效、关键审计事件、数据分类保留归档恢复法律保留处置、不可逆删除约束、撤权后搜索报表30秒移除与附件导出会话复核 |
| US14 | FR-058、FR-059、FR-060 | 权限范围跨项目跨对象搜索、统计钻取有权明细、P1 统一站内通知中心幂等已读链接重提权限复核 |
| US18 | FR-102、FR-103、FR-104、FR-105、FR-106、FR-107、FR-108、FR-109、FR-110、FR-111、FR-165 | 核心与其他在线能力可用性、割接保障窗口无计划停机、RTO/RPO、非核心故障降级、关键数据恢复验证、1000 并发、页面与搜索报表时限、大批量后台处理、可用性计算口径与排除项 |
| US20 | FR-139、FR-140、FR-141、FR-142、FR-143、FR-144、FR-145、FR-146、FR-147、FR-148、FR-149、FR-150 | 迁移独立可重复可审计工作流、源库盘点、表字段映射与排除、标识稳定映射、类型差异处理、增量追平、预检分批断点续传幂等回退、核对覆盖、切换冻结与Go/No-Go、隔离区分级处置、回退点与不可逆点、旧系统只读保留与下线 |

### 3.3 非 P1 范围（仅登记边界，不进入本期追踪闭环）

| 范围 | FR 编号 | 说明 |
|---|---|---|
| P2 自动化/报表订阅 | FR-061、FR-062、FR-163 | 见 `backlog-p2-p3.md` |
| P2 项目组合/资源成本/报表 | FR-072～FR-082 | US12、US13 |
| P2 外部参与者/移动/集成 | FR-083～FR-093 | US15、US16 |
| P2 行业领域包 | FR-094～FR-101、FR-138 | US17（FR-135～FR-137 属 P1 标准实施包） |
| P2 多数据库 | FR-151～FR-162 | US21 |
| P3 国际化/无障碍/AI | FR-112～FR-116、FR-170 | US19 |

## 4. SC → 验收追踪表

以下 SC 编号取自 `spec.md`（SC-001～SC-046）。量化标准为简述，完整定义见 `spec.md`。证据路径
当前标注为“待实现”，发布阶段 T329 关闭。

| SC | 量化标准（简述） | 验收证据路径 | 责任人 |
|---|---|---|---|
| SC-001 | 后续子规格均可追溯到 L0 范围、故事和 FR | 本矩阵 + `spec.md` | 平台架构负责人 |
| SC-002 | 模板创建项目默认对象生成准确率 100% | `evidence/us3-project-template.md` | 项目经理 |
| SC-003 | 5 分钟内创建 ≥10 任务、2 里程碑及依赖 | `evidence/us5-task-collaboration.md`、`evidence/us6-plan-baseline.md` | 项目经理 |
| SC-004 | 非法完成/推进/关闭拦截率 100% | `evidence/us5-…`、`evidence/us7-…`、`evidence/us4-…` | 平台质量负责人 |
| SC-005 | P1 视图/搜索/基础统计数据状态权限一致率 100% | `evidence/us9-…`、`evidence/us14-…` | 平台质量负责人 |
| SC-006 | 已发布交付件无未授权覆盖，版本审批签核归档完整率 100% | `evidence/us7-deliverable.md` | 项目团队 |
| SC-007 | 工作空间到字段级越权访问拦截率 100% | `evidence/us11-security-lifecycle.md` | 安全负责人 |
| SC-008 | 关键审计事件覆盖率 100% | `evidence/us11-security-lifecycle.md` | 审计人员 |
| SC-009 | P2 管理者 30 秒内定位异常项目 | P2 backlog | — |
| SC-010 | P2 组合/仪表盘/报表与有权源数据一致率 100% | P2 backlog | — |
| SC-011 | P2 六类行业包可表达专有能力不改核心 | P2 backlog | — |
| SC-012 | P2 ≥80% 代表性需求可由受治理配置完成 | P2 backlog | — |
| SC-013 | 领域包发布前无效引用/不可达状态/循环/越界/冲突识别率 100% | `evidence/us2-domain-package.md` | 领域管理员 |
| SC-014 | 领域包生产版本独立发布审核 100%，超三层继承或无来源配置为 0 | `evidence/us2-domain-package.md` | 独立发布者 |
| SC-015 | 外部/扩展故障不破坏已完成核心事务，失败事项可见可追踪率 100% | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-016 | 核心业务可用性 ≥99.95%，其他在线 ≥99.9% | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-017 | 恢复演练 RTO ≤30 分钟、RPO ≤5 分钟，季度抽样成功率 100% | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-018 | 1000 并发下核心交互 P95 ≤2 秒，搜索/基础统计 P95 ≤3 秒或渐进，失败率 ≤0.1% | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-019 | ≥20 名试点成员仅靠引导 ≥90% 独立完成核心操作 | `evidence/`（P1 闭环） | 试点负责人 |
| SC-020 | 试点三个月后状态汇总时间降 ≥60%，重复录入不一致降 ≥80% | `evidence/`（P1 闭环） | 试点负责人 |
| SC-021 | AI/报表/搜索/非关键集成不可用时 100% 核心流程可人工完成 | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-022 | 已发布领域包运行阶段唯一顶层生命周期映射，跨项目统计一致率 100% | `evidence/us2-…`、`evidence/us4-…` | 领域管理员 |
| SC-023 | 所有项目唯一主工作空间，跨空间协作有范围期限审计，未授权跨空间拦截 100% | `evidence/us1-workspace-governance.md` | 平台管理员 |
| SC-024 | 统一进度可追溯至里程碑权重与必需产出，权重归一准确率 100%，无法解释手工进度为 0 | `evidence/us6-plan-baseline.md` | 项目经理 |
| SC-025 | 公共核心字段跨领域语义一致率 100%，重复或冲突扩展字段为 0 | `evidence/us2-domain-package.md` | 领域管理员 |
| SC-026 | P1 上线前 ≥1 个真实/等价项目完成端到端验收，强制业务环节通过率 100% | `evidence/`（阶段 17/18） | 平台架构负责人 |
| SC-027 | 上线范围 MySQL 表字段均有已批准映射或排除结论，未决映射为 0 | `evidence/us20-mysql-migration.md` | 数据负责人 |
| SC-028 | 必迁对象记录/标识/层级/审批链/附件哈希核对通过率 100%，阻断问题为 0 | `evidence/us20-mysql-migration.md` | 数据负责人 |
| SC-029 | 上线前 ≥2 次全量迁移和彩排切换；冻结前增量延迟连续 30 分钟 ≤30 秒，未应用变更为 0 | `evidence/us20-migration-rehearsal.md` | 上线团队 |
| SC-030 | 迁移后 100% 历史核心对象可追溯到原系统标识/批次/转换版本，业务抽样通过率 100% | `evidence/us20-mysql-migration.md` | 数据负责人 |
| SC-031 | 回退/前向修复演练在批准窗口完成，冻结期变更丢失为 0，重复业务结果为 0 | `evidence/us20-mysql-migration.md` | 上线团队 |
| SC-032 | P2 认证数据库测试矩阵各项通过率 100% | P2 backlog | — |
| SC-033 | P2 同分析器版本在所有认证数据库上核心 API/状态/权限/过滤/排序/分页/精度/时间/审计一致率 100% | P2 backlog | — |
| SC-034 | P2 上线前两种认证数据库各完成 1 次切换演练，核对通过率 100% | P2 backlog | — |
| SC-035 | 架构检查发现的领域/应用层数据库专有依赖为 0 | P2 backlog | — |
| SC-036 | 撤权测试中新请求立即生效、缓存 5 秒失效、搜索报表 30 秒移除、会话 1 分钟撤销、实时会话 30 秒刷新、附件导出复核达标率 100% | `evidence/us11-security-lifecycle.md` | 安全负责人 |
| SC-037 | 每月核心与其他在线能力生成可审计报告，服务目录/请求分类/交互时限/排除项证据完整率 100% | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-038 | P1 核心对象状态迁移自动化测试，允许通过率与非法拦截率 100%，拒绝含稳定原因和下一步建议 | `evidence/`（各故事状态机证据） | 平台质量负责人 |
| SC-039 | 高风险操作目录 100% 具有影响预览、确认、审计和撤销/回退/补偿验证证据 | `evidence/`（US2/US6/US7/US8/US10/US11/US20） | 平台架构负责人 |
| SC-040 | P3 无障碍可选范围获批准后目标旅程与验收指标通过率 100%；未批准不适用 | P3 backlog | — |
| SC-041 | P1 关键能力服务等级档案覆盖率 100%，每项关联 SLI/SLO、容量、失败模式、告警、责任人、运行手册和最近验证证据 | `evidence/us18-business-continuity.md` | 平台运维 |
| SC-042 | 实现前和发布前威胁模型已评审，未处理且未接受严重/高风险为 0，跨空间/敏感字段/凭据/附件/导出/审计保护验证通过率 100% | `evidence/`（安全评审） | 安全负责人 |
| SC-043 | P1 对外 HTTP、事件和领域包接口版本化契约覆盖率及兼容性校验通过率 100%，无先实现后补契约 | 本矩阵 + `contracts/` | 平台架构负责人 |
| SC-044 | 14 个 P1 用户故事均具备角色、触发、状态、规则、输出、异常补偿、责任人和闭环证据，完整率 100% | 本矩阵第 2 节 + 各 `evidence/us*-*.md` | 平台架构负责人 |
| SC-045 | 核心术语目录覆盖率 100%，每个术语可追溯行业标准或 PDP 扩展定义，未批准同义冲突为 0 | `terminology.md` | 平台架构负责人 |
| SC-046 | P1 BPMN 流程定义、版本、关联、幂等恢复和权限复核测试通过率 100%，仅存于流程引擎而无法从权威业务对象还原的核心结论为 0 | `evidence/us8-approval.md` | 平台架构负责人 |

## 5. 故事 → 契约 → 任务 追踪表

HTTP 契约路径来自 `contracts/openapi.yaml`（基础路径 `/api/v1`）与 `contracts/coverage.md`；
事件/Schema 来自 `contracts/events.md`、`contracts/domain-package.schema.json`、
`contracts/migration-report.schema.json`；契约测试与 MySQL 契约测试文件来自 `tasks.md`。

| 故事 | HTTP 契约路径 | 事件或 Schema | 主要消费者 | 契约测试 | MySQL 契约测试 | 实现任务范围 | evidence |
|---|---|---|---|---|---|---|---|
| US1 | `/workspaces`、`/workspaces/{workspaceId}/collaboration-grants`、`…/revoke` | `pdp.workspace.membership.changed`、`pdp.workspace.collaboration.changed` | Web、权限、审计、搜索 | `tests/contracts/us1-workspace-governance.spec.ts` | `tests/backend/contract/workspace/WorkspaceGovernanceDatabaseContractTest.java` | T097–T110 | `evidence/us1-workspace-governance.md` |
| US2 | `/domain-packages`、`…/versions/{versionId}/validate`、`…/publish` | `domain-package.schema.json`、`pdp.domain-package.published`、`pdp.domain-package.migration.requested` | 设计器、运行时、迁移器 | `tests/contracts/us2-domain-package.spec.ts` | `tests/backend/contract/domainconfig/DomainPackageDatabaseContractTest.java` | T111–T127 | `evidence/us2-domain-package.md` |
| US3 | `/project-templates`、`/projects` | `pdp.project.created` | Web、项目、计划、通知 | `tests/contracts/us3-project-template.spec.ts` | `tests/backend/contract/template/ProjectTemplateDatabaseContractTest.java` | T128–T140 | `evidence/us3-project-template.md` |
| US4 | `/projects/{projectId}`、`/projects/{projectId}/transitions` | `pdp.project.lifecycle.changed` | Web、计划、交付、审计 | `tests/contracts/us4-project-lifecycle.spec.ts` | `tests/backend/contract/project/ProjectLifecycleDatabaseContractTest.java` | T141–T153 | `evidence/us4-project-lifecycle.md` |
| US5 | `/projects/{projectId}/tasks`、`/tasks/{taskId}/transitions` | `pdp.task.state.changed`、`pdp.project.progress.recalculation.requested` | Web、进度、通知、搜索 | `tests/contracts/us5-task-collaboration.spec.ts` | `tests/backend/contract/planning/TaskCollaborationDatabaseContractTest.java` | T154–T167 | `evidence/us5-task-collaboration.md` |
| US6 | `/projects/{projectId}/baselines` | `pdp.milestone.state.changed`、基线批准/取代事件 | Web、项目、审批 | `tests/contracts/us6-plan-baseline.spec.ts` | `tests/backend/contract/planning/PlanBaselineDatabaseContractTest.java` | T168–T180 | `evidence/us6-plan-baseline.md` |
| US7 | `/projects/{projectId}/deliverables`、`/deliverables/{deliverableId}/versions` | `pdp.deliverable.version.published` | Web、审批、归档、通知 | `tests/contracts/us7-deliverable.spec.ts` | `tests/backend/contract/deliverable/DeliverableDatabaseContractTest.java` | T181–T194 | `evidence/us7-deliverable.md` |
| US8 | `/approvals`、`/approvals/{approvalId}/actions`、`/workflow-definitions/validate`、`/workflow-definitions/deploy`、`/workflow-instances/{workflowInstanceId}`、`/workflow-instances/{workflowInstanceId}/actions` | `pdp.approval.completed`、`pdp.workflow.orchestration.requested/failed`、BPMN 流程部署与关联契约 | Web、业务模块、`workflow`、通知 | `tests/contracts/us8-approval.spec.ts` | `tests/backend/contract/approval/ApprovalDatabaseContractTest.java` | T195–T209 | `evidence/us8-approval.md` |
| US9 | `/projects/{projectId}/saved-views` 及项目查询接口 | 无新增权威状态事件 | Web | `tests/contracts/us9-project-workspace.spec.ts` | `tests/backend/contract/experience/SavedViewDatabaseContractTest.java` | T210–T222 | `evidence/us9-project-workspace.md` |
| US10 | `/projects/{projectId}/risks`、`/projects/{projectId}/issues`、`/projects/{projectId}/change-requests` | 风险/问题/变更状态事件、`pdp.change.approved` | Web、审批、计划、通知 | `tests/contracts/us10-governance-control.spec.ts` | `tests/backend/contract/governance/GovernanceControlDatabaseContractTest.java` | T223–T239 | `evidence/us10-governance-control.md` |
| US11 | `/authorization-policies`、`/audit-events`、`/disposition-requests` | `pdp.audit.export.requested`、权限与处置事件 | 管理端、全部业务模块 | `tests/contracts/us11-authorization.spec.ts`、`tests/contracts/us11-audit-lifecycle.spec.ts` | `tests/backend/contract/security/AuthorizationAuditDatabaseContractTest.java` | T240–T257 | `evidence/us11-security-lifecycle.md` |
| US14 | `/search`、`/notifications`、`/notifications/{notificationId}/read` | 核心业务事件目录（消费侧） | Web、投影器、通知器 | `tests/contracts/us14-search-notification.spec.ts` | `tests/backend/contract/experience/SearchNotificationDatabaseContractTest.java` | T258–T270 | `evidence/us14-search-notification.md` |
| US18 | `/operations/service-levels`、`/operations/availability` | 告警和恢复事件 | 运维控制台、值守系统 | `tests/contracts/us18-business-continuity.spec.ts` | `tests/backend/contract/operations/OperationsDatabaseContractTest.java` | T271–T285 | `evidence/us18-business-continuity.md` |
| US20 | `/data-migrations`、`/data-migrations/{programId}`、`…/runs`、`…/reconciliation`、`…/cutover-decisions` | `migration-report.schema.json`、`pdp.migration.run.progressed`、`pdp.migration.issue.detected`、`pdp.migration.cutover.decided` | 迁移控制台、审计、值守 | `tests/contracts/us20-mysql-migration.spec.ts` | `tests/backend/contract/datamigration/MigrationControlDatabaseContractTest.java` | T286–T304 | `evidence/us20-mysql-migration.md`、`evidence/us20-migration-rehearsal.md` |
| 横向高风险操作 | `/operation-previews` 及对应确认命令 | 审计事件 | 所有高风险页面和服务 | 由各故事高风险操作测试覆盖 | 各故事 MySQL 契约测试 | 跨 T124/T176/T189/T203/T234/T253/T299 | `evidence/`（各故事） |
| 横向工作流编排 | `/workflow-definitions/validate`、`/workflow-definitions/deploy`、`/workflow-instances/{id}` 及受控管理动作 | `pdp.workflow.orchestration.requested/failed` | `workflow`、审批、项目、领域包、运维 | `tests/contracts/us8-approval.spec.ts`（含工作流） | `tests/backend/contract/approval/ApprovalDatabaseContractTest.java` | 平台工作流基础（阶段 2）+ US8 | `evidence/us8-approval.md` |

> 契约测试文件、MySQL 契约测试文件、evidence 文件当前均为“待实现”，由对应任务在实现阶段生成。

## 6. 契约一致性校验矩阵

校验四类契约在实现前基线的完整性与一致性。基线状态分“已冻结”（规格/契约已定，可进入实现）
和“待补齐”（实现前需完成）。

| 契约类型 | 文件 | 版本 | 覆盖故事 | 消费者 | 兼容策略 | 弃用窗口 | 校验测试 | 基线状态 |
|---|---|---|---|---|---|---|---|---|
| OpenAPI HTTP | `contracts/openapi.yaml` | `1.1.0`（`/api/v1`） | US1–US11、US14、US18、US20 及横向高风险操作/工作流 | Web 前端、设计器、运维控制台、迁移控制台、外部集成 | 向后兼容新增可选字段/枚举/端点；删除/改义/收紧/改状态语义为不兼容，须升主版本并记录消费者 | 不兼容变更须记录弃用开始、迁移窗口、替代契约和移除批准 | `tests/contracts/*.spec.ts`（每故事一份） | 已冻结 |
| 业务事件 | `contracts/events.md` | 信封 `eventVersion=1`，P1 目录 18 类事件 | US1、US2、US3、US4、US5、US6、US7、US8、US10、US11、US14、US18、US20 及工作流 | 权限缓存、搜索投影、通知器、审计投影、进度计算器、`workflow` 编排适配器、迁移执行器、值守系统 | 增加可选字段为兼容；删除/改义/改必填须提升 `eventVersion`；至少一次投递，消费者按 `eventId` 幂等，按 `aggregateRevision` 定序 | 旧 `eventVersion` 在消费者迁移完成后移除，须记录弃用窗口 | 事件样例与消费者兼容测试 | 已冻结 |
| 领域包 Schema | `contracts/domain-package.schema.json` | `$id` v1，`schemaVersion` 必填，JSON Schema Draft 2020-12 | US2（主）、横向领域扩展 | 设计器、运行时、迁移器、发布审核 | Schema 独立版本化；新增可选字段兼容；结构变更须升 `schemaVersion` 并通过发布校验 | 旧 `schemaVersion` 包保留可读，弃用须记录迁移路径 | `tests/contracts/us2-domain-package.spec.ts` + Schema 校验 | 已冻结 |
| 迁移报告 Schema | `contracts/migration-report.schema.json` | `$id` v1，`schemaVersion` 必填，JSON Schema Draft 2020-12 | US20（P1）、US21（P2 扩展） | 迁移控制台、审计、值守、上线门禁 | 独立版本化；P1 覆盖历史迁移与上线切换，P2 由多数据库切换扩展 | P2 切换字段扩展不破坏 P1 报告可读性 | `tests/contracts/us20-mysql-migration.spec.ts` + Schema 校验 | 已冻结（`migration-report.schema.json` 已完成） |

> 校验测试目录约定为 `tests/contracts/*.spec.ts`；当前为“待实现”，由各故事首个 `[P]` 契约测试
> 任务（如 T098、T112、T129…）在实现阶段生成并先于 Controller/消费者实现失败。

## 7. 消费者与版本登记

| 契约 | 消费者 | 当前版本 | 兼容影响类型 | 弃用窗口 |
|---|---|---|---|---|
| OpenAPI `/workspaces`、协作授权及撤销 | Web、权限缓存、审计投影、搜索投影 | `/api/v1` 1.1.0 | 新增（P1 首版） | 无（首版） |
| OpenAPI `/domain-packages` 校验/发布 | 设计器、运行时、迁移器 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/projects`、`/project-templates` | Web、项目、计划、通知 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/projects/{id}/transitions` | Web、计划、交付、审计 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI 任务/转换接口 | Web、进度、通知、搜索 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/projects/{id}/baselines` | Web、项目、审批 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI 交付件及版本接口 | Web、审批、归档、通知 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/approvals` 及动作 | Web、业务模块、`workflow`、通知 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI 保存视图与项目查询 | Web | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI 风险/问题/变更接口 | Web、审批、计划、通知 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI 策略/审计/导出/处置 | 管理端、全部业务模块 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/search`、`/notifications` | Web、投影器、通知器 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/operations/*` | 运维控制台、值守系统 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/data-migrations/*` | 迁移控制台、审计、值守 | `/api/v1` 1.1.0 | 新增 | 无（首版） |
| OpenAPI `/operation-previews` | 所有高风险页面和服务 | `/api/v1` 1.1.0 | 新增（P1 公共高风险操作框架） | 无（首版） |
| OpenAPI `/workflow-definitions/*`、`/workflow-instances/*` | `workflow`、审批、项目、领域包、运维 | `/api/v1` 1.1.0 | 新增（不暴露 Flowable REST） | 无（首版）；P1 不暴露 Flowable 类型/表/异常文本 |
| 事件 `pdp.workspace.*` | 权限缓存、通知、审计投影、搜索投影 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.domain-package.*` | 安装目录、文档、测试报告、迁移执行器 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.project.*`、`pdp.task.*`、`pdp.milestone.*` | 活动、搜索、通知、进度计算器、看板投影 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.deliverable.version.published` | 归档清单、通知、外部订阅 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.workflow.orchestration.*` | `workflow` 编排适配器、审批、运维、人工补偿 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.approval.completed`、`pdp.change.approved` | 业务对象条件回写、计划/范围更新协调器 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.audit.export.requested`、`pdp.integration.delivery.failed` | 后台导出执行器、集成健康度、值守 | `eventVersion=1` | 新增 | 无（首版） |
| 事件 `pdp.migration.*` | 迁移控制台、告警、数据责任人、上线门禁、流量控制 | `eventVersion=1` | 新增 | 无（首版） |
| 领域包 Schema | 设计器、运行时、迁移器、发布审核 | `schemaVersion` v1 | 新增 | 无（首版）；旧版本包保留可读 |
| 迁移报告 Schema | 迁移控制台、审计、值守、上线门禁 | `schemaVersion` v1 | 新增（P1 历史迁移） | 无（首版）；P2 多数据库切换字段扩展，不破坏 P1 可读性 |

> 兼容影响类型当前统一为“新增（P1 首版）”。后续任何“变更/弃用/迁移”影响必须在本表登记，并按
> `contracts/README.md` 兼容性规则记录消费者、弃用开始时间、迁移窗口、替代契约和移除批准。

## 8. 断链与待补齐项

实现前基线已知断链：以下代码、测试、运行手册和验收证据在实现阶段产出，当前统一标注为
“待实现”，由发布阶段 T329 关闭。

| 类别 | 待补齐项 | 产出任务 | 关闭责任 |
|---|---|---|---|
| 应用代码 | 各故事领域模型、仓储端口、Mapper/适配器、应用服务、Controller、Web 页面 | 各故事 T101–T304 中实现任务 | 各故事负责人 |
| 公共变更集 | `modules/public-persistence/.../db/changelog/common/010–200-*.xml` | T100、T114、T131、T144、T157、T171、T184、T198、T213、T226/T227、T244/T245、T261、T274、T289 | 持久化负责人 |
| 契约测试 | `tests/contracts/us*-*.spec.ts`（14 故事 + 工作流） | T098、T112、T129、T142、T155、T169、T182、T196、T211、T224、T241/T242、T259、T272、T287 | 各故事负责人 |
| MySQL 契约测试 | `tests/backend/contract/**/*.java` | T104、T120、T134、T148、T161、T174、T188、T202、T216、T232、T250、T264、T277、T294 | 持久化负责人 |
| 端到端测试 | `tests/e2e/us*-*.spec.ts` | T099、T113、T130、T143、T156、T170、T183、T197、T212、T225、T243、T260、T273、T288 | 各故事负责人 |
| 安全/恢复/韧性测试 | `tests/backend/security/`、`tests/backend/resilience/`、`tests/backend/integration/` | T109、T193、T269、T282–T284、T301、T303 等 | 安全/运维负责人 |
| 运行手册 | P1 关键能力服务等级档案与运行手册（SC-041） | T285 及阶段 18 | 平台运维 |
| 验收证据 | `evidence/us*-*.md`（14 故事）及 P1 闭环证据（SC-026） | T110、T127、T140、T153、T167、T180、T194、T209、T222、T239、T257、T270、T285、T304、T302 | 各故事负责人 |
| 迁移彩排 | 至少两轮全量和一轮增量切换彩排（SC-029） | T302 | 上线团队 |
| 状态机测试 | P1 核心对象状态机自动化测试（SC-038） | 各故事状态机任务 | 平台质量负责人 |
| 威胁模型 | 实现前和发布前威胁模型评审（SC-042） | 阶段 18 | 安全负责人 |

> 上述断链不阻塞实现进入（契约与规格已冻结满足 DoR），但必须在发布阶段 T329 全部关闭方可发布。

## 9. 维护规则

1. 每次规格、契约或任务变更 MUST 先更新本矩阵及对应 OpenAPI、事件或 Schema，再实现 Controller、
   消费者或界面（契约先行门禁，见 `plan.md`）。
2. CI MUST 比较实现路由、事件样例、Schema 和本矩阵覆盖率，覆盖率必须为 100%；实际路由或事件
   与本矩阵/契约文件不一致时发布门禁失败。
3. 不兼容变更 MUST 在第 7 节登记消费者、兼容影响类型（新增/变更/弃用/迁移）、弃用开始时间、
   迁移窗口、替代契约和移除批准；无版本、无通知的破坏性变化禁止进入实现。
4. 契约任务在每个故事内 MUST 位于领域模型、服务、Controller、消费者和界面任务之前；契约测试
   必须能因契约差异而失败，并先于提供方或消费者实现。
5. 发布阶段 T329 MUST 关闭第 8 节全部断链，并将各 evidence 路径由“待实现”更新为实际证据。
6. 本矩阵与 `contracts/coverage.md` 互为参照：`coverage.md` 是契约覆盖清单，本矩阵追加 FR/SC/
   任务/evidence 追踪与一致性校验；两者冲突时以契约文件为准并同步修正。

## 10. 追溯

- 宪章原则：`constitution.md` **V. 契约优先、兼容演进与数据库独立**（外部 HTTP、事件、领域包和
  持久化端口先定义版本化契约及可执行契约测试，兼容性策略明确新增/变更/弃用/迁移窗口和消费者
  影响）；**VII. 可执行质量与证据化验收**（模块边界、契约、持久化、权限、并发、集成、端到端、
  性能、迁移和恢复均需可执行证据）。
- 计划门禁：`plan.md` 契约先行门禁——任何外部接口先修改 OpenAPI、事件或领域包 Schema 并使
  契约测试失败，再实现 Controller 或消费者；契约目录维护 P1 故事、接口、消费者、版本、兼容和
  测试的完整覆盖矩阵。
- 任务节点：`tasks.md` **T012**（阶段 0，建立本基础追踪矩阵并校验四类契约一致，登记消费者、
  版本、兼容影响及弃用窗口）；**T329**（阶段 18，更新本矩阵补齐代码、测试、运行手册和验收证据
  并关闭全部断链）。
- 规格来源：FR/SC 编号取自 `spec.md`；故事定义见 `spec.md` 用户故事 1–20（P1 为 US1–US11、US14、
  US18、US20）；契约覆盖见 `contracts/coverage.md`；事件目录见 `contracts/events.md`；Schema 见
  `contracts/domain-package.schema.json` 与 `contracts/migration-report.schema.json`。
