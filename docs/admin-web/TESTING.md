# admin-web TESTING

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-10
```

```bash
cd apps/admin-web
pnpm lint
pnpm build
node scripts/check-imports.mjs
node scripts/check-import-casing.mjs
npm run test:boundary
```

`test:boundary`：静态扫描 `src/pages/agents/**`，禁止 `createModelEntry` / `updateModelEntry` / `defaultEntry: true`（Agent Console V2 P0；平台默认只允许在模型 API 管理页写入）。

**BROWSER E2E: NOT IMPLEMENTED** — 不得将 lint/build 称为 E2E PASS。
