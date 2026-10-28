import { expect, test } from '@playwright/test'

import { createAgentViaUi, loginAsAdmin, uniqueSuffix } from './helpers'

/**
 * Golden path 1 (Issue #2): Admin login → create agent → reload,
 * the agent is still listed. ("site" was renamed to "agent" in PR #108;
 * the admin UI still labels it Site, the routes/APIs say agent.)
 */
test('admin login -> create agent -> still listed after reload', async ({ browser }) => {
  const suffix = uniqueSuffix()
  const agentName = `E2E Agent ${suffix}`
  const agentCode = `e2e-${suffix}`

  const { context, page } = await loginAsAdmin(browser)
  try {
    const agentId = await createAgentViaUi(page, agentName, agentCode)
    expect(Number(agentId)).toBeGreaterThan(0)

    await page.goto('/apps/sites')
    await expect(page.getByText(agentName)).toBeVisible()
    await expect(page.getByText(agentCode)).toBeVisible()

    await page.reload()
    await expect(page.getByText(agentName)).toBeVisible()
    await expect(page.getByText(agentCode)).toBeVisible()
  } finally {
    await context.close()
  }
})
