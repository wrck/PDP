import { expect, test, type Page } from '@playwright/test'

interface HighRiskPreview {
  previewId: string
  operationType: string
  targetSummary: string
  affectedCounts: Record<string, number>
  warnings: string[]
  irreversibleAt: string
  compensation?: string
  confirmationPhrase: string
  confirmationToken: string
  expiresAt: string
  availability: {
    enabled: boolean
    reasonCode: string
    reason: string
  }
}

interface RiskEvent {
  type: 'confirm' | 'compensate' | 'refresh'
  payload?: {
    previewId: string
    confirmationToken?: string
  }
}

declare global {
  interface Window {
    __riskHarness?: {
      events: RiskEvent[]
      update: (preview: HighRiskPreview) => void
    }
  }
}

const validPreview: HighRiskPreview = {
  previewId: 'preview-v7',
  operationType: 'DATABASE_SWITCH',
  targetSummary: '将写入主权从 mysql-a 切换到 mysql-b',
  affectedCounts: { workspaces: 12, activeJobs: 3 },
  warnings: ['切换期间写入将短暂停止'],
  irreversibleAt: '目标部署开放写入后',
  compensation: '关闭目标写入并执行受控回退',
  confirmationPhrase: '确认切换 mysql-b v7',
  confirmationToken: 'signed-token-v7',
  expiresAt: '2099-01-01T00:00:00Z',
  availability: {
    enabled: true,
    reasonCode: 'CERTIFIED',
    reason: 'MYSQL 8.4 → MYSQL 8.4 组合已认证',
  },
}

test('过期预览禁止确认并要求重新预览', async ({ page }) => {
  await mountRealPanel(page, {
    ...validPreview,
    expiresAt: '2000-01-01T00:00:00Z',
  })

  await expect(page.getByRole('alert')).toContainText('影响预览已过期')
  await page.locator('[data-test="confirmation-input"]').fill(validPreview.confirmationPhrase)
  await page.locator('[data-test="irreversible-ack"]').check()
  await expect(page.locator('[data-test="confirm-operation"]')).toBeDisabled()
  await page.getByRole('button', { name: '重新预览' }).click()

  await expect
    .poll(() => events(page))
    .toContainEqual({ type: 'refresh' })
})

test('目标版本变化后旧确认输入失效且只能提交新预览令牌', async ({ page }) => {
  await mountRealPanel(page, validPreview)
  await page.locator('[data-test="confirmation-input"]').fill(validPreview.confirmationPhrase)
  await page.locator('[data-test="irreversible-ack"]').check()
  await expect(page.locator('[data-test="confirm-operation"]')).toBeEnabled()

  const changedPreview: HighRiskPreview = {
    ...validPreview,
    previewId: 'preview-v8',
    confirmationPhrase: '确认切换 mysql-b v8',
    confirmationToken: 'signed-token-v8',
  }
  await page.evaluate(
    (preview) => window.__riskHarness?.update(preview),
    changedPreview,
  )

  await expect(page.getByText('输入“确认切换 mysql-b v8”以确认')).toBeVisible()
  await expect(page.locator('[data-test="confirmation-input"]')).toHaveValue('')
  await expect(page.locator('[data-test="irreversible-ack"]')).not.toBeChecked()
  await expect(page.locator('[data-test="confirm-operation"]')).toBeDisabled()
  await page.locator('[data-test="confirmation-input"]').fill(changedPreview.confirmationPhrase)
  await expect(page.locator('[data-test="confirm-operation"]')).toBeDisabled()
  await page.locator('[data-test="irreversible-ack"]').check()
  await page.locator('[data-test="confirm-operation"]').click()

  await expect
    .poll(() => events(page))
    .toContainEqual({
      type: 'confirm',
      payload: {
        previewId: 'preview-v8',
        confirmationToken: 'signed-token-v8',
      },
    })
  expect(await events(page)).not.toContainEqual(
    expect.objectContaining({
      payload: expect.objectContaining({ confirmationToken: 'signed-token-v7' }),
    }),
  )
})

test('有效预览可明确确认并发起补偿', async ({ page }) => {
  await mountRealPanel(page, validPreview)

  await page.locator('[data-test="confirmation-input"]').fill(validPreview.confirmationPhrase)
  await page.locator('[data-test="irreversible-ack"]').check()
  await page.locator('[data-test="confirm-operation"]').click()
  await page.getByRole('button', { name: '发起补偿' }).click()

  const emitted = await events(page)
  expect(emitted).toContainEqual({
    type: 'confirm',
    payload: {
      previewId: validPreview.previewId,
      confirmationToken: validPreview.confirmationToken,
    },
  })
  expect(emitted).toContainEqual({
    type: 'compensate',
    payload: { previewId: validPreview.previewId },
  })
})

async function mountRealPanel(page: Page, initialPreview: HighRiskPreview) {
  await page.goto('/')
  await page.evaluate(async (preview) => {
    const dynamicImport = (specifier: string) =>
      (0, eval)(`import(${JSON.stringify(specifier)})`) as Promise<Record<string, unknown>>
    const vue = (await dynamicImport('/@id/vue')) as {
      createApp: (component: object) => { mount: (target: Element) => void }
      h: (
        component: unknown,
        props: Record<string, unknown>,
      ) => unknown
      shallowRef: <T>(value: T) => { value: T }
    }
    const panelModule = await dynamicImport(
      '/src/components/high-risk-operation/HighRiskOperationPanel.vue',
    )
    const Panel = panelModule.default
    const currentPreview = vue.shallowRef(preview)
    const riskEvents: RiskEvent[] = []

    document.body.innerHTML = '<main><div id="risk-operation-test-root"></div></main>'
    const target = document.querySelector('#risk-operation-test-root')
    if (!target) throw new Error('高风险操作测试挂载点不存在')

    const app = vue.createApp({
      setup() {
        return () =>
          vue.h(Panel, {
            preview: currentPreview.value,
            canCompensate: true,
            onConfirm: (payload: RiskEvent['payload']) =>
              riskEvents.push({ type: 'confirm', payload }),
            onCompensate: (payload: RiskEvent['payload']) =>
              riskEvents.push({ type: 'compensate', payload }),
            onRefresh: () => riskEvents.push({ type: 'refresh' }),
          })
      },
    })
    window.__riskHarness = {
      events: riskEvents,
      update(nextPreview) {
        currentPreview.value = nextPreview
      },
    }
    app.mount(target)
  }, initialPreview)
  await expect(
    page.getByRole('heading', { name: '高风险操作影响确认' }),
  ).toBeVisible()
}

async function events(page: Page) {
  return page.evaluate(() => window.__riskHarness?.events ?? [])
}
