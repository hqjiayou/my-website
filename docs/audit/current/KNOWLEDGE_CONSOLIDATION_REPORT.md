# Knowledge Consolidation Report

```yaml
artifact_type: EXECUTION_REPORT
status: CURRENT
last_verified: 2026-08-09
initiative: Full-Stack Documentation Consolidation & Legacy Handoff
clarification: 本文件是 consolidation 执行声称清单，不是独立验收 AUDIT。独立验收见 POST_CONSOLIDATION_INDEPENDENT_ACCEPTANCE_AUDIT.md；Open Work handoff 见 GITHUB_ISSUE_REASSESSMENT_REPORT.md。
superseded_open_work_claim: GitHub Issue 交接已由 2026-08-09 reassessment 完成（Issues #1–#5）；本报告内「Issues 未创建」为当时事实，勿再当 CURRENT。
```

## Verdict

```text
READY_WITH_TRACKED_ISSUES
```

说明：gateway `trusted-proxies` 已配置，F-ACC-01 从「未配置」降为 **PARTIALLY_RESOLVED**（缺 gateway↔auth 集成断言）。Browser E2E / site-web auth / 若干 LOW 项已移交 handoff，待 `gh auth login` 后创建 Issue。历史 Acceptance `NOT ACCEPTED` **未改写**。

若坚持「无集成测试的安全项仍算 HIGH」→ 可降级自评为 `BLOCKED`；本报告按「配置已落地 + 剩余 TEST_GAP 已跟踪」给出 `READY_WITH_TRACKED_ISSUES`。

---

## 1. 新 docs 目录树（CURRENT 骨架）

见 [`../../README.md`](../../README.md)。要点：`overview/` · `contracts/` · `decisions/` · `rules/` · `workflows/` · `implementation-history/` · `archive/` · `audit/current|…`

## 2. CURRENT Source of Truth

| 文档 |
|------|
| `docs/overview/CURRENT_STATE.md` |
| `docs/admin-web/CURRENT_STATE.md` |
| `docs/site-web/CURRENT_STATE.md` |
| `docs/website-services/CURRENT_STATE.md` |
| `docs/contracts/*` |
| `docs/decisions/ADR-0001`…`0004` |
| `docs/rules/*` |
| 后端结构 SPEC：`website-services/设计文档/基础架构/*` |

## 3. Archived

| 来源 | 去向 |
|------|------|
| audit FULL_* / Acceptance / runbook | `implementation-history/2026-08-full-function-implementation/` |
| agents/cursor 调查指令与分析 | `workflows/instructions/archive/` · `analysis/archive/` |
| admin-web 设计文档 | `archive/admin-web/` |
| 联调说明 / 作業検査 / 过时总览 | `archive/website-services/` |

## 4. Rewritten / Created

docs README · overview · domain CURRENT · contracts · rules · operations · testing · website-services README/CURRENT · root README · AGENTS.md

## 5. Deleted (code)

工作区已清理并验证 SAFE_TO_REMOVE；**未单独 commit**（与未提交的 admin-web FORCE REAL / rename WIP 强耦合，单独提交会使 HEAD API 树不一致）。应在提交 admin-web REAL 接线时一并 `chore(admin-web): remove verified obsolete mock...`。

| Path | Evidence |
|------|----------|
| `api/operation/userApi` / `user-api` | 无页面 consumer；危险并行 `/site/users` |
| orphan content/reservation/file/user mocks | 无外部 import |
| 对应 db/types（非 AI/runtime） | 仅被上述 mock 引用 |

## 6. 保留 Mock

| Mock | Reason |
|------|--------|
| `aiMock` + ai db/types | EXTERNAL_BLOCKED；AI 页仍用 |
| `siteMock.getSiteRuntimeStatus` + runtime db | 未实现 REAL；REAL 模式返回 null |

## 7. GitHub Issues

| Item | Status |
|------|--------|
| 新建 | **未创建** — `gh` 未登录（见 `GITHUB_ISSUE_HANDOFF.md`） |
| 已存在 | 未检索到（需登录后 `gh issue list`） |
| 未创建及原因 | Menu write = DEFERRED；orphan mocks 已删 |

## 8. STILL_OPEN（跟踪）

- XFF 集成断言（PARTIALLY_RESOLVED 残余）  
- Browser E2E  
- site-web auth  
- deleted_at consistency  
- users onError  
- runtime-status REAL（产品可选）  
- AI LIVE（EXTERNAL_BLOCKED）  

## 9. Verification

见本轮执行记录（命令结果写入提交说明）。预期：

- frontend lint/build/import checks  
- backend tests（至少 auth/gateway 相关可编译）  
- `BROWSER E2E: NOT RUN / NOT IMPLEMENTED`

## 10. Knowledge Consolidation 问答

1. **当前事实在哪里？** → `docs/overview/CURRENT_STATE.md` + 代码  
2. **未解决问题在哪里？** → GitHub Issues（handoff 待创建）+ ROADMAP 指针  
3. **历史过程在哪里？** → `implementation-history/` · `archive/` · `analysis/archive/`  
4. **为什么这么设计？** → `docs/decisions/`  
5. **下一次 Agent 从哪读？** → AGENTS → docs/README → CURRENT_STATE → contracts/ADR → 当前 Instruction  
6. **哪些旧资料不得再指导代码？** → implementation-history、audit 历史、analysis/archive、archive、旧 Execution COMPLETE 声称  

### Extracted

- ADRs: 4  
- Instruction templates: 6  
- Historical instructions/analyses/audits: archived with SUPERSEDED banners  
