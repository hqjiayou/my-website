import { expect, test } from '@playwright/test'

import { loginWithMocks } from './mockApi'

/**
 * PR-4：配置中心 + ResourcePicker 冒烟（Mock API）。
 * 不证明真实后端契约。运行：npm run test:e2e:mock
 */
test.describe('Agent Config PR-4 (mock API)', () => {
  test('model chain picker, disabled gate, save order', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/models')
    await expect(page.getByRole('heading', { name: 'Chat chain' })).toBeVisible()
    await expect(page.getByText('Mock Model')).toBeVisible()
    await expect(page.getByText('Primary', { exact: true })).toBeVisible()

    // ROW_ORDER 先 CLI 后 KEY；停用入口仅在 KEY 列表。
    await page.getByRole('button', { name: 'Add from model library' }).nth(1).click()
    const drawer = page.getByRole('dialog')
    await expect(drawer.getByText(/Add KEY model entries|添加 KEY/)).toBeVisible()
    await expect(drawer.getByText('Disabled Model')).toBeVisible()
    const disabledRow = drawer.locator('tr', { hasText: 'Disabled Model' })
    await expect(disabledRow.getByRole('checkbox')).toBeDisabled()
    // 抽屉页脚 Cancel（排除 header 的 aria-label Cancel）
    await drawer.locator('button').filter({ hasText: /^Cancel$/ }).click()

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('heading', { name: 'Chat chain' })).toBeVisible()
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.screenshot({ path: 'e2e/artifacts/agent-config-pr4-models.png', fullPage: true })
  })

  test('prompt skills picker and knowledge link flow', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/prompt')
    await expect(page.getByRole('heading', { name: 'Prompt' })).toBeVisible()
    await expect(page.getByText(/Applying a template copies|套用模板会拷贝/)).toBeVisible()
    await page.getByRole('button', { name: 'Choose from catalog' }).click()
    const skillDrawer = page.getByRole('dialog').filter({ hasText: 'Select agent default skills' })
    await expect(skillDrawer).toBeVisible()
    await skillDrawer.getByRole('checkbox', { name: 'orchestration' }).check()
    await skillDrawer.getByRole('button', { name: 'Apply to draft' }).click()
    await expect(page.getByText('orchestration')).toBeVisible()
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByText('Prompt settings saved')).toBeVisible()

    await page.goto('/agent/42/agent/config/knowledge')
    await expect(page.getByRole('heading', { name: 'Knowledge spaces' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Linked spaces' })).toBeVisible()
    await expect(page.locator('li').filter({ hasText: 'Demo KB' })).toBeVisible()
    await page.getByRole('button', { name: 'Link knowledge space' }).click()
    const kbDrawer = page.getByRole('dialog').filter({ hasText: 'Select knowledge spaces to link' })
    await expect(kbDrawer.getByText('Shared KB')).toBeVisible()
    await kbDrawer.getByRole('checkbox', { name: '8' }).check()
    await kbDrawer.getByRole('button', { name: 'Confirm link' }).click()
    await expect(page.locator('li').filter({ hasText: 'Shared KB' })).toBeVisible()
  })

  test('MCP modes and cross-agent isolation', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/tools')
    await expect(page.getByRole('heading', { name: 'Skills / MCP' })).toBeVisible({ timeout: 20_000 })
    await page.getByText('Custom subset', { exact: false }).click()
    await page.getByRole('button', { name: 'Choose from registry' }).click()
    const mcpDrawer = page.getByRole('dialog').filter({ hasText: 'Select this agent' })
    await expect(mcpDrawer.getByText('filesystem')).toBeVisible()
    await expect(mcpDrawer.locator('tr', { hasText: 'legacy-off' }).getByRole('checkbox')).toBeDisabled()
    await mcpDrawer.getByRole('checkbox', { name: 'filesystem' }).check()
    await mcpDrawer.getByRole('button', { name: 'Apply to draft' }).click()
    await page.getByRole('button', { name: 'Save selection' }).click()
    await expect(page.getByText('MCP selection saved')).toBeVisible()

    await page.goto('/agent/43/agent/config/prompt')
    await expect(page.getByRole('heading', { name: 'Other Agent' })).toBeVisible()
    await expect(page.getByText('orchestration')).toHaveCount(0)
  })

  test('readonly without update permissions', async ({ page }) => {
    await loginWithMocks(page, {
      permissions: ['admin:agent:list', 'admin:ai-prompt:read', 'admin:ai-model:read', 'admin:ai-kb:list'],
    })
    await page.goto('/agent/42/agent/config/models')
    await expect(page.getByRole('button', { name: 'Add from model library' })).toHaveCount(0)
    await page.goto('/agent/42/agent/config/prompt')
    await expect(page.getByRole('button', { name: 'Choose from catalog' })).toHaveCount(0)
    await page.goto('/agent/42/agent/config/knowledge')
    await expect(page.getByRole('button', { name: 'Link knowledge space' })).toHaveCount(0)
  })
})
