# RBAC

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- Method Security + 每次请求查库得到权限码。JWT 不带权限列表（5A §3.3，与 `JwtTokenService` 的签发字段一致）。
- 角色 CRUD、角色权限绑定、用户角色、权限只读列表已在 `AdminRoleController`、`AdminUserRoleController`、`AdminPermissionController`。
- 菜单只读：`GET /api/admin/menus` 与 `/api/admin/auth/me` 的 menus。`AdminMenuController` 没有 POST/PUT/DELETE。
- 原则正文：[`阶段5A`](./设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md)（文首已按 Controller 核对）。菜单进度：[`阶段5E`](./设计文档/权限与后台管理/阶段5E-菜单权限与导航说明.md) 第 8 节。  
