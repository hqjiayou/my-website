# TEST_STRATEGY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

## 层级

| 层 | 现状 |
|----|------|
| Backend unit/MVC | 有（auth/admin/site-app） |
| Persistence PG (Testcontainers) | 有（`-Ppersistence-verify-pg`） |
| Frontend lint/build/import checks | 有（仅 `apps/admin-web`；工作区没有 site-web 应用） |
| Contract/vertical（UI+API+DB 手工/MVC） | 有 |
| **Browser E2E** | **NOT_IMPLEMENTED** |

禁止：把 MockMvc / `pnpm build` 写成「E2E PASS」。

## 命令

见 [`FRONTEND_TEST_MATRIX.md`](./FRONTEND_TEST_MATRIX.md)、[`BACKEND_TEST_MATRIX.md`](./BACKEND_TEST_MATRIX.md)。
