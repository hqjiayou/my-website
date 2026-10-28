# E2E_GOLDEN_PATHS

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-28
```

## Status

```text
BROWSER E2E: IMPLEMENTED (Playwright, Chromium)
BROWSER E2E: RUN IN CI (browser-e2e-ci) + LOCALLY VIA e2e/run-e2e.sh
```

Tracked in GitHub Issue #2. The suite lives in `apps/admin-web`:

| Piece | Location |
|---|---|
| Playwright config | `apps/admin-web/playwright.config.ts` |
| Specs | `apps/admin-web/e2e/01-login-create-agent.spec.ts`, `02-user-role-permission.spec.ts`, `03-media-content.spec.ts` |
| Shared helpers | `apps/admin-web/e2e/helpers.ts` |
| Disposable stack | `apps/admin-web/e2e/docker-compose.e2e.yml` |
| One-command local run | `apps/admin-web/e2e/run-e2e.sh` |
| CI | `.github/workflows/browser-e2e-ci.yml` |

## Covered golden paths (real browser, real stack)

The stack under test is gateway + auth + admin + PostgreSQL + Redis +
S3 (SeaweedFS) + admin-web (Caddy), seeded with the repo's local seed
(`seed-admin-hqjiayou-super.sql`, super_admin `hqjiayou` — disposable
stack only). Every test creates its own data with a unique suffix, so
the suite is re-runnable without cleanup.

1. **Admin login → create agent → reload** (`01`)
   Logs in as super admin, creates an agent through the 3-step wizard
   UI, then asserts the agent name/code are still listed after a full
   page reload. ("site" was renamed "agent" in PR #108; the admin UI
   still says Site, routes/APIs say agent.)
2. **Admin user → assign role → permission effect** (`02`)
   A newly created user with no roles gets the 403 "Access denied"
   page on the agent list route. After a super admin assigns the
   seeded `admin` role in the UI, a fresh session for that user can
   open the agent list. Both directions are asserted.
3. **Media upload → content with cover → reload** (`03`)
   In a freshly created agent workspace: uploads a PNG on the Files
   page (waits for status READY), creates a content item selecting
   that media as cover, and asserts the content row is still listed
   after reload.

## Void path (do not re-add as-is)

- ~~Reservation → status transition → reload~~ — **VOID.** The
  Reservation feature was removed wholesale in the 2026-10-27
  generic-cleanup, so this Issue #2 path no longer exists in the
  product. If reservations ever return, re-scope it as a new path.

## How to run

Local (requires Docker compose v2, pnpm, openssl):

```bash
cd apps/admin-web
./e2e/run-e2e.sh
# teardown afterwards:
docker compose -f e2e/docker-compose.e2e.yml down -v
```

CI: `browser-e2e-ci` runs the same stack and suite on pull requests
targeting `dev`/`main` that touch E2E-relevant paths, and via
`workflow_dispatch`. Failures upload the Playwright HTML report and
test results as an artifact, plus stack logs in the job log.

Notes / limits:

- Tests pin the admin UI locale to en-US via the app's own
  `admin-web.locale` localStorage key so selectors stay stable.
- The Python agent service and the Java agent-app are intentionally
  not in the E2E stack: none of the admin golden paths call them
  (agent CRUD, RBAC, media and content are all admin-service APIs
  behind the gateway). The gateway's `/api/agent/**` route is wired
  to admin in the E2E compose so it fails honestly (404).
- This is a functional golden-path suite, not visual regression.
