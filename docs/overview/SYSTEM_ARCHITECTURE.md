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

- **gateway**：路由 / CORS / RequestId；**不做业务鉴权**。`trusted-proxies` 已配置（dev 默认 `.*` + for-append；prod profile 强制由环境变量收窄），XFF 信任边界已有集成断言（PR #112），auth 限流读其最后一段。
- **auth**：唯一 JWT 签发方（RS256 + JWKS）；Admin / Agent 会话与 Cookie、audience 隔离（ADR-0002，audience 现为 ADMIN / AGENT）。
- **admin**：OAuth2 Resource Server + RBAC + 后台业务 API。
- **agent-app**（原 site-app）：站点业务 API（含 Agent AI 代理路径，`/api/agent/**`）；不签发 JWT。站点用户端是 `apps/agent-portal`（浏览器入口）；公开站 `apps/site-web` 已于 2026-10-28 移除。
- **common / infra**：契约与基础设施（含 Flyway 宿主）。
- **agent-services**：Python 只做 AI 执行；无 Provider 时诚实失败。业务真相在 Java。

## 规格（不在本文重复）

- Maven 依赖：[`../website-services/总览/模块职责与依赖边界.md`](../website-services/总览/模块职责与依赖边界.md)
- 模块说明：[`../website-services/设计文档/基础架构/架构设计.md`](../website-services/设计文档/基础架构/架构设计.md)
- 单模块文件怎么放：[`../website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](../website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)
