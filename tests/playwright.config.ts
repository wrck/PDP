// Playwright 端到端配置（T023）
//
// 覆盖 PDP Web 前端的桌面与移动端冒烟测试。webServer 复用已在 5173 端口启动的
// `pdp-web` 开发服务器，避免在本地与 CI 中重复拉起实例。
//
// 运行：`pnpm --filter pdp-tests exec playwright test`
// 依赖：`@playwright/test` 为 tests/package.json 的 devDependency。

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  reporter: [['html'], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['Mobile Safari'] },
    },
  ],
  webServer: {
    command: 'pnpm --filter pdp-web dev',
    port: 5173,
    reuseExistingServer: true,
  },
});
