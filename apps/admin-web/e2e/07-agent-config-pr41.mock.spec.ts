import { expect, test } from '@playwright/test'

import { loginWithMocks } from './mockApi'

/**
 * PR-4.1：未保存离开保护、抽屉会话稳定、知识空间部分失败（Mock API）。
 */
test.describe('Agent Config PR-4.1 (mock API)', () => {
  test('unsaved draft blocks config navigation; stay keeps edits', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/prompt')
    await expect(page.getByRole('heading', { name: 'Prompt' })).toBeVisible()

    const promptBox = page.locator('textarea').first()
    await promptBox.fill('Draft prompt — do not lose me')
    await expect(page.getByText('Unsaved changes').or(page.getByRole('button', { name: 'Discard' }))).toBeVisible()

    await page.getByRole('link', { name: 'Knowledge' }).click()
    const leaveDialog = page.getByRole('dialog').filter({ hasText: 'Unsaved changes' })
    await expect(leaveDialog).toBeVisible()
    await leaveDialog.getByRole('button', { name: 'Keep editing' }).click()
    await expect(page).toHaveURL(/\/config\/prompt/)
    await expect(promptBox).toHaveValue('Draft prompt — do not lose me')

    await page.getByRole('link', { name: 'Knowledge' }).click()
    await expect(leaveDialog).toBeVisible()
    await leaveDialog.getByRole('button', { name: 'Discard and leave' }).click()
    await expect(page).toHaveURL(/\/config\/knowledge/)
    await expect(page.getByRole('heading', { name: 'Knowledge spaces' })).toBeVisible()
  })

  test('resource picker keeps in-progress selection across list refresh', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/prompt')
    await page.getByRole('button', { name: 'Choose from catalog' }).click()
    const drawer = page.getByRole('dialog').filter({ hasText: 'Select agent default skills' })
    await expect(drawer).toBeVisible()
    await drawer.getByRole('checkbox', { name: 'orchestration' }).check()
    await expect(drawer.getByText('1 selected')).toBeVisible()

    // 触发目录刷新：父级可能重渲染；会话 key 不绑 initialSelectedIds，勾选应保留。
    await page.evaluate(async () => {
      await fetch('/api/admin/ai/skill-catalog')
    })
    await page.waitForTimeout(300)
    await expect(drawer.getByRole('checkbox', { name: 'orchestration' })).toBeChecked()
    await expect(drawer.locator('[data-testid="resource-picker-session"]')).toBeVisible()
  })

  test('knowledge batch link reports partial success without losing succeeded', async ({ page }) => {
    await loginWithMocks(page)
    await page.goto('/agent/42/agent/config/knowledge')
    await page.getByRole('button', { name: 'Link knowledge space' }).click()
    const kbDrawer = page.getByRole('dialog').filter({ hasText: 'Select knowledge spaces to link' })
    await expect(kbDrawer.getByText('Shared KB')).toBeVisible()
    await expect(kbDrawer.getByText('Fail KB')).toBeVisible()
    await kbDrawer.getByRole('checkbox', { name: '8' }).check()
    await kbDrawer.getByRole('checkbox', { name: '9' }).check()
    await kbDrawer.getByRole('button', { name: 'Confirm link' }).click()

    await expect(page.getByText(/Linked 1, failed 1|已关联 1/)).toBeVisible()
    await expect(page.locator('li').filter({ hasText: 'Shared KB' })).toBeVisible()
    // 抽屉仍开：失败项可重试；已成功的 Shared KB 不再出现在可选列表。
    await expect(kbDrawer.getByText('Fail KB')).toBeVisible()
    await expect(kbDrawer.getByText('Shared KB')).toHaveCount(0)
  })
})
