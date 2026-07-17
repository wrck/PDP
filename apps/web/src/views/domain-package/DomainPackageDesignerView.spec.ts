import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'

import { usePlatformStore } from '../../stores'
import DomainPackageDesignerView from './DomainPackageDesignerView.vue'
import type { DomainPackageDesignerApi } from './types'

function createApi(): DomainPackageDesignerApi {
  return {
    list: vi.fn().mockResolvedValue({
      items: [
        {
          id: 'package-1',
          stableKey: 'network.cutover',
          name: '网络设备割接',
          layer: 'INDUSTRY',
          status: 'DRAFT',
          revision: 0,
        },
      ],
      nextCursor: null,
    }),
    createPackage: vi.fn(),
    createVersion: vi.fn().mockResolvedValue({
      id: 'version-1',
      packageId: 'package-1',
      semanticVersion: '1.0.0',
      contentHash: 'hash',
      status: 'DRAFT',
      frozen: false,
      revision: 0,
    }),
    validate: vi.fn().mockResolvedValue({
      valid: true,
      errors: [],
      warnings: [],
      compatibility: 'COMPATIBLE',
    }),
    submitReview: vi.fn().mockResolvedValue({
      id: 'version-1',
      packageId: 'package-1',
      semanticVersion: '1.0.0',
      contentHash: 'hash',
      status: 'REVIEW_PENDING',
      frozen: false,
      revision: 2,
    }),
    review: vi.fn(),
    publish: vi.fn(),
    previewMigration: vi.fn(),
    startMigration: vi.fn(),
  }
}

describe('DomainPackageDesignerView', () => {
  it('支持结构化建模并按创建者身份提交审核', async () => {
    const api = createApi()
    const pinia = createPinia()
    usePlatformStore(pinia).selectWorkspace('workspace-a')
    const wrapper = mount(DomainPackageDesignerView, {
      props: { designerApi: api },
      global: {
        plugins: [pinia],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('网络设备割接')
    const addObject = wrapper
      .findAll('button')
      .find((button) => button.text() === '添加对象')
    await addObject!.trigger('click')
    expect(wrapper.findAll('.object-editor')).toHaveLength(1)

    const releaseTab = wrapper
      .findAll('.designer-tabs button')
      .find((button) => button.text() === '校验与发布')
    await releaseTab!.trigger('click')
    const button = (label: string) =>
      wrapper.findAll('.release-flow button').find((item) => item.text() === label)!
    await button('保存版本草稿').trigger('click')
    await flushPromises()
    await button('执行发布校验').trigger('click')
    await flushPromises()
    await button('创建者提交审核').trigger('click')
    await flushPromises()

    expect(api.createVersion).toHaveBeenCalled()
    expect(api.validate).toHaveBeenCalledWith(
      'workspace-a',
      'package-1',
      'version-1',
      0,
    )
    expect(api.submitReview).toHaveBeenCalledWith(
      'workspace-a',
      'package-1',
      'version-1',
      1,
    )
  })
})
