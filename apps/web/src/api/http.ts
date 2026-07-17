/**
 * 全局 HTTP 客户端（T092、FR-070、FR-174）。
 *
 * 替代 `views/admin/workflow/http.ts` 中的自包含 axios 实例，作为前端唯一的网络出口。
 * <p><strong>核心职责</strong>：
 * <ul>
 *   <li>请求拦截：附加工作空间头（X-Workspace-Id）、Authorization 头、
 *       链路追踪头（X-Trace-Id、X-Correlation-Id），高风险写操作自动生成 Idempotency-Key；</li>
 *   <li>响应拦截：将后端 RFC 7807 Problem Details 转换为统一 {@link ApiError}，
 *       透传 X-Correlation-Id 到当前请求追踪上下文；</li>
 *   <li>跨工作空间访问由后端返回 404，前端按"不存在"处理，不泄露存在性。</li>
 * </ul>
 *
 * <p><strong>与 T090 临时 http.ts 的关系</strong>：
 * 工作流管理页面在 T090 自包含实现了 axios 实例；T092 完成后该页面应迁移至引用本模块。
 * 迁移工作保留至 T108 工作空间选择页面完成后再统一进行，避免破坏当前可用功能。
 */
import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import { ApiError, type ProblemDetails } from './errors'
import {
  type RequestTrace,
  generateTraceId,
  generateCorrelationId,
  generateIdempotencyKey,
  getCurrentTrace,
  setCurrentTrace,
  type SpanContext,
} from './tracing'

/** 公共请求头常量（与后端 RequestContextFilter 对齐）。 */
export const HEADERS = {
  WORKSPACE: 'X-Workspace-Id',
  IDEMPOTENCY: 'Idempotency-Key',
  TRACE: 'X-Trace-Id',
  CORRELATION: 'X-Correlation-Id',
  AUTHORIZATION: 'Authorization',
  CONTENT_TYPE: 'Content-Type',
  ACCEPT: 'Accept',
  IF_MATCH: 'If-Match',
} as const

/** 默认超时 30 秒。 */
const DEFAULT_TIMEOUT_MS = 30_000

/** 后端 API 基础路径前缀。 */
const API_BASE_URL = '/api/v1'

/** 高风险写操作路径正则：匹配 deploy、actions、transitions、approvals 等子路径。 */
const HIGH_RISK_PATH_PATTERNS: readonly RegExp[] = [
  /\/workflow-definitions\/deploy$/,
  /\/workflow-instances\/[^/]+\/actions$/,
  /\/workflow-definitions\/[^/]+\/transitions$/,
  /\/approvals\/[^/]+\/actions$/,
  /\/projects\/[^/]+\/rollback$/,
  /\/domain-packages\/[^/]+\/publish$/,
  /\/baselines\/[^/]+\/replace$/,
  /\/data-disposals$/,
]

/** 判断请求路径是否为高风险写操作。 */
export function isHighRiskPath(method: string | undefined, url: string | undefined): boolean {
  if (!url) return false
  if (method && method.toUpperCase() !== 'POST' && method.toUpperCase() !== 'PUT' && method.toUpperCase() !== 'DELETE') {
    return false
  }
  return HIGH_RISK_PATH_PATTERNS.some((pattern) => pattern.test(url))
}

/** 当前工作空间 ID（由 T108 工作空间选择页面注入；这里从 localStorage 读取占位）。 */
function currentWorkspaceId(): string | null {
  return localStorage.getItem('pdp.workspaceId')
}

/** 当前认证 token（由 T063 OIDC 登录注入；这里从 localStorage 读取占位）。 */
function currentToken(): string | null {
  return localStorage.getItem('pdp.token')
}

/** 创建 axios 实例并注册拦截器。 */
function createHttpClient(): AxiosInstance {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: DEFAULT_TIMEOUT_MS,
    headers: {
      [HEADERS.CONTENT_TYPE]: 'application/json',
      [HEADERS.ACCEPT]: 'application/json',
    },
  })

  // 请求拦截：附加工作空间头、Authorization 头、追踪头、幂等键
  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    // 链路追踪：每个请求创建或继承 traceId/correlationId
    const parentTrace = getCurrentTrace()
    const traceId = parentTrace?.traceId ?? generateTraceId()
    const correlationId = parentTrace?.correlationId ?? generateCorrelationId()
    const spanId = generateCorrelationId()

    config.headers[HEADERS.TRACE] = traceId
    config.headers[HEADERS.CORRELATION] = correlationId

    // 工作空间边界
    const workspaceId = currentWorkspaceId()
    if (workspaceId) {
      config.headers[HEADERS.WORKSPACE] = workspaceId
    }

    // 认证 token
    const token = currentToken()
    if (token) {
      config.headers[HEADERS.AUTHORIZATION] = `Bearer ${token}`
    }

    // 高风险写操作 MUST 携带 Idempotency-Key（未显式提供时自动生成）
    if (isHighRiskPath(config.method, config.url) && !config.headers[HEADERS.IDEMPOTENCY]) {
      config.headers[HEADERS.IDEMPOTENCY] = generateIdempotencyKey()
    }

    // 将 span 上下文存入请求元数据，便于响应拦截器关联日志
    const span: SpanContext = {
      traceId,
      correlationId,
      spanId,
      startedAt: Date.now(),
      method: (config.method ?? 'GET').toUpperCase(),
      url: config.url ?? '',
    }
    ;(config as InternalAxiosRequestConfig & { _span?: SpanContext })._span = span

    // 为当前请求设置 trace 上下文（嵌套请求可继承）
    setCurrentTrace({ traceId, correlationId })

    return config
  })

  // 响应拦截：成功时清除当前请求 trace；失败时转换为 ApiError
  client.interceptors.response.use(
    (response: AxiosResponse) => {
      const span = (response.config as InternalAxiosRequestConfig & { _span?: SpanContext })._span
      if (span) {
        const durationMs = Date.now() - span.startedAt
        // 响应头中可能携带后端返回的 correlationId，透传到 trace
        const responseCorrelation = response.headers[HEADERS.CORRELATION.toLowerCase()]
        if (responseCorrelation && typeof responseCorrelation === 'string') {
          span.correlationId = responseCorrelation
        }
        // eslint-disable-next-line no-console
        console.debug(
          `[http] ${span.method} ${span.url} → ${response.status} (${durationMs}ms) ` +
            `trace=${span.traceId} corr=${span.correlationId}`,
        )
      }
      // 清除当前请求 trace，避免影响后续独立请求
      setCurrentTrace(null)
      return response
    },
    (error: AxiosError<ProblemDetails>) => {
      setCurrentTrace(null)
      const span = (error.config as InternalAxiosRequestConfig & { _span?: SpanContext } | undefined)?._span
      if (error.response) {
        const { status, data, headers } = error.response
        const problem = data
        const msg =
          problem?.detail ||
          problem?.title ||
          (status === 404
            ? '资源不存在或跨工作空间访问被拒绝'
            : status === 409
              ? '状态冲突或版本不匹配'
              : status === 403
                ? '无权限执行此操作'
                : status === 401
                  ? '未认证或会话已过期'
                  : status === 429
                    ? '请求过于频繁，请稍后重试'
                    : `请求失败（HTTP ${status}）`)
        const traceId =
          (headers?.[HEADERS.TRACE.toLowerCase()] as string | undefined) ?? span?.traceId
        const correlationId =
          (headers?.[HEADERS.CORRELATION.toLowerCase()] as string | undefined) ??
          span?.correlationId
        const apiError = new ApiError(msg, status, problem?.errorCode, traceId, correlationId, problem, error)
        // 5xx 服务端错误统一提示，业务错误由调用方自行处理
        if (status >= 500) {
          message.error(`服务器错误：${msg}`)
        }
        // eslint-disable-next-line no-console
        console.error(
          `[http] ${span?.method ?? '?'} ${span?.url ?? '?'} → ${status} ` +
            `trace=${traceId ?? '-'} corr=${correlationId ?? '-'} code=${problem?.errorCode ?? '-'}`,
          apiError,
        )
        return Promise.reject(apiError)
      }
      if (error.request) {
        const apiError = new ApiError(
          '网络异常，请检查连接后重试',
          0,
          undefined,
          span?.traceId,
          span?.correlationId,
          undefined,
          error,
        )
        // eslint-disable-next-line no-console
        console.error(`[http] network error trace=${span?.traceId ?? '-'}`, apiError)
        return Promise.reject(apiError)
      }
      return Promise.reject(
        new ApiError(
          error.message || '未知错误',
          0,
          undefined,
          span?.traceId,
          span?.correlationId,
          undefined,
          error,
        ),
      )
    },
  )

  return client
}

/** 全局共享 axios 实例（前端唯一网络出口）。 */
export const httpClient: AxiosInstance = createHttpClient()

/** 通用 GET 请求。 */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response: AxiosResponse<T> = await httpClient.get(url, config)
  return response.data
}

/** 通用 POST 请求。 */
export async function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await httpClient.post(url, data, config)
  return response.data
}

/** 通用 PUT 请求（带乐观锁 If-Match）。 */
export async function put<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await httpClient.put(url, data, config)
  return response.data
}

/** 通用 DELETE 请求。 */
export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response: AxiosResponse<T> = await httpClient.delete(url, config)
  return response.data
}

/** 获取完整响应（含 headers，用于读取 Location 等头）。 */
export async function postRaw<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(url, data, config)
}

/** 构造乐观锁 If-Match 头。 */
export function ifMatchHeader(revision: number): { [HEADERS.IF_MATCH]: string } {
  return { [HEADERS.IF_MATCH]: `"${revision}"` }
}

/** 构造幂等键头。 */
export function idempotencyHeader(key: string): { [HEADERS.IDEMPOTENCY]: string } {
  return { [HEADERS.IDEMPOTENCY]: key }
}

/** 重新导出 tracing 工具以便业务页面使用。 */
export type { RequestTrace }
