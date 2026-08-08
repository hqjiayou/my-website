# GitHub Issue Handoff

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

`gh` 已安装但会话未登录（无 `GH_TOKEN`）。下列 Issue **待创建**。创建后把 `#编号` 回填 [`../../overview/ROADMAP.md`](../../overview/ROADMAP.md)。

登录后：

```bash
gh auth login
# 然后对每个 body 文件：gh issue create --title "..." --body-file ...
```

---

## Issue A — Auth XFF integration assertion（SECURITY / TEST_GAP）

**Title:** test(security): add gateway↔auth X-Forwarded-For trust chain integration assertion  

**Labels (if exist):** `area:security` `area:backend` `area:testing` `type:tech-debt` `priority:high`

### Body

```markdown
## Background
Acceptance F-ACC-01. On 2026-08-09 gateway gained `spring.cloud.gateway.server.webflux.trusted-proxies` + `x-forwarded.for-append` so `XForwardedHeadersFilter` can append peer IP; auth still takes XFF last segment. **Config mitigation landed; automated proof via gateway is still missing.** Production should tighten `trusted-proxies` away from `.*`.

## Current Behavior
Unit tests cover ClientIpUtils last-segment semantics only.

## Expected Behavior
Integration (or contract) test through gateway proving spoofed XFF prefix cannot control the rate-limit IP bucket; document prod trusted-proxies hardening.

## Scope
- gateway↔auth automated assertion
- ops note for prod CIDR/regex

## Out of Scope
- Menu write, AI LIVE, site-web UI

## Acceptance Criteria
- [ ] Automated test covers trust behavior via gateway
- [ ] Production trusted-proxies guidance documented
- [ ] ROADMAP/SECURITY status updated to RESOLVED when green

## Evidence
- historical ACCEPTANCE_AUDIT F-ACC-01
- `gateway/.../application.yml` trusted-proxies
- `auth/.../ClientIpUtils.java`

## Severity / Priority
HIGH residual until proven; then close

## Dependencies
None
```

---

## Issue B — Browser E2E golden paths

**Title:** test: add browser E2E golden paths for admin critical flows  

### Body

```markdown
## Background
Browser E2E is NOT_IMPLEMENTED. Vertical/MVC tests exist but were sometimes over-labeled as E2E.

## Current Behavior
No Playwright/Cypress (or equivalent) suite.

## Expected Behavior
Automated browser paths for admin login → site → appearance/media/content/reservation; plus auth denial path.

## Scope
admin-web (+ gateway stack) golden paths

## Out of Scope
Full visual regression; AI LIVE

## Acceptance Criteria
- [ ] CI-runnable browser E2E for listed golden paths
- [ ] docs/testing/E2E_GOLDEN_PATHS.md updated from NOT_IMPLEMENTED

## Severity
MEDIUM / TEST_GAP
```

---

## Issue C — site-web authentication integration

**Title:** feat(site-web): integrate Site Auth login/refresh/logout/me  

### Body

```markdown
## Background
Backend Site Auth is BACKEND_COMPLETE; site-web has no client integration.

## Current Behavior
No site-web login/refresh/me against `/api/auth/site/**`.

## Expected Behavior
SITE audience session with refresh cookie, logout, me; isolated from admin tokens.

## Scope
site-web auth client + minimal gated UI

## Out of Scope
Full industry business features

## Acceptance Criteria
- [ ] login/refresh/logout/me work via gateway
- [ ] Admin token rejected for site routes
- [ ] docs/site-web/CURRENT_STATE.md updated

## Severity
MEDIUM / FEATURE
```

---

## Issue D — Soft-delete deleted_at consistency

**Title:** fix(backend): align soft-delete deleted_at for Site/Appearance  

### Body

```markdown
## Background
F-ACC-04. Site/Appearance soft-delete may flip `deleted` without `deleted_at`; Media/Content/Reservation set `deletedAt`.

## Current Behavior
Inconsistent audit timestamps.

## Expected Behavior
Uniform `deletedAt` write on soft-delete paths.

## Acceptance Criteria
- [ ] delete paths set deletedAt
- [ ] test coverage

## Severity
LOW
```

---

## Issue E — Admin user mutation error feedback

**Title:** fix(admin-web): toast onError for user status/delete mutations  

### Body

```markdown
## Background
F-ACC-08. userListPage status/delete mutations lack onError toast.

## Expected Behavior
Failed mutations show toast.error via existing patterns.

## Severity
LOW
```

---

## Issue F — runtime-status REAL (optional)

**Title:** feat: implement site runtime-status REAL API or remove mock KPI UI  

### Body

```markdown
## Background
getSiteRuntimeStatus is mock/null placeholder.

## Expected Behavior
Product decision: implement REAL metrics API or keep explicit placeholder without mock KPIs in prod.

## Severity
LOW / PRODUCT
```

---

## Issue G — AI LIVE (blocked)

**Title:** feat(ai): enable LIVE provider integration (blocked on external provider)  

### Body

```markdown
## Background
AI Java path fails honestly without provider; admin-web uses mock.

## Current Behavior
EXTERNAL_BLOCKED

## Expected Behavior
Real provider configuration + admin-web FORCE REAL AI client; no fake success.

## Severity
status:blocked until provider/keys available
```

---

## NOT creating

| Topic | Reason |
|-------|--------|
| Menu write | DEFERRED_BY_DESIGN |
| Orphan mocks | cleaned in chore commit (if done) |
| Appearance docs wording | CURRENT docs fixed |
| E2E naming in old reports | archived SUPERSEDED |
