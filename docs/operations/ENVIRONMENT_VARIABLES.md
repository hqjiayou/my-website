# ENVIRONMENT_VARIABLES

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-08-09
```

权威列表以各服务 `.env.example` / `application.yml` / `services/website-services/README.md` 为准。

常见：

| 变量 | 域 |
|------|-----|
| DB / Redis / JWT / JWKS URI | website-services |
| `VITE_USE_MOCK` | admin-web（勿用于核心域假闭环） |
| AI Provider keys | agent-services（缺失→诚实失败） |

生产网关应配置可信代理 CIDR（XFF）；见 SECURITY。
