# site-web AUTH_INTEGRATION

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

后端契约见 [`../contracts/AUTH_CONTRACT.md`](../contracts/AUTH_CONTRACT.md) 与 [`../contracts/SITE_API_CONTRACT.md`](../contracts/SITE_API_CONTRACT.md)。

当前没有前台应用可接入。恢复站点应用后的前端待办（跟踪用 GitHub Issue，不在此展开实现）：

- login / refresh / logout / me  
- SITE audience 校验  
- refresh cookie 凭证请求  
- 与 Admin token 隔离（勿混用 localStorage key）  
