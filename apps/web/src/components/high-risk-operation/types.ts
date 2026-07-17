/**
 * 高风险操作前端类型定义（FR-168、SC-039）。
 *
 * 对应后端 com.pdp.shared.operation 包的 TypeScript 镜像。
 * 类型稳定键与后端 stableKey() 保持一致，禁止前端硬编码变更。
 */

/** 高风险操作类型稳定键（与后端 HighRiskOperationType.stableKey() 对齐）。 */
export type HighRiskOperationTypeStableKey =
  | 'DOMAIN_PACKAGE.PUBLISH'
  | 'PROJECT.ROLLBACK'
  | 'BASELINE.REPLACE'
  | 'DELIVERABLE.RELEASE'
  | 'DATA.DISPOSAL'
  | 'HISTORY.MIGRATION'
  | 'DATABASE.SWITCH'

/** 操作状态（与后端 OperationState 枚举对齐）。 */
export type OperationState =
  | 'DRAFT'
  | 'PREVIEWED'
  | 'CONFIRMED'
  | 'EXECUTING'
  | 'COMPLETED'
  | 'COMPENSATED'
  | 'FAILED'
  | 'CANCELLED'

/** 影响严重度（与后端 ImpactSeverity 枚举对齐）。 */
export type ImpactSeverity = 'INFO' | 'WARNING' | 'IRREVERSIBLE'

/** 补偿策略（与后端 CompensationStrategy 枚举对齐）。 */
export type CompensationStrategy = 'ROLLBACK' | 'REVERSE_SYNC' | 'MANUAL' | 'NONE'

/** 禁用原因稳定键（P1 仅 DATABASE_SWITCH.P1_DISABLED）。 */
export type DisabledReasonStableKey = 'DATABASE_SWITCH.P1_DISABLED' | string

/** 影响条目（与后端 ImpactItem record 对齐）。 */
export interface ImpactItem {
  category: string
  description: string
  affectedObjectCount: number
  severity: ImpactSeverity
  irreversible: boolean
  detailJson?: string | null
}

/** 不可逆点（与后端 PointOfNoReturn record 对齐）。 */
export interface PointOfNoReturn {
  stage: string
  description: string
  prerequisiteForReversal?: string | null
  compensationStrategy: CompensationStrategy
}

/** 影响预览（与后端 ImpactPreview record 对齐）。 */
export interface ImpactPreview {
  previewId: string
  operationType: HighRiskOperationTypeStableKey
  scope: string
  version: number
  summary: string
  items: ImpactItem[]
  pointOfNoReturn?: PointOfNoReturn | null
  sourceRevision: number
  generatedAt: string
  expiresAt: string
}

/** 补偿计划（与后端 CompensationPlan record 对齐）。 */
export interface CompensationPlan {
  strategy: CompensationStrategy
  steps: string[]
  estimatedDurationSeconds?: number | null
  runbookReference?: string | null
  responsibleRole: string
}

/** 禁用原因（与后端 DisabledReason record 对齐）。 */
export interface DisabledReason {
  stableKey: DisabledReasonStableKey
  summary: string
  targetPhase: string
}

/** 预览结果（与后端 PreviewResult record 对齐）。 */
export interface PreviewResult {
  preview?: ImpactPreview | null
  compensationPlan?: CompensationPlan | null
  disabledReason?: DisabledReason | null
}

/** 操作确认记录（与后端 OperationConfirmation record 对齐）。 */
export interface OperationConfirmation {
  confirmationId: string
  previewId: string
  previewVersion: number
  confirmedBy: ActorRef
  confirmedAt: string
  expectedOutcome?: string | null
  acknowledgedIrreversible: boolean
}

/** 参与者引用（与后端 ActorRef record 对齐）。 */
export interface ActorRef {
  actorType: 'USER' | 'SYSTEM' | 'ORGANIZATION' | 'ROLE' | 'EXTERNAL'
  actorId: string
  displaySnapshot?: string | null
}

/** 执行结果（与后端 ExecutionResult record 对齐）。 */
export interface ExecutionResult {
  operationId: string
  finalState: OperationState
  completedAt: string
  actualOutcome?: string | null
  failureReason?: string | null
  compensationApplied?: string | null
  disabledReason?: DisabledReason | null
}

/**
 * 判断操作类型在 P1 是否启用。
 * DATABASE_SWITCH 在 P1 禁用，仅预注册类型与禁用原因契约。
 */
export function isExecutableInP1(type: HighRiskOperationTypeStableKey): boolean {
  return type !== 'DATABASE.SWITCH'
}

/**
 * 判断预览是否包含不可逆变更。
 */
export function hasIrreversibleImpact(preview: ImpactPreview): boolean {
  return (
    preview.pointOfNoReturn != null ||
    preview.items.some((item) => item.irreversible)
  )
}

/**
 * 判断预览是否已过期。
 */
export function isPreviewExpired(preview: ImpactPreview, now: Date = new Date()): boolean {
  return new Date(preview.expiresAt).getTime() <= now.getTime()
}
