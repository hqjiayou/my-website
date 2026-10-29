# CURRENT_STATE — 全栈当前事实

```yaml
artifact_type: SPEC
status: CURRENT
owner: platform
last_verified: 2026-10-28
source_of_truth: services/website-services + apps/admin-web + apps/agent-portal（代码优先）
supersedes:
  - docs/website-services/总览/当前功能完成度总览.md（已于 2026-10-29 移除，见 git 历史）
  - docs/audit/FULL_FUNCTION_IMPLEMENTATION_EXECUTION_REPORT.md（已于 2026-10-29 移除，见 git 历史）
```

> 禁止使用无范围的裸 `COMPLETE` / `DONE` / `PASS`。历史 Execution Report 的 COMPLETE 声称已被 Acceptance Audit 部分推翻；以本文 + 代码为准。

---


> **2026-10-28 架构变更（site→agent 深度改名）**：原 `site` 服务与 `agent` client 库合并为单一 `agent` 模块（`agent-core`＋`agent-app`，包 `com.mywebsite.agent`）；API 前缀 `/api/agent/**`、auth 站点登录 `/api/auth/agent/**`、admin 站点管理段 `/api/admin/agents`；JWT audience 改为 `AGENT`、claim 改 `agent_id`；表经 Flyway V33 改名（`agent`、`agent_user*`、`agent_ai_*`、`agent_model_entry`），CMS 表（`site_content` 等）未动。公开站前端 `apps/site-web` 与公开内容 API 已移除。

## 一句话

中央控制的网站集合：后台总控 `admin-web`；Java `website-services`（`gateway` 统一入口，`auth` 签发 RS256 JWT，`admin` Resource Server + 后台业务）；Python `agent-services` 做 AI 执行。`admin-web` 主纵向域与站点 AI 管理已 FORCE REAL；公开站前端 `apps/site-web` 已随 PR #108 移除，站点用户端为 `apps/agent-portal`（登录在此）；AI LIVE 已在本机环境实证（2026-10-25 Portal Gemini 真实回复，见下表 #5）；Browser E2E 套件已落地（PR #111 合入 dev；首次 CI 实跑待 GitHub Actions 额度恢复后以 workflow_dispatch 补验）；Auth XFF 信任边界已由 gateway 集成断言证明、prod 配置已收窄（RESOLVED，见下表 #1）。

---

## 模块状态

| 域 | 状态 | 说明 |
|----|------|------|
| **Admin Web** | PARTIAL（主域 + 运营聚合 + 站点 AI 管理 FORCE REAL；工作台以 Agent 为中心、发布检查由 REAL API 推导；占位页） | 见 [`../admin-web/CURRENT_STATE.md`](../admin-web/CURRENT_STATE.md) |
| **Site Web** | REMOVED（2026-10-28，PR #108） | `apps/site-web` 与公开内容 API 已移除（见上方架构变更）；站点用户登录在 `apps/agent-portal` |
| **Backend (website-services)** | PARTIAL（主业务 COMPLETE_V1；安全门（Auth XFF）RESOLVED） | 见 [`../website-services/CURRENT_STATE.md`](../website-services/CURRENT_STATE.md) |
| **Database** | COMPLETE_V1（Flyway 以 `infra/.../db/migration` 为准，当前至 **V35**） | 以代码为准 |
| **Auth Admin** | PARTIAL | 登录/刷新/登出/注册门控 REAL；XFF：gateway 集成断言已落地（`GatewayXForwardedTrustBoundaryTest`），prod 以 prod profile 收窄 trusted-proxies |
| **Auth Agent（原 Auth Site）** | BACKEND_COMPLETE | login/refresh/logout/me + aud 隔离；前端登录在 `apps/agent-portal`（#3 已结案） |
| **RBAC** | COMPLETE_V1 | roles/permissions/me/menus 读；Method Security |
| **Menu write** | COMPLETE_V1 | `POST/PUT/DELETE /api/admin/menus`；权限目录页已撤下，角色按菜单分配并带上该页操作权限 |
| **Agent Core（原 Site Core）** | COMPLETE_V1 | CRUD/status/乐观锁/软删；runtime-status 非 Core |
| **Template** | REMOVED（2026-10-27，PR #72） | 行业种子模板经 Flyway V30 软删，Java Template 后端与后台模板管理页已移除；不要再按 COMPLETE 引用 |
| **Appearance** | COMPLETE_V1 | 字段：`primaryColor` / `fontStyle` / `buttonStyle` / `heroLayout`（**不是** heroStyle / logoPlaceholder） |
| **Media** | COMPLETE_V1 | 服务端 object key；MIME；站点归属 |
| **Content** | COMPLETE_V1 | CMS CRUD/status |
| **Reservation** | REMOVED（2026-10-27） | 功能整包删除（代码零残留）；删除前规格文档已于 2026-10-29 随历史文档一并移除，见 git 历史；`STATUS_ENUMS` 中的状态闭集仅作历史记录 |
| **AI** | PARTIAL_ADMIN_SCOPE；LIVE 已实证（2026-10-25 kou 本机环境 Portal 以 Gemini 真实回复；2026-10-27 M11 真模型 eval 通过） | admin Prompt/KB/会话/预览 FORCE REAL；平台模型入口在 Java `ai_model_entry`（后台「模型入口」维护，响应不含 key；启用的默认入口随对话注入 Python，没有则回退进程 `AI_MODEL`）；模型入口**双模式（KEY / CLI）已合入**（PR #109 后端 + PR #114 Portal 前端，Flyway V35，Issue #110 进行中；向量索引不跟随模式，只用 KEY 入口的 embedding spec 与其 key，没有则用进程嵌入模型，CLI 客户端 key 不参与嵌入）；站点聊天经 `apps/agent-portal`；部署环境需各自配置 `AI_PROVIDER_API_KEY`，两边都没有模型时诚实 503。Agent 平台（无业务工具）：每次对话由 `assemble_harness` 把平台 background、护栏、生效的运行上限、技能正文、函数工具说明和 MCP 服务器名送进系统提示，并按历史字符预算裁剪送模记忆（不写 URL，不改写下一轮；超预算时把被裁掉的前缀摘要进系统提示，摘要失败则只裁剪，不回写会话；未做摘要落库与跨轮复用）；`GET /internal/v1/health` 的 `harness` 只报告这三个平台文件是否存在，`provider` 仍是进程环境模型；MCP（配置驱动，`transport` 支持 http/sse/stdio，深检 `GET /internal/v1/mcp/ready`，连接失败 `MCP_UNAVAILABLE`）/ Skills（目录 + 预览可选）/ Todo（站点会话 V20 持久化、预览前端回传）已端到端接通；运行预算（Python `AGENT_BUDGET_EXCEEDED` → Java `ErrorCode` 1008 / HTTP 422）/ 工具超时 / 历史字符预算 / Fallback 模型（进程 `AI_MODEL` 挂 `AI_FALLBACK_MODELS`；带了模型入口的请求只走入口备援链，不再追加进程备援。仅 Provider API 错误时切换；入口本身 BLOCKED 时不顶替。后台试聊只调用所选模式的主用入口）/ Agent OTel / 运行轨迹（V21 `agent_ai_message.agent_meta`，后台会话可见）/ 后台“运行时”面板，见 Agent 设计 V1 §19–§24。多模態輸入 v1（傳圖）已落地（分支） |
| **Mock 策略** | admin-web 无 mock 数据源；`VITE_USE_MOCK` 不再生效 | 见 [`../rules/MOCK_POLICY.md`](../rules/MOCK_POLICY.md) |
| **Testing** | Vertical/Contract 有；Browser E2E 套件已落地（Playwright，PR #111） | 禁止称 MockMvc/build 为 Browser E2E；套件首次 CI 实跑待 Actions 额度恢复补验（见下表） |
| **External** | PostgreSQL / Redis /（可选 MinIO）/ AI Provider | AI Provider 缺失 → 诚实 503 |

---

## 安全与未决（指针）

详细描述不在此重复维护。见 [`ROADMAP.md`](./ROADMAP.md) 与 **GitHub Issues**（权威 Tracker）。

| 项 | 状态 | Tracking |
|----|------|----------|
| Auth trusted client IP / XFF | **RESOLVED**（2026-10-28：gateway 集成断言 `GatewayXForwardedTrustBoundaryTest` 证明伪造 XFF 前缀不控制限流末段；prod 以 prod profile 强制收窄 trusted-proxies；残留：本机 smoke 直连 Auth 的 XFF 行为属本地调试面、已文档化于 SECURITY.md） | [#1](https://github.com/hqjiayou/my-website/issues/1) |
| Browser E2E golden paths | **LANDED**（2026-10-28，PR #111 合入 dev，#2 已关闭）：Playwright 套件（3 条现行黄金路径）+ `browser-e2e-ci` workflow 已在树里；首次 CI 实跑待 2026-11-01 Actions 额度重置后以 workflow_dispatch 补跑，未取得真实结论前不得称 E2E 已验证通过 | [#2](https://github.com/hqjiayou/my-website/issues/2) |
| site-web authentication integration | **RESOLVED（superseded）**：`apps/site-web` 已移除，站点登录整套已在 `apps/agent-portal` 落地（后端更名为 Agent Auth） | [#3](https://github.com/hqjiayou/my-website/issues/3) |
| Soft-delete `deleted_at` consistency | **RESOLVED**（Site / Appearance / User 删除用例已写 `deleted_at`；Role / 绑定仍只 `deleteById`，不在 #4 范围） | [#4](https://github.com/hqjiayou/my-website/issues/4) |
| AI LIVE | **RESOLVED**：LIVE 已于 2026-10-25 在 kou 本机环境实证（Portal 以 Gemini 真实回复）、2026-10-27 M11 真模型 eval 通过；admin 管理页已 REAL；部署环境需各自配置 `AI_PROVIDER_API_KEY`，未配置时诚实 503（#5 已关闭） | [#5](https://github.com/hqjiayou/my-website/issues/5) |
| Site runtime-status REAL | **DEFERRED**（无独立端点；由现有 REAL API 推导，访问量 / 当日 AI 会话为 null） | — |
| Admin user mutation error feedback | **RESOLVED（文档漂移）** — 全局 `mutations.onError`→`handleApiError` | — |
| Orphan mocks / dead userApi | **RESOLVED in worktree**（待与 FORCE REAL WIP 同交；非 Tracker） | — |
| Menu write | **COMPLETE_V1** | `POST/PUT/DELETE /api/admin/menus` |

---

## 相关 ADR

- [`ADR-0001`](../decisions/ADR-0001-core-domains-force-real-api.md) — 核心域强制 REAL API
- [`ADR-0002`](../decisions/ADR-0002-admin-site-jwt-audience-isolation.md) — Admin/Site JWT audience 隔离
- [`ADR-0003`](../decisions/ADR-0003-media-server-generated-object-key.md) — Media object key 服务端生成
- [`ADR-0004`](../decisions/ADR-0004-ai-must-fail-honestly-without-provider.md) — AI 无 Provider 诚实失败
- [`ADR-0005`](../decisions/ADR-0005-java-business-truth-python-ai-execution.md) — Java 持有业务真相，Python 只做 AI 执行

---

## 历史材料

2026-08 Full Function Implementation 的 Instruction / Execution / Acceptance 原在 `docs/implementation-history/2026-08-full-function-implementation/`，已于 2026-10-29 随历史文档整体移除，见 git 历史。**不得**再当作当前完成度来源。Acceptance 结论 `NOT ACCEPTED`（HIGH F-ACC-01）保留为历史事实。
