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
| Site AI 代理路径 | Java 路径存在；Provider **EXTERNAL_BLOCKED** |

Audience 必须为 SITE；Admin token 不可调用 Site 受保护资源（反之亦然）。
