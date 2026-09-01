# Cursor 指令索引（my-website）

本目录为 Cursor Agent / Rules 的**项目级编排**，与根目录 [`AGENTS.md`](../AGENTS.md) 互补：`AGENTS.md` 是全仓 Source of Truth 与阅读顺序；此处是可复用的**角色分工与硬约束**。

## Rules（`.cursor/rules/`）

| 文件 | 作用域 | 说明 |
|------|--------|------|
| [`project.mdc`](rules/project.mdc) | 全仓 `alwaysApply` | 调查优先、修改边界、不过度拆分 |
| [`backend-structure.mdc`](rules/backend-structure.mdc) | `services/website-services/**/*.java` | Maven 模块、包树、Flyway |
| [`agent-services.mdc`](rules/agent-services.mdc) | `services/agent-services/**` | Python AI Execution（ADR-0004/0005） |
| [`ai-domain.mdc`](rules/ai-domain.mdc) | AI 相关 Java + admin-web `ai/**` | 跨模块 AI 归属与契约 |
| [`admin-web.mdc`](rules/admin-web.mdc) | `apps/admin-web/src/**` | API/mock、权限、i18n |
| [`admin-web-router.mdc`](rules/admin-web-router.mdc) | `router/**` | 路由表与 permission |
| [`admin-web-styles.mdc`](rules/admin-web-styles.mdc) | `assets/styles/**` | 全局样式三层 |
| [`docs.mdc`](rules/docs.mdc) | `docs/**` | CURRENT_STATE / 契约同步 |

## Agents（`.cursor/agents/`）

| Agent | 何时用 |
|-------|--------|
| [`codebase-explorer`](agents/codebase-explorer.md) | 只读盘点：实现 / mock / 调用链 |
| [`architecture-planner`](agents/architecture-planner.md) | 范围不清、要最小方案与验收标准 |
| [`admin-backend`](agents/admin-backend.md) | Java admin、Flyway、RBAC、Admin AI API |
| [`admin-frontend`](agents/admin-frontend.md) | admin-web UI、mock→real、i18n |
| [`agent-services`](agents/agent-services.md) | Python runtime、SSE、RAG、MCP/Skills/Todo |
| [`integration-reviewer`](agents/integration-reviewer.md) | 合并前契约 / 边界 / Flyway / mock 审查 |

## 推荐协作流

```
需求不清 → codebase-explorer（事实）→ architecture-planner（方案）
                ↓
    admin-backend / admin-frontend / agent-services（实现）
                ↓
         integration-reviewer（审查）
```

## AI 域快速指针

- **业务真相**：Java admin + site-app + PostgreSQL  
- **执行层**：`services/agent-services`（internal only）  
- **契约**：`services/website-services/agent` 的 `HttpAgentClient`  
- **前端**：`aiApi` FORCE REAL；见 [`docs/rules/MOCK_POLICY.md`](../docs/rules/MOCK_POLICY.md)

## 修改本目录时

- Rule 单文件宜 **简洁可执行**（约 50 行内）；一文件一关切。
- Agent 描述（frontmatter `description`）决定 Cursor 何时自动选用；写清 **Scope / Forbidden / Trigger**。
- 与 `AGENTS.md`、ADR、CURRENT_STATE 冲突时，以代码与 ADR 为准，并更新漂移文档。
