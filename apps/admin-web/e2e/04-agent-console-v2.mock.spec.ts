import { expect, test } from '@playwright/test'

import { loginWithMocks } from './mockApi'

/**
 * Agent Console V2 布局/路由冒烟（Mock API）。
 *
 * 真实后端栈未启动时使用本文件；不证明服务端契约。
 * 运行：npx playwright test -c playwright.mock.config.ts
 *
 * 未验证的真实接口：Agent 列表/详情 CRUD、kind 更新、知识订阅、模型链读写、
 * Prompt/KB/Runtime/MCP、会话列表等均走 Mock；需后端栈时用 `npm run test:e2e`。
 */
test.describe('Agent Console V2 (mock API)', () => {
  test('list, tabs, config subnav, legacy redirects', async ({ page }) => {
    const consoleErrors: string[] = []
    page.on('pageerror', (error) => consoleErrors.push(error.message))
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })

    await loginWithMocks(page)

    await page.goto('/agent')
    await expect(page.getByRole('heading', { name: 'Agents' })).toBeVisible()
    await expect(page.getByText('Demo Agent')).toBeVisible()
    await expect(page.getByText('demo-agent')).toBeVisible()
    await expect(page.getByLabel('Status')).toBeVisible()
    await expect(page.getByText('1 items')).toBeVisible()

    await page.getByText('Demo Agent').click()
    await page.waitForURL('**/agent/42/agent')
    await expect(page.getByRole('heading', { name: 'Demo Agent' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Agent' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Overview' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Configuration' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Debug' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Runs' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Settings' })).toBeVisible()

    await page.getByRole('link', { name: 'Configuration' }).click()
    await page.waitForURL('**/agent/42/agent/config/models')
    await expect(page.getByRole('link', { name: 'Model chain' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Prompt' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Knowledge' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Skills / MCP' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Configuration' })).toBeVisible()

    await page.getByRole('link', { name: 'Prompt' }).click()
    await page.waitForURL('**/agent/42/agent/config/prompt')

    await page.getByRole('link', { name: 'Knowledge' }).click()
    await page.waitForURL('**/agent/42/agent/config/knowledge')

    await page.getByRole('link', { name: 'Skills / MCP' }).click()
    await page.waitForURL('**/agent/42/agent/config/tools')
    await expect(page.getByText(/Runtime|运行|MCP/i).first()).toBeVisible()

    const workspaceNav = page.getByRole('navigation', { name: 'Agent' })
    await workspaceNav.getByRole('link', { name: 'Debug' }).click()
    await page.waitForURL('**/agent/42/agent/debug')

    await workspaceNav.getByRole('link', { name: 'Runs' }).click()
    await page.waitForURL('**/agent/42/agent/runs')

    await workspaceNav.getByRole('link', { name: 'Settings' }).click()
    await page.waitForURL('**/agent/42/agent/settings')
    await expect(page.getByText('Basic information')).toBeVisible()
    await expect(page.getByText('Advanced')).toBeVisible()

    await page.goto('/agent/42/agent/preview')
    await page.waitForURL('**/agent/42/agent/debug')

    await page.goto('/agent/42/agent/prompt')
    await page.waitForURL('**/agent/42/agent/config/prompt')

    await page.goto('/agent/42/agent/runtime')
    await page.waitForURL('**/agent/42/agent/config/tools')

    await page.goto('/agent/42/basic')
    await page.waitForURL('**/agent/42/agent/settings')

    await page.goBack()
    await page.waitForURL('**/agent/42/agent/config/tools')
    await page.goForward()
    await page.waitForURL('**/agent/42/agent/settings')

    await expect(page.getByText('Basic information')).toBeVisible()

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('heading', { name: 'Demo Agent' })).toBeVisible()
    await expect(page.getByText('Advanced')).toBeVisible()
    await page.screenshot({ path: 'e2e/artifacts/agent-console-v2-mobile.png', fullPage: true })

    await page.setViewportSize({ width: 1024, height: 768 })
    await expect(page.getByRole('link', { name: 'Configuration' })).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 900 })
    await expect(page.getByRole('navigation', { name: 'Agent' }).getByRole('link', { name: 'Settings', exact: true })).toBeVisible()
    await expect(page.getByText('Basic information')).toBeVisible()
    await page.screenshot({ path: 'e2e/artifacts/agent-console-v2-desktop.png', fullPage: true })

    expect(consoleErrors, `unexpected console errors: ${consoleErrors.join('\n')}`).toEqual([])
  })

  test('create, kind change, subscription entry from list', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent')

    await page.getByRole('button', { name: 'New' }).click()
    const createDialog = page.getByRole('dialog')
    await expect(createDialog.getByRole('heading', { name: 'New agent' })).toBeVisible()
    await expect(createDialog.getByText('Name', { exact: true })).toBeVisible()
    await expect(createDialog.getByText('Code', { exact: true })).toBeVisible()
    await page.keyboard.press('Escape')

    const kindSelect = page.locator('select').filter({ has: page.locator('option[value="AI_ONLY"]') }).first()
    await expect(kindSelect).toBeVisible()
    await kindSelect.selectOption('SITE')

    await page.getByRole('button', { name: 'Row actions' }).click()
    await page.getByRole('menuitem', { name: 'Manage subscriptions' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText('Demo KB')).toBeVisible()
  })

  test('hides AI tabs without AI permissions', async ({ page }) => {
    await loginWithMocks(page, { permissions: ['admin:agent:list'] })
    await page.goto('/agent/42/agent/settings')
    await expect(page.getByRole('heading', { name: 'Demo Agent' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Settings' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Configuration' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Debug' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Runs' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Debug' })).toHaveCount(0)
  })
})
