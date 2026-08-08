# Agent 指引（my-website）

多站点平台 Monorepo。当前主要活跃面：`apps/admin-web`（管理后台）与 `services/website-services`（Java 后端：gateway / auth / admin / site / common / infra）。`site-web` 与 `agent-services` 仍为骨架或 mock，非默认主战场。

## Source of Truth 顺序

| 层次 | 权威 |
|------|------|
| **Current Reality** | 代码 + DB/Flyway → [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md) → [`docs/contracts/`](docs/contracts/) → [`docs/decisions/`](docs/decisions/)（ADR） |
| **Open Work** | **GitHub Issues**（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 号） |
| **High-level Direction** | ROADMAP |
| **Historical Evidence** | `docs/audit/` · Execution/Verification · `implementation-history/` · `archive/` · `analysis/archive/` — **不指导未来实施** |

## 默认阅读顺序（任务开始前）

1. 本文 + `.cursor/rules/`
2. [`docs/README.md`](docs/README.md)
3. [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)
4. 相关域 `CURRENT_STATE` / [`docs/contracts/`](docs/contracts/) / [`docs/decisions/`](docs/decisions/)
5. 未决工作：GitHub Issues（经 ROADMAP 指针）
6. **当前** Task Brief / Instruction（若有）

以下**默认不得**作为当前实现依据：`docs/implementation-history/`、`docs/audit/archive/`、`docs/analysis/archive/`、`docs/archive/`。

## 调查优先

修改前先读取与任务相关的**当前真实代码**。不以文件名、过期文档或历史印象猜测现状。文档与代码冲突时，以代码为准，并标明文档漂移。

## 修改边界

- 只改当前任务必要范围；禁止无关重构与顺手修债。
- 新增内容必须与模块 / 目录 / 文件职责一致。
- 发现无关问题 → 记录即可，不自动扩大范围。

## 模块职责（摘要）

| 区域 | 职责 |
|------|------|
| gateway | 路由 / CORS / RequestId；不做业务与鉴权业务 |
| auth | 登录 / 刷新 / JWT 签发 / JWKS |
| admin | 后台 API、RBAC、Resource Server 验签 |
| common / infra | 横切契约与基础设施；不放单模块业务 |
| admin-web | 管理后台 UI；经 gateway 调 `/api/**` |

## 不过度拆分

职责清楚优先。实现应在职责清楚前提下相对集中。不为“解耦”制造只有转发逻辑的小文件或模糊 Helper / Manager / Processor。

## 必要注释

非显然的边界、安全、兼容、特殊业务逻辑写清楚。禁止逐行废话注释。

## Cursor Rules

| Rule | 作用 |
|------|------|
| [`.cursor/rules/project.mdc`](.cursor/rules/project.mdc) | 项目级（alwaysApply） |
| [`.cursor/rules/backend-structure.mdc`](.cursor/rules/backend-structure.mdc) | `website-services` Java |
| [`.cursor/rules/admin-web.mdc`](.cursor/rules/admin-web.mdc) | `admin-web` 实现边界 |
| [`.cursor/rules/admin-web-router.mdc`](.cursor/rules/admin-web-router.mdc) | router 目录 |
| [`.cursor/rules/admin-web-styles.mdc`](.cursor/rules/admin-web-styles.mdc) | 全局样式分层 |

## 后端权威文档

修改 `services/website-services/` 前阅读：

1. [`docs/website-services/CURRENT_STATE.md`](docs/website-services/CURRENT_STATE.md)
2. [`单模块文件结构与AI开发约束规则V1.md`](docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)（结构总纲，冲突时以此为准）
3. [`.cursor/rules/backend-structure.mdc`](.cursor/rules/backend-structure.mdc)
4. [`后端包结构与命名规范V1.md`](docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md)

## Menu / 5E 现状

**Menu 只读能力已存在**（如 `/api/admin/auth/me` 的 `menus`、`GET /api/admin/menus`，以及现有 Menu Service / Mapper / DTO）。**Menu 写操作尚未完成**。后续写操作应基于现有 Menu API / Service / Mapper / DTO 与 permission 模型扩展，不另起平行实现。
