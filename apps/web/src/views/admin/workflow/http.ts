/**
 * 平台工作流管理页面的最小 HTTP 客户端（T090）。
 *
 * T092 将创建通用 `apps/web/src/api/` 客户端、错误处理与请求追踪；
 * 此处仅为 T090 工作流管理页面提供自包含的 axios 实例与拦截器，
 * 后续 T092 完成后可重构为引用全局客户端。
 *
 * <p><strong>拦截器职责</strong>：
 * <ul>
 *   <li>请求拦截：附加工作空间头（X-Workspace-Id）、Authorization 头；</li>
 *   <li>响应拦截：将后端 RFC 7807 Problem Details 转换为统一 ApiError；</li>
 *   <li>跨工作空间访问由后端返回 404，前端按"不存在"处理，不泄露存在性。</li>
 * </ul>
 */
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type AxiosError,
} from 'axios'
import { message } from 'ant-design-vue'

/** RFC 7807 Problem Details（与后端 GlobalExceptionHandler 对齐）。 */
export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** PDP 扩展：稳定错误码（如 DEFINITION_INVALID、ILLEGAL_STATE_TRANSITION）。 */
  errorCode?: string
  /** PDP 扩展：追踪 ID，用于关联日志指标。 */
  traceId?: string
  /** PDP 扩展：字段级校验错误。 */
  errors?: Array<{ field: string; message: string }>
}

/** 统一 API 错误。 */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly errorCode?: string,
    public readonly traceId?: string,
    public readonly problem?: ProblemDetails,
    public readonly cause?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 当前工作空间 ID（由 T108 工作空间选择页面注入；这里从 localStorage 读取占位）。 */
function currentWorkspaceId(): string | null {
  return localStorage.getItem('pdp.workspaceId')
}

/** 当前认证 token（由 T063 OIDC 登录注入；这里从 localStorage 读取占位）。 */
function currentToken(): string | null {
  return localStorage.getItem('pdp.token')
}

/** 生成幂等键（写操作未显式提供时由前端兜底生成）。 */
export function generateIdempotencyKey(): string {
  // RFC 4122 v4 UUID，足够做幂等键；后端 IdempotencyKey.of 接受任意非空字符串
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  // 设置 version (4) 和 variant 位
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
}

/** 创建 axios 实例并注册拦截器。 */
function createHttpClient(): AxiosInstance {
  const client = axios.create({
    baseURL: '/api/v1',
    timeout: 30_000,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
  })

  // 请求拦截：附加工作空间头与 Authorization 头
  client.interceptors.request.use((config) => {
    const workspaceId = currentWorkspaceId()
    if (workspaceId) {
      config.headers['X-Workspace-Id'] = workspaceId
    }
    const token = currentToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  // 响应拦截：将 RFC 7807 Problem Details 转换为 ApiError
  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError<ProblemDetails>) => {
      if (error.response) {
        const { status, data } = error.response
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
                  : `请求失败（HTTP ${status}）`)
        const apiError = new ApiError(
          msg,
          status,
          problem?.errorCode,
          problem?.traceId,
          problem,
          error,
        )
        // 网络层错误提示（页面层应通过 catch 自行处理具体反馈）
        if (status >= 500) {
          message.error(`服务器错误：${msg}`)
        }
        return Promise.reject(apiError)
      }
      if (error.request) {
        const apiError = new ApiError(
          '网络异常，请检查连接后重试',
          0,
          undefined,
          undefined,
          undefined,
          error,
        )
        return Promise.reject(apiError)
      }
      return Promise.reject(
        new ApiError(error.message || '未知错误', 0, undefined, undefined, undefined, error),
      )
    },
  )

  return client
}

/** 共享 axios 实例。 */
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

/** 获取完整响应（含 headers，用于读取 Location 等头）。 */
export async function postRaw<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<AxiosResponse<T>> {
  return httpClient.post<T>(url, data, config)
}
