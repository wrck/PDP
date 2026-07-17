/**
 * 统一 API 错误模型与处理工具（T092、FR-070、FR-174）。
 *
 * 与后端 RFC 7807 Problem Details 对齐，所有 HTTP 错误统一转换为 {@link ApiError}，
 * 业务页面通过 {@code instanceOf ApiError} 判断错误类型并据此展示反馈。
 *
 * <p><strong>错误分类</strong>：
 * <ul>
 *   <li>4xx 客户端错误：业务校验失败、权限不足、状态冲突、跨工作空间访问 404；</li>
 *   <li>5xx 服务端错误：基础设施故障，由全局拦截器统一提示；</li>
 *   <li>0 网络错误：超时、断网、CORS 等，建议用户重试。</li>
 * </ul>
 *
 * <p><strong>稳定错误码</strong>：与后端 {@code WorkflowEngineException.Reason}、
 * {@code ErrorCode} 对齐，业务页面可据此决定是否重试或展示特定 UI。
 */

/** RFC 7807 Problem Details（与后端 GlobalExceptionHandler 对齐）。 */
export interface ProblemDetails {
  /** 问题类型 URI（如 "https://pdp.example.com/problems/definition-invalid"）。 */
  type?: string
  /** 问题标题（人类可读，如 "流程定义无效"）。 */
  title?: string
  /** HTTP 状态码。 */
  status?: number
  /** 详细描述（含具体校验失败原因）。 */
  detail?: string
  /** 问题实例 URI（如发生问题的具体资源路径）。 */
  instance?: string
  /** PDP 扩展：稳定错误码（如 DEFINITION_INVALID、ILLEGAL_STATE_TRANSITION）。 */
  errorCode?: string
  /** PDP 扩展：追踪 ID，用于关联日志指标。 */
  traceId?: string
  /** PDP 扩展：关联 ID，用于跨服务追踪。 */
  correlationId?: string
  /** PDP 扩展：字段级校验错误列表。 */
  errors?: Array<{ field: string; message: string }>
  /** PDP 扩展：可重试标识（5xx 中部分错误可重试）。 */
  retryable?: boolean
  /** PDP 扩展：建议重试等待秒数。 */
  retryAfterSeconds?: number
}

/** 统一 API 错误。所有 HTTP 错误均转换为此类型，业务页面据此处理。 */
export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP 状态码；0 表示网络层错误。 */
    public readonly status: number,
    /** 稳定错误码（与后端 ErrorCode 对齐）。 */
    public readonly errorCode?: string,
    /** 链路追踪 ID。 */
    public readonly traceId?: string,
    /** 关联 ID（跨服务追踪）。 */
    public readonly correlationId?: string,
    /** 原始 Problem Details。 */
    public readonly problem?: ProblemDetails,
    /** 原始错误对象（axios error）。 */
    public readonly cause?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
    // 维护原型链，使 instanceOf ApiError 在 ES5 target 下仍可用
    Object.setPrototypeOf(this, ApiError.prototype)
  }

  /** 是否为客户端错误（4xx）。 */
  isClientError(): boolean {
    return this.status >= 400 && this.status < 500
  }

  /** 是否为服务端错误（5xx）。 */
  isServerError(): boolean {
    return this.status >= 500
  }

  /** 是否为网络层错误（status=0）。 */
  isNetworkError(): boolean {
    return this.status === 0
  }

  /** 是否为未认证或会话过期（401）。 */
  isUnauthorized(): boolean {
    return this.status === 401
  }

  /** 是否为权限不足（403）。 */
  isForbidden(): boolean {
    return this.status === 403
  }

  /** 是否为资源不存在或跨工作空间访问（404）。 */
  isNotFound(): boolean {
    return this.status === 404
  }

  /** 是否为状态冲突或版本不匹配（409）。 */
  isConflict(): boolean {
    return this.status === 409
  }

  /** 是否为校验失败（400/422）。 */
  isValidationFailed(): boolean {
    return this.status === 400 || this.status === 422
  }

  /** 是否为请求过于频繁（429）。 */
  isRateLimited(): boolean {
    return this.status === 429
  }

  /** 是否可重试（5xx 或网络错误或 429）。 */
  isRetryable(): boolean {
    if (this.problem?.retryable !== undefined) return this.problem.retryable
    return this.isServerError() || this.isNetworkError() || this.isRateLimited()
  }

  /** 获取字段级校验错误映射（仅校验失败时有值）。 */
  fieldErrors(): Record<string, string> {
    if (!this.problem?.errors) return {}
    const result: Record<string, string> = {}
    for (const err of this.problem.errors) {
      result[err.field] = err.message
    }
    return result
  }
}

/** 常见稳定错误码（与后端对齐，业务页面可据此决定 UI 反馈）。 */
export const ERROR_CODES = {
  // 通用
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  RATE_LIMITED: 'RATE_LIMITED',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  // 工作流
  DEFINITION_INVALID: 'DEFINITION_INVALID',
  DEFINITION_NOT_FOUND: 'DEFINITION_NOT_FOUND',
  INSTANCE_NOT_FOUND: 'INSTANCE_NOT_FOUND',
  ILLEGAL_STATE_TRANSITION: 'ILLEGAL_STATE_TRANSITION',
  TASK_NOT_FOUND: 'TASK_NOT_FOUND',
  TASK_NOT_ASSIGNABLE: 'TASK_NOT_ASSIGNABLE',
  MIGRATION_PLAN_INVALID: 'MIGRATION_PLAN_INVALID',
  ENGINE_UNAVAILABLE: 'ENGINE_UNAVAILABLE',
  ORCHESTRATION_FAILED: 'ORCHESTRATION_FAILED',
  DEADLOCK_DETECTED: 'DEADLOCK_DETECTED',
  PERMISSION_REVOKED: 'PERMISSION_REVOKED',
} as const

/** 判断错误是否为指定错误码。 */
export function hasErrorCode(err: unknown, code: string): boolean {
  return err instanceof ApiError && err.errorCode === code
}

/** 判断错误是否为可重试错误。 */
export function isRetryableError(err: unknown): boolean {
  return err instanceof ApiError && err.isRetryable()
}

/** 判断错误是否为跨工作空间访问被拒绝（后端统一返回 404）。 */
export function isCrossWorkspaceAccess(err: unknown): boolean {
  return err instanceof ApiError && err.isNotFound()
}

/** 获取错误的可读描述（用于 message.error 等）。 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return '未知错误'
}
