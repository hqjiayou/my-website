# CURRENT_STATE — 全栈当前事实

```yaml
artifact_type: SPEC
status: CURRENT
owner: platform
last_verified: 2026-10-10
source_of_truth: services/website-services + apps/admin-web + apps/site-web（代码优先）
supersedes:
  - docs/website-services/总览/当前功能完成度总览.md
  - docs/audit/FULL_FUNCTION_IMPLEMENTATION_EXECUTION_REPORT.md（作为事实来源）
```

> 禁止使用无范围的裸 `COMPLETE` / `DONE` / `PASS`。历史 Execution Report 的 COMPLETE 声称已被 Acceptance Audit 部分推翻；以本文 + 代码为准。

---

## 一句话

中央控制的网站集合：后台总控 `admin-web`；Java `website-services`（`gateway` 统一入口，`auth` 签发 RS256 JWT，`admin` Resource Server + 后台业务）；Python `agent-services` 做 AI 执行。`admin-web` 主纵向域与站点 AI 管理已 FORCE REAL；访客前台 v1 已落地（`apps/site-web` generic 单应用＋公开只读 API，见 site-web CURRENT_STATE）；AI LIVE（无 `AI_MODEL`）/ Browser E2E 未达产品完成；Auth XFF 已做 gateway `trusted-proxies` 缓解，**仍缺集成断言**（PARTIALLY_RESOLVED）。

---

## 模块状态

| 域 | 状态 | 说明 |
|----|------|------|
| **Admin Web** | PARTIAL（主域 + 运营聚合 + 站点 AI 管理 FORCE REAL；概览指标由 REAL API 推导；占位页） | 见 [`../admin-web/CURRENT_STATE.md`](../admin-web/CURRENT_STATE.md) |
| **Site Web** | v1 IN_TREE（公开只读）/ NOT_IMPLEMENTED（auth） | `apps/site-web` 按 siteCode 渲染公开内容（仅 PUBLISHED）；Site Auth 前端未接（登录在 `apps/agent-portal`） |
| **Backend (website-services)** | PARTIAL（主业务 COMPLETE_V1；安全门 PARTIALLY_RESOLVED） | 见 [`../website-services/CURRENT_STATE.md`](../website-services/CURRENT_STATE.md) |
| **Database** | COMPLETE_V1（Flyway 以 `infra/.../db/migration` 为准，当前至 V21） | 以代码为准 |
| **Auth Admin** | PARTIAL | 登录/刷新/登出/注册门控 REAL；XFF：gateway trusted-proxies 已配置，缺集成测试 |
| **Auth Site** | BACKEND_COMPLETE | login/refresh/logout/me + aud 隔离；**frontend NOT_IMPLEMENTED** |
| **RBAC** | COMPLETE_V1 | roles/permissions/me/menus 读；Method Security |
| **Menu write** | DEFERRED_BY_DESIGN | 只读已落地；勿平行架构 |
| **Site Core** | COMPLETE_V1 | CRUD/status/乐观锁/软删；runtime-status 非 Core |
| **Template** | COMPLETE_V1（目录+status） | 本体 CRUD = DEFERRED_BY_DESIGN |
| **Appearance** | COMPLETE_V1 | 字段：`primaryColor` / `fontStyle` / `buttonStyle` / `heroLayout`（**不是** heroStyle / logoPlaceholder） |
| **Media** | COMPLETE_V1 | 服务端 object key；MIME；站点归属 |
| **Content** | COMPLETE_V1 | CMS CRUD/status |
| **Reservation** | COMPLETE_V1_ADMIN_SCOPE | 状态：`NEW` / `CONFIRMED` / `DONE` / `CANCELLED`（**不是** COMPLETED）；无公开预约/冲突引擎 |
| **AI** | PARTIAL_ADMIN_SCOPE；LIVE 仍 EXTERNAL_BLOCKED（无 Provider） | admin Prompt/KB/会话/预览 FORCE REAL；平台模型入口在 Java `ai_model_entry`（后台「模型入口」维护，响应不含 key；启用的默认入口随对话注入 Python，没有则回退进程 `AI_MODEL`）；site-web 聊天 NOT_IMPLEMENTED；两边都没有模型时诚实 503。Agent 平台（无业务工具）：每次对话由 `assemble_harness` 把平台 background、护栏、生效的运行上限、技能正文、函数工具说明和 MCP 服务器名送进系统提示，并按历史字符预算裁剪送模记忆（不写 URL，不改写下一轮）；`GET /internal/v1/health` 的 `harness` 只报告这三个平台文件是否存在，`provider` 仍是进程环境模型；MCP（配置驱动，`transport` 仅 http/sse，深检 `GET /internal/v1/mcp/ready`，连接失败 `MCP_UNAVAILABLE`）/ Skills（目录 + 预览可选）/ Todo（站点会话 V20 持久化、预览前端回传）已端到端接通；运行预算（Python `AGENT_BUDGET_EXCEEDED` → Java `ErrorCode` 1008 / HTTP 422）/ 工具超时 / 历史字符预算 / Fallback 模型（仅进程 `AI_MODEL` 链，入口调用不挂 fallback）/ Agent OTel / 运行轨迹（V21 `site_ai_message.agent_meta`，后台会话可见）/ 后台“运行时”面板，见 Agent 设计 V1 §19–§24 |
| **Mock 策略** | admin-web 无 mock 数据源；`VITE_USE_MOCK` 不再生效 | 见 [`../rules/MOCK_POLICY.md`](../rules/MOCK_POLICY.md) |
| **Testing** | Vertical/Contract 有；Browser E2E = **NOT_IMPLEMENTED** | 禁止称 MockMvc/build 为 Browser E2E |
| **External** | PostgreSQL / Redis /（可选 MinIO）/ AI Provider | AI Provider 缺失 → 诚实 503 |

---

## 安全与未决（指针）

详细描述不在此重复维护。见 [`ROADMAP.md`](./ROADMAP.md) 与 **GitHub Issues**（权威 Tracker）。

| 项 | 状态 | Tracking |
|----|------|----------|
| Auth trusted client IP / XFF | **PARTIALLY_RESOLVED**（配置已加；集成断言 / 暴露面硬化 STILL_OPEN） | [#1](https://github.com/hqjiayou/my-website/issues/1) |
| Browser E2E golden paths | **NOT_IMPLEMENTED** | [#2](https://github.com/hqjiayou/my-website/issues/2) |
| site-web authentication integration | FE **NOT_IMPLEMENTED**（BE BACKEND_COMPLETE） | [#3](https://github.com/hqjiayou/my-website/issues/3) |
| Soft-delete `deleted_at` consistency | **RESOLVED**（Site / Appearance / User 删除用例已写 `deleted_at`；Role / 绑定仍只 `deleteById`，不在 #4 范围） | [#4](https://github.com/hqjiayou/my-website/issues/4) |
| AI LIVE | **EXTERNAL_BLOCKED**（无 `AI_MODEL`）；admin 管理页已 REAL | [#5](https://github.com/hqjiayou/my-website/issues/5) |
| Site runtime-status REAL | **DEFERRED**（无独立端点；由现有 REAL API 推导，访问量 / 当日 AI 会话为 null） | — |
| Admin user mutation error feedback | **RESOLVED（文档漂移）** — 全局 `mutations.onError`→`handleApiError` | — |
| Orphan mocks / dead userApi | **RESOLVED in worktree**（待与 FORCE REAL WIP 同交；非 Tracker） | — |
| Menu write | **DEFERRED_BY_DESIGN** | — |

---

## 相关 ADR

- [`ADR-0001`](../decisions/ADR-0001-core-domains-force-real-api.md) — 核心域强制 REAL API
- [`ADR-0002`](../decisions/ADR-0002-admin-site-jwt-audience-isolation.md) — Admin/Site JWT audience 隔离
- [`ADR-0003`](../decisions/ADR-0003-media-server-generated-object-key.md) — Media object key 服务端生成
- [`ADR-0004`](../decisions/ADR-0004-ai-must-fail-honestly-without-provider.md) — AI 无 Provider 诚实失败
- [`ADR-0005`](../decisions/ADR-0005-java-business-truth-python-ai-execution.md) — Java 持有业务真相，Python 只做 AI 执行

---

## 历史材料

2026-08 Full Function Implementation 的 Instruction / Execution / Acceptance 在：

`docs/implementation-history/2026-08-full-function-implementation/`

**不得**再当作当前完成度来源。Acceptance 结论 `NOT ACCEPTED`（HIGH F-ACC-01）保留为历史事实。
