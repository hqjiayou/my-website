# MOCK_POLICY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

## 原则

- Mock ≠ 后端已完成。  
- **核心业务域禁止**隐式 Mock fallback。  
- `VITE_USE_MOCK` 只影响**明确允许** mock 的域（当前：site runtime-status）。  

## FORCE REAL（不受 USE_MOCK）

| API 模块 | 路径前缀 |
|----------|----------|
| `siteApi` Core | `/admin/sites` |
| `templateApi` | `/admin/templates` |
| `appearanceApi` | `/admin/sites/{id}/appearance` |
| `mediaApi` | `/admin/sites/{id}/media` |
| `contentApi` | `/admin/sites/{id}/contents` |
| `reservationApi` | `/admin/sites/{id}/reservations` |
| `aiApi` | `/admin/sites/{id}/ai/**` |
| `authApi` / `rbacApi` | `/auth/admin/**`、`/admin/**` RBAC |

## 允许 Mock

| 域 | 原因 |
|----|------|
| `getSiteRuntimeStatus` | 后端未实现；REAL 模式返回 `null`，禁止假 KPI |

`apps/admin-web/src/api/ai/aiApi.ts` 与 `aiMock` 已无页面消费，不得重新接到 FORCE REAL 页。

## 禁止

- 把 Site/Content/Reservation / AI 管理重新包回 `USE_MOCK ? mock : http`  
- 为 AI 制造假成功冒充 LIVE  
- 把无 consumer 的 `aiMock` 重新接到页面  
