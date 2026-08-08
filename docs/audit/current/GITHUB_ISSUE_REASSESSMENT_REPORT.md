# GitHub Issue Reassessment Report

```yaml
artifact_type: AUDIT
status: CURRENT
date: 2026-08-09
initiative: Project-wide Open Work Reassessment & Handoff
repo: hqjiayou/my-website
```

---

## Executive Summary

基于**当前代码**、CURRENT / Contracts / ADR、独立验收 Audit、Git 状态与本地验证结果，对全项目 Open Work 重新分类并完成 GitHub 交接。

- 现有 Issues 起始为空；创建 **5** 个长期跟踪 Issue（#1–#5）。
- **未**把 Audit 中所有 Finding 机械建 Issue。
- FALSE_POSITIVE / RESOLVED / DEFERRED_BY_DESIGN / 交付动作（拆 commit）均未污染 backlog。
- ROADMAP / CURRENT / AGENTS 已指向真实 `#N`。
- 文档卫生：KNOWLEDGE → `EXECUTION_REPORT`；VERIFICATION → `VERIFICATION_REPORT`；设计文档 `docs/数据字典/` 链接已归一到 `docs/website-services/数据字典/`。

**Final Verdict: `HANDOFF_COMPLETE`**

---

## Existing GitHub Issues

| Number | Title | State | Labels | Scope / Domain |
|--------|-------|-------|--------|----------------|
| — | （reassessment 前） | — | — | **空**（`gh issue list --state all` → `[]`） |

**Repository:** `hqjiayou/my-website`

**Labels:** 复用默认 `bug` / `enhancement` / `documentation`；新增最小集：`security` `testing` `technical-debt` `blocked` `frontend` `backend` `P1` `P2` `P3`。

---

## Candidate Reassessment

| Candidate | Domain | Type | Current Status | Evidence | Should Create Issue | Reason |
|-----------|--------|------|----------------|----------|---------------------|--------|
| F-ACC-01 / XFF trust boundary | Auth/Gateway | Security + Testing | PARTIALLY_RESOLVED；经 GW 设计缓解；直连 Auth 仍可 spoof 末段；`.*` 过宽；无 GW→Auth IT | gateway `application.yml`；`ClientIpUtils`；Auth MVC；DEPLOYMENT 意图内网 | **YES** → #1 | POTENTIAL_HIGH + DEFENSE_IN_DEPTH + TEST_GAP；统一一个 Issue |
| Browser E2E | Testing | Testing Gap | NONE / TEST_PLAN_ONLY | 无 Playwright/Cypress；`E2E_GOLDEN_PATHS.md` | **YES** → #2 | 跨会话回归保护；单 Issue 含 3–5 golden paths |
| Site Web Auth FE | site-web | Product Capability | BE BACKEND_COMPLETE；FE NOT_IMPLEMENTED | `docs/site-web/CURRENT_STATE`；site-web 无 auth client | **YES** → #3 | 明确产品缺口；不含整个 site-web |
| Soft-delete `deleted_at` | Persistence | Tech Debt | 非 DB CHECK 不变量；跨域约定不一致 | MP `@TableLogic` 只管 `deleted`；Media/Content 写 `deletedAt`；Site/Appearance 不写；设计文档要求显式写 | **YES** → #4 | LOW 一致性债，值得跟踪 |
| Users mutation onError | admin-web | UX | 全局已覆盖 | `queryClient.ts` `mutations.onError` → `handleApiError` | **FALSE_POSITIVE** | 勿建 Issue |
| Runtime status REAL | Site | Product optional | PLACEHOLDER；REAL→null；UI `notConnected` | `siteOverviewPage` `enabled: USE_MOCK` | **DEFERRED** | 产品未承诺；勿建 |
| AI LIVE | AI | External / Feature | EXTERNAL_BLOCKED；FE mock；BE 诚实失败 | CURRENT；ADR-0004；`HttpAgentClient` | **YES** → #5 | 产品能力 blocked；非 Bug |
| Orphan mocks / userApi | admin-web | Tech Debt | WT 已删无 consumer | mocks 仅 ai+runtime；operation CONNECTED=false | **RESOLVED** | 交付提交动作，非 Tracker |
| Docs path `docs/数据字典` | Docs | Cleanup | 活跃 SPEC 曾错链 | 设计文档多处 | **NO**（本轮 cleanup） | 已批量改链；不建 Issue |
| artifact_type 错标 | Docs | Cleanup | KNOWLEDGE/VERIFICATION 类型错 | metadata | **NO**（本轮 cleanup） | 已纠正 metadata |
| 534 staged WIP 拆 commit | Delivery | Delivery Action | 大型混合 index | `git status` ~534 staged | **NO** | Commit Strategy 见下；非产品 Issue |
| Menu write | RBAC | Deferred | DEFERRED_BY_DESIGN | CURRENT / ROADMAP | **DEFERRED** | 勿建 |
| Template body CRUD | Template | Deferred | DEFERRED_BY_DESIGN | FEATURE_MATRIX | **DEFERRED** | 勿建 |
| Reservation public/conflict | Reservation | Future | NOT_PLANNED 本阶段 | CURRENT ADMIN_SCOPE | **DEFERRED** | 勿建 |
| Media ownership/MIME/SVG | Media | Security scan | 未见实质缺口 | requireMediaInSite；拒 SVG/HTML；服务端 key | **NO** | NONE |
| Content cross-site cover | Content | Security scan | 未见实质缺口 | requireReadyCoverMediaInSite + MVC | **NO** | NONE |
| Appearance logo/homeSections 虚报 | Appearance | Product | UI 仅四字段闭集 | style/theme pages | **NO** | 无 gap |

---

## False Positives

| ID | Claim | Reality |
|----|-------|---------|
| F-PCA-07 / users onError | mutation 失败静默 | **FALSE_POSITIVE** — `createAppQueryClient` 全局 `mutations.onError`→`handleApiError` toast |
| 「localhost:8083 = 生产公网暴露」 | 自动 CRITICAL | **过度外推** — 部署意图为内网；仍属 **POTENTIAL_HIGH**（默认绑定 + 文档直连烟测），已并入 #1 |

---

## Resolved Findings

| Item | Notes |
|------|-------|
| Orphan mocks / dead `userApi`（工作区） | 已删除且无 consumer；与 REAL WIP 同交即可 |
| Docs `docs/数据字典/` 错链（设计 SPEC） | 本轮归一到 `docs/website-services/数据字典/` |
| KNOWLEDGE `artifact_type: AUDIT` | 改为 `EXECUTION_REPORT`（未改写历史 NOT_ACCEPTED） |
| VERIFICATION `artifact_type: AUDIT` | 改为 `VERIFICATION_REPORT` |
| GitHub Issue handoff 空指针 | 本轮创建 #1–#5 + ROADMAP 回填 |

---

## Deferred By Design

| Item | Reason |
|------|--------|
| Menu write | 只读已够用 |
| Template 本体 CRUD | 目录+status 闭环 |
| Reservation 公开预约/冲突引擎 | ADMIN_SCOPE 外 |
| Site runtime-status REAL | 产品未确认；REAL 已诚实占位 |
| Analytics / System | 有意 PLACEHOLDER |

---

## Issues Created

| # | Title | Priority | Domain | Type |
|---|-------|----------|--------|------|
| [#1](https://github.com/hqjiayou/my-website/issues/1) | security(auth): secure trusted-proxy boundary for auth rate limiting | P1 | Auth/Gateway | Security + Testing |
| [#2](https://github.com/hqjiayou/my-website/issues/2) | test: add browser golden-path E2E for admin critical flows | P2 | Testing / admin-web | Testing |
| [#3](https://github.com/hqjiayou/my-website/issues/3) | feat(site-web): integrate Site Auth login/refresh/logout/me | P1 | site-web | Feature |
| [#4](https://github.com/hqjiayou/my-website/issues/4) | fix(backend): align soft-delete deleted_at for Site/Appearance | P3 | Persistence | Tech debt / bug |
| [#5](https://github.com/hqjiayou/my-website/issues/5) | feat(ai): enable AI LIVE provider integration | P2 | AI | Feature (blocked) |

---

## Issues Not Created

| Candidate | Why |
|-----------|-----|
| users onError | FALSE_POSITIVE |
| runtime-status REAL | DEFERRED（产品可选） |
| orphan mocks | RESOLVED in WT（交付动作） |
| Menu write / Template CRUD / Reservation public | DEFERRED_BY_DESIGN / NOT_PLANNED |
| Media/Content security “findings” | 复验 NONE |
| Appearance logo/homeSections | 无虚报 |
| Docs typo / artifact_type / 链接 | 本轮 cleanup |
| 拆 534 staged commits | Delivery Action，见 Commit Strategy |

---

## Security Tracking

- **#1** — Trusted proxy / XFF / Auth 暴露面 / Gateway→Auth 集成断言（单一 root cause）

Media/Content 跨站与 MIME：**无新 Issue**。

---

## Product Tracking

- **#3** — Site Auth 前端接入  
- **#5** — AI LIVE（`blocked`）  

非 Tracker：runtime-status、Menu write、Template body CRUD。

---

## Testing Tracking

- **#2** — Browser golden paths（4 条，非每页一 Issue）  
- **#1** 内含安全集成测试要求（不另建第四个 XFF Issue）

---

## Technical Debt Tracking

- **#4** — soft-delete `deleted_at` 跨域一致性  

Orphan mock：**不**进 Tracker。

---

## Documentation Cleanup（本轮已做）

- `KNOWLEDGE_CONSOLIDATION_REPORT.md` → `artifact_type: EXECUTION_REPORT` + open-work 声明澄清  
- `VERIFICATION_RESULTS.md` → `artifact_type: VERIFICATION_REPORT`  
- `GITHUB_ISSUE_HANDOFF.md` → SUPERSEDED，映射到 #1–#5  
- 设计文档数据字典路径归一  
- ROADMAP / CURRENT / site-web / admin-web / SITE / AGENTS / docs README 指针更新  
- **未**改写历史 Acceptance `NOT ACCEPTED`

---

## ROADMAP Mapping

见 [`../../overview/ROADMAP.md`](../../overview/ROADMAP.md)：

| Priority | Issues |
|----------|--------|
| P1 | #1, #3 |
| P2 | #2, #5 |
| P3 | #4 |

---

## Source of Truth Result

| Layer | Authority |
|-------|-----------|
| Current Reality | Code + DB → CURRENT_STATE → Contracts → ADR |
| Open Work | **GitHub Issues #1–#5** |
| Direction | ROADMAP（仅 pointer） |
| Historical Evidence | Audit / Execution / Verification / History / Archive |

AGENTS.md 已写入上述顺序。

---

## Commit Strategy（非 GitHub Issue）

当前约 **534** 个已暂存路径为混合 WIP（admin-web rename/FORCE REAL/mock 清理、`.cursor` agents、backend/CI 等）。建议后续人工拆分：

1. admin-web FORCE REAL + camelCase rename  
2. admin-web remove obsolete mocks（紧随 1）  
3. `.cursor` agents/rules（若需）  
4. 其余 backend/CI 按域  
5. 本轮 docs pointer / reassessment 报告单独 docs commit  

**不要**为此创建 GitHub Issue。

---

## Remaining Untracked Work

无「应跟踪却未建 Issue」的产品/安全/测试项。

仅剩**交付动作**：拆分并提交工作区 WIP（非 Tracker）。

---

## Final Verification

```text
gh auth status     → logged in as hqjiayou
gh issue list --state open → #1 #2 #3 #4 #5
ROADMAP #N         → 均存在
Duplicates         → 无
DEFERRED/FP/RESOLVED 误建 → 无
```

---

## Final Verdict

```text
HANDOFF_COMPLETE
```

Open Work 权威源已切换为 GitHub Issues；CURRENT 表达事实；Audit 保留为时点证据。

*Stop. No business code changes. No issue implementation started.*
