# Document and Legacy Inventory

```yaml
artifact_type: AUDIT
status: CURRENT
owner: docs-governance
last_verified: 2026-08-09
scope: full-stack documentation consolidation prep
source_of_truth: code + this inventory (pre-move snapshot)
```

> 本文件在移动/删除之前生成。事实以 2026-08-09 代码复核为准；Acceptance Audit（2026-08-08）作参考，不自动假定问题仍在。

---

## 1. Finding Status Re-Verification

| ID | Topic | Acceptance (2026-08-08) | Code recheck (2026-08-09) | Status |
|----|-------|-------------------------|--------------------------|--------|
| F-ACC-01 | Auth IP / XFF trust chain | HIGH | gateway `application.yml` 仍无 `trusted-proxies`；`ClientIpUtils` 仍取 XFF 末段 | **STILL_OPEN** |
| F-ACC-02 | Appearance「默认外观」夸大 | MEDIUM docs | create 仅成对 insert；无服务端默认 config | **STILL_OPEN**（文档口径；本轮用 CURRENT 纠正） |
| F-ACC-03 | 「E2E」命名过度 | MEDIUM | 无浏览器 E2E | **STILL_OPEN**（本轮测试文档纠正） |
| F-ACC-04 | Site/Appearance `deleted_at` | LOW | 需后续修代码 | **STILL_OPEN** → Issue |
| F-ACC-05 | Orphan mock / dead `userApi` | MEDIUM | 无页面 consumer；仅 `api/index` 导出 `userApi` | **STILL_OPEN** → SAFE_TO_REMOVE（本轮清理） |
| F-ACC-06 | Open registration warn-only | DEFENSE | 默认 false + warn | **DEFERRED_BY_DESIGN** / Issue optional |
| F-ACC-07 | Menu 写 | DEFERRED | 只读存在，写未做 | **DEFERRED_BY_DESIGN**（不建 Issue，除非进 roadmap） |
| F-ACC-08 | users mutation `onError` | LOW | 仍缺 | **STILL_OPEN** → Issue |
| — | Browser E2E | NONE | 无 Playwright/Cypress 等 | **STILL_OPEN** → Issue |
| — | site-web auth UI | NOT_IMPLEMENTED | site-web 无 login/refresh/me 客户端 | **STILL_OPEN** → Issue |
| — | AI LIVE | EXTERNAL_BLOCKED | agent blocked provider；admin AI mock | **EXTERNAL_BLOCKED** |
| — | Reservation status COMPLETED | 旧文档风险 | 代码/DB：`NEW\|CONFIRMED\|DONE\|CANCELLED` | **RESOLVED in code**；文档需清扫 |
| — | Appearance heroStyle | 旧文档风险 | 代码：`heroLayout` | **RESOLVED in code**；文档需清扫 |

---

## 2. Module Reality Matrix (code-verified)

| Module | Backend | Frontend (admin-web) | Frontend (site-web) | Status label |
|--------|---------|----------------------|---------------------|--------------|
| Auth Admin | REAL | REAL (`authApi`) | N/A | **PARTIAL**（IP 限流信任链缺口） |
| Auth Site | REAL | N/A | **NOT_IMPLEMENTED** | **BACKEND_COMPLETE** |
| RBAC / Menu read | REAL | REAL (`rbacApi`) | N/A | **COMPLETE_V1** |
| Menu write | — | 只读 UI | N/A | **DEFERRED_BY_DESIGN** |
| Admin User | REAL | REAL (`rbacApi` users) | N/A | **COMPLETE_V1** |
| Site Core | REAL | FORCE REAL (`siteApi`) | skeleton | **COMPLETE_V1** |
| Site runtime-status | — | mock when `USE_MOCK`；REAL→null | — | **PLACEHOLDER** / mock |
| Template catalog | REAL | FORCE REAL | — | **COMPLETE_V1**（无本体 CRUD） |
| Appearance | REAL (`heroLayout`) | FORCE REAL | — | **COMPLETE_V1** |
| Media | REAL | FORCE REAL | — | **COMPLETE_V1** |
| Content | REAL | FORCE REAL | — | **COMPLETE_V1** |
| Reservation | REAL (`DONE`) | FORCE REAL | — | **COMPLETE_V1_ADMIN_SCOPE** |
| AI | Java path + blocked provider | ACTIVE mock (`aiApi`) | — | **EXTERNAL_BLOCKED** + FE mock |
| Analytics / System / Dev | — | placeholder pages | — | **PLACEHOLDER** |
| Browser E2E | — | — | — | **NOT_IMPLEMENTED** |

---

## 3. Mock / Legacy API Inventory (admin-web)

| Path | Consumer | Classification | Action |
|------|----------|----------------|--------|
| `mocks/services/aiMock.ts` + `db/ai.ts` + `types/ai.ts` | `api/ai/aiApi.ts` | **STILL_REQUIRED** | KEEP |
| `mocks/services/siteMock.ts` + `db/sites.ts` + `types/site.ts` | `siteApi.getSiteRuntimeStatus` when USE_MOCK | **STILL_REQUIRED**（仅 runtime） | KEEP；可后续瘦身 CRUD |
| `mocks/services/contentMock.ts` + db/types | none outside mocks | **SAFE_TO_REMOVE** | DELETE |
| `mocks/services/reservationMock.ts` + db/types | none | **SAFE_TO_REMOVE** | DELETE |
| `mocks/services/fileMock.ts` + db/types | none（`fileApi` 已转 mediaApi） | **SAFE_TO_REMOVE** | DELETE |
| `mocks/services/userMock.ts` + db/types | only `operation/userApi.ts` | **SAFE_TO_REMOVE** | DELETE |
| `api/operation/userApi.ts` | only `api/index.ts` export；页面用 `rbacApi` | **SAFE_TO_REMOVE** | DELETE |
| `api/ai/aiApi.ts` USE_MOCK gate | AI pages | STILL_REQUIRED | KEEP |
| `config/apiMode.ts` | AI / runtime | STILL_REQUIRED；注释过时 | REWRITE comment |
| `utils/delay.ts` / `utils/id.ts` | ai/site mocks | KEEP if mocks remain | KEEP |

**DANGEROUS_PARALLEL_PATH**：`userApi`（`/site/users`）与 `rbacApi` 用户管理并行——删除 `userApi` 消除。

---

## 4. Docs Inventory

### 4.1 Root / overview targets (CREATE)

| Path | Artifact Type | Action | Reason |
|------|---------------|--------|--------|
| `docs/README.md` | REFERENCE | CREATE | 文档地图 |
| `docs/overview/CURRENT_STATE.md` | SPEC | CREATE | 最高级 Current SoT |
| `docs/overview/FEATURE_MATRIX.md` | SPEC | CREATE | 模块×状态矩阵 |
| `docs/overview/SYSTEM_ARCHITECTURE.md` | SPEC | CREATE | 系统拓扑摘要 |
| `docs/overview/ROADMAP.md` | REFERENCE | CREATE | Issue 指针 + 方向 |
| `docs/rules/*` | SPEC | CREATE | 生命周期 / SoT / mock / API / deprecation |
| `docs/contracts/*` | CONTRACT | CREATE | 前后端共同事实 |
| `docs/decisions/ADR-*` | DECISION | CREATE | 从分析/验收提取 |
| `docs/workflows/instructions/templates/*` | INSTRUCTION | CREATE | 可复用模板 |
| `docs/testing/*` | SPEC | CREATE | 测试策略（无 Browser E2E PASS） |
| `docs/operations/*` | RUNBOOK | CREATE / MERGE | 本地开发入口 |
| `docs/site-web/*` | SPEC | CREATE | site-web 独立状态 |
| `docs/admin-web/CURRENT_STATE.md` 等 | SPEC | CREATE | 前端 SoT |
| `docs/website-services/CURRENT_STATE.md` | SPEC | CREATE | 后端 SoT（取代过时完成度） |

### 4.2 admin-web docs

| Path | Artifact Type | Validity | Action | Reason |
|------|---------------|----------|--------|--------|
| `docs/admin-web/开发规范.md` | SPEC | STILL_VALID（命名/结构） | KEEP_CURRENT | 与代码大体一致；补 SoT 指针 |
| `docs/admin-web/设计文档/架构设计.md` | ANALYSIS/SPEC | PARTIALLY_VALID | REWRITE→`ARCHITECTURE.md` + ARCHIVE 旧稿 | 可能含旧 mock 叙述 |
| `docs/admin-web/设计文档/API设计.md` | CONTRACT | SUPERSEDED pattern | ARCHIVE + EXTRACT to contracts | 仍写统一 USE_MOCK 模式，误导 FORCE REAL |
| `docs/admin-web/设计文档/应用设计.md` | ANALYSIS | HISTORICAL | ARCHIVE | 非当前 SoT |

### 4.3 audit / full-function (2026-08)

| Path | Artifact Type | Action | Reason |
|------|---------------|--------|--------|
| `docs/audit/POST_IMPLEMENTATION_ACCEPTANCE_AUDIT.md` | AUDIT | ARCHIVE → `implementation-history/.../ACCEPTANCE_AUDIT.md` | 历史验收；不改 PASS/FAIL |
| `docs/audit/FULL_FUNCTION_IMPLEMENTATION_EXECUTION_REPORT.md` | EXECUTION_REPORT | ARCHIVE | SUPERSEDED；保留历史 COMPLETE 声称 |
| `docs/audit/FULL_FUNCTION_IMPLEMENTATION_AUDIT.md` | AUDIT | ARCHIVE | 实施前审计 |
| `docs/audit/my-website_master_full_function_implementation_runbook.md` | INSTRUCTION | ARCHIVE + EXTRACT_TEMPLATE | 任务指令，非当前规范 |

### 4.4 agents / cursor investigation

| Path | Artifact Type | Action | Reason |
|------|---------------|--------|--------|
| `docs/agents/cursor_agent_rules_architecture_investigation_instruction.md` | INSTRUCTION | ARCHIVE | 一次性调查指令 |
| `docs/agents/cursor_project_investigation_agent_rules_methodology.md` | ANALYSIS | ARCHIVE + EXTRACT_TEMPLATE | 方法可提炼 |
| `docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md` | ANALYSIS | ARCHIVE + EXTRACT_ADR | 含旧 mock 事实 |

### 4.5 website-services — KEEP CURRENT (SPECs)

| Path | Artifact Type | Action |
|------|---------------|--------|
| `设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md` | SPEC | KEEP_CURRENT |
| `设计文档/基础架构/后端包结构与命名规范V1.md` | SPEC | KEEP_CURRENT |
| `设计文档/基础架构/架构设计.md` | SPEC | KEEP_CURRENT（与 CURRENT_STATE 交叉引用） |
| `设计文档/基础架构/整体架构说明.md` | REFERENCE | KEEP_CURRENT |
| `设计文档/基础架构/WSL与Docker环境配置说明.md` | RUNBOOK | KEEP → 也可链到 operations |
| `设计文档/数据与持久层/数据库模型设计V1.md` | SPEC | KEEP_CURRENT（注意与 Flyway 漂移时以 schema 为准） |
| `设计文档/数据与持久层/Java持久层设计V1.md` | SPEC | KEEP_CURRENT |
| `设计文档/权限与后台管理/阶段5A…5E*.md` | SPEC/REFERENCE | KEEP_CURRENT + banner（5E 写=deferred） |
| `设计文档/认证与安全/阶段4A/4D/4F/4G*.md` | SPEC/DECISION | KEEP_CURRENT as auth history+spec |
| `数据字典/*` | CONTRACT/REFERENCE | KEEP_CURRENT |
| `README.md` | REFERENCE | REWRITE 指向新 overview |

### 4.6 website-services — ARCHIVE (phase execution / review)

| Path | Artifact Type | Action |
|------|---------------|--------|
| `总览/当前功能完成度总览.md` | REFERENCE | SUPERSEDE → archive；被 `overview/CURRENT_STATE` 取代（严重过时：仍写 Site 登录未做） |
| `总览/后端开发路线图与阶段状态.md` | REFERENCE | SUPERSEDE partial → archive copy；ROADMAP 接管未决项 |
| `联调说明/*` | EXECUTION_REPORT | ARCHIVE |
| `作業検査/*` | AUDIT/ANALYSIS | ARCHIVE |
| `数据文档/阶段5C.1-*.md`、`阶段3B-*.md` | EXECUTION_REPORT | ARCHIVE |
| `数据文档/持久层真实PostgreSQL验证说明.md` | RUNBOOK | KEEP or MERGE → operations/testing |
| `设计文档/后续规划/README.md` | DRAFT | ARCHIVE / DEFERRED |
| `规则/1.文件名.md` | SPEC | MERGE into rules or KEEP |

### 4.7 Root README / AGENTS / .cursor

| Path | Action | Reason |
|------|--------|--------|
| `README.md` | REWRITE pointers | 仍指向旧 docs 树 / 过时完成度 |
| `AGENTS.md` | UPDATE | 增加 CURRENT_STATE 阅读顺序；保留结构规则 |
| `.cursor/rules/*` | KEEP + light fix | 无任务态事实；backend Menu 描述仍准 |
| `.cursor/agents/*` | KEEP | 角色定义，非产品事实 |

---

## 5. DELETE candidates (docs)

| Path | Proof | Action |
|------|-------|--------|
| （本轮）无独立 DELETE 文档正文 | 优先 ARCHIVE | 避免「删文档丢问题」 |
| Duplicate stub-only jump pages if any | 若仅一行跳转且目标已迁 | DELETE_DUPLICATE after rewrite |

**代码 DELETE**（见 §3）：orphan mocks + `userApi`。

---

## 6. GitHub Issue Handoff Plan

| Finding | Handoff | Notes |
|---------|---------|-------|
| F-ACC-01 XFF | CREATE_GITHUB_ISSUE | priority high / security；或本轮单独 fix commit |
| Browser E2E | CREATE_GITHUB_ISSUE | area:testing |
| Orphan mocks | NO_ACTION if cleaned this round | else CREATE |
| deleted_at consistency | CREATE_GITHUB_ISSUE | tech-debt |
| users onError | CREATE_GITHUB_ISSUE | admin-web UX |
| site-web auth | CREATE_GITHUB_ISSUE | area:site-web |
| runtime-status real | CREATE_GITHUB_ISSUE | product optional |
| AI REAL | EXTERNAL_BLOCKED | Issue with status:blocked |
| Menu write | DEFERRED_BY_DESIGN | **不创建** Issue |
| Open-reg fail-fast | optional Issue | low |

`gh` CLI 已安装但 **未登录**（无 `GH_TOKEN`）→ Issue 创建可能受阻；见 `GITHUB_ISSUE_HANDOFF.md`。

---

## 7. Action Legend Applied

Allowed actions used: `KEEP_CURRENT` / `REWRITE` / `MERGE` / `ARCHIVE` / `SUPERSEDE` / `EXTRACT_ADR` / `EXTRACT_TEMPLATE` / `CREATE_ISSUE` / `DELETE` / `DELETE_DUPLICATE` / `CREATE`.

---

## 8. Read Order After Consolidation (preview)

1. `AGENTS.md` + `.cursor/rules`
2. `docs/README.md`
3. `docs/overview/CURRENT_STATE.md`
4. domain `CURRENT_STATE`
5. `docs/contracts/`
6. `docs/decisions/`
7. current Task Brief / Instruction only

**Do not use as current truth:** `docs/implementation-history/**`, `docs/audit/archive/**`, `docs/analysis/archive/**`, `docs/archive/**`.
