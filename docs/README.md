# docs

```yaml
artifact_type: REFERENCE
status: CURRENT
owner: docs-governance
last_verified: 2026-10-10
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
| 功能 × 端（后端 / 库 / admin-web / site-web） | [`overview/FEATURE_MATRIX.md`](./overview/FEATURE_MATRIX.md) |
| 进程怎么连（gateway、auth、admin、site-app、agent） | [`overview/SYSTEM_ARCHITECTURE.md`](./overview/SYSTEM_ARCHITECTURE.md) |
| 还没做 / 明确延期 | [`overview/ROADMAP.md`](./overview/ROADMAP.md)（只有 Issue 号；细节在 GitHub） |
| 管理后台做到哪 | [`admin-web/CURRENT_STATE.md`](./admin-web/CURRENT_STATE.md) |
| 前台站点做到哪 | [`site-web/CURRENT_STATE.md`](./site-web/CURRENT_STATE.md) |
| Java 后端做到哪 | [`website-services/CURRENT_STATE.md`](./website-services/CURRENT_STATE.md) |

后端域摘要是这格的分册，不另起完成度：[`AUTH.md`](./website-services/AUTH.md)、[`RBAC.md`](./website-services/RBAC.md)、[`SITE.md`](./website-services/SITE.md)、[`MEDIA.md`](./website-services/MEDIA.md)、[`CONTENT.md`](./website-services/CONTENT.md)、[`RESERVATION.md`](./website-services/RESERVATION.md)、[`PERSISTENCE.md`](./website-services/PERSISTENCE.md)。

不要从这些文件读「现在」：`总览/当前功能完成度总览.md`、`总览/后端开发路线图与阶段状态.md`、`总览/后端整体架构总览.md`、`设计文档/基础架构/整体架构说明.md`、`audit/`、`implementation-history/`、`archive/`。它们只跳转或保留当时文字。

---

## 规格

按功能分类。同一功能不要再打开同级的第二篇「说明」。

### 平台与模块边界

| 功能 | 正文 | 说明 |
|------|------|------|
| Maven 谁依赖谁 | [`website-services/总览/模块职责与依赖边界.md`](./website-services/总览/模块职责与依赖边界.md) | 依赖表以这份为准 |
| 各 Java 模块做什么 | [`website-services/设计文档/基础架构/架构设计.md`](./website-services/设计文档/基础架构/架构设计.md) | 长文；完成度仍回「现在」 |
| 单模块目录与文件名 | [`website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](./website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md) | Java 新文件先看这篇 |
| 包名 | [`website-services/设计文档/基础架构/后端包结构与命名规范V1.md`](./website-services/设计文档/基础架构/后端包结构与命名规范V1.md) | |
| 管理端目录、路由、样式 | [`admin-web/开发规范.md`](./admin-web/开发规范.md) | 栈摘要在 [`admin-web/ARCHITECTURE.md`](./admin-web/ARCHITECTURE.md) |

### 认证、权限、数据、AI

| 功能 | 正文 |
|------|------|
| 登录 / JWT / Cookie | [`contracts/AUTH_CONTRACT.md`](./contracts/AUTH_CONTRACT.md)。决策：[`ADR-0002`](./decisions/ADR-0002-admin-site-jwt-audience-isolation.md) |
| 后台 API 路径 | [`contracts/ADMIN_API_CONTRACT.md`](./contracts/ADMIN_API_CONTRACT.md) |
| 前台 API | [`contracts/SITE_API_CONTRACT.md`](./contracts/SITE_API_CONTRACT.md) |
| 状态枚举 | [`contracts/STATUS_ENUMS.md`](./contracts/STATUS_ENUMS.md) |
| 错误体 | [`contracts/ERROR_CONTRACT.md`](./contracts/ERROR_CONTRACT.md) |
| 分页 | [`contracts/PAGINATION_CONTRACT.md`](./contracts/PAGINATION_CONTRACT.md) |
| 角色与菜单规则 | [`website-services/设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md`](./website-services/设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md)、[`阶段5E-菜单权限与导航说明.md`](./website-services/设计文档/权限与后台管理/阶段5E-菜单权限与导航说明.md)。菜单写仍延期 |
| 表结构字典 | [`website-services/数据字典/`](./website-services/数据字典/)。与 Flyway 冲突时以 Flyway 为准。`docs/数据字典/README.md` 只是跳转 |
| 持久层写法与现行表 | [`website-services/设计文档/数据与持久层/Java持久层设计V1.md`](./website-services/设计文档/数据与持久层/Java持久层设计V1.md)。表以 Flyway 为准；《数据库模型设计 V1》文首「现行模型」是对照结论，第 4.4 节以后的旧表名不要当现表 |
| 接口消息语言 | [`website-services/设计文档/认证与安全/后端多语言支持策略设计V1.md`](./website-services/设计文档/认证与安全/后端多语言支持策略设计V1.md) |
| AI 执行（Python 不持有业务真相） | [`ADR-0004`](./decisions/ADR-0004-ai-must-fail-honestly-without-provider.md)、[`ADR-0005`](./decisions/ADR-0005-java-business-truth-python-ai-execution.md)、[`Agent服务技术选型与架构设计V1.md`](./website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md) |
| 核心域禁止 mock 回退 | [`ADR-0001`](./decisions/ADR-0001-core-domains-force-real-api.md)、[`rules/MOCK_POLICY.md`](./rules/MOCK_POLICY.md) |
| 媒体 object key | [`ADR-0003`](./decisions/ADR-0003-media-server-generated-object-key.md) |

`设计文档/认证与安全/阶段4*.md`、`设计文档/权限与后台管理/阶段5B–5D*.md` 是落地记录。约束已经收进上面的契约、ADR 和 5A/5E 时，不要再把阶段文当成第二份现状。

### 怎么跑、怎么改文档

| 功能 | 正文 |
|------|------|
| 本地拉起 | [`operations/LOCAL_DEVELOPMENT.md`](./operations/LOCAL_DEVELOPMENT.md) |
| 环境变量 | [`operations/ENVIRONMENT_VARIABLES.md`](./operations/ENVIRONMENT_VARIABLES.md) |
| 部署 | [`operations/DEPLOYMENT.md`](./operations/DEPLOYMENT.md) |
| 安全边界 | [`operations/SECURITY.md`](./operations/SECURITY.md) |
| WSL / Docker 只谈基础设施 | [`website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](./website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。启动命令以本地开发那篇为准 |
| 测试策略 | [`testing/TEST_STRATEGY.md`](./testing/TEST_STRATEGY.md)。浏览器 E2E 未做，见 ROADMAP |
| 文档状态词、事实优先级、废弃 | [`rules/DOCUMENT_LIFECYCLE.md`](./rules/DOCUMENT_LIFECYCLE.md)、[`rules/SOURCE_OF_TRUTH.md`](./rules/SOURCE_OF_TRUTH.md)、[`rules/DEPRECATION_POLICY.md`](./rules/DEPRECATION_POLICY.md)、[`rules/API_CHANGE_POLICY.md`](./rules/API_CHANGE_POLICY.md) |
| 可复用任务模板 | [`workflows/instructions/templates/`](./workflows/instructions/templates/) |

管理端分册（不重复完成度）：[`ROUTING.md`](./admin-web/ROUTING.md)、[`API_INTEGRATION.md`](./admin-web/API_INTEGRATION.md)、[`STATE_AND_QUERY.md`](./admin-web/STATE_AND_QUERY.md)、[`TESTING.md`](./admin-web/TESTING.md)。前台分册：[`site-web/`](./site-web/)。

---

## 历史

只保留当时的文字，不改结论，也不用来判断现在。

| 目录 | 里面是什么 |
|------|------------|
| [`implementation-history/2026-08-full-function-implementation/`](./implementation-history/2026-08-full-function-implementation/) | 2026-08 那次全功能实施的指令、执行、验收 |
| [`audit/`](./audit/) | 2026-08-09 知识治理快照。见 [`audit/README.md`](./audit/README.md) |
| [`archive/`](./archive/) | 已替换的前台设计、后端联调与审查 |
| [`analysis/archive/`](./analysis/archive/) | 旧的仓库调查 |
| [`workflows/instructions/archive/`](./workflows/instructions/archive/) | 用过的一次性指令 |
| [`website-services/归档/`](./website-services/归档/) | 后端树内归档说明 |

---

## 四个代码项目

| 项目 | 代码 | 现在看 | 规格看 |
|------|------|--------|--------|
| admin-web | `apps/admin-web` | `admin-web/CURRENT_STATE.md` | `admin-web/开发规范.md` |
| site-web | `apps/site-web`（工作区无行业前台） | `site-web/CURRENT_STATE.md` | `contracts/SITE_API_CONTRACT.md`、`contracts/AUTH_CONTRACT.md` |
| website-services | `services/website-services` | `website-services/CURRENT_STATE.md` | 上表「平台与模块边界」+ `contracts/` |
| agent-services | `services/agent-services` | 全栈 `CURRENT_STATE` 的 AI 行 | ADR-0004、ADR-0005、Agent 设计 V1 |

没有 `docs/agent-services/`。不要再为它单开一层目录。

## 还停在阶段原文里的设计

这些文件保持当时的文字，现行结论在各自文首或域摘要里，不要从中间的「待开始 / 占位 / 下一阶段」读现状：

| 文件 | 现行结论写在 |
|------|----------------|
| `设计文档/认证与安全/阶段4A` 至 `4H` | 文首或 [`website-services/AUTH.md`](./website-services/AUTH.md) |
| `设计文档/权限与后台管理/阶段5B`–`5D` | [`website-services/RBAC.md`](./website-services/RBAC.md) 与 5A 文首 |
| `设计文档/AI/` 第 1、4、5、11 节 | 该文第 0 节 |
| `audit/`、`archive/`、`implementation-history/` | 历史，不改 |

`rules/` 是写文档和改 API 的规矩，不是系统事实，这次没有改条文。
