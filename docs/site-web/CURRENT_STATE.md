# site-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

| 能力 | 状态 |
|------|------|
| 访客前台 | **NOT_IN_TREE**（`apps/site-web/` 不在工作区） |
| Site Auth（login/refresh/logout/me） | **NOT_IMPLEMENTED**（前端） |
| Backend Site Auth | **BACKEND_COMPLETE**（见 website-services） |
| JWT audience SITE / Cookie | 后端已实现；前端未接 |
| 业务 API 对接 | **NOT_IMPLEMENTED**（无前台应用） |

**禁止**将 site-web 描述为「认证已完整的产品前台」，也不要再写成仓库里仍有 `car-web` / `cake-web`。正确组合标签：

```text
Site Auth: BACKEND_COMPLETE + frontend NOT_IMPLEMENTED
Site apps: NOT_IN_TREE
Tracking: #3
```
