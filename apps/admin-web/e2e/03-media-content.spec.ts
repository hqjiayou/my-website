import { expect, test } from '@playwright/test'

import { createAgentViaUi, loginAsAdmin, uniqueSuffix } from './helpers'

/** Smallest valid 1x1 transparent PNG; media whitelist accepts image/png. */
const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAwMCAO+ip1sAAAAASUVORK5CYII=',
  'base64',
)

/**
 * Golden path 3 (Issue #2): media upload → create content using that
 * media as its cover → reload, the content is still listed.
 * Runs inside its own freshly created agent, so it is self-isolating.
 */
test('media upload -> content with cover -> still listed after reload', async ({ browser }) => {
  const suffix = uniqueSuffix()
  const agentName = `E2E Media ${suffix}`
  const agentCode = `e2e-media-${suffix}`
  const fileName = `e2e-cover-${suffix}.png`
  const contentTitle = `E2E Content ${suffix}`

  const { context, page } = await loginAsAdmin(browser)
  try {
    const agentId = await createAgentViaUi(page, agentName, agentCode)

    // --- upload media through the workspace Files page ---
    await page.goto(`/apps/sites/${agentId}/files`)
    await page.locator('input[type="file"]').setInputFiles({
      name: fileName,
      mimeType: 'image/png',
      buffer: PNG_1X1,
    })
    const mediaRow = page.locator('tr', { hasText: fileName })
    await expect(mediaRow).toBeVisible()
    await expect(mediaRow.getByText('Ready', { exact: true })).toBeVisible({ timeout: 30_000 })

    // --- create content and pick the uploaded media as cover ---
    await page.goto(`/apps/sites/${agentId}/contents`)
    await page.getByRole('button', { name: 'Add Content' }).click()
    const editor = page.locator('article', {
      has: page.getByRole('heading', { name: 'Create Content' }),
    })
    await editor.getByLabel('Title', { exact: true }).fill(contentTitle)
    const coverSelect = editor.getByLabel('Cover Media', { exact: true })
    const coverOption = coverSelect.locator('option', { hasText: fileName })
    await expect(coverOption).toHaveCount(1)
    const coverValue = await coverOption.getAttribute('value')
    expect(coverValue).toBeTruthy()
    await coverSelect.selectOption(coverValue!)
    await editor.getByRole('button', { name: 'Save' }).click()

    const contentRow = page.locator('tr', { hasText: contentTitle })
    await expect(contentRow).toBeVisible()

    await page.reload()
    await expect(page.locator('tr', { hasText: contentTitle })).toBeVisible()
  } finally {
    await context.close()
  }
})
