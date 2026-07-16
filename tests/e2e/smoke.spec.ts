import { expect, test } from '@playwright/test'

test('展示 PDP 平台入口', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '企业级项目交付管理平台' })).toBeVisible()
})

