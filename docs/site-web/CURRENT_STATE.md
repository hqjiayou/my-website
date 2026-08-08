# site-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

| 能力 | 状态 |
|------|------|
| 行业站点骨架（car / cake） | PROTOTYPE_MOCK / skeleton |
| Site Auth（login/refresh/logout/me） | **NOT_IMPLEMENTED**（前端） |
| Backend Site Auth | **BACKEND_COMPLETE**（见 website-services） |
| JWT audience SITE / Cookie | 后端已实现；前端未接 |
| 业务 API 对接 | NOT_IMPLEMENTED / 骨架 |

**禁止**将 site-web 描述为「认证已完整的产品前台」。正确组合标签：

```text
Site Auth: BACKEND_COMPLETE + frontend NOT_IMPLEMENTED
```
