# Agent 指引（my-website）

中央控制的网站集合 Monorepo。一个后台总控 `apps/admin-web`，一个 Java 服务 `services/website-services`（gateway / auth / admin / site / common / infra / agent），一个 Python AI `services/agent-services`（internal only，经 Java `HttpAgentClient` 调用），一个访客前台 `apps/site-web`（v1：按 siteCode 渲染公开内容，只读）。Site Auth 后端已完成；site-web 未接登录（登录在 `apps/portal`）。

## Source of Truth 顺序

| 层次 | 权威 |
|------|------|
| **Current Reality** | 代码 + DB/Flyway → [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md) → 域 `CURRENT_STATE` → [`docs/contracts/`](docs/contracts/) → [`docs/decisions/`](docs/decisions/)（ADR） |
| **Open Work** | **GitHub Issues**（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 号） |
| **High-level Direction** | ROADMAP |
| **Historical Evidence** | `docs/audit/` · Execution/Verification · `implementation-history/` · `archive/` · `analysis/archive/` — **不指导未来实施** |

## 默认阅读顺序（任务开始前）

1. 本文 + `.cursor/rules/`
2. [`docs/README.md`](docs/README.md)
3. [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)
4. 相关域 `CURRENT_STATE`
5. [`docs/contracts/`](docs/contracts/)，然后 [`docs/decisions/`](docs/decisions/)（ADR）
6. 未决工作：GitHub Issues（经 ROADMAP 指针）
7. **当前** Task Brief / Instruction（若有）

以下**默认不得**作为当前实现依据：`docs/implementation-history/`、`docs/audit/`、`docs/analysis/`、`docs/archive/`。文档先看 [`docs/README.md`](docs/README.md) 的三格（现在 / 规格 / 历史），不要沿总览和整体架构说明再往下找第二份现状。

## 模块职责（摘要）

| 区域 | 职责 |
|------|------|
| gateway | 路由 / CORS / RequestId；不做业务与鉴权业务 |
| auth | 登录 / 刷新 / JWT 签发 / JWKS |
| admin | 后台 API、RBAC、Resource Server 验签；**AI 业务主数据**（Prompt/KB/Conversation） |
| site-app | 站点业务 API；站点 AI chat / internal tools |
| agent | `HttpAgentClient` 与 Java↔Python 契约 DTO |
| agent-services | Python AI Execution（internal only，经 Java 调用） |
| common / infra | 横切契约与基础设施；不放单模块业务 |
| admin-web | 管理后台 UI；经 gateway 调 `/api/**` |

新建、引用和落点以 [`.cursor/rules/project.mdc`](.cursor/rules/project.mdc) 为准。

## Cursor Rules

| Rule | 作用 |
|------|------|
| [`.cursor/rules/project.mdc`](.cursor/rules/project.mdc) | 新建、引用与落点（alwaysApply） |
| [`.cursor/rules/backend-structure.mdc`](.cursor/rules/backend-structure.mdc) | `website-services` Java 与 Flyway SQL |
| [`.cursor/rules/agent-services.mdc`](.cursor/rules/agent-services.mdc) | `agent-services` Python AI Execution |
| [`.cursor/rules/ai-domain.mdc`](.cursor/rules/ai-domain.mdc) | AI 跨模块边界（含 agent 客户端与 `aiApi`） |
| [`.cursor/rules/admin-web.mdc`](.cursor/rules/admin-web.mdc) | `admin-web` 实现边界 |
| [`.cursor/rules/admin-web-router.mdc`](.cursor/rules/admin-web-router.mdc) | router 目录 |
| [`.cursor/rules/admin-web-styles.mdc`](.cursor/rules/admin-web-styles.mdc) | 全局样式分层 |
| [`.cursor/rules/docs.mdc`](.cursor/rules/docs.mdc) | 文档与 CURRENT_STATE 同步 |

索引与 Agent 协作流见 [`.cursor/README.md`](.cursor/README.md)。变更审查在 [`.cursor/agents/integration-reviewer.md`](.cursor/agents/integration-reviewer.md)。按域缺陷判定和功能完善盘点在 [`.cursor/agents/area-reviewer.md`](.cursor/agents/area-reviewer.md)。工作方式入口（`/dev`、`/fix`、`/hunt`、`/review`、`/audit`、`/pair`）在 [`.cursor/skills/`](.cursor/skills/)，闸门与续跑在 [`.cursor/hooks.json`](.cursor/hooks.json)，说明见 `.cursor/README.md`。

## Cursor Agents（Subagent 角色）

| Agent | 用途 |
|-------|------|
| `codebase-explorer` | 只读：定位代码、mock/real、调用链 |
| `architecture-planner` | 方案：边界、顺序、验收项（`/dev` 按验收项执行） |
| `admin-backend` | Java admin / Flyway / RBAC / Admin AI API |
| `admin-frontend` | admin-web UI / API 对接 / i18n |
| `agent-services` | Python runtime / SSE / RAG / MCP·Skills·Todo |
| `integration-reviewer` | 变更审查：单文件、逻辑、功能、模块 |
| `area-reviewer` | 按域：长期缺陷审查，或功能完善盘点 |

## 后端权威文档

修改 `services/website-services/` 前阅读：

1. [`docs/website-services/CURRENT_STATE.md`](docs/website-services/CURRENT_STATE.md)
2. [`单模块文件结构与AI开发约束规则V1.md`](docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)（结构总纲，冲突时以此为准）
3. [`.cursor/rules/backend-structure.mdc`](.cursor/rules/backend-structure.mdc)
4. [`后端包结构与命名规范V1.md`](docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md)

修改 `services/agent-services/` 前阅读：

1. [`docs/decisions/ADR-0004`](docs/decisions/ADR-0004-ai-must-fail-honestly-without-provider.md)、[`ADR-0005`](docs/decisions/ADR-0005-java-business-truth-python-ai-execution.md)
2. [`Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)
3. [`.cursor/rules/agent-services.mdc`](.cursor/rules/agent-services.mdc)

## Menu / 5E 现状

**Menu 只读能力已存在**（如 `/api/admin/auth/me` 的 `menus`、`GET /api/admin/menus`，以及现有 Menu Service / Mapper / DTO）。**Menu 写操作尚未完成**。后续写操作应基于现有 Menu API / Service / Mapper / DTO 与 permission 模型扩展，不另起平行实现。
