export interface ProblemDetails {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code?: string
  traceId: string
  violations?: Array<{
    field: string
    code: string
    message?: string
  }>
}

export class ApiProblemError extends Error {
  readonly status: number
  readonly code?: string
  readonly traceId: string
  readonly problem: ProblemDetails

  constructor(problem: ProblemDetails) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiProblemError'
    this.status = problem.status
    this.code = problem.code
    this.traceId = problem.traceId
    this.problem = problem
  }
}

export interface ApiClientOptions {
  baseUrl?: string
  accessToken?: () => string | undefined
  workspaceId?: () => string | undefined
  traceIdFactory?: () => string
  fetcher?: typeof fetch
}

export interface ApiRequestInit
  extends Omit<RequestInit, 'body' | 'headers'> {
  body?: unknown
  headers?: HeadersInit
  idempotencyKey?: string
  revision?: number | string
  workspaceId?: string
}

export interface ApiClient {
  request<T>(path: string, init?: ApiRequestInit): Promise<T>
}

export const API_BASE_PATH = '/api/v1'

function createTraceId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `trace-${Date.now()}`
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const fetcher = options.fetcher ?? globalThis.fetch
  const baseUrl = options.baseUrl ?? ''

  return {
    async request<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
      const traceId = (options.traceIdFactory ?? createTraceId)()
      const headers = new Headers(init.headers)
      headers.set('Accept', 'application/json, application/problem+json')
      headers.set('X-Trace-Id', traceId)

      const token = options.accessToken?.()
      if (token) headers.set('Authorization', `Bearer ${token}`)

      const workspaceId = init.workspaceId ?? options.workspaceId?.()
      if (workspaceId) headers.set('X-Workspace-Id', workspaceId)

      if (init.idempotencyKey) {
        headers.set('Idempotency-Key', init.idempotencyKey)
      }
      if (init.revision !== undefined) {
        const value = String(init.revision)
        headers.set('If-Match', value.startsWith('"') ? value : `"${value}"`)
      }

      const hasBody = init.body !== undefined
      if (hasBody && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
      }

      const response = await fetcher(`${baseUrl}${path}`, {
        ...init,
        headers,
        body: hasBody ? JSON.stringify(init.body) : undefined,
      })

      if (!response.ok) {
        let problem: ProblemDetails
        try {
          problem = (await response.json()) as ProblemDetails
        } catch {
          problem = {
            type: 'urn:pdp:problem:unexpected-response',
            title: response.statusText || '请求失败',
            status: response.status,
            traceId,
          }
        }
        throw new ApiProblemError({ ...problem, traceId: problem.traceId || traceId })
      }

      if (response.status === 204) return undefined as T
      return (await response.json()) as T
    },
  }
}

export const apiClient = createApiClient({ baseUrl: API_BASE_PATH })
