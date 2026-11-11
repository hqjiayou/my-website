import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'

import { loginWithMocks } from './mockApi'

/**
 * P0 验收截图。单独运行：
 * pnpm exec playwright test -c playwright.p0-shots.config.ts
 */
const OUT = path.resolve('tmp/console-v2-ui-audit/p0')

const VIEWPORTS = [
  { name: '390', width: 390, height: 844 },
  { name: '768', width: 768, height: 900 },
  { name: '1024', width: 1024, height: 768 },
  { name: '1279', width: 1279, height: 800 },
  { name: '1280', width: 1280, height: 900 },
  { name: '1440', width: 1440, height: 900 },
] as const

async function shot(page: Page, viewport: string, name: string, fullPage = true) {
  await page.screenshot({
    path: path.join(OUT, viewport, `${name}.png`),
    fullPage,
  })
}

test.describe('P0 screenshots', () => {
  test('capture key Agent Console surfaces', async ({ page }) => {
    test.setTimeout(180_000)
    await loginWithMocks(page)

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height })

      await page.goto('/agent')
      await expect(page.getByRole('heading', { name: 'Agents' })).toBeVisible()
      await shot(page, vp.name, 'agents-list')

      const lastRow = page.getByRole('row', { name: /Other Agent/ })
      await lastRow.scrollIntoViewIfNeeded()
      await lastRow.getByRole('button', { name: 'Row actions' }).click()
      await expect(page.getByRole('menuitem', { name: 'Open' })).toBeVisible()
      await shot(page, vp.name, 'agents-list-last-row-menu', false)
      await page.keyboard.press('Escape')

      await page.goto('/agent/42/agent/config/models')
      await expect(page.getByRole('navigation', { name: 'Configuration' })).toBeVisible()
      await shot(page, vp.name, 'config-models')

      await page.goto('/agent/42/access')
      await expect(page.getByText(/CLI|KEY/).first()).toBeVisible()
      await shot(page, vp.name, 'settings-access')

      await page.goto('/agent/42/agent/config/tools')
      await expect(page.getByRole('heading', { name: 'Skills / MCP' })).toBeVisible({ timeout: 20_000 })
      await page.getByText('Custom subset', { exact: false }).click()
      await page.getByRole('button', { name: 'Choose from registry' }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await shot(page, vp.name, 'resource-picker-drawer', false)
      await page.keyboard.press('Escape')

      await page.goto('/agent/42/agent/debug')
      await expect(page.getByRole('button', { name: /Open debug parameters|打开调试/ })).toBeVisible({
        timeout: 20_000,
      })
      await shot(page, vp.name, 'debug-inspector-closed')
      await page.getByRole('button', { name: /Open debug parameters|打开调试/ }).click()
      await expect(page.getByRole('heading', { name: /Debug parameters|调试参数/ })).toBeVisible()
      await shot(page, vp.name, 'debug-inspector-open', false)
      await page.keyboard.press('Escape')
    }
  })
})
