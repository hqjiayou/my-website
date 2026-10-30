# SYSTEM_ARCHITECTURE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-28
```

## 运行拓扑

```text
Browser
  ├─ admin-web     ──► gateway :8080 ──► auth :8083      (/api/auth/**)
  └─ agent-portal  ──► gateway :8080 ──► admin :8081     (/api/admin/**)
                                   └──► agent-app :8082 (/api/agent/**)
```

- **gateway**：路由 / CORS / RequestId；**不做业务鉴权**。`trusted-proxies` 为 `(?!)`，出站 `X-Forwarded-For` 由 `ClientIpGatewayFilter` 写成 TCP 对端（Issue #136），auth 限流读其最后一段。
- **auth**：唯一 JWT 签发方（RS256 + JWKS）；Admin / Agent 会话与 Cookie、audience 隔离（ADR-0002，audience 现为 ADMIN / AGENT）。
- **admin**：OAuth2 Resource Server + RBAC + 后台业务 API。
- **agent-app**（原 site-app）：站点业务 API（含 Agent AI 代理路径，`/api/agent/**`）；不签发 JWT。站点用户端是 `apps/agent-portal`（浏览器入口）；公开站 `apps/site-web` 已于 2026-10-28 移除。
- **common / infra**：契约与基础设施（含 Flyway 宿主）。
- **agent-services**：Python 只做 AI 执行；无 Provider 时诚实失败。业务真相在 Java。

## 规格（不在本文重复）

- Maven 依赖与模块说明：[`../website-services/ARCHITECTURE.md`](../website-services/ARCHITECTURE.md) 与 [`../../services/website-services/README.md`](../../services/website-services/README.md)
- 单模块文件怎么放：[`.cursor/rules/backend-structure.mdc`](../../.cursor/rules/backend-structure.mdc)（原《总览》《设计文档》长文已于 2026-10-29 移除，见 git 历史）
