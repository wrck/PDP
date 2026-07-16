import { describe, expect, it, vi } from 'vitest'

import { ApiProblemError, createApiClient } from './client'

describe('API 客户端', () => {
  it('统一附加工作空间、鉴权、链路和并发控制请求头', async () => {
    const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
      void _input
      void _init
      return new Response(JSON.stringify({ id: 'project-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    const client = createApiClient({
      baseUrl: '/api',
      accessToken: () => 'token',
      workspaceId: () => 'workspace-1',
      traceIdFactory: () => 'trace-1',
      fetcher,
    })

    await client.request('/projects/project-1', {
      method: 'PATCH',
      body: { name: '交付平台' },
      idempotencyKey: 'idempotency-key-1',
      revision: 7,
    })

    const [url, init] = fetcher.mock.calls[0]!
    const headers = new Headers(init?.headers)
    expect(url).toBe('/api/projects/project-1')
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('X-Workspace-Id')).toBe('workspace-1')
    expect(headers.get('X-Trace-Id')).toBe('trace-1')
    expect(headers.get('Idempotency-Key')).toBe('idempotency-key-1')
    expect(headers.get('If-Match')).toBe('"7"')
  })

  it('将 application/problem+json 转换为稳定异常', async () => {
    const client = createApiClient({
      fetcher: async () =>
        new Response(
          JSON.stringify({
            type: 'urn:pdp:problem:revision-conflict',
            title: '资源已被更新',
            status: 409,
            code: 'REVISION_CONFLICT',
            traceId: 'trace-server',
          }),
          {
            status: 409,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      traceIdFactory: () => 'trace-client',
    })

    await expect(client.request('/projects/project-1')).rejects.toMatchObject({
      name: 'ApiProblemError',
      status: 409,
      code: 'REVISION_CONFLICT',
      traceId: 'trace-server',
    } satisfies Partial<ApiProblemError>)
  })
})
