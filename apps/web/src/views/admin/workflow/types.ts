/**
 * 平台工作流前端类型定义（T090、FR-174、ADR-0005）。
 *
 * 与后端 `com.pdp.workflow.model` 与 `WorkflowController` 内嵌 DTO 对齐，
 * 不携带任何 Flowable 实体/任务对象/异常类型（ADR-0005 § 4）。
 *
 * 字段命名与 OpenAPI schemas 保持一致，禁止前端硬编码枚举值。
 */

/** 流程定义状态（与 WorkflowDefinitionStatus 对齐）。 */
export type WorkflowDefinitionStatus =
  | 'VALIDATED'
  | 'DEPLOYED'
  | 'DEPRECATED'
  | 'RETIRED'

/** 流程实例状态（与 WorkflowInstanceState 对齐）。 */
export type WorkflowInstanceState =
  | 'STARTING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'COMPLETED'
  | 'TERMINATED'
  | 'INCIDENT'

/** 受控管理动作类型（与 WorkflowAdminAction.Action 对齐）。 */
export type WorkflowAdminActionType =
  | 'PAUSE'
  | 'RESUME'
  | 'RETRY'
  | 'MIGRATE'
  | 'TERMINATE'
  | 'MANUAL_COMPENSATE'

/** Incident 状态（与 WorkflowIncidentStatus 对齐）。 */
export type WorkflowIncidentStatus =
  | 'OPEN'
  | 'RETRYING'
  | 'MANUAL_ACTION'
  | 'DEAD_LETTER'
  | 'RESOLVED'

/** 补偿策略（与 CompensationStrategy 对齐）。 */
export type CompensationStrategy =
  | 'ROLLBACK'
  | 'REVERSE_SYNC'
  | 'MANUAL'
  | 'NONE'

/** 校验发现项严重度。 */
export type ValidationFindingSeverity = 'ERROR' | 'WARNING'

/** BPMN 校验发现项。 */
export interface ValidationFinding {
  severity: ValidationFindingSeverity
  code: string
  message: string
}

/** BPMN 校验结果。 */
export interface ValidationResult {
  valid: boolean
  contentHash: string
  findings: ValidationFinding[]
}

/** 流程定义摘要（对应 WorkflowDefinitionSummary schema）。 */
export interface WorkflowDefinitionSummary {
  id: string
  processDefinitionKey: string
  businessVersion: string
  contentHash: string
  domainPackageVersionId?: string | null
  status: WorkflowDefinitionStatus
  deployedAt?: string | null
  findings?: ValidationFinding[]
}

/** 业务对象引用（自由结构，按业务对象类型不同）。 */
export type BusinessObjectRef = Record<string, unknown>

/** 流程实例摘要（对应 WorkflowInstanceSummary schema）。 */
export interface WorkflowInstanceSummary {
  id: string
  definitionId: string
  businessObjectRef: BusinessObjectRef
  state: WorkflowInstanceState
  currentActivityKeys?: string[]
  incidentCount: number
  revision: number
}

/** 平台 incident 读模型（对应 WorkflowIncident）。 */
export interface WorkflowIncident {
  incidentId: string
  instanceId: string
  activityKey?: string | null
  incidentType: string
  errorMessage?: string | null
  occurredAt: string
  resolvedAt?: string | null
  retryCount: number
}

/** 迁移历史记录（对应 WorkflowAdministrationServiceMigrationRecord）。 */
export interface MigrationRecord {
  migrationId: string
  instanceId: string
  sourceDefinitionId: string
  targetDefinitionId: string
  triggeredBy: string
  migratedAt: string
  batchSize: number
  successful: boolean
  failureReason?: string | null
}

/** 活动节点映射。 */
export interface ActivityMapping {
  sourceActivityKey: string
  targetActivityKey: string
}

/** 补偿计划。 */
export interface CompensationPlan {
  strategy: CompensationStrategy
  steps: string[]
  estimatedDurationSeconds?: number | null
  runbookReference?: string | null
  responsibleRole: string
}

/** 迁移计划（对应 MigrationPlan）。 */
export interface MigrationPlan {
  sourceDefinitionId: string
  targetDefinitionId: string
  activityMappings: ActivityMapping[]
  pointOfNoReturn?: string | null
  compensationPlan: CompensationPlan
  batchSize: number
}

/** 操作确认记录（对应 OperationConfirmation）。 */
export interface OperationConfirmation {
  confirmationId: string
  previewId: string
  previewVersion: number
  confirmedBy: ActorRef
  confirmedAt: string
  expectedOutcome?: string | null
  acknowledgedIrreversible: boolean
}

/** 参与者引用。 */
export interface ActorRef {
  actorType: 'USER' | 'SYSTEM' | 'ORGANIZATION' | 'ROLE' | 'EXTERNAL'
  actorId: string
  displaySnapshot?: string | null
}

/** 游标分页结果（对应 PageResult）。 */
export interface PageResult<T> {
  data: T[]
  nextCursor?: string | null
  hasMore: boolean
  total?: number | null
}

/** 校验定义请求。 */
export interface ValidateDefinitionRequest {
  processDefinitionKey: string
  businessVersion: string
  domainPackageVersionId?: string | null
  bpmnXml: string
}

/** 部署定义请求。 */
export interface DeployDefinitionRequest {
  processDefinitionKey: string
  businessVersion: string
  contentHash: string
  bpmnResource: string
  domainPackageVersionId?: string | null
}

/** 迁移定义状态请求。 */
export interface TransitionDefinitionRequest {
  targetStatus: WorkflowDefinitionStatus
  expectedRevision: number
  reason: string
}

/** 迁移预览请求。 */
export interface MigrationPreviewRequest {
  targetDefinitionId: string
}

/** 操作确认 DTO。 */
export interface ConfirmationDto {
  confirmationId?: string | null
  previewId: string
  previewVersion: number
  expectedOutcome?: string | null
  acknowledgedIrreversible: boolean
}

/** 执行管理动作请求（对应 WorkflowAdminActionCommand）。 */
export interface ApplyActionRequest {
  action: WorkflowAdminActionType
  reason: string
  expectedRevision: number
  migrationPlan?: MigrationPlan | null
  confirmation?: ConfirmationDto | null
  impactPreviewId?: string | null
}

/** 后台作业受理响应（对应 JobAccepted）。 */
export interface JobAccepted {
  jobId: string
  status: 'QUEUED'
}

/** 判断管理动作是否为高风险（与后端 WorkflowAdminAction.isHighRisk() 对齐）。 */
export function isHighRiskAction(action: WorkflowAdminActionType): boolean {
  return action === 'MIGRATE' || action === 'TERMINATE' || action === 'MANUAL_COMPENSATE'
}

/** 判断实例状态是否为终态。 */
export function isTerminalInstanceState(state: WorkflowInstanceState): boolean {
  return state === 'COMPLETED' || state === 'TERMINATED'
}

/** 流程定义状态中文名。 */
export function definitionStatusLabel(status: WorkflowDefinitionStatus): string {
  switch (status) {
    case 'VALIDATED':
      return '已校验'
    case 'DEPLOYED':
      return '已部署'
    case 'DEPRECATED':
      return '已弃用'
    case 'RETIRED':
      return '已退役'
    default:
      return status
  }
}

/** 流程定义状态对应的 Tag 颜色。 */
export function definitionStatusColor(status: WorkflowDefinitionStatus): string {
  switch (status) {
    case 'VALIDATED':
      return 'blue'
    case 'DEPLOYED':
      return 'green'
    case 'DEPRECATED':
      return 'orange'
    case 'RETIRED':
      return 'red'
    default:
      return 'default'
  }
}

/** 流程实例状态中文名。 */
export function instanceStatusLabel(state: WorkflowInstanceState): string {
  switch (state) {
    case 'STARTING':
      return '启动中'
    case 'ACTIVE':
      return '运行中'
    case 'SUSPENDED':
      return '已暂停'
    case 'COMPLETED':
      return '已完成'
    case 'TERMINATED':
      return '已终止'
    case 'INCIDENT':
      return '异常中'
    default:
      return state
  }
}

/** 流程实例状态对应的 Tag 颜色。 */
export function instanceStatusColor(state: WorkflowInstanceState): string {
  switch (state) {
    case 'STARTING':
      return 'blue'
    case 'ACTIVE':
      return 'green'
    case 'SUSPENDED':
      return 'orange'
    case 'COMPLETED':
      return 'default'
    case 'TERMINATED':
      return 'red'
    case 'INCIDENT':
      return 'red'
    default:
      return 'default'
  }
}

/** 管理动作中文名。 */
export function actionLabel(action: WorkflowAdminActionType): string {
  switch (action) {
    case 'PAUSE':
      return '暂停'
    case 'RESUME':
      return '恢复'
    case 'RETRY':
      return '重试'
    case 'MIGRATE':
      return '迁移'
    case 'TERMINATE':
      return '终止'
    case 'MANUAL_COMPENSATE':
      return '人工补偿'
    default:
      return action
  }
}

/** Incident 状态中文名。 */
export function incidentStatusLabel(status: WorkflowIncidentStatus | string): string {
  switch (status) {
    case 'OPEN':
      return '未处理'
    case 'RETRYING':
      return '重试中'
    case 'MANUAL_ACTION':
      return '待人工处理'
    case 'DEAD_LETTER':
      return '死信'
    case 'RESOLVED':
      return '已解决'
    default:
      return status
  }
}

/** Incident 状态对应的 Tag 颜色。 */
export function incidentStatusColor(status: WorkflowIncidentStatus | string): string {
  switch (status) {
    case 'OPEN':
      return 'red'
    case 'RETRYING':
      return 'orange'
    case 'MANUAL_ACTION':
      return 'volcano'
    case 'DEAD_LETTER':
      return 'magenta'
    case 'RESOLVED':
      return 'green'
    default:
      return 'default'
  }
}

/** 补偿策略中文名。 */
export function compensationStrategyLabel(strategy: CompensationStrategy): string {
  switch (strategy) {
    case 'ROLLBACK':
      return '回滚'
    case 'REVERSE_SYNC':
      return '反向同步'
    case 'MANUAL':
      return '人工补偿'
    case 'NONE':
      return '无补偿（不可逆）'
    default:
      return strategy
  }
}
