# SYSTEM_ARCHITECTURE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

## 运行拓扑

```text
Browser
  ├─ admin-web  ──► gateway :8080 ──► auth :8083   (/api/auth/**)
  │                              ├──► admin :8081  (/api/admin/**)
  │                              └──► site  :8082  (/api/site/**)
  └─ site-web (骨架) ──►（目标）gateway /api/site/** + /api/auth/site/**
```

- **gateway**：路由 / CORS / RequestId；**不做业务鉴权**。应配置可信代理以使 XFF 可被 auth 用于限流（见安全 Issue）。
- **auth**：唯一 JWT 签发方（RS256 + JWKS）；Admin / Site 会话与 Cookie 隔离。
- **admin**：OAuth2 Resource Server + RBAC + 后台业务 API。
- **site-app**：前台业务（含 Site AI 代理路径）；不签发 JWT。
- **common / infra**：契约与基础设施（含 Flyway 宿主）。
- **agent-services**：AI 编排；无 Provider 时诚实失败。

## 权威细文

- 后端模块边界：[`../website-services/设计文档/基础架构/架构设计.md`](../website-services/设计文档/基础架构/架构设计.md)
- 单模块包树：[`../website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](../website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)
- 仓库级说明：[`../website-services/设计文档/基础架构/整体架构说明.md`](../website-services/设计文档/基础架构/整体架构说明.md)
