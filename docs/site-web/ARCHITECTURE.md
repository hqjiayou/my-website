# site-web ARCHITECTURE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- 目标形态：`apps/site-web/<industry>-web` 独立 Vite/React 应用，经 gateway 访问 `/api/site/**` 与 `/api/auth/site/**`。
- 当前工作区没有这些应用，也没有与 admin-web 对等的 auth client / token store。
