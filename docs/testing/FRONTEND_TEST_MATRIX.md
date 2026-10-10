# FRONTEND_TEST_MATRIX

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-10
```

| Check | Command | Notes |
|-------|---------|-------|
| lint | `cd apps/admin-web && pnpm lint` | 工作区没有 site-web 应用 |
| build | `pnpm build` | 同上，在 admin-web 目录 |
| imports | `node scripts/check-imports.mjs` | |
| import casing | `node scripts/check-import-casing.mjs` | |
| Browser E2E | — | **NOT_IMPLEMENTED** |
