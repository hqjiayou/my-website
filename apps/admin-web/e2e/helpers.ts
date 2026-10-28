import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test'

/**
 * Shared helpers for the admin golden-path E2E suite.
 *
 * Accounts: the stack is seeded with the repo's own local seed
 * (services/website-services/scripts/local/seed-admin-hqjiayou-super.sql),
 * which creates the super_admin user below. It exists only inside the
 * disposable E2E stack (see e2e/docker-compose.e2e.yml); never use these
 * credentials anywhere else.
 */
export const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'hqjiayou'
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456'

/** Unique, pattern-safe suffix so tests are re-runnable without cleanup. */
export function uniqueSuffix(): string {
  const time = Date.now().toString(36)
  const random = Math.floor(Math.random() * 100_000).toString()
  return `x${time}${random}`
}

/** Open a fresh browser context pinned to the en-US admin UI. */
export async function newContext(browser: Browser): Promise<BrowserContext> {
  const context = await browser.newContext({ locale: 'en-US' })
  // admin-web resolves its initial locale from this localStorage key
  // (see src/i18n/constants.ts); pinning it keeps selectors stable.
  await context.addInitScript(() => {
    window.localStorage.setItem('admin-web.locale', 'en-US')
  })
  return context
}

export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.locator('button[type="submit"]').click()
  await page.waitForURL('**/dashboard')
}

export async function loginAsAdmin(
  browser: Browser,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await newContext(browser)
  const page = await context.newPage()
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  return { context, page }
}

/**
 * Field lookup for forms whose <label> is a sibling of the <input>
 * (e.g. the admin user editor), where getByLabel has no association.
 * Matches the wrapper div that directly contains the label.
 */
export function fieldByLabelText(page: Page, label: string) {
  return page.locator(`div:has(> label:text-is("${label}")) > input`).first()
}

/**
 * Create an agent (formerly "site") through the real 3-step wizard UI.
 * Returns the new agent id parsed from the workspace URL.
 */
export async function createAgentViaUi(
  page: Page,
  agentName: string,
  agentCode: string,
): Promise<string> {
  await page.goto('/apps/sites/create')
  await page.getByLabel('Site Name', { exact: true }).fill(agentName)
  await page.getByLabel('Site Code', { exact: true }).fill(agentCode)
  await page.getByRole('button', { name: 'Next' }).click()
  // Appearance step: defaults are valid, continue.
  await page.getByRole('button', { name: 'Next' }).click()
  await page.getByRole('button', { name: 'Create Site' }).click()
  await page.waitForURL(/\/apps\/sites\/\d+\/agent$/)
  const match = /\/apps\/sites\/(\d+)\/agent$/.exec(page.url())
  expect(match, `expected workspace URL after create, got ${page.url()}`).not.toBeNull()
  return match![1]
}
