# site-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-24
```

| 能力 | 状态 |
|------|------|
| 访客前台 | **IN_TREE**（`apps/site-web/` generic 单应用，按路径 `/:siteCode` 渲染；v1 只读已发布内容，无登录） |
| Site Auth（login/refresh/logout/me） | **NOT_IMPLEMENTED**（前端） |
| Backend Site Auth | **BACKEND_COMPLETE**（见 website-services） |
| JWT audience SITE / Cookie | 后端已实现；前端未接 |
| 业务 API 对接 | **IMPLEMENTED**（公开只读 `GET /api/site/public/{siteCode}`、内容列表与详情；只接 PUBLISHED） |

**禁止**将 site-web 描述为「认证已完整的产品前台」，也不要再写成仓库里仍有 `car-web` / `cake-web`。正确组合标签：

```text
Site Auth: BACKEND_COMPLETE + frontend NOT_IMPLEMENTED
Site apps: apps/site-web v1（generic 单应用，按 siteCode 只读 PUBLISHED；无登录）
Tracking: #3
```
