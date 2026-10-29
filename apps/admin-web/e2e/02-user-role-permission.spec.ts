import { expect, test } from '@playwright/test'

import { fieldByLabelText, login, loginAsAdmin, newContext, uniqueSuffix } from './helpers'

/**
 * Golden path 2 (Issue #2): Admin user → assign role → permission effect.
 *
 * A freshly created admin user has no roles: the agent list route is
 * denied (403 page). After a super admin assigns the seeded `admin`
 * role, the same user can open the agent list. Both directions are
 * asserted, each in a fresh browser context (fresh session).
 */
test('user without role is denied; after admin role assignment the agent list opens', async ({
  browser,
}) => {
  const suffix = uniqueSuffix()
  const username = `e2e_${suffix}`
  const password = 'E2ePass1234'
  const displayName = `E2E User ${suffix}`

  const admin = await loginAsAdmin(browser)
  try {
    // --- create the user through the list dialog ---
    await admin.page.goto('/system/users')
    await admin.page.getByRole('button', { name: 'New user' }).click()
    await fieldByLabelText(admin.page, 'Username').fill(username)
    await admin.page.locator('input[type="password"]').fill(password)
    await fieldByLabelText(admin.page, 'Display name').fill(displayName)
    await admin.page.getByRole('button', { name: 'Save' }).click()
    await expect(admin.page.getByText('User created')).toBeVisible()

    // --- before: the new user is denied on the agent list route ---
    const deniedContext = await newContext(browser)
    const deniedPage = await deniedContext.newPage()
    await login(deniedPage, username, password)
    await deniedPage.goto('/agent')
    await expect(deniedPage.getByRole('heading', { name: 'Access denied' })).toBeVisible()
    await deniedContext.close()

    // --- assign the seeded `admin` role through the UI ---
    await admin.page.getByPlaceholder('Username', { exact: true }).fill(username)
    await admin.page.getByRole('button', { name: 'Search' }).click()
    const row = admin.page.locator('tr', { hasText: username })
    await expect(row).toBeVisible()
    await row.getByText('Assign roles').click()
    const adminRoleCard = admin.page
      .locator('label')
      .filter({ has: admin.page.getByText('admin', { exact: true }) })
    await adminRoleCard.locator('input[type="checkbox"]').check()
    await admin.page.getByRole('button', { name: 'Save role bindings' }).click()
    await expect(admin.page.getByText('User roles saved')).toBeVisible()

    // --- after: a fresh session for the user can open the agent list ---
    const allowedContext = await newContext(browser)
    const allowedPage = await allowedContext.newPage()
    await login(allowedPage, username, password)
    await allowedPage.goto('/agent')
    await expect(allowedPage.getByRole('heading', { name: 'Site Management' })).toBeVisible()
    await expect(allowedPage.getByRole('heading', { name: 'Access denied' })).toHaveCount(0)
    await allowedContext.close()
  } finally {
    await admin.context.close()
  }
})
