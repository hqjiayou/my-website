# admin-web TESTING

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-08-09
```

```bash
cd apps/admin-web
pnpm lint
pnpm build
node scripts/check-imports.mjs
node scripts/check-import-casing.mjs
```

**BROWSER E2E: NOT IMPLEMENTED** — 不得将 lint/build 称为 E2E PASS。
