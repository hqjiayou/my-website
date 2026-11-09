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

`test:table`：表格底座纯逻辑（排序作用域、列隐藏下限、筛选重置页码、选择集合、批量 available 过滤）。

`test:e2e`：真实栈 golden path（需 docker E2E）。

`test:e2e:mock`：Agent Console V2 冒烟（Mock API + `vite preview`；不证明真实后端；含 `04`–`08`：布局、模型页、配置 ResourcePicker、PR-4.1 安全补丁、PR-5 调试 Inspector / Runs）。

**BROWSER E2E: 真实栈与 mock 冒烟分开标注，不得将 lint/build 称为 E2E PASS。**
