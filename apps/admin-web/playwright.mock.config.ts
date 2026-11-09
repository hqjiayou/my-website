import { defineConfig, devices } from '@playwright/test'

/**
 * Mock API 浏览器冒烟：不依赖 docker E2E 栈。
 * 先 `npm run build`，再：
 *   npx playwright test -c playwright.mock.config.ts
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/04-agent-console-v2.mock.spec.ts', '**/05-model-entries.mock.spec.ts'],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
