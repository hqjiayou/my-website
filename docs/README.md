# docs

```yaml
artifact_type: REFERENCE
status: CURRENT
owner: docs-governance
last_verified: 2026-10-29
```

代码、Flyway、测试优先于文档。未完成的工作只记在 GitHub Issues。

文档只有三格。先选格，再打开那一格里的**一篇**正文。不要沿「总览 → 架构说明 → 架构设计 → 现状」往下翻，那些重复入口已经收成跳转。

| 格 | 回答 | 不回答 |
|----|------|--------|
| **现在** | 系统今天是什么、做到哪、还没做什么 | 为什么这样设计、某次阶段怎么落地 |
| **规格** | 写代码时必须遵守的结构、契约、跑法 | 完成度、历史验收是否 PASS |
| **历史** | 某次实施或审查当时写了什么 | 现在做完没有 |

---

## 现在

每行只有一篇正文。

| 问题 | 正文 |
|------|------|
| 全栈做到哪 | [`overview/CURRENT_STATE.md`](./overview/CURRENT_STATE.md) |
| 功能 × 端（后端 / 库 / admin-web / agent-portal） | [`overview/FEATURE_MATRIX.md`](./overview/FEATURE_MATRIX.md) |
| 进程怎么连（gateway、auth、admin、agent-app、agent-services） | [`overview/SYSTEM_ARCHITECTURE.md`](./overview/SYSTEM_ARCHITECTURE.md) |
| 还没做 / 明确延期 | [`overview/ROADMAP.md`](./overview/ROADMAP.md)（只有 Issue 号；细节在 GitHub） |
| 管理后台做到哪 | [`admin-web/CURRENT_STATE.md`](./admin-web/CURRENT_STATE.md) |
| 站点用户端做到哪 | 全栈 [`overview/CURRENT_STATE.md`](./overview/CURRENT_STATE.md) 的 AI／Admin Web 行（`apps/agent-portal` 无独立 CURRENT_STATE 分册） |
| Java 后端做到哪 | [`website-services/CURRENT_STATE.md`](./website-services/CURRENT_STATE.md) |

后端域摘要是这格的分册，不另起完成度：[`AUTH.md`](./website-services/AUTH.md)、[`RBAC.md`](./website-services/RBAC.md)、[`AGENT.md`](./website-services/AGENT.md)、[`MEDIA.md`](./website-services/MEDIA.md)、[`CONTENT.md`](./website-services/CONTENT.md)（Reservation 已于 2026-10-27 删除，其规格文档已于 2026-10-29 随历史文档一并移除，见 git 历史）、[`PERSISTENCE.md`](./website-services/PERSISTENCE.md)。

原 `总览/`、`设计文档/`、`audit/`、`implementation-history/`、`archive/`、`analysis/` 等历史文档已于 2026-10-29 整体移除（见本页「历史」节）；需要查当时文字看 git 历史，不要从旧总览/旧设计读「现在」。

---

## 规格

按功能分类。同一功能不要再打开同级的第二篇「说明」。

### 平台与模块边界

| 功能 | 正文 | 说明 |
|------|------|------|
| Maven 谁依赖谁 | [`services/website-services/README.md`](../services/website-services/README.md) | 模块树与依赖图以该 README 与各 `pom.xml` 为准；原《模块职责与依赖边界》已于 2026-10-29 移除，见 git 历史 |
| 各 Java 模块做什么 | [`website-services/ARCHITECTURE.md`](./website-services/ARCHITECTURE.md) | 原《架构设计》长文已于 2026-10-29 移除，见 git 历史；完成度仍回「现在」 |
| 单模块目录、文件名与包名 | [`.cursor/rules/backend-structure.mdc`](../.cursor/rules/backend-structure.mdc) | Java 新文件先看这条规则；原《单模块文件结构与AI开发约束规则 V1》《后端包结构与命名规范 V1》已于 2026-10-29 移除，见 git 历史与现行代码包结构 |
| 管理端目录、路由、样式 | [`admin-web/开发规范.md`](./admin-web/开发规范.md) | 栈摘要在 [`admin-web/ARCHITECTURE.md`](./admin-web/ARCHITECTURE.md) |

### 认证、权限、数据、AI

| 功能 | 正文 |
|------|------|
| 登录 / JWT / Cookie | [`contracts/AUTH_CONTRACT.md`](./contracts/AUTH_CONTRACT.md)。决策：[`ADR-0002`](./decisions/ADR-0002-admin-site-jwt-audience-isolation.md) |
| 后台 API 路径 | [`contracts/ADMIN_API_CONTRACT.md`](./contracts/ADMIN_API_CONTRACT.md) |
| 站点（Agent）API | [`contracts/AGENT_API_CONTRACT.md`](./contracts/AGENT_API_CONTRACT.md) |
| 状态枚举 | [`contracts/STATUS_ENUMS.md`](./contracts/STATUS_ENUMS.md) |
| 错误体 | [`contracts/ERROR_CONTRACT.md`](./contracts/ERROR_CONTRACT.md) |
| 分页 | [`contracts/PAGINATION_CONTRACT.md`](./contracts/PAGINATION_CONTRACT.md) |
| 角色与菜单规则 | [`website-services/RBAC.md`](./website-services/RBAC.md) | 菜单写仍延期；原阶段 5A/5E 长文已于 2026-10-29 移除，见 git 历史 |
| 表结构字典 | [`website-services/数据字典/`](./website-services/数据字典/)。与 Flyway 冲突时以 Flyway 为准。`docs/数据字典/README.md` 只是跳转 |
| 持久层写法与现行表 | [`website-services/PERSISTENCE.md`](./website-services/PERSISTENCE.md)、[`website-services/数据字典/`](./website-services/数据字典/) | 表以 Flyway 为准；原《Java持久层设计 V1》《数据库模型设计 V1》已于 2026-10-29 移除，见 git 历史 |
| AI 执行（Python 不持有业务真相） | [`ADR-0004`](./decisions/ADR-0004-ai-must-fail-honestly-without-provider.md)、[`ADR-0005`](./decisions/ADR-0005-java-business-truth-python-ai-execution.md) | 原《Agent 服务技术选型与架构设计 V1》已于 2026-10-29 移除，见 git 历史 |
| 核心域禁止 mock 回退 | [`ADR-0001`](./decisions/ADR-0001-core-domains-force-real-api.md)、[`rules/MOCK_POLICY.md`](./rules/MOCK_POLICY.md) |
| 媒体 object key | [`ADR-0003`](./decisions/ADR-0003-media-server-generated-object-key.md) |

原 `设计文档/` 的阶段 4/5 落地记录已于 2026-10-29 移除；约束以契约、ADR 与各域摘要为准，不要再把 git 历史里的阶段文当成第二份现状。

### 怎么跑、怎么改文档

| 功能 | 正文 |
|------|------|
| 本地拉起 | [`operations/LOCAL_DEVELOPMENT.md`](./operations/LOCAL_DEVELOPMENT.md) |
| 环境变量 | [`operations/ENVIRONMENT_VARIABLES.md`](./operations/ENVIRONMENT_VARIABLES.md) |
| 部署 | [`operations/DEPLOYMENT.md`](./operations/DEPLOYMENT.md) |
| 安全边界 | [`operations/SECURITY.md`](./operations/SECURITY.md) |
| WSL / Docker 基础设施 | [`operations/LOCAL_DEVELOPMENT.md`](./operations/LOCAL_DEVELOPMENT.md) | 原 WSL/Docker 专项文档已于 2026-10-29 移除；启动命令以本地开发那篇为准 |
| 测试策略 | [`testing/TEST_STRATEGY.md`](./testing/TEST_STRATEGY.md)。浏览器 E2E 已落地（PR #111），细则见 [`testing/E2E_GOLDEN_PATHS.md`](./testing/E2E_GOLDEN_PATHS.md) |
| 文档状态词、事实优先级、废弃 | [`rules/DOCUMENT_LIFECYCLE.md`](./rules/DOCUMENT_LIFECYCLE.md)、[`rules/SOURCE_OF_TRUTH.md`](./rules/SOURCE_OF_TRUTH.md)、[`rules/DEPRECATION_POLICY.md`](./rules/DEPRECATION_POLICY.md)、[`rules/API_CHANGE_POLICY.md`](./rules/API_CHANGE_POLICY.md) |
| 可复用任务模板 | [`workflows/instructions/templates/`](./workflows/instructions/templates/) |

管理端分册（不重复完成度）：[`ROUTING.md`](./admin-web/ROUTING.md)、[`API_INTEGRATION.md`](./admin-web/API_INTEGRATION.md)、[`STATE_AND_QUERY.md`](./admin-web/STATE_AND_QUERY.md)、[`TESTING.md`](./admin-web/TESTING.md)。站点用户端（`apps/agent-portal`）无 docs 分册，现状回全栈 CURRENT_STATE、契约回 `contracts/`。

---

## 历史

原历史目录已于 **2026-10-29 整体移除**：`implementation-history/`（2026-08 全功能实施包）、`audit/`（知识治理快照）、`archive/`（含 RESERVATION 规格与联调/审查记录）、`analysis/`（仓库调查）、`website-services/` 下的 `设计文档/`、`总览/`、`数据文档/`、`规则/`、`归档/`。共 63 个文件。需要查当时文字，用 git 历史（本 PR 之前的提交）。

只剩一处仍在仓内的历史目录：

| 目录 | 里面是什么 |
|------|------------|
| [`workflows/instructions/archive/`](./workflows/instructions/archive/) | 用过的一次性指令 |

---

## 四个代码项目

| 项目 | 代码 | 现在看 | 规格看 |
|------|------|--------|--------|
| admin-web | `apps/admin-web` | `admin-web/CURRENT_STATE.md` | `admin-web/开发规范.md` |
| agent-portal | `apps/agent-portal`（站点用户端；`apps/site-web` 已于 2026-10-28 移除） | 全栈 `overview/CURRENT_STATE.md` | `contracts/AGENT_API_CONTRACT.md`、`contracts/AUTH_CONTRACT.md` |
| website-services | `services/website-services` | `website-services/CURRENT_STATE.md` | 上表「平台与模块边界」+ `contracts/` |
| agent-services | `services/agent-services` | 全栈 `CURRENT_STATE` 的 AI 行 | ADR-0004、ADR-0005 |

没有 `docs/agent-services/`。不要再为它单开一层目录。

## 已移除的阶段原文与历史设计

原「还停在阶段原文里的设计」（`设计文档/` 阶段 4/5 原文、AI 设计长文等）已于 2026-10-29 随历史文档整体移除。现行结论一律以本页「现在」「规格」两格与各域摘要（[`website-services/AUTH.md`](./website-services/AUTH.md)、[`website-services/RBAC.md`](./website-services/RBAC.md) 等）为准；查原文看 git 历史。

`rules/` 是写文档和改 API 的规矩，不是系统事实，这次没有改条文。
