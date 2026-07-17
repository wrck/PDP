import { describe, expect, it, vi } from 'vitest'

import type { ApiClient } from '../../api/client'
import { createDomainPackageDesignerApi } from './domainPackageApi'

describe('domainPackageDesignerApi', () => {
  it('按职责分离调用提交审核、独立审核与发布端点', async () => {
    const request = vi.fn().mockResolvedValue({})
    const api = createDomainPackageDesignerApi({ request } as ApiClient)

    await api.submitReview('workspace-a', 'package-1', 'version-1', 2)
    await api.review(
      'workspace-a',
      'package-1',
      'version-1',
      3,
      '审核通过',
    )
    await api.publish(
      'workspace-a',
      'package-1',
      'version-1',
      4,
      '批准发布',
    )

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/domain-packages/package-1/versions/version-1/submit-review',
      { method: 'POST', workspaceId: 'workspace-a', revision: 2 },
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/domain-packages/package-1/versions/version-1/review',
      {
        method: 'POST',
        workspaceId: 'workspace-a',
        revision: 3,
        body: { decision: 'APPROVE', comment: '审核通过' },
      },
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/domain-packages/package-1/versions/version-1/publish',
      {
        method: 'POST',
        workspaceId: 'workspace-a',
        revision: 4,
        body: { reviewComment: '批准发布' },
      },
    )
  })
})
