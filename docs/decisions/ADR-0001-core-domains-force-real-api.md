# ADR-0001 Core domains force REAL API

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

admin-web 曾用统一 `USE_MOCK` 门控业务 API，导致「页面有数据」被误判为后端完成。

## Decision

Site / Template / Appearance / Media / Content / Reservation（及 auth/rbac）客户端 **强制 REAL**，不受 `USE_MOCK` 切换。Mock 仅保留 AI 与明确未实现能力（如 runtime-status）。

## Consequences

- 开发环境无后端时这些页面会失败——这是诚实失败。  
- 禁止为统一开关把核心域重新包回 mock fallback。  
