/**
 * 前端请求追踪上下文（T092、FR-070）。
 *
 * 与后端 {@code RequestContextFilter} 的 {@code X-Trace-Id}、
 * {@code X-Correlation-Id} 头对齐，确保一次用户操作产生的多次 API 请求
 * 携带同一 correlationId，便于后端日志关联。
 *
 * <p><strong>追踪模型</strong>：
 * <ul>
 *   <li>{@link RequestTrace}：当前请求的追踪上下文，存于模块级变量，
 *       支持嵌套请求（如批量加载）继承父请求的 traceId/correlationId；</li>
 *   <li>{@link SpanContext}：单个 HTTP 请求的 span 上下文，存于 axios config 元数据，
 *       响应拦截器据此输出调试日志；</li>
 *   <li>幂等键：高风险写操作 MUST 携带，由 {@link generateIdempotencyKey} 生成。</li>
 * </ul>
 */

/** 当前请求的追踪上下文（嵌套请求可继承父请求 ID）。 */
export interface RequestTrace {
  /** 链路追踪 ID（一次用户操作的所有请求共享）。 */
  traceId: string
  /** 关联 ID（跨服务追踪，由发起方生成或后端透传）。 */
  correlationId: string
}

/** 单个 HTTP 请求的 span 上下文（存于 axios config 元数据）。 */
export interface SpanContext {
  traceId: string
  correlationId: string
  spanId: string
  startedAt: number
  method: string
  url: string
}

/** 模块级当前请求追踪上下文（同步访问，无并发问题，因 JS 单线程）。 */
let currentTrace: RequestTrace | null = null

/** 获取当前请求追踪上下文。 */
export function getCurrentTrace(): RequestTrace | null {
  return currentTrace
}

/** 设置当前请求追踪上下文（请求开始时设置，结束时清除）。 */
export function setCurrentTrace(trace: RequestTrace | null): void {
  currentTrace = trace
}

/**
 * 生成新的追踪 ID（UUID v4）。
 * 与后端 {@code UUID.randomUUID()} 对齐，便于日志关联。
 */
export function generateTraceId(): string {
  return uuidV4()
}

/**
 * 生成新的关联 ID（UUID v4）。
 * 若用户操作有明确的业务关联（如审批 ID），可传入业务 ID 作为种子。
 */
export function generateCorrelationId(seed?: string): string {
  if (seed) {
    // 业务种子场景下生成确定性 ID 的前缀 + 随机后缀，保留可追溯性
    return `${seed.slice(0, 8)}-${uuidV4().slice(9)}`
  }
  return uuidV4()
}

/**
 * 生成幂等键（UUID v4）。
 * 高风险写操作 MUST 携带 Idempotency-Key 头。
 */
export function generateIdempotencyKey(): string {
  return uuidV4()
}

/**
 * 生成 UUID v4（RFC 4122）。
 * 优先使用浏览器原生 crypto.randomUUID，回退到 polyfill。
 */
function uuidV4(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // Polyfill：基于 crypto.getRandomValues 的 v4 UUID
  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    // 极端情况下回退到 Math.random（不推荐生产使用）
    for (let i = 0; i < 16; i++) {
      bytes[i] = Math.floor(Math.random() * 256)
    }
  }
  // 设置 version (4) 和 variant (10xx) 位
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
}

/**
 * 为批量操作创建追踪上下文（同一用户操作产生多次请求时调用）。
 *
 * @param seed 业务种子（如审批 ID、迁移 ID），用于关联 ID 可追溯
 * @returns 追踪上下文，调用方应通过 {@link setCurrentTrace} 设置为当前上下文
 */
export function createBatchTrace(seed?: string): RequestTrace {
  const traceId = generateTraceId()
  const correlationId = generateCorrelationId(seed)
  return { traceId, correlationId }
}

/**
 * 在指定追踪上下文中执行回调（自动设置/清除 currentTrace）。
 *
 * @param trace 追踪上下文
 * @param fn 回调函数（其中发出的 HTTP 请求将继承此 trace）
 */
export async function withTrace<T>(trace: RequestTrace, fn: () => Promise<T>): Promise<T> {
  const previous = currentTrace
  setCurrentTrace(trace)
  try {
    return await fn()
  } finally {
    setCurrentTrace(previous)
  }
}
