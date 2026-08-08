# site-web ARCHITECTURE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

- 目录：`apps/site-web/<industry>-web` 独立 Vite/React 应用。  
- 目标：经 gateway 访问 `/api/site/**` 与 `/api/auth/site/**`。  
- 当前无统一 auth client / token store 实现（相对 admin-web）。  
