# SITE_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
```

前缀：`/api/site/**`（业务）+ `/api/auth/site/**`（认证）。

| 能力 | 状态 |
|------|------|
| Site Auth login/refresh/logout/me | **BACKEND_COMPLETE** |
| site-web 对接 | **NOT_IMPLEMENTED** |
| Site AI chat/stream | **BACKEND_COMPLETE**（会话落库；无 `AI_MODEL` 时诚实 503） |
| site-web AI UI | **NOT_IMPLEMENTED** |

Audience 必须为 SITE；Admin token 不可调用 Site 受保护资源（反之亦然）。
