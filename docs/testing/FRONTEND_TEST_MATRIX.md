# FRONTEND_TEST_MATRIX

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

| Check | Command | Notes |
|-------|---------|-------|
| lint | `cd apps/admin-web && pnpm lint` | |
| build | `pnpm build` | |
| imports | `node scripts/check-imports.mjs` | |
| import casing | `node scripts/check-import-casing.mjs` | |
| Browser E2E | — | **NOT_IMPLEMENTED** |
