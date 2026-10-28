import { defineConfig, devices } from '@playwright/test'

/**
 * Browser E2E for admin-web golden paths (GitHub Issue #2).
 *
 * The tests drive a real browser against a real stack
 * (gateway + auth + admin + PostgreSQL + Redis + MinIO + admin-web),
 * started via e2e/docker-compose.e2e.yml. See docs/testing/E2E_GOLDEN_PATHS.md.
 *
 * E2E_BASE_URL points at the admin-web entry (Caddy :8081, which also
 * reverse-proxies /api to the gateway). Default matches the compose stack.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8081'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  // The three golden paths share one seeded stack and one super-admin
  // account; run serially to keep data assertions deterministic.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
})
