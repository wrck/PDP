import { describe, expect, it, vi } from 'vitest'

import type { ApiClient } from '../../../api/client'
import { createWorkflowAdministrationApi } from './workflowApi'

describe('workflowAdministrationApi', () => {
  it('部署和管理动作必须携带幂等键', async () => {
    const request = vi.fn().mockResolvedValue({})
    const api = createWorkflowAdministrationApi({ request } as ApiClient)

    await api.deploy({ processDefinitionKey: 'approval.flow' }, 'deploy-1')
    await api.applyAction(
      'instance-1',
      { action: 'RETRY', expectedRevision: 3 },
      'retry-1',
    )

    expect(request).toHaveBeenNthCalledWith(1, '/workflow-definitions/deploy', {
      method: 'POST',
      idempotencyKey: 'deploy-1',
      body: { processDefinitionKey: 'approval.flow' },
    })
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/workflow-instances/instance-1/actions',
      {
        method: 'POST',
        idempotencyKey: 'retry-1',
        body: { action: 'RETRY', expectedRevision: 3 },
      },
    )
  })
})
