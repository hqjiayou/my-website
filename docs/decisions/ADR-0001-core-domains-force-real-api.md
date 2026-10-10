# ADR-0001 Core domains force REAL API

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

admin-web 曾用统一 `USE_MOCK` 门控业务 API，导致「页面有数据」被误判为后端完成。

## Decision

Site / Template / Appearance / Media / Content / Reservation（及 auth/rbac）以及站点 AI（Prompt / KB / Conversation / Preview）客户端 **强制 REAL**。admin-web 不再用 `USE_MOCK` 切换数据源；站点概览由真实 API 推导，不另做 mock。

## Consequences

- 开发环境无后端时这些页面会失败——这是诚实失败。  
- 禁止为统一开关把核心域重新包回 mock fallback。

## 后续事实（2026-10-10）

admin-web 已删除 mock 数据源，`VITE_USE_MOCK` 无消费者。站点概览指标由现有真实 API 推导（访问量 / 当日 AI 会话为 null），不再用 mock 填充 runtime-status。上面的「强制真实 API、禁止 mock 回退」仍然有效。  
