# Post-Consolidation Independent Acceptance Audit

```yaml
artifact_type: AUDIT
status: CURRENT
auditor: independent-acceptance
date: 2026-08-09
initiative: Full-Stack Documentation Consolidation & Legacy Handoff
scope: STRICT_READ_ONLY
input_claim_list: docs/audit/current/KNOWLEDGE_CONSOLIDATION_REPORT.md
clarification: 本验收结论（NOT_ACCEPTED）保留为当时时点证据，勿改写。Open Work handoff 已于同日 reassessment 完成，见 GITHUB_ISSUE_REASSESSMENT_REPORT.md 与 Issues #1–#5；KNOWLEDGE 报告 artifact_type 已更正为 EXECUTION_REPORT。
```

> 本文件是**独立验收 AUDIT**。`KNOWLEDGE_CONSOLIDATION_REPORT.md` 仅作 **EXECUTION CLAIM LIST**，不得当作事实。

---

## 1. Executive Summary

知识治理骨架（`docs/README` → CURRENT / contracts / ADR / archive）大体成立，主域契约（Reservation `DONE`、Appearance `heroLayout`、Core FORCE REAL、历史 Acceptance `NOT ACCEPTED` 未篡改）与**当前工作区代码**大体一致，本地 `mvn test` / admin-web / site-web car-web 验证均 **PASS**。

但 initiative 的硬门槛未满足：

1. **GitHub Issue handoff = NOT_DONE**（`gh` 未登录；ROADMAP 无 `#N`；SoT 却宣称 Issues 是未决工作唯一源）  
2. **F-ACC-01 = STILL_HIGH / PARTIALLY_RESOLVED**：gateway 已配 `trusted-proxies` + `for-append`，经网关时 Auth 取 XFF **末段**可缓解前缀伪造；但 **Auth `:8083` 可直连**、**无 Gateway→Auth 集成测试**、生产向 **`.*` 过宽**  
3. **工作区 ≈ 534 个已暂存文件**的大型混合 WIP（rename / FORCE REAL / mock 清理 / agents / backend…），不适合作为「已完成 consolidation 后的正式单一 commit/PR」

因此最终判定：**NOT_ACCEPTED**。

---

## 2. Final Verdict

```text
NOT_ACCEPTED
```

阻断依据（满足任一即否决）：

| 规则 | 本轮 |
|------|------|
| 未解决 HIGH | **是**（F-PCA-01 / F-PCA-02 / F-PCA-03 / F-PCA-05） |
| F-ACC-01 仍可利用 | **是**（直连 Auth + 缺集成证明；见 §15） |
| GitHub Issue handoff 未完成且 initiative 要求 handoff | **是** |
| REAL→Mock 假闭环 | **工作区未发现**（非本轮否决主因） |
| CURRENT SoT 严重冲突 | **部分**：完成度矩阵大体一致；**未决工作 SoT 断裂**（Issues 空） |

---

## 3. Scope

| 做 | 不做 |
|----|------|
| 验证、举证、判定 | 修代码 / 改文档（除本 AUDIT） |
| 复跑适用命令 | 清理 Mock / 创建 Issue / Commit / PR |
| 对照 Execution Claim | 改写历史 PASS/FAIL |

对照基线：

- **已推送 consolidation**：`origin/dev`（含 docs SoT、security trusted-proxies、handoff 草稿）  
- **工作区 index**：534 paths staged（admin-web REAL/rename/mock 清理等）——验收同时覆盖 **HEAD 与 WT**，避免只认 Claim

---

## 4. Source of Truth Review

**判定：PARTIAL**

`docs/README.md` 对第一次进入仓库的人给出清晰地图：

| 问题 | 指针 | 存在？ |
|------|------|--------|
| 系统当前状态 | `overview/CURRENT_STATE.md` | 是 |
| Admin / Site / Backend | 各域 `CURRENT_STATE.md` | 是 |
| 契约 / ADR / workflows | `contracts/` `decisions/` `workflows/` | 是 |
| 历史开发 / 分析 | `implementation-history/` `analysis/archive/` | 是 |
| 验收 | `audit/` | 是（本文件属 `audit/current/`） |
| 未解决工程工作 | **GitHub Issues** | **否——无法兑现** |

完成度事实：overview / admin-web / website-services / site-web **未发现互相冲突的 COMPLETE_V1 级矛盾**（Reservation/Appearance/Site Auth 标签一致）。

未决工作事实：README / AGENTS / ROADMAP 均写「以 GitHub Issues 为准」，但 Issues **未创建**，ROADMAP 仅写 `handoff` —— **SoT 承诺落空**（Finding F-PCA-03）。

---

## 5. Documentation Lifecycle Review

| 检查项 | 结果 |
|--------|------|
| Instruction / Execution / Acceptance 区分 | **PASS**（`implementation-history/2026-08-full-function-implementation/`） |
| SUPERSEDED banner + replacement pointer | **PASS**（抽查 Instruction/Execution/Acceptance/analysis/archive） |
| 历史 `NOT ACCEPTED` / F-ACC-01 未改写 | **PASS** |
| 历史 Execution `PASS`/`COMPLETE` 未漂白 | **PASS**（仍保留；CURRENT 另述） |
| KNOWLEDGE 报告类型 | **FAIL 类型标注**：自称 `AUDIT`，实为 Execution Claim |
| CURRENT 引用 archive 作实现依据 | **未发现**系统性引用；AGENTS 明确禁止 |
| archive 被 Rule 当 CURRENT | **未发现** `.cursor` 指向 archive 为实现真相 |

---

## 6. Current State Accuracy

逐域核验（禁止裸 COMPLETE/DONE/PASS）：

| Domain | Documentation Status | Code Reality | Frontend Reality | Persistence Reality | Test Reality | Verdict |
|--------|---------------------|--------------|------------------|---------------------|--------------|---------|
| Admin Auth | PARTIAL（XFF PARTIALLY_RESOLVED） | login/refresh/logout/register+限流 REAL | `authApi` REAL | N/A（auth 无业务表权威） | Auth MVC 有；**无 GW 集成** | **PARTIAL** |
| Site Auth | BACKEND_COMPLETE + FE NOT_IMPLEMENTED | login/refresh/logout/me + aud | site-web **未接** | site_user 等 Flyway | Site Auth MVC 有 | **BACKEND_COMPLETE** / FE **NOT_IMPLEMENTED** |
| RBAC | COMPLETE_V1 | Method Security + APIs | `rbacApi` REAL | admin_* 表 | 有相关测试 | **COMPLETE_V1** |
| Admin User | COMPLETE_V1（缺口 onError） | CRUD/status | users 页 REAL；mutation 缺 onError | admin_user | 有 | **COMPLETE_V1** + LOW UX gap |
| Site | COMPLETE_V1；runtime 非 Core | CRUD/status | FORCE REAL；runtime MOCK/null | site 表；软删路径见 Finding | MVC/PG 类测试存在 | **COMPLETE_V1**（runtime **PLACEHOLDER**） |
| Template | COMPLETE_V1（目录+status） | list/get/status | FORCE REAL | template 表 | 有 | **COMPLETE_V1**；本体 CRUD **DEFERRED_BY_DESIGN** |
| Appearance | COMPLETE_V1；字段 heroLayout | get/put | FORCE REAL；`primaryColor/fontStyle/buttonStyle/heroLayout` | appearance JSON | MVC 断言 heroLayout | **COMPLETE_V1** |
| Media | COMPLETE_V1 | 服务端 object key | FORCE REAL | media 表 | 有 | **COMPLETE_V1** |
| Content | COMPLETE_V1 | CMS CRUD | FORCE REAL（站点工作区）；运营全局 CONNECTED=false | content 表 | 有 | **COMPLETE_V1** |
| Reservation | COMPLETE_V1_ADMIN_SCOPE；`DONE` | 状态机 DONE | FORCE REAL；类型 `DONE` | reservation | 有 | **COMPLETE_V1_ADMIN_SCOPE** |
| AI | EXTERNAL_BLOCKED + FE mock | Java 诚实失败路径 | `aiApi`→`aiMock` if USE_MOCK；else HTTP | N/A | Agent client 测 503/BLOCKED | **EXTERNAL_BLOCKED** |
| Runtime Status | PLACEHOLDER | 无 REAL endpoint 消费 | USE_MOCK→siteMock；else `null` | N/A | 无 E2E | **PLACEHOLDER** / MOCK-gated |
| Analytics/System/Placeholder | PLACEHOLDER | 占位 | EmptyModule 等 | N/A | N/A | **PLACEHOLDER** |
| Admin Web | PARTIAL | — | 主域 REAL；AI/runtime mock；占位页 | — | lint/build/import **PASS** | **PARTIAL** |
| Site Web | PROTOTYPE_MOCK | — | car-web lint/build **PASS**；无 auth | — | 无产品 auth 测 | **PROTOTYPE_MOCK** |
| Database | COMPLETE_V1（V1–V15） | Flyway 在 infra | — | 权威 DDL | default suite 非全量 PG verify | **COMPLETE_V1**（PG profile **NOT RUN**） |
| Testing | Vertical 有；Browser E2E 无 | — | — | — | mvn 305/0；无 Playwright/Cypress | **PARTIAL** / E2E **NOT_IMPLEMENTED** |

文档漂移点：

- `CURRENT_STATE`「Orphan mocks / dead userApi = 本轮 cleanup 目标」vs 工作区已删、HEAD 仍有 —— **状态依赖 WIP**，属 DOC/交付耦合，非错误枚举，但影响「已完成」叙事（F-PCA-05）。

---

## 7. Admin Web Legacy Review

### 7.1 Execution Claim：已删除 orphan mocks / userApi

| 对象 | HEAD (`origin/dev`) | 工作区（staged） | Consumer（WT） |
|------|---------------------|------------------|----------------|
| `operation/user-api` / `userApi` | **仍存在**（mock `/site/users` 风格） | **已删除** | 无 |
| `content.mock` / `reservation.mock` / `file.mock` / `user.mock` 等 | **仍在 tree** | **已删除** | 无外部 import |
| `aiMock` / `siteMock`（runtime） | 旧名 `*.mock.ts` | **保留** camelCase | **有**（`aiApi` / `siteApi`） |

结论：Claim「工作区已清理」**对 WT 成立**；**未进入已推送 HEAD**。单独把 mock 清理当成 consolidation 完成项 → **不成立**。Claim「与 FORCE REAL / rename WIP 强耦合」——**成立**（index 中 mock 删除与 API rename/REAL 同批 staged）。

### 7.2 FORCE REAL / 假闭环

工作区证据：

- Site/Template/Appearance/Media/Content/Reservation：文件头声明不受 `USE_MOCK`；`siteApi` 仅 `getSiteRuntimeStatus` 门控 mock/null  
- `operation/*`：`CONNECTED=false` 占位，**不**再请求假 `/site/*`  
- 搜索未发现 REAL 失败后自动 `aiMock`/`contentMock` 兜底

**REAL→Mock 假闭环：未复现（WT）**。HEAD 仍含旧 mock 树，若回滚/半提交会复活风险 → 归入 commit 拆分风险，不单列为假闭环 HIGH。

### 7.3 重复 API / 旧 DTO / 旧 enum

| 项 | 结果 |
|----|------|
| 重复危险 client（user→`/site/users`） | WT **已移除**；HEAD **仍有** |
| operation vs apps | 并存但 operation 为 **未接入标志**，非并行真假双实现 |
| Reservation `COMPLETED` | CURRENT/contracts/FE/BE **均为 DONE** |
| Appearance `heroStyle`/`logoPlaceholder` | CURRENT/contracts **禁止**；代码用 `heroLayout` |

---

## 8. Site Web Review

- `docs/site-web/CURRENT_STATE.md`：**FRONTEND auth NOT_IMPLEMENTED** — 与代码一致  
- car-web：`pnpm lint` **PASS**；`pnpm build` **PASS**  
- 无 Site Auth client 对接  

**Verdict：PROTOTYPE_MOCK / auth NOT_IMPLEMENTED**（文档准确）。

---

## 9. Backend Review

- 主域 Controller/Service/常量与 CURRENT 标签一致（抽查 Reservation/Appearance/Site 软删）  
- Soft-delete：`AdminSiteServiceImpl` / `AdminAppearanceServiceImpl` 走 `deleteById`；与 Media/Content 写 `deletedAt` 不一致 → **STILL_OPEN LOW**（handoff 文稿有，Issue 无）  
- AI：`HttpAgentClient` 对非 LIVE / 503 诚实失败 — 与 ADR-0004 一致  
- Auth 直连端口 **8083** 为文档与配置常态（`.env` / README / gateway uri）

---

## 10. API Contract Review

| 契约点 | docs/contracts | FE | BE | 结论 |
|--------|----------------|----|----|------|
| Reservation statuses | NEW/CONFIRMED/**DONE**/CANCELLED | 同 | `AdminReservationConstants` 同 | **对齐** |
| 禁止 COMPLETED | 明确 | 类型无 COMPLETED | 无 STATUS_COMPLETED | **对齐** |
| Appearance 字段 | primaryColor/fontStyle/buttonStyle/**heroLayout** | `types/appearance.ts` | MVC JSON `heroLayout` | **对齐** |
| 禁止 heroStyle/logoPlaceholder（CURRENT） | 明确 | 业务类型未用 | 测试用 heroLayout | **对齐** |

---

## 11. Mock Residue Review

| Mock | Claim | Consumer | 判定 |
|------|-------|----------|------|
| `aiMock` | 保留 | `aiApi` | **STILL_REQUIRED**；EXTERNAL_BLOCKED 可接受 |
| `siteMock` runtime | 保留 | `getSiteRuntimeStatus` only | **STILL_REQUIRED**；REAL 模式 **null**（非伪造 KPI） |
| 其它 orphan | 已删（WT） | 无 | WT OK；HEAD 残留 |

AI：允许 FE mock；禁止后端无 provider 伪造 success —— **后端路径未见伪造 success**。

Runtime 准确描述应为：

```text
USE_MOCK=true  → MOCK (siteMock)
USE_MOCK=false → NULL FALLBACK (Promise.resolve(null))
REAL endpoint  → NOT_IMPLEMENTED
```

CURRENT 描述与此一致。

---

## 12. Instruction / Analysis Governance Review

| 历史类 | 去向 | 通用方法 | ADR | Issue |
|--------|------|----------|-----|-------|
| Full-function Instruction/Execution/Acceptance | `implementation-history/...` | templates 下有提炼 | ADR-0001…0004 | **未建** |
| Cursor 调查 Instruction | `workflows/instructions/archive/` | lifecycle rules | — | — |
| Architecture analysis | `analysis/archive/` | — | 部分进入 ADR | — |

**残留风险（MEDIUM）**：Agent 全文搜索仍可能读到 archive 内旧 COMPLETE 叙事；缓解依赖 AGENTS「默认不得作为实现依据」。未发现永久 Rule 把单次 Prompt 当产品规范。

---

## 13. ADR Review

| ADR | 性质 | 与代码 | 判定 |
|-----|------|--------|------|
| 0001 Core FORCE REAL | 长期决策 | WT 核心 API 符合 | **ACCEPTED 有效** |
| 0002 Admin/Site JWT isolation | 长期决策 | Site Auth 测 + 文档一致 | **有效** |
| 0003 Media server object key | 长期决策 | 与 COMPLETE_V1 Media 一致（抽查未反证） | **有效** |
| 0004 AI fail honestly | 长期决策 | HttpAgentClient 503/BLOCKED | **有效** |

未见 ADR 被代码推翻仍标 ACCEPTED。

---

## 14. AGENTS / Cursor Rules Review

| 检查 | 结果 |
|------|------|
| 默认阅读顺序指向 CURRENT | **PASS** |
| 禁止 archive 作实现依据 | **PASS** |
| 旧 COMPLETED/heroStyle/user-api 写入 Rule | **未发现** |
| Menu 只读 / 写 deferred | **与代码一致** |
| 「未决=GitHub Issues」 | **与空 Issues 冲突** → F-PCA-03 **HIGH** |

`.cursor/agents/*` 与规则增补在 **staged WIP** 中，属治理增强，但与 534 文件大包一同暂存，增加审计难度。

---

## 15. F-ACC-01 Security Reassessment

### 链路上一手证据

| 检查点 | 证据 | 结论 |
|--------|------|------|
| A. SCG 版本 | Spring Cloud Gateway Server **4.3.3**（本地 m2 jar） | 与 `server.webflux.trusted-proxies` 路径一致 |
| B. 配置路径 | `gateway/.../application.yml` → `spring.cloud.gateway.server.webflux.trusted-proxies` | **路径正确** |
| C/D. 信任范围 | `trusted-proxies: ".*"` | **过宽**（任意 remote hostString 均 `Pattern.matches`） |
| E. Filter 注册条件 | `XForwardedHeadersFilter`：remote **trusted** 才改写；否则 **原样返回** client headers | `.*` 使 filter **几乎总启用** |
| F. XFF 行为 | `for-append: true`：`write(..., append, trustedProxiesPredicate)` → **add peer**，再按 predicate 过滤后逗号拼接 | 经网关时典型结果：`client..., <peer>` |
| G. Auth 取值 | `ClientIpUtils`：**XFF 最后一段**，否则 remoteAddr | 与 append-last 策略匹配 |
| H. 经 Gateway 伪造前缀 | 末段应为 peer → **前缀伪造难以改桶**（逻辑推断；**无自动化证明**） | 缓解 **未测实** |
| I. 直连 Auth | 文档/配置默认 **`:8083` 可达**；Auth MVC 测试直接 `.header(XFF)` 即改限流键 | **可利用** |

### 分类

```text
F-ACC-01: PARTIALLY_RESOLVED（配置缓解） + STILL_HIGH（可利用面 / 证明缺口）
```

- 经 Gateway + append + last-hop：**设计上缓解**原「未配置 trusted-proxies → filter 不跑 → 客户端 XFF 直达」问题  
- **直连 Auth**：客户端可指定 XFF 末段 → register/refresh/login 限流键可被操纵  
- **`.*`**：削弱「只信任真实上游」语义；与 predicate 过滤叠加后，**客户端提供的 XFF 条目会被保留**（依赖 last=peer）  
- **无 Gateway→Auth integration test** → **TEST_GAP**（不能把配置声明当 RESOLVED）

**独立 severity：HIGH（不可因 Execution Report 降级）。**

---

## 16. GitHub Issue Handoff Review

| 动作 | 结果 |
|------|------|
| `gh auth status` | **未登录** |
| `gh issue list` | **不可用**（需 login / GH_TOKEN） |
| `GITHUB_ISSUE_HANDOFF.md` | 仅有待创建 body（A–…） |
| ROADMAP Issue 列 | 全为 `handoff`，**无 `#N`** |
| Execution Claim「未解决问题在 GitHub Issues」 | **不成立**（报告正文亦承认未创建，但问答仍指向 Issues） |

| STILL_OPEN 项 | 分类 |
|---------------|------|
| XFF integration assertion | **MISSING_HANDOFF**（文稿有） |
| Browser E2E | **MISSING_HANDOFF** |
| site-web auth | **MISSING_HANDOFF** |
| deleted_at | **MISSING_HANDOFF** |
| users onError | **MISSING_HANDOFF** |
| runtime-status REAL | **MISSING_HANDOFF** / 产品可选 |
| AI LIVE | **EXTERNAL_BLOCKED** + **MISSING_HANDOFF** |
| Menu write | **DEFERRED_BY_DESIGN**（允许无 Issue） |

```text
GitHub Issue Handoff: NOT_DONE
```

handoff 文稿质量：Issue A–C 含 Background / AC / Scope，**草稿可用**；但未创建 ≠ 完成交接。

---

## 17. Browser E2E Review

搜索：无 Playwright / Cypress / `*.e2e.*` 套件。

`docs/testing/E2E_GOLDEN_PATHS.md` / `TEST_STRATEGY.md`：**NOT_IMPLEMENTED**（计划文档）。

```text
BROWSER E2E COVERAGE: NONE
Browser E2E: TEST_PLAN_ONLY（文档） / IMPLEMENTED=false
```

禁止将 MockMvc / `pnpm build` 称为 E2E PASS —— CURRENT 已正确禁止；本轮验证未发现此类误标于 CURRENT。

---

## 18. Verification Results

| Command | Exit Code | Result | Notes |
|---------|-----------|--------|-------|
| `mvn test`（`services/website-services`） | **0** | **PASS** | Surefire 汇总：**305** tests，**0** failures，**0** errors（57 XML suites） |
| `mvn -Ppersistence-verify-pg test` | — | **NOT RUN** | 需 Postgres verify 环境；本轮未启该依赖 |
| `pnpm lint`（admin-web） | **0** | **PASS** | |
| `pnpm build`（admin-web） | **0** | **PASS** | JS bundle ~464.84 kB / gzip ~141.42 kB |
| `node scripts/check-imports.mjs` | **0** | **PASS** | broken imports: 0 |
| `node scripts/check-import-casing.mjs` | **0** | **PASS** | casing mismatches: 0 |
| `pnpm lint`（site-web/car-web） | **0** | **PASS** | |
| `pnpm build`（site-web/car-web） | **0** | **PASS** | |
| Browser E2E | — | **NOT RUN** | **NOT_IMPLEMENTED** |
| Gateway↔Auth XFF IT | — | **NOT RUN** | **不存在** |

---

## 19. Git Change / Commit Readiness

| 观察 | 证据 |
|------|------|
| 分支 | `dev...origin/dev`（consolidation commits 已在远端） |
| 工作区 | **534 paths staged**；unstaged/untracked **0** |
| 内容性质 | admin-web 大规模 rename + FORCE REAL + mock 删除 + `.cursor/agents` + backend/CI 等 **混装** |
| docs consolidation | 已单独提交并 push；本 WT **几乎不再改 docs**（除本 AUDIT 将新增） |
| HEAD mock 树 vs WT | HEAD 全量旧 mocks；WT 仅 ai+runtime |
| 重复副本 | 未见 docs 双 CURRENT 副本；设计文档内仍有旧 `docs/数据字典/` 路径字符串 |
| dead exports（WT） | import/casing checks PASS |

**Commit readiness（initiative 完成后「正式 commit/PR」）：不适合把当前 index 当作单一可审计交付。**

「mock cleanup 与 FORCE REAL/rename 强耦合」——**独立判断：成立**（同 index）；因此不应假装 consolidation 已安全删光 HEAD mocks。

---

## 20. Findings

### F-PCA-01 — GitHub Issue handoff 未完成

- **Severity:** HIGH  
- **Domain:** Governance / Handoff  
- **Files:** `docs/audit/current/GITHUB_ISSUE_HANDOFF.md`, `docs/overview/ROADMAP.md`, `docs/audit/current/KNOWLEDGE_CONSOLIDATION_REPORT.md`  
- **Symbols:** N/A  
- **Claim:** 未解决问题已在 GitHub Issues  
- **Evidence:** `gh auth status` → not logged in；`gh issue list` 失败；ROADMAP Issue 列均为 `handoff`  
- **Current Behavior:** 仅有本地 markdown 草稿  
- **Expected Behavior:** 真实 Issues + ROADMAP `#N`  
- **Impact:** initiative 自身未完成；后续开发者无法按 SoT 跟踪工作  
- **Confidence:** HIGH  
- **Recommended Fix:** `gh auth login` 后按 handoff 创建 Issue 并回填 ROADMAP（本轮验收不执行）

### F-PCA-02 — F-ACC-01 仍为可利用 HIGH / 缺集成证明

- **Severity:** HIGH  
- **Domain:** Auth / Gateway / Security  
- **Files:** `gateway/.../application.yml`, `auth/.../ClientIpUtils.java`, Auth `*RateLimit*MvcTest.java`, SCG `XForwardedHeadersFilter` 4.3.3  
- **Symbols:** `trusted-proxies`, `for-append`, `ClientIpUtils.resolve`  
- **Claim:** trusted-proxies 已配置 → 问题基本解决 / READY  
- **Evidence:** Auth `:8083` 暴露；MVC 测试证明直连 XFF 可改限流键；无 GW→Auth IT；`trusted-proxies: ".*"`  
- **Current Behavior:** 经网关逻辑上可 append peer；直连仍可 spoof；无自动化闭环证明  
- **Expected Behavior:** 伪造 XFF 不能改限流键（经官方入口）；集成测试覆盖；生产信任范围收紧；Auth 非必要不暴露  
- **Impact:** 注册/刷新/登录 IP 限流可被绕过（直连拓扑）  
- **Confidence:** HIGH  
- **Recommended Fix:** 增加 gateway↔auth 断言；收紧 proxies；网络层隔离 Auth（本轮不实施）

### F-PCA-03 — CURRENT SoT 将空 GitHub Issues 宣称为未决工作唯一源

- **Severity:** HIGH  
- **Domain:** Documentation / Agent guidance  
- **Files:** `docs/README.md`, `AGENTS.md`, `docs/overview/ROADMAP.md`  
- **Claim:** 未完成工作以 GitHub Issues 为准  
- **Evidence:** Issues 不可列举；ROADMAP 无编号  
- **Current Behavior:** Agent/人类被导向空跟踪系统  
- **Expected Behavior:** Issues 存在，或 CURRENT 明确「handoff 草稿暂代、Issues 未创建」  
- **Impact:** 错误执行优先级；误判 initiative 完成  
- **Confidence:** HIGH  
- **Recommended Fix:** 完成 F-PCA-01 或暂时改 SoT 措辞（验收轮禁止改）

### F-PCA-04 — KNOWLEDGE_CONSOLIDATION_REPORT 类型与 Verdict 过度宣称

- **Severity:** MEDIUM  
- **Domain:** Audit hygiene  
- **Files:** `docs/audit/current/KNOWLEDGE_CONSOLIDATION_REPORT.md`  
- **Claim:** `artifact_type: AUDIT` + `READY_WITH_TRACKED_ISSUES`  
- **Evidence:** 同文件承认 Issues 未创建；本轮为真正 AUDIT  
- **Current Behavior:** Execution 自我验收  
- **Expected Behavior:** `EXECUTION_REPORT`；Verdict 反映 handoff/安全缺口  
- **Impact:** 混淆验收权威  
- **Confidence:** HIGH  
- **Recommended Fix:** 更正 artifact_type；Verdict 降为 BLOCKED/NOT_READY（由后续文档修正，非本轮）

### F-PCA-05 — 巨型暂存混合 WIP，不适合正式单一交付

- **Severity:** HIGH（相对 commit readiness）  
- **Domain:** Git / Delivery  
- **Files:** index 内 ~534 paths（admin-web / .cursor / services / …）  
- **Claim:** 工作区适合进入正式 commit/PR；mock cleanup 仅因耦合暂缓  
- **Evidence:** `git status --short` 534 staged；HEAD 仍含全量 mocks  
- **Current Behavior:** 不可审计的大包待提交  
- **Expected Behavior:** 按域拆分 commit；mock 清理与 REAL 接线可审计  
- **Impact:** review/回滚/归责困难；半提交可破坏 API/mock 一致性  
- **Confidence:** HIGH  
- **Recommended Fix:** 见 §24 拆分建议

### F-PCA-06 — 设计文档仍大量指向 `docs/数据字典/` 权威路径

- **Severity:** MEDIUM / DOC_DRIFT  
- **Domain:** Docs  
- **Files:** `docs/website-services/设计文档/数据与持久层/数据库模型设计V1.md`, `Java持久层设计V1.md` 等  
- **Evidence:** rg 命中多处 `docs/数据字典/...`；真实字典在 `docs/website-services/数据字典/`；顶层仅为跳转 stub  
- **Current Behavior:** 读者可能打开错误层级或过时索引习惯  
- **Expected Behavior:** CURRENT/活跃 SPEC 统一权威路径  
- **Impact:** 中等误导  
- **Confidence:** HIGH  
- **Recommended Fix:** 批量改链到 `website-services/数据字典/`（非本轮）

### F-PCA-07 — users mutation 缺少 onError

- **Severity:** LOW  
- **Domain:** Admin Web UX  
- **Files:** `apps/admin-web/src/pages/users/userListPage.tsx`  
- **Evidence:** `useMutation` 仅有 success toast，无 onError  
- **Impact:** 失败静默  
- **Confidence:** HIGH  
- **Recommended Fix:** 补 onError toast（已有 handoff 文稿）

### F-PCA-08 — Soft-delete `deleted_at` 不一致

- **Severity:** LOW  
- **Domain:** Persistence  
- **Files:** `AdminSiteServiceImpl`, `AdminAppearanceServiceImpl`  
- **Evidence:** `deleteById`；对比其它域写 `deletedAt`  
- **Impact:** 审计时间戳不一致  
- **Confidence:** HIGH  
- **Recommended Fix:** 统一写 `deletedAt` + 测试

### F-PCA-09 — `trusted-proxies: ".*"` 过宽

- **Severity:** DEFENSE_IN_DEPTH（并入 F-PCA-02 的 HIGH 论述）  
- **Domain:** Gateway  
- **Files:** `gateway/.../application.yml`  
- **Evidence:** `TrustedProxies.from` = regex `matches`；`.*` 信任所有 peer  
- **Expected Behavior:** 生产 CIDR/已知 LB  
- **Confidence:** HIGH  

### F-PCA-10 — Browser E2E 缺口

- **Severity:** TEST_GAP / MEDIUM（产品门禁）  
- **Domain:** Testing  
- **Evidence:** 无 E2E 框架；文档标 NOT_IMPLEMENTED  
- **Impact:** 无浏览器金路径回归  
- **Confidence:** HIGH  

---

## 21. Still Open

| 项 | 状态 | Issue 现实 |
|----|------|------------|
| F-ACC-01 / XFF IT + 拓扑硬化 | STILL_HIGH / PARTIALLY_RESOLVED | MISSING_HANDOFF |
| Browser E2E | NONE | MISSING_HANDOFF |
| site-web auth FE | NOT_IMPLEMENTED | MISSING_HANDOFF |
| deleted_at | STILL_OPEN LOW | MISSING_HANDOFF |
| users onError | STILL_OPEN LOW | MISSING_HANDOFF |
| runtime-status REAL | PLACEHOLDER | MISSING_HANDOFF |
| AI LIVE | EXTERNAL_BLOCKED | MISSING_HANDOFF |
| Menu write | DEFERRED_BY_DESIGN | NO_ACTION |
| 534 文件 WIP 拆分提交 | 交付风险 | N/A |
| 设计文档数据字典路径 | DOC_DRIFT | N/A |

---

## 22. Things That Should NOT Be Changed

- 历史 Acceptance **`NOT ACCEPTED` / F-ACC-01** 原文结论  
- 历史 Execution 中的 PASS/COMPLETE 自我声称（可保留为历史；CURRENT 覆盖事实）  
- Reservation **`DONE`**（不要改回 COMPLETED）  
- Appearance **`heroLayout`**（不要写回 heroStyle/logoPlaceholder 到 CURRENT/contract）  
- Core 域 FORCE REAL / 禁止假闭环  
- AI 无 provider **诚实失败**  
- Menu **只读已有、写基于现有模型扩展**（勿平行架构）  
- archive / implementation-history 的 SUPERSEDED 边界  

---

## 23. Required Fixes Before Commit

针对「宣称 consolidation initiative 可 ACCEPTED 并正式收口」——在 **NOT_ACCEPTED** 解除前至少：

1. **真正创建** GitHub Issues 并回填 ROADMAP `#N`（关闭 F-PCA-01/03）  
2. **F-ACC-01**：集成断言 + 明确 Auth 暴露面处理 + 收紧/文档化 `trusted-proxies`（关闭或降级 F-PCA-02 至可跟踪 TEST_GAP 且不可利用）  
3. **拆分** 534 staged WIP；禁止单 commit 混装 docs-claim + 全量 rename + backend  
4. 修正 KNOWLEDGE 报告 `artifact_type`（F-PCA-04）——可在后续 docs 微修，非安全阻断但属卫生项  
5. （建议）设计文档数据字典链接纠偏（F-PCA-06）

本地测试绿 **不能**单独解锁 ACCEPTED。

---

## 24. Recommended Commit Split

在 Issues/安全门槛满足后，建议拆分（示例顺序）：

1. `chore(admin-web): FORCE REAL API clients + camelCase rename`（含必要 types）  
2. `chore(admin-web): remove obsolete mocks / dead userApi`（紧随 1，同 PR 可）  
3. `feat/chore(cursor): agents + admin-web rules`（若需）  
4. 其余 backend/CI **按域**独立 commit  
5. docs 仅允许：Issue 号回填、F-ACC 状态更新、artifact_type 纠正——**勿**改历史 audit 结论  

**不要**再把「已 push 的 docs consolidation」与上述 WIP 揉成新的巨型 commit。

---

## 25. Final Recommendation

```text
Final Recommendation: NOT_ACCEPTED
Do not treat Knowledge Consolidation as closed.
Do not merge/push the 534-file staged WIP as a single “done” delivery.
First: create real GitHub Issues; close or honestly prove F-ACC-01 residual risk; then split commits.
```

### 特别问题（强制回答）

| 问题 | 答案 |
|------|------|
| 当前事实在哪里？ | **代码优先**；文档入口 `docs/overview/CURRENT_STATE.md` + 域 CURRENT + `docs/contracts/` + ADR |
| 未解决问题是否真的已经在 GitHub？ | **否** → Handoff **NOT_DONE** |
| 过去 Instruction 是否还可能影响未来 Agent？ | **可能**（搜索可达 archive）；默认 Rule **禁止**当实现依据——残余误用风险 **MEDIUM** |
| 过去 Analysis 是否已正确提炼？ | **大体是**（ADR×4 + templates + SUPERSEDED banners）；未决未进 Issues |
| 历史失败记录有没有被篡改？ | **没有**（Acceptance 仍 `NOT ACCEPTED`） |
| CURRENT 是否引用 archive 作实现依据？ | **未发现**系统性引用 |
| archive 是否被 Agent Rule 当 CURRENT？ | **否** |
| 新/旧 API 并存？ | **WT：** operation 占位 + apps REAL（可接受）；**HEAD：** 旧 user-api/mocks 仍在 |
| REAL 与 Mock 并存？ | **是（有意）**：Core REAL；AI mock；runtime mock/null——**非**错误假闭环 |
| 新/旧 DTO 并存？ | 未见 Appearance/Reservation 旧字段主导 CURRENT；AI 仍用 mocks/types |
| 新/旧 enum 并存？ | CURRENT 路径为 DONE/heroLayout；COMPLETED/heroStyle **不得**作 CURRENT |
| F-ACC-01？ | **PARTIALLY_RESOLVED** + **STILL_HIGH** |
| Browser E2E？ | **NONE** / 文档 **TEST_PLAN_ONLY** |
| GitHub Issue Handoff？ | **NOT_DONE** |

---

*End of independent acceptance audit. Stop. No implementation performed.*
