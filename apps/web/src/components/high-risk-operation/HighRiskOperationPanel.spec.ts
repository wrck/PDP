import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import HighRiskOperationPanel from './HighRiskOperationPanel.vue'

const preview = {
  previewId: 'preview-1',
  operationType: 'DATABASE_SWITCH',
  targetSummary: '将写入主权从 mysql-a 切换到 mysql-b',
  affectedCounts: { workspaces: 12, activeJobs: 3 },
  warnings: ['切换期间写入将短暂停止'],
  irreversibleAt: '目标库开放写入后',
  compensation: '关闭目标写入并执行受控回退',
  confirmationPhrase: '确认切换 mysql-b',
  confirmationToken: 'signed-token',
  expiresAt: '2099-01-01T00:00:00Z',
  availability: {
    enabled: true,
    reasonCode: 'CERTIFIED',
    reason: 'MYSQL 8.4→MYSQL 8.4 组合已认证',
  },
}

describe('高风险操作面板', () => {
  it('确认短语和不可逆点未确认前禁止执行', async () => {
    const wrapper = mount(HighRiskOperationPanel, { props: { preview } })
    const button = wrapper.get('[data-test="confirm-operation"]')

    expect(button.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-test="confirmation-input"]').setValue(
      preview.confirmationPhrase,
    )
    await wrapper.get('[data-test="irreversible-ack"]').setValue(true)
    await button.trigger('click')

    expect(wrapper.emitted('confirm')?.[0]).toEqual([
      {
        previewId: 'preview-1',
        confirmationToken: 'signed-token',
      },
    ])
  })

  it('未认证组合展示稳定禁用原因', () => {
    const wrapper = mount(HighRiskOperationPanel, {
      props: {
        preview: {
          ...preview,
          availability: {
            enabled: false,
            reasonCode: 'DATABASE_COMBINATION_NOT_CERTIFIED',
            reason: '数据库产品与版本组合未认证',
          },
        },
      },
    })

    expect(wrapper.text()).toContain('DATABASE_COMBINATION_NOT_CERTIFIED')
    expect(wrapper.get('[data-test="confirm-operation"]').attributes('disabled')).toBeDefined()
  })

  it('预览身份或令牌变化后清空确认输入并撤销不可逆点确认', async () => {
    const wrapper = mount(HighRiskOperationPanel, { props: { preview } })
    const confirmationInput = wrapper.get('[data-test="confirmation-input"]')
    const irreversibleAck = wrapper.get('[data-test="irreversible-ack"]')
    const confirmButton = wrapper.get('[data-test="confirm-operation"]')

    await confirmationInput.setValue(preview.confirmationPhrase)
    await irreversibleAck.setValue(true)
    expect(confirmButton.attributes('disabled')).toBeUndefined()

    const changedPreview = {
      ...preview,
      previewId: 'preview-2',
      confirmationPhrase: '确认切换 mysql-b v2',
      confirmationToken: 'signed-token-v2',
    }
    await wrapper.setProps({ preview: changedPreview })

    expect(confirmationInput.element).toHaveProperty('value', '')
    expect(irreversibleAck.element).toHaveProperty('checked', false)
    await confirmationInput.setValue(changedPreview.confirmationPhrase)
    expect(confirmButton.attributes('disabled')).toBeDefined()

    await irreversibleAck.setValue(true)
    await confirmButton.trigger('click')
    expect(wrapper.emitted('confirm')?.[0]).toEqual([
      {
        previewId: 'preview-2',
        confirmationToken: 'signed-token-v2',
      },
    ])
  })
})
