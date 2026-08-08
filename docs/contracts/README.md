# contracts

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

前后端必须共同遵守的契约。细节以 Controller / DTO / Flyway / admin-web `api/*` 为准；本文浓缩易漂移点。

| 文档 | 内容 |
|------|------|
| [`AUTH_CONTRACT.md`](./AUTH_CONTRACT.md) | Admin/Site 认证、JWT、Cookie |
| [`ADMIN_API_CONTRACT.md`](./ADMIN_API_CONTRACT.md) | 后台资源路径摘要 |
| [`SITE_API_CONTRACT.md`](./SITE_API_CONTRACT.md) | 前台 API / Site Auth |
| [`STATUS_ENUMS.md`](./STATUS_ENUMS.md) | 状态枚举（Reservation DONE 等） |
| [`ERROR_CONTRACT.md`](./ERROR_CONTRACT.md) | ApiResponse / 错误码 |
| [`PAGINATION_CONTRACT.md`](./PAGINATION_CONTRACT.md) | PageResult |
