# MOCK_POLICY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

## 原则

- Mock ≠ 后端已完成。  
- **核心业务域禁止**隐式 Mock fallback。  
- admin-web **不再读取** `VITE_USE_MOCK`。设置该变量不会切换任何数据源。  

## FORCE REAL

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

## 无 Mock 域

站点概览指标由 `deriveSiteRuntimeStatus` 用现有 REAL API 推导，不是独立 runtime-status 端点，也不走 mock。访问量与当日 AI 会话无后端时为 `null`。发布清单的 AI 配置以 prompt profile 是否已落库（`id != null`）为准。

旧 `api/ai/aiApi.ts` 与 `mocks/**` 已删除，不得接回页面。

## 禁止

- 把 Site/Content/Reservation / AI 管理重新包回 `USE_MOCK ? mock : http`  
- 为 AI 制造假成功冒充 LIVE  
- 把无 consumer 的 `aiMock` 重新接到页面  
