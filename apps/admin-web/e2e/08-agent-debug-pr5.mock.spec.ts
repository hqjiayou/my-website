import { expect, test } from '@playwright/test'

import { loginWithMocks } from './mockApi'

/**
 * PR-5：调试 Inspector / 定位默认关闭 / 运行记录（Mock API）。
 * 不证明真实 SSE 契约。
 */
test.describe('Agent Debug PR-5 (mock API)', () => {
  test('inspector open/close, CLI/KEY, no auto geolocation', async ({ page }) => {
    await loginWithMocks(page)

    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'geolocation', {
        configurable: true,
        value: {
          getCurrentPosition: (_success: PositionCallback, error?: PositionErrorCallback) => {
            ;(window as unknown as { __geoCalls?: number }).__geoCalls =
              ((window as unknown as { __geoCalls?: number }).__geoCalls ?? 0) + 1
            error?.({
              code: 1,
              message: 'denied',
              PERMISSION_DENIED: 1,
              POSITION_UNAVAILABLE: 2,
              TIMEOUT: 3,
            } as GeolocationPositionError)
          },
          watchPosition: () => 0,
          clearWatch: () => undefined,
        },
      })
    })

    await page.goto('/agent/42/agent/debug')
    await expect(page.getByRole('button', { name: /Open debug parameters|打开调试/ })).toBeVisible({
      timeout: 20_000,
    })

    // 默认不占左侧常驻 Inspector
    await expect(page.getByRole('heading', { name: /Debug parameters|调试参数/ })).toHaveCount(0)

    await page.getByRole('button', { name: /Open debug parameters|打开调试/ }).click()
    await expect(page.getByRole('heading', { name: /Debug parameters|调试参数/ })).toBeVisible()
    await expect(page.getByText(/Applies only to the current turns|仅作用于本预览/)).toBeVisible()

    await page.getByRole('button', { name: 'API Key' }).click()
    await expect(page.locator('select').first()).toBeVisible()

    await page.getByRole('button', { name: 'CLI' }).click()

    // 未开启定位：发送也不会触发 geolocation（mock 流可能失败，只断言未调定位）
    const callsBefore = await page.evaluate(() => (window as unknown as { __geoCalls?: number }).__geoCalls ?? 0)
    await page.getByPlaceholder('Preview message…').fill('hello without geo')
    await page.getByRole('button', { name: 'Send' }).click()
    await page.waitForTimeout(400)
    const callsAfter = await page.evaluate(() => (window as unknown as { __geoCalls?: number }).__geoCalls ?? 0)
    expect(callsAfter).toBe(callsBefore)

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByPlaceholder('Preview message…')).toBeVisible()
    await page.screenshot({ path: 'e2e/artifacts/agent-debug-pr5-390.png', fullPage: true })

    await page.setViewportSize({ width: 1024, height: 768 })
    await page.screenshot({ path: 'e2e/artifacts/agent-debug-pr5-1024.png', fullPage: true })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.getByRole('complementary', { name: /Debug parameters|调试参数/ }).getByRole('button', { name: /Close/ }).click()
    await expect(page.getByRole('heading', { name: /Debug parameters|调试参数/ })).toHaveCount(0)
    await page.screenshot({ path: 'e2e/artifacts/agent-debug-pr5-1440.png', fullPage: true })
  })

  test('runs list distinguishes sources and shows no-trace hint', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/runs')
    await expect(page.getByText('Preview debug session')).toBeVisible()
    await expect(page.getByText('Portal user chat')).toBeVisible()
    await expect(page.getByText('Preview', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('Portal', { exact: true }).first()).toBeVisible()

    await page.getByRole('button', { name: /Preview debug session/ }).click()
    await expect(page.getByText('Hello from preview')).toBeVisible()
    await expect(page.getByText('This is a conversation record')).toBeVisible()

    await page.goto('/agent/43/agent/runs')
    await expect(page.getByText('Hello from preview')).toHaveCount(0)
  })

  test('debug hidden without prompt permission', async ({ page }) => {
    await loginWithMocks(page, {
      permissions: ['admin:agent:list', 'admin:ai-model:read', 'admin:ai-kb:list'],
    })
    await page.goto('/agent/42/agent/debug')
    await expect(page.getByRole('button', { name: /Open debug parameters|打开调试/ })).toHaveCount(0)
  })
})
