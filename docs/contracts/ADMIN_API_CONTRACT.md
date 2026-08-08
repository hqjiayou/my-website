# ADMIN_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
```

前缀：`/api/admin/**`（经 gateway）。均需 Admin JWT + 对应 permission（`@PreAuthorize`）。

| 域 | 主要路径 | 前端 client |
|----|----------|-------------|
| Me / Menus | `/auth/me`、`/menus` | `rbacApi` |
| Roles / Permissions | `/roles`、`/permissions` | `rbacApi` |
| Users | `/users`、`/users/{id}/roles`… | `rbacApi`（**不是** legacy `userApi`） |
| Sites | `/sites` CRUD + `/status` | `siteApi` FORCE REAL |
| Templates | `/templates` list/get/by-code/status | `templateApi` |
| Appearance | `/sites/{id}/appearance` GET/PUT | `appearanceApi` |
| Media | `/sites/{id}/media` | `mediaApi` |
| Contents | `/sites/{id}/contents` | `contentApi` |
| Reservations | `/sites/{id}/reservations` | `reservationApi` |

细节字段以各 `Admin*Dtos` 与 admin-web `api/**` 为准。分页见 [`PAGINATION_CONTRACT.md`](./PAGINATION_CONTRACT.md)。
