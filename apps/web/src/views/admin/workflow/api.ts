/**
 * 平台工作流 API 客户端（T090、FR-174、ADR-0005）。
 *
 * 对接后端 `WorkflowController` 暴露的 10 个端点（`/api/v1/workflow-definitions/*`
 * 与 `/api/v1/workflow-instances/*`）。所有响应均为 PDP 自有稳定契约，
 * 不携带任何 Flowable 实体/任务对象/异常类型（ADR-0005 § 4）。
 *
 * <p><strong>幂等键约定</strong>：
 * <ul>
 *   <li>部署定义（deploy）与执行管理动作（applyAction）为高风险写操作，MUST 携带 Idempotency-Key；</li>
 *   <li>调用方未显式提供时由 {@link generateIdempotencyKey} 自动生成。</li>
 * </ul>
 */
import { get, post, postRaw, generateIdempotencyKey } from './http'
import type {
  ApplyActionRequest,
  MigrationPlan,
  MigrationPreviewRequest,
  PageResult,
  TransitionDefinitionRequest,
  ValidateDefinitionRequest,
  DeployDefinitionRequest,
  ValidationResult,
  WorkflowDefinitionStatus,
  WorkflowDefinitionSummary,
  WorkflowIncident,
  WorkflowInstanceSummary,
  MigrationRecord,
} from './types'

/** 查询参数类型。 */
type QueryParams = Record<string, string | number | boolean | undefined | null>

/** 将对象转为查询参数（过滤 null/undefined）。 */
function toQuery(params: QueryParams): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      result[key] = String(value)
    }
  }
  return result
}

// ============================================================
// 流程定义端点
// ============================================================

/**
 * 校验 BPMN 流程定义。
 * 对应 `POST /workflow-definitions/validate`。
 */
export async function validateDefinition(
  request: ValidateDefinitionRequest,
): Promise<ValidationResult> {
  return post<ValidationResult>('/workflow-definitions/validate', request)
}

/**
 * 部署已批准的 BPMN 流程定义。
 * 对应 `POST /workflow-definitions/deploy`，MUST 携带 Idempotency-Key。
 */
export async function deployDefinition(
  request: DeployDefinitionRequest,
  idempotencyKey?: string,
): Promise<WorkflowDefinitionSummary> {
  const key = idempotencyKey ?? generateIdempotencyKey()
  return post<WorkflowDefinitionSummary>('/workflow-definitions/deploy', request, {
    headers: { 'Idempotency-Key': key },
  })
}

/**
 * 查询流程定义详情。
 * 对应 `GET /workflow-definitions/{definitionId}`。
 */
export async function getDefinition(
  definitionId: string,
): Promise<WorkflowDefinitionSummary> {
  return get<WorkflowDefinitionSummary>(`/workflow-definitions/${definitionId}`)
}

/**
 * 分页查询流程定义。
 * 对应 `GET /workflow-definitions`。
 */
export async function listDefinitions(params: {
  keyPrefix?: string
  status?: WorkflowDefinitionStatus
  cursor?: string | null
  size?: number
}): Promise<PageResult<WorkflowDefinitionSummary>> {
  return get<PageResult<WorkflowDefinitionSummary>>('/workflow-definitions', {
    params: toQuery({
      keyPrefix: params.keyPrefix,
      status: params.status,
      cursor: params.cursor,
      size: params.size ?? 20,
    }),
  })
}

/**
 * 迁移流程定义状态（DEPLOYED → DEPRECATED → RETIRED）。
 * 对应 `POST /workflow-definitions/{definitionId}/transitions`。
 */
export async function transitionDefinition(
  definitionId: string,
  request: TransitionDefinitionRequest,
): Promise<WorkflowDefinitionSummary> {
  return post<WorkflowDefinitionSummary>(
    `/workflow-definitions/${definitionId}/transitions`,
    request,
  )
}

// ============================================================
// 流程实例端点
// ============================================================

/**
 * 查询流程实例诊断摘要。
 * 对应 `GET /workflow-instances/{instanceId}`。
 */
export async function getInstance(
  instanceId: string,
): Promise<WorkflowInstanceSummary> {
  return get<WorkflowInstanceSummary>(`/workflow-instances/${instanceId}`)
}

/**
 * 分页查询工作空间内有 incident 的实例（运维监控）。
 * 对应 `GET /workflow-instances`。
 */
export async function listInstancesWithIncidents(params: {
  cursor?: string | null
  size?: number
}): Promise<PageResult<WorkflowInstanceSummary>> {
  return get<PageResult<WorkflowInstanceSummary>>('/workflow-instances', {
    params: toQuery({
      cursor: params.cursor,
      size: params.size ?? 20,
    }),
  })
}

/**
 * 查询实例 incident 列表（运行诊断）。
 * 对应 `GET /workflow-instances/{instanceId}/incidents`。
 */
export async function listIncidents(
  instanceId: string,
  includeResolved = false,
): Promise<WorkflowIncident[]> {
  return get<WorkflowIncident[]>(`/workflow-instances/${instanceId}/incidents`, {
    params: toQuery({ includeResolved }),
  })
}

/**
 * 查询实例迁移历史（审计回查）。
 * 对应 `GET /workflow-instances/{instanceId}/migration-history`。
 */
export async function listMigrationHistory(
  instanceId: string,
): Promise<MigrationRecord[]> {
  return get<MigrationRecord[]>(`/workflow-instances/${instanceId}/migration-history`)
}

/**
 * 预览流程实例迁移影响。
 * 对应 `POST /workflow-instances/{instanceId}/migration-previews`。
 */
export async function previewMigration(
  instanceId: string,
  request: MigrationPreviewRequest,
): Promise<MigrationPlan> {
  return post<MigrationPlan>(
    `/workflow-instances/${instanceId}/migration-previews`,
    request,
  )
}

/**
 * 执行受控流程管理动作。
 * 对应 `POST /workflow-instances/{instanceId}/actions`。
 * 高风险动作（MIGRATE/TERMINATE/MANUAL_COMPENSATE）MUST 携带 Idempotency-Key。
 *
 * @returns 受理后的实例摘要（后端返回 202 + Location 头）
 */
export async function applyAction(
  instanceId: string,
  request: ApplyActionRequest,
  idempotencyKey?: string,
): Promise<WorkflowInstanceSummary> {
  const key = idempotencyKey ?? generateIdempotencyKey()
  const response = await postRaw<WorkflowInstanceSummary>(
    `/workflow-instances/${instanceId}/actions`,
    request,
    { headers: { 'Idempotency-Key': key } },
  )
  return response.data
}

/** 工作流 API 客户端 barrel。 */
export const workflowApi = {
  validateDefinition,
  deployDefinition,
  getDefinition,
  listDefinitions,
  transitionDefinition,
  getInstance,
  listInstancesWithIncidents,
  listIncidents,
  listMigrationHistory,
  previewMigration,
  applyAction,
}
