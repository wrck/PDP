import { describe, expect, it, vi } from 'vitest'

import type { ApiClient } from '../../api/client'
import { createWorkspaceGovernanceApi } from './workspaceApi'

describe('workspaceGovernanceApi', () => {
  it('对治理请求显式传递工作空间上下文和并发版本', async () => {
    const request = vi.fn().mockResolvedValue(undefined)
    const api = createWorkspaceGovernanceApi({ request } as ApiClient)

    await api.revokeGrant('workspace-a', 'grant-1', 7, '协作已结束')

    expect(request).toHaveBeenCalledWith(
      '/workspaces/workspace-a/collaboration-grants/grant-1/revoke',
      {
        method: 'POST',
        workspaceId: 'workspace-a',
        revision: 7,
        body: { reason: '协作已结束' },
      },
    )
  })
})
