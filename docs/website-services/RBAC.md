# RBAC

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- Method Security + 每次请求查库得到权限码。JWT 不带权限列表（与 `JwtTokenService` 的签发字段一致；原 5A §3.3 已于 2026-10-29 移除）。
- 角色 CRUD、角色权限绑定、用户角色、权限只读列表已在 `AdminRoleController`、`AdminUserRoleController`、`AdminPermissionController`。
- 菜单只读：`GET /api/admin/menus` 与 `/api/admin/auth/me` 的 menus。`AdminMenuController` 没有 POST/PUT/DELETE。
- 原则正文：本页与现行 Controller 实现（原阶段 5A/5E 长文已于 2026-10-29 移除，见 git 历史）。  
