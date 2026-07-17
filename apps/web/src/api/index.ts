/**
 * 前端 API 客户端 barrel 导出（T092、FR-070）。
 *
 * 业务页面通过此 barrel 导入 HTTP 客户端、错误模型与追踪工具：
 * @example
 * ```ts
 * import { get, post, ApiError, hasErrorCode, ERROR_CODES } from '@/api'
 *
 * try {
 *   const data = await get<MyData>('/my-resource')
 * } catch (err) {
 *   if (err instanceof ApiError && err.isNotFound()) {
 *     // 处理跨工作空间访问
 *   }
 * }
 * ```
 */
export { httpClient, get, post, put, del, postRaw, ifMatchHeader, idempotencyHeader, isHighRiskPath, HEADERS } from './http'
export type { RequestTrace } from './http'
export {
  ApiError,
  ERROR_CODES,
  hasErrorCode,
  isRetryableError,
  isCrossWorkspaceAccess,
  describeError,
  type ProblemDetails,
} from './errors'
export {
  getCurrentTrace,
  setCurrentTrace,
  generateTraceId,
  generateCorrelationId,
  generateIdempotencyKey,
  createBatchTrace,
  withTrace,
  type SpanContext,
} from './tracing'
