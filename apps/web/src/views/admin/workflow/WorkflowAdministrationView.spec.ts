import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import WorkflowAdministrationView from './WorkflowAdministrationView.vue'
import type { WorkflowAdministrationApi } from './types'

function createApi(): WorkflowAdministrationApi {
  return {
    validate: vi.fn().mockResolvedValue({
      valid: true,
      contentHash: 'hash-1',
      findings: [],
    }),
    deploy: vi.fn().mockResolvedValue({
      id: 'definition-1',
      processDefinitionKey: 'approval.flow',
      businessVersion: '1.0.0',
      contentHash: 'hash-1',
      status: 'DEPLOYED',
      deployedAt: '2026-07-17T03:00:00Z',
    }),
    getInstance: vi.fn().mockResolvedValue({
      id: 'instance-1',
      definitionId: 'definition-1',
      businessObjectRef: { objectType: 'approval', objectId: 'approval-1' },
      state: 'INCIDENT',
      currentActivityKeys: ['externalCall'],
      incidentCount: 1,
      revision: 3,
      incidents: [
        {
          id: 'incident-1',
          code: 'ENGINE_TIMEOUT',
          message: '引擎调用超时',
          status: 'DEAD_LETTER',
          attempts: 3,
          occurredAt: '2026-07-17T03:00:00Z',
        },
      ],
    }),
    applyAction: vi.fn().mockResolvedValue({ jobId: 'job-1', status: 'QUEUED' }),
  }
}

describe('WorkflowAdministrationView', () => {
  it('校验通过后允许部署同一内容哈希', async () => {
    const api = createApi()
    const wrapper = mount(WorkflowAdministrationView, {
      props: { administrationApi: api },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    await wrapper.find('form.workflow-card').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('BPMN 定义校验通过')

    const deployButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('部署已校验版本'))
    await deployButton!.trigger('click')
    await flushPromises()

    expect(api.deploy).toHaveBeenCalledWith(
      expect.objectContaining({
        processDefinitionKey: 'approval.flow',
        contentHash: 'hash-1',
      }),
      expect.stringContaining('workflow-deploy-'),
    )
    expect(wrapper.text()).toContain('流程版本 1.0.0 已部署')
  })

  it('展示dead-letter诊断并使用实例版本提交重试', async () => {
    const api = createApi()
    const wrapper = mount(WorkflowAdministrationView, {
      props: { administrationApi: api },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    const instanceTab = wrapper
      .findAll('button')
      .find((button) => button.text() === '实例与故障')
    await instanceTab!.trigger('click')

    await wrapper.get('.workflow-search input').setValue('instance-1')
    await wrapper.get('.workflow-search').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('DEAD_LETTER')
    expect(wrapper.text()).toContain('当前对象版本：3')

    const actionForm = wrapper.findAll('form.workflow-card')[0]!
    await actionForm.get('select').setValue('RETRY')
    await actionForm.get('textarea').setValue('恢复下游服务后重试')
    await actionForm.trigger('submit')
    await flushPromises()

    expect(api.applyAction).toHaveBeenCalledWith(
      'instance-1',
      expect.objectContaining({
        action: 'RETRY',
        expectedRevision: 3,
      }),
      expect.stringContaining('workflow-action-'),
    )
    expect(wrapper.text()).toContain('管理动作已进入队列')
    expect(wrapper.text()).toContain('作业 job-1，状态 QUEUED')
  })
})
