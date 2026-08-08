# ADR-0002 Admin / Site JWT audience isolation

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

同一 Auth 服务签发多类会话，需防止 Admin token 调用 Site 资源或反向使用。

## Decision

JWT 使用独立 audience / subject_type；Refresh Cookie 路径隔离；跨 audience 访问返回 401。

## Consequences

site-web 与 admin-web 不得共享 access token 存储约定；对接 site-web 时必须使用 SITE 认证端点。
