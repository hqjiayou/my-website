# admin-web API_INTEGRATION

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

1. 经 gateway 的 `/api/**`（开发代理见 Vite config）。  
2. 核心域 **FORCE REAL**——见 [`../rules/MOCK_POLICY.md`](../rules/MOCK_POLICY.md)。  
3. 用户管理只用 **`rbacApi`**，不要使用已删除/退役的 `operation/userApi`。  
4. 契约枚举见 [`../contracts/STATUS_ENUMS.md`](../contracts/STATUS_ENUMS.md)。  
5. REAL 页面应处理 loading / empty / error；mutation 建议 `onError` → toast。  
