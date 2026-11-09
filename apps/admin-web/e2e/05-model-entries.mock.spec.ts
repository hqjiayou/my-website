import { expect, test } from '@playwright/test'

import { loginWithMocks } from './mockApi'

/**
 * 模型 API CLI/KEY 双模式（Mock API）。
 *
 * 覆盖：mode URL、非法 mode 规范化、KEY 无明文密钥、derived 只读。
 * 真实后端接口本次未验证。
 *
 * 运行：npx playwright test -c playwright.mock.config.ts
 */
test.describe('Model entries CLI/KEY modes (mock API)', () => {
  test('mode URL, invalid mode, KEY hint, derived readonly', async ({ page }) => {
    const consoleErrors: string[] = []
    page.on('pageerror', (error) => consoleErrors.push(error.message))
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })

    await loginWithMocks(page)

    await page.goto('/ai/model-entries')
    await page.waitForURL(/\/ai\/model-entries\?mode=CLI/)
    await expect(page.getByRole('heading', { name: 'Model API Management' })).toBeVisible()
    await expect(page.getByRole('tab', { name: /CLI/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /API Key/ })).toBeVisible()
    await expect(page.getByText('gpt-test-cli').first()).toBeVisible()
    await expect(page.getByText('Derived').first()).toBeVisible()

    await page.getByRole('tab', { name: /API Key/ }).click()
    await page.waitForURL(/\/ai\/model-entries\?mode=KEY/)
    await expect(page.getByText('Mock Model')).toBeVisible()
    await expect(page.getByText('openai:gpt-test')).toBeVisible()
    await expect(page.getByText('sk-…test')).toBeVisible()
    await expect(page.getByText(/sk-[a-zA-Z0-9]{8,}/)).toHaveCount(0)
    await expect(page.locator('input[type="password"]')).toHaveCount(0)

    await page.getByRole('tab', { name: /CLI/ }).click()
    await page.waitForURL(/\/ai\/model-entries\?mode=CLI/)
    await page.goBack()
    await page.waitForURL(/\/ai\/model-entries\?mode=KEY/)
    await expect(page.getByText('Mock Model')).toBeVisible()
    await page.goForward()
    await page.waitForURL(/\/ai\/model-entries\?mode=CLI/)

    await page.goto('/ai/model-entries?mode=BOGUS')
    await page.waitForURL(/\/ai\/model-entries\?mode=CLI/)
    await expect(page.getByText('gpt-test-cli').first()).toBeVisible()

    await page.goto('/ai/model-entries?mode=KEY')
    await page.waitForURL(/\/ai\/model-entries\?mode=KEY/)
    await page.reload()
    await page.waitForURL(/\/ai\/model-entries\?mode=KEY/)
    await expect(page.getByText('Mock Model')).toBeVisible()

    await page.goto('/ai/model-entries?mode=CLI')
    await page.waitForURL(/\/ai\/model-entries\?mode=CLI/)
    await page.getByRole('button', { name: 'Row actions' }).first().click()
    await page.getByRole('menuitem', { name: 'View' }).click()
    await expect(page.getByText(/Derived entries are synced automatically/i)).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.screenshot({ path: 'e2e/artifacts/model-entries-1440.png', fullPage: true })

    await page.setViewportSize({ width: 1024, height: 768 })
    await page.screenshot({ path: 'e2e/artifacts/model-entries-1024.png', fullPage: true })

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('heading', { name: 'Model API Management' })).toBeVisible()
    await page.screenshot({ path: 'e2e/artifacts/model-entries-390.png', fullPage: true })

    expect(consoleErrors, `unexpected console errors: ${consoleErrors.join('\n')}`).toEqual([])
  })
})
