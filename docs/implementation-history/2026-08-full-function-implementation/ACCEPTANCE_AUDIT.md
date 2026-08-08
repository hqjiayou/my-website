> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/overview/CURRENT_STATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# Post-Implementation Independent Acceptance Audit

> **性质**：严格 READ ONLY 独立验收。除本文件外未修改任何代码 / 配置 / migration / mock / `.cursor` / AGENTS / baseline。  
> **对象**：`Full Function Implementation`（执行声明见 `FULL_FUNCTION_IMPLEMENTATION_EXECUTION_REPORT.md`）。  
> **方法**：一手阅读当前代码 + Git change map + 实测 `mvn` / `pnpm` + [codebase-explorer](f837a123-63ec-4862-aa89-6cef77233c54) / [codebase-explorer](5a67b8e0-cb6a-4be7-933f-fc577ca4dd89) / [architecture-planner](7a8d9667-0b67-424c-bd51-477b8e38b25a) / [integration-reviewer](36a943e7-5207-4f37-a69e-31831811d5ff)。  
> **统计日**：2026-08-08。Execution Report 只作 Claim 清单，不作为验收事实。

---

# 1. Executive Summary

大规模实施**确实把** Site / Admin User / Template（目录）/ Appearance / Media / Content / Reservation / Site Auth / AI Java 路径从「Mock/骨架」推进到了**可验证的纵向闭环**（Controller→Service→Mapper→Flyway→MVC/PG 测→admin-web FORCE REAL），且本机实测：

| 命令 | 结果 |
|------|------|
| `mvn -B -ntp test` | **BUILD SUCCESS**（auth 66 / admin 139 / site-app 10） |
| `mvn -B -ntp -Ppersistence-verify-pg test` | **BUILD SUCCESS**（admin 16 + site 4；Testcontainers `postgres:16-alpine`；Flyway **15** migrations） |
| `pnpm lint` / `pnpm build` | **PASS**；主 JS **464.84 kB** / gzip **141.41 kB** |
| `node scripts/check-imports.mjs` / `check-import-casing.mjs` | **0** / **0** |

但独立验收发现 **1 个 HIGH**：Phase 1 安全门依赖的「客户端 IP」在当前 gateway 配置下**不可信**——`ClientIpUtils` 取 `X-Forwarded-For` 末段，而 gateway **未配置** `trusted-proxies`，SCG 4.3.3 的 `XForwardedHeadersFilter` **不会注册**，客户端可伪造 XFF，从而绕过 **register / refresh** 的纯 IP 限流（login 仍残留 username 维）。

按验收规则（存在 HIGH → 否决）：

```text
NOT ACCEPTED
```

主业务域质量大体成立；Execution Report 的「无 HIGH / READY FOR NEXT PRODUCT PHASE」**过乐观**。修完 F-ACC-01 并完成清理后，才适合进入最终 Commit 阶段。

---

# 2. Acceptance Result

```text
NOT ACCEPTED
```

| 条件 | 本轮 |
|------|------|
| CRITICAL | **0** |
| HIGH | **1**（F-ACC-01 Auth IP / XFF 信任链） |
| 必须 Commit 前处理的 MEDIUM | 若干（报告口径、orphan mock 清理建议等） |
| 已知延期 / 外部阻塞 | Menu 写、AI LIVE、site-web、Reservation 冲突等 |

---

# 3. Git Change Map

工作区为**未拆分的大型 WIP**（大量已 staged）。不能把全部 rename/casing/router 重构自动归因于本轮 Full Function Implementation；下表按**当前用途**与**是否属于实施目标域**划分。

| Area | Files（代表） | Current Purpose | Belongs To Implementation |
|------|---------------|-----------------|---------------------------|
| Auth Security Gate | `auth/.../Redis*RateLimit*`, `AdminAuthServiceImpl`, `ClientIpUtils`, rate-limit MVC tests | Redis 滑窗限流 / dummy BCrypt / open-reg warn | **YES（Phase 1）** |
| Site Auth | `AuthSite*Controller`, `SiteAuthServiceImpl`, `SiteRefresh*`, site JWT claims/cookies | SITE login/refresh/logout | **YES（Phase 9）** |
| Admin Site Core | `AdminSiteController/ServiceImpl/Entity/Dtos`, V5, PG/MVC tests | Site CRUD/status/乐观锁/软删 | **YES（Phase 2）** |
| Admin User | `AdminUserController`, `AdminUserManagementServiceImpl`, V6, user pages | 用户 CRUD/status + 角色绑定 UI | **YES（Phase 3）** |
| Template | `AdminTemplate*`, V7, template pages/API | list/detail/by-code/status | **YES（Phase 4，无本体 CRUD）** |
| Appearance | `AdminAppearance*`, V8, style/theme pages | get/put + create pair insert | **YES（Phase 5）** |
| Media | `AdminMedia*`, `ObjectStorageService*`, V9/V10/V13 | upload/list/preview/delete/orphan | **YES（Phase 6）** |
| Content | `AdminContent*`, V11/V12, `FieldStrategy.ALWAYS` | CMS CRUD/status/cover | **YES（Phase 7）** |
| Reservation | `AdminReservation*`, V14/V15 | 工单 + 状态机（无字段 PUT） | **YES（Phase 8）** |
| AI path | `HttpAgentClient`, `SiteAi*`, `agent-services` blocked provider / internal routes | Java→agent 诚实失败 | **YES（Phase 10）** |
| admin-web REAL APIs | `siteApi/templateApi/appearanceApi/mediaApi/contentApi/reservationApi/rbacApi` | FORCE REAL 客户端 | **YES（Phase 12）** |
| Lazy routes | `lazyNamed.ts`, route chunks | 主包拆分 | **YES（Phase 13）** |
| Flyway V5–V15 | `infra/.../db/migration/V5`…`V15` | 权限种子 + 业务表 | **YES** |
| Orphan mocks | `contentMock/reservationMock/fileMock/userMock/userApi` | 无页面消费残留 | **实施附带残留（应清理）** |
| admin-web rename/casing | 大量 `*-page`→`*Page`、API 驼峰 | 命名规范化 | **MIXED / 先前 WIP** |
| Router/layout 重组 | `router/**`, `layouts/**` | 路由/布局结构 | **MIXED / 先前 WIP** |
| `.cursor` agents/rules | `.cursor/agents/**`, `.cursor/rules/**` | Agent 分工规则 | **并行 WIP（非业务实施）** |
| Audit docs | `docs/audit/FULL_*`, runbook | 审计基线与执行声明 | **审计产物** |
| Docs drift updates | 若干 `docs/website-services/**` | 文档同步 | **部分实施相关** |

**说明**：`git diff --stat` 在「全部已 staged」状态下对工作树为空；变更以 `git status --short` / `git diff --name-status`（index vs HEAD）为准。

---

# 4. Execution Report Accuracy

| Claim | Result | Evidence |
|-------|--------|----------|
| Phase 1 Security Gate **PASS** | **PARTIALLY VERIFIED → 现判 HIGH 缺口** | 限流/dummy/warn **存在且单测绿**；但 IP 维度依赖的 gateway XFF 追加**未配置**（F-ACC-01） |
| Phase 2–8 主域 **PASS** | **VERIFIED**（范围需收紧标签） | Controllers/Services/Flyway/MVC/PG + FORCE REAL 页面存在 |
| Phase 9 Site Auth **BACKEND_COMPLETE PASS** | **VERIFIED** | login/refresh/logout/me + aud 隔离测存在；无 site-web UI |
| Phase 10 AI **PASS (Java path)** | **VERIFIED** | `HttpAgentClient` + Blocked/503；admin-web AI 仍 mock |
| Phase 12 FE 收口 **PASS** | **PARTIALLY VERIFIED** | REAL 列表有三态；users mutation 缺 `onError`；无统一 queryKeys |
| Phase 13 lazy **PASS**（~810→466KB） | **VERIFIED**（数字近似） | 本机 build 主包 **464.84 kB** / gzip **141.41 kB** |
| Flyway V1–V15 / 54 ACTIVE perms | **VERIFIED** | PG 应用 15 migrations；`EXPECTED_ACTIVE_PERMISSION_COUNT = 54` |
| Appearance「Create 同事务**默认**外观」 | **INCORRECT（措辞）** | 仅 `templateId`+`appearance` 成对提交才 insert；无服务端默认 config |
| Reservation「COMPLETE CRUD」 | **PARTIALLY VERIFIED** | 有 create/list/detail/status/delete；**无字段 PUT**；无冲突检测 |
| Template「COMPLETE（阶段范围）」 | **VERIFIED**（若写产品全量 COMPLETE 则过度） | 无模板本体 create/update/delete |
| Mock retirement「主路径假闭环已消」 | **VERIFIED** | Core FORCE REAL；orphan mock 仍物理残留 |
| 「真正 E2E」多域 | **OVERSTATED** | 实为 Vertical/Contract（UI+API+DB+MockMvc/PG）；**无浏览器 E2E** |
| `mvn test` / PG / lint / build **SUCCESS** | **VERIFIED**（本机复跑） | 见 §1 |
| Final：`PASS WITH KNOWN GAPS` / 无 HIGH | **INCORRECT（过时）** | 本轮 HIGH F-ACC-01 |
| `READY FOR NEXT PRODUCT PHASE` | **OVER-OPTIMISTIC** | 应先修 IP/XFF 信任链 |

---

# 5. Site Acceptance

## SITE CORE ACCEPTANCE

| Capability | Verdict |
|------------|---------|
| list / detail / create / update / delete / status | **PASS** |
| pagination | **PASS**（`PageResult`） |
| search/filter | **PARTIAL**（`status`/`siteType`；无 keyword） |
| version / optimistic lock | **PASS** |
| soft delete | **PASS**（`@TableLogic`；级联软删子资源） |
| not found / validation / permission | **PASS**（`admin:site:*`） |
| create → PostgreSQL + 真实 ID | **PASS**（insert + `toDetail`） |
| update 字段覆盖 | **PASS（有意限制）**：可改 name/type/domain/description；不可改 `siteCode`/`templateId`；status 走 PATCH |
| 前端 invalidate / 重查 | **PASS**（create 成功 invalidate `['sites']`） |
| Ownership / Permission | **PASS（RBAC）**：方法级 `@PreAuthorize`；非多租户 site-owner 模型（admin 平台权限） |

**Status：`COMPLETE_V1`**（runtime-status 仍 mock → 不计入 Core）。

**注意**：`deleted_at` 在 Site 软删路径上可能未写（仅 `deleted=true`）——见 F-ACC-04（LOW）。

---

# 6. Admin User Acceptance

| Capability | Verdict |
|------------|---------|
| list / detail / create / update | **PASS** |
| enable/disable | **PASS**（ACTIVE/DISABLED） |
| role assignment | **PASS**（既有 User↔Role API + UI） |
| protected / super_admin | **PASS** |
| self status/delete/role | **PASS（禁止）**；资料自改允许 |
| mass assignment | **PASS（防护）**（DTO 无 passwordHash/status/roles） |
| password hash 泄漏 | **PASS（未返回）** |
| unique username/email | **PASS**（应用层 + partial unique） |
| pagination | **ABSENT**（返回 `List`） |
| 与 bootstrap register 混用 | **未混用**（管理 API ≠ `/api/auth/admin/register`） |

**Status：`COMPLETE_V1`**。

---

# 7. Template Acceptance

| Capability | Present |
|------------|---------|
| list / detail / by-code / PATCH status | **YES** |
| frontend REAL + persistence + permission + tests | **YES** |
| create / update body / delete / template editor | **NO**（刻意未做） |

**Status：`COMPLETE_V1`（目录 + status 范围）**；本体 CRUD = **`DEFERRED_BY_DESIGN`**（若产品需要）。  
不得将整个 Template 产品域标为无条件「完全完成」。

---

# 8. Appearance Acceptance

### v1 真实 schema 字段

```text
primaryColor, fontStyle, buttonStyle, heroLayout
```

| 审计点名 | 现状 |
|----------|------|
| `buttonStyle` | **SAVED** |
| `fontStyle` | **SAVED** |
| `heroStyle` | **REPLACED → `heroLayout`** |
| `logoPlaceholder` | **DROPPED**（不落库） |

| Claim | Verdict |
|-------|---------|
| get / put | **PASS** |
| Site Create integration | **PASS（客户端 pair）**：向导必送 `templateId`+`appearance`；API 允许 Core-only（两者皆空） |
| 「服务端默认外观」 | **FALSE** |
| persistence / reload / permission / validation | **PASS**（闭集 fail-closed） |
| UI 收集但不提交 | **未发现**（向导四字段均提交） |

**Status：`COMPLETE_V1`**。

---

# 9. Media Security Acceptance

| Control | Classification |
|---------|----------------|
| 大小限制（图 5MiB / PDF 20MiB） | **ACCEPTABLE** |
| MIME magic bytes；不信任扩展名/客户端 Content-Type | **ACCEPTABLE** |
| object key 服务端生成 `sites/{siteId}/{yyyy}/{MM}/{uuid}.{ext}` | **ACCEPTABLE** |
| path traversal（文件名不进 key） | **ACCEPTABLE** |
| 跨站 mediaId → 统一 404（preview/get/delete） | **ACCEPTABLE** |
| SVG/HTML/类脚本拒绝 | **ACCEPTABLE**（防 stored XSS / MIME confusion） |
| visibility PRIVATE + presigned preview | **ACCEPTABLE** |
| delete：DB 软删 + 尽力删对象；失败 → orphan + `object_purged_at` | **DATA CONSISTENCY ISSUE（有补偿）→ ACCEPTABLE for v1** |
| DB 已删 / object 仍在（短暂） | **ACCEPTABLE**（orphan 任务） |
| object 已删 / DB 未打 purged | **DEFENSE_IN_DEPTH**（下轮再扫） |

**未发现 REAL VULNERABILITY（CRITICAL/HIGH）**于 Media 域本身。

**Status：`COMPLETE_V1`**。

---

# 10. Content Acceptance

| Capability | Verdict |
|------------|---------|
| CRUD + status PATCH | **PASS** |
| coverMediaId | **PASS** |
| null clear（`body`/`coverMediaId` `FieldStrategy.ALWAYS`） | **PASS** |
| 跨站 media 引用 | **拒绝**（`requireReadyCoverMediaInSite`） |
| Content 删 vs Media | Media **保留**；删 Media 前检查 content 引用（FK RESTRICT） |
| update 不混 status | **PASS** |

**Status：`COMPLETE_V1`**。

---

# 11. Reservation Acceptance

### 状态机（代码真相）

```text
NEW       → CONFIRMED | CANCELLED
CONFIRMED → DONE | CANCELLED
DONE      → ∅
CANCELLED → ∅
```

（常量名是 `DONE`，不是 `COMPLETED`。）

| Capability | Verdict |
|------------|---------|
| create / list / detail / status / delete | **PASS** |
| 字段 PUT update | **ABSENT** |
| 非法迁移 / 终态再迁 | **拒绝** |
| conflict detection / timezone / public booking | **未实施（FUTURE，非 Bug）** |

**Status：`COMPLETE_V1_ADMIN_SCOPE`**。

---

# 12. Site Auth Acceptance

| Claim | Verdict |
|-------|---------|
| login → SITE access + refresh cookie | **PASS** |
| refresh / logout / `/site/auth/me` | **PASS**（me 查库；roles/perms 固定空） |
| ADMIN token → SITE API | **失败（测覆盖）** |
| SITE token → ADMIN API | **失败（RS aud/subject_type）** |
| claims：aud/subject_type/site_id/iss/exp | **PASS** |
| disabled：login/refresh/me | **一致拒绝**（验密后再 disabled；防枚举） |
| Cookie 隔离 | **PASS**：`MYWEBSITE_REFRESH_TOKEN` path `/api/auth/admin` vs `MYWEBSITE_SITE_REFRESH_TOKEN` path `/api/auth/site` |

**Status：`BACKEND_COMPLETE`**（准确；site-web UI 仍缺）。

---

# 13. Auth Security Acceptance

| Control | Verdict | Rating |
|---------|---------|--------|
| login/register/refresh 分策略 Redis 滑窗 | **代码存在 + 单测绿** | — |
| key：IP / username / 组合；有 TTL expire | **VERIFIED** | — |
| 非全局单 key | **VERIFIED** | — |
| dummy BCrypt 对齐不存在用户路径 | **VERIFIED** | — |
| open-registration 启动 warn | **VERIFIED**（非 fail-fast） | **DEFENSE_IN_DEPTH** |
| Redis fail-closed | **VERIFIED** | **ACCEPTABLE TRADEOFF**（可用性↓ / 安全↑） |
| **IP 可信性（gateway XFF）** | **断裂** | **HIGH（F-ACC-01）** |
| 独立账号锁定 | **未做**（报告已承认） | **DEFERRED / PARTIAL 安全门** |

---

# 14. AI Acceptance

| Layer | Status |
|-------|--------|
| admin-web AI UI | **ACTIVE_MOCK**（`aiApi`→`aiMock`）— **不得标 COMPLETE** |
| Java `HttpAgentClient` → agent-services `/internal/v1/chat` | **REAL path**（token/timeout） |
| 无 Provider Key / non-LIVE | **诚实失败 503 / BLOCKED**（无假成功） |
| agent-services | BlockedProvider + internal auth（相对旧公开 mock 已收紧） |

**Status：`EXTERNAL_BLOCKED`（LIVE）+ FE `PROTOTYPE/ACTIVE_MOCK`**。

---

# 15. Mock Residue

| Domain | Mock Exists | Consumer Exists | Real API Exists | Decision |
|--------|-------------|-----------------|-----------------|----------|
| Site Core | `siteMock`（含死 CRUD） | 仅 `getSiteRuntimeStatus` | `siteApi` FORCE REAL | **SAFE_TO_REMOVE**（非 runtime 方法）；runtime **STILL_REQUIRED** 或删 UI |
| Template / Appearance | 无 | — | REAL | 干净 |
| Media | `fileMock` | 无 | `mediaApi` | **SAFE_TO_REMOVE** |
| Content | `contentMock` | 无 | `contentApi` | **SAFE_TO_REMOVE** |
| Reservation | `reservationMock` | 无 | `reservationApi` | **SAFE_TO_REMOVE** |
| operation/userApi + userMock | 有 | **无页面** | 系统用户走 `rbacApi` | **DANGEROUS_PARALLEL_PATH** / **SAFE_TO_REMOVE** |
| AI | `aiMock` | AI 四页 | 无 admin REAL | **STILL_REQUIRED** |
| Analytics/System mock db | 已删 | EmptyModule | — | 已清 |

**未发现「页面以为 REAL → fallback 落 Mock」的主路径假闭环**（Core FORCE REAL）。orphan 残留属回归隐患 → **MEDIUM（清理）**，非当前可利用 HIGH。

---

# 16. API Contract Review

| Domain | FE Type → Client → DTO → DB | Verdict |
|--------|-----------------------------|---------|
| Site | 对齐；PageResult；version | **MATCH** |
| User | 对齐；列表非分页（有意） | **MATCH** |
| Template | 对齐；无写 API | **MATCH**（范围） |
| Appearance | v1 四字段一致；`heroLayout` | **MATCH** |
| Media | 对齐；preview URL/expires | **MATCH** |
| Content | 对齐；coverMediaId nullable clear | **MATCH** |
| Reservation | 对齐；status=`DONE`；无 PUT | **MATCH**（范围） |

已知：**DUPLICATE** 风险仅在前端死 `userApi`（未接线）。ID `number` vs Java `Long` 为常见 JSON 精度边界（**PARTIAL/ACCEPTABLE**）。

---

# 17. Flyway V5–V15 Review

| Ver | Purpose | Notes |
|-----|---------|-------|
| V5 | Site 权限 + MENU | 职责清晰 |
| V6 | User 管理写权限 | 清晰 |
| V7 | `site_template` + perms | 注释写明无运营 CRUD |
| V8 | `site.template_id` FK RESTRICT + `site_appearance` | v1 config 注释准确 |
| V9 | `media_file` + unique object_key | 清晰 |
| V10 | media perms | 清晰 |
| V11 | `site_content` + cover FK RESTRICT | 清晰 |
| V12 | content perms | 清晰 |
| V13 | `object_purged_at` | orphan 打标 |
| V14 | `site_reservation` | **无冲突唯一约束**（有意） |
| V15 | reservation perms | 清晰 |

- 危险 cascade：**未见**（多用 `ON DELETE RESTRICT`）。  
- Menu 写权限种子仍 ACTIVE 但无写 API：**已知 DEFER**。  
- 权限码与 Controller：主业务域已对齐；Menu 写超前。  
- 本轮**未改**任何 migration。

---

# 18. PostgreSQL Contract Review

| Domain | Important DB Contract | PG Test Exists |
|--------|----------------------|----------------|
| RBAC seed / Flyway V15 | 54 ACTIVE perms；15 migrations | `AdminRbacSeedVerifyPgTest` |
| Site | soft delete / version / site_code unique | `AdminSitePersistenceVerifyPgTest` |
| User | unique / soft delete | `AdminUserPersistenceVerifyPgTest` |
| Template | code unique / status | `AdminTemplatePersistenceVerifyPgTest` |
| Appearance | 1:1 site / JSONB v1 | `AdminAppearancePersistenceVerifyPgTest` |
| Media | object_key unique / purged_at | `AdminMediaPersistenceVerifyPgTest` |
| Content | cover FK / soft delete | `AdminContentPersistenceVerifyPgTest` |
| Reservation | status / site FK | `AdminReservationPersistenceVerifyPgTest` |
| Site module | site_user 等 | `SitePersistenceVerifyPgTest` |

缺口（**TEST_GAP**，非必然 Bug）：gateway↔auth XFF 集成测；浏览器 E2E；Auth VerifyPg；Reservation 冲突（未实现故无测）。

---

# 19. Frontend Real API Review

| Page domain | loading | error/retry | empty | mutation | invalidate | 吞错成 empty？ |
|-------------|---------|-------------|-------|----------|------------|----------------|
| users | ✓ | ✓ | ✓ | pending；**缺 onError toast** | ✓ | **否**（有 isError 分支） |
| sites | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |
| templates | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |
| appearance/theme | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |
| media/files | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |
| content | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |
| reservation | ✓ | ✓ | ✓ | ✓ | ✓ | **否** |

`query.data ?? []` 在 REAL 列表与 `isError` 门控共存——**未发现 CRITICAL 硬吞错**。

---

# 20. Browser E2E Gap

```text
BROWSER E2E COVERAGE: NONE
```

（无 Playwright / Cypress / `*.e2e*`。不得把 MockMvc + `pnpm build` 称为浏览器 E2E。）

### 建议未来 Golden Paths（本轮不实现）

1. Admin 登录 → 创建站点（含 appearance）→ 列表/详情重载仍在  
2. 上传媒体 → preview → 作为 content cover → 删引用保护  
3. Reservation NEW→CONFIRMED→DONE 与非法迁移  
4. 用户创建 / 禁自删 / 角色绑定  
5. ADMIN token 调 SITE API / SITE token 调 ADMIN API 均 401/403  

---

# 21. Architecture Regression

| Area | Verdict |
|------|---------|
| common 污染 | **ACCEPTABLE**（安全契约扩展，无业务 DTO） |
| infra 业务化 | **ACCEPTABLE**（ObjectStorage 基础设施） |
| gateway 业务化 | **无**；但 **缺 XFF 信任配置**（安全配置缺口） |
| admin 多域聚合 | **ACCEPTABLE**（本阶段后台 Resource API） |
| site/admin 职责 | **ACCEPTABLE**（前台 auth/AI vs 后台管理） |
| 重复 Entity 映射（auth/admin/site-core Site） | **ACCEPTABLE** 风险点（漂移需纪律） |
| FE orphan mock / dead userApi | **DUPLICATE / 残留** |
| lazyNamed | **ACCEPTABLE**（非纯转发垃圾） |
| 巨型页（create/contents） | **UNDER-SEPARATED（气味）**，非否决 |

---

# 22. Code Quality

| Item | Note |
|------|------|
| orphan mocks | content/reservation/file/user + siteMock CRUD | **SAFE_TO_REMOVE** |
| `operation/userApi` 仍 export | 无消费者；HTTP 指向可疑 `/site/users` | **SAFE_TO_REMOVE** |
| `apiMode.ts` 注释 | 仍写「多数业务域尚无真实后端」 | **文档漂移** |
| USE_MOCK dev 默认 true | 仅影响 AI/runtime 等非 FORCE REAL | **KEEP 策略可，注释需更新** |
| 双重 Suspense | MainContainer + workspace | **MODERNIZATION** |
| TODO/placeholder 页 | Analytics/System/Dev/Domain/AI workspace | **DEFERRED / PLACEHOLDER（有意）** |

---

# 23. Security Findings

### F-ACC-01 — HIGH — Auth IP / XFF trust

| 字段 | 内容 |
|------|------|
| **Domain** | Auth rate-limit / Gateway |
| **File** | `auth/.../ClientIpUtils.java`；`gateway/.../application.yml`；SCG 4.3.3 `GatewayAutoConfiguration#xForwardedHeadersFilter` |
| **Symbol** | `ClientIpUtils.resolveClientIp`；`TrustedProxies$XForwardedTrustedProxiesCondition` |
| **Evidence** | ① 取 XFF **最后**一段，注释称 gateway 会追加直连 IP。② gateway **无** `spring.cloud.gateway.server.webflux.trusted-proxies`。③ SCG 4.3.3 中 `XForwardedHeadersFilter` 带 `@Conditional(XForwardedTrustedProxiesCondition)`（含 `OnTrustedProxiesNotEmpty`）→ **当前不注册**。④ 客户端 XFF 可原样到达 auth。⑤ `registerAllowed` / `refreshAllowed` **仅 IP**；login 仍有 username 维。⑥ 单测只锁「取末段」语义，不证明网关追加。 |
| **Current Behavior** | 攻击者可轮换伪造 `X-Forwarded-For` 末段，绕过 register/refresh IP 滑窗；login IP/IP+user 维可被稀释。 |
| **Expected Behavior** | 仅信任经 gateway 清洗/追加后的客户端 IP；或忽略不可信入站 XFF。 |
| **Impact** | Phase 1 安全门在 IP 维度失效；注册滥用 / refresh 打点可绕过。 |
| **Confidence** | **HIGH** |
| **Minimal Recommended Fix** | 为 gateway 配置真实前置的 `trusted-proxies`，确认 Filter **append** 对端 IP；或 auth 在无可信代理时只用 `getRemoteAddr()` 并剥离/忽略入站 XFF。补一条经 gateway 的集成断言。 |

### F-ACC-02 — MEDIUM — Execution Report「默认外观」夸大

| 字段 | 内容 |
|------|------|
| **Domain** | Appearance / Docs |
| **File** | `AdminSiteServiceImpl#createSite`；`siteCreatePage.tsx`；Execution Report §3 |
| **Evidence** | 成对提交才 `insertForSite`；无服务端默认 config；向导必带 pair。 |
| **Impact** | 完成度口径误导。 |
| **Confidence** | HIGH |
| **Minimal Recommended Fix** | 报告改为「同事务可选写入客户端 appearance（wizard 必填 pair）」。 |

### F-ACC-03 — MEDIUM / TEST_GAP — 「E2E」命名过度

| 字段 | 内容 |
|------|------|
| **Domain** | Verification |
| **File** | Execution Report §5 / §17 |
| **Evidence** | 无浏览器 E2E；矩阵 E2E ✅ 实为 Vertical/Contract。 |
| **Impact** | 验收读者高估覆盖。 |
| **Confidence** | HIGH |
| **Minimal Recommended Fix** | 列改名；浏览器覆盖标 `NONE`/`TEST_GAP`。 |

### F-ACC-04 — LOW — Soft-delete `deleted_at` 不一致

| 字段 | 内容 |
|------|------|
| **Domain** | Site / Appearance |
| **File** | `AdminSiteServiceImpl#deleteSite`；`AdminAppearanceServiceImpl#softDeleteBySiteId` |
| **Evidence** | 仅 `@TableLogic` 翻 `deleted`；Media/Content/Reservation 显式 `setDeletedAt`。 |
| **Impact** | 审计时间戳不一致；列表隔离仍靠 `deleted`。 |
| **Confidence** | HIGH |
| **Minimal Recommended Fix** | 删除前统一写 `deletedAt`。 |

### F-ACC-05 — MEDIUM — Orphan mock / dead `userApi`

| 字段 | 内容 |
|------|------|
| **Domain** | admin-web |
| **File** | `api/operation/userApi.ts`；`mocks/services/*Mock.ts` |
| **Evidence** | 无页面消费；可被误接回假闭环。 |
| **Impact** | 回归隐患。 |
| **Confidence** | HIGH |
| **Minimal Recommended Fix** | 删除未用 export 与 orphan mock；保留 AI/runtime 明确 mock。 |

### F-ACC-06 — DEFENSE_IN_DEPTH — Open registration warn-only

| 字段 | 内容 |
|------|------|
| **Domain** | Auth |
| **File** | `AdminAuthStartupGuardConfig` |
| **Evidence** | 误开仅 warn；默认 false。 |
| **Minimal Recommended Fix** | prod profile 可选 fail-fast。 |

### F-ACC-07 — LOW — Menu 写权限种子无 API

| 字段 | 内容 |
|------|------|
| **Domain** | RBAC |
| **Evidence** | 已知 `DEFERRED_BY_DESIGN`；非假闭环。 |
| **Minimal Recommended Fix** | 保持延期；实现时扩展现有 Menu API。 |

### F-ACC-08 — LOW — users mutation 无 `onError` toast

| 字段 | 内容 |
|------|------|
| **File** | `userListPage.tsx` status/delete mutations |
| **Minimal Recommended Fix** | 补 `onError` → `toast.error`。 |

---

# 24. Test Gaps

| Gap | Severity |
|-----|----------|
| Gateway↔Auth XFF/IP 集成断言 | **TEST_GAP**（支撑 F-ACC-01） |
| Browser E2E | **TEST_GAP** |
| Auth / Session VerifyPg | **TEST_GAP**（非阻断） |
| Reservation 冲突（未实现） | N/A |
| Media 真 MinIO 集成（若仅 mock storage in unit） | **TEST_GAP**（视测试替身而定） |

---

# 25. Completion Reassessment

| Domain | Scope | Backend | DB | API | Frontend | Security | Tests | Persistence | Status |
|--------|-------|---------|----|-----|----------|----------|-------|-----|-------------|--------|
| Auth Admin session | 登录/刷新/登出/注册门控 | ✅ | ✅ | ✅ | ✅ | ⚠️ IP 限流缺口 | ✅ | ✅ | **PARTIAL**（安全门至 F-ACC-01 修复） |
| Auth Site | SITE JWT | ✅ | ✅ | ✅ | ❌ site-web | ✅ aud | ✅ | ✅ | **BACKEND_COMPLETE** |
| RBAC Role/Perm/Me/Menu读 | 原有 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **COMPLETE_V1** |
| Menu 写 | — | ❌ | 种子 | ❌ | 只读 | — | — | — | **DEFERRED_BY_DESIGN** |
| Admin User | CRUD/status/roles | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **COMPLETE_V1** |
| Site Core | CRUD/status | ✅ | ✅ | ✅ | ✅ | ✅ RBAC | ✅ | ✅ | **COMPLETE_V1** |
| Template | list/status | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **COMPLETE_V1**（无本体 CRUD） |
| Appearance | v1 四字段 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **COMPLETE_V1** |
| Media | 站点媒体 | ✅ | ✅ | ✅ | ✅ | ✅ MIME/归属 | ✅ | ✅ | **COMPLETE_V1** |
| Content | CMS | ✅ | ✅ | ✅ | ✅ | ✅ 跨站 cover | ✅ | ✅ | **COMPLETE_V1** |
| Reservation | 后台工单 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **COMPLETE_V1_ADMIN_SCOPE** |
| AI | LIVE | 路径✅ | — | site✅ | mock | 503 | ✅ | — | **EXTERNAL_BLOCKED** + FE mock |
| Analytics/System/Dev | — | ❌ | ❌ | ❌ | 占位 | — | — | — | **DEFERRED_BY_DESIGN** / PLACEHOLDER |
| FE Lazy routes | 拆包 | — | — | — | ✅ | — | build | — | **COMPLETE_V1** |

禁止使用模糊裸 `COMPLETE` 掩盖范围。

---

# 26. Things That Should NOT Be Changed

- JWT RS256 + JWKS + iss/aud/subject_type 隔离与 refresh 旋转/重放吊销  
- gateway **不做业务鉴权**（但应补 **可信代理/XFF** 配置，属安全配置而非「加业务」）  
- Menu 写 `DEFERRED_BY_DESIGN`；勿平行 Menu 架构  
- 核心域 **FORCE REAL**；勿为统一开关把 Site/Content 等重新包回 `USE_MOCK`  
- AI 无 Key **诚实失败**；勿伪造成功  
- Reservation 冲突/公开预约 **FUTURE**  
- 保留 BCrypt；**不改已执行 Flyway**  
- Appearance v1 闭集四字段（含 `heroLayout` 命名）  
- Media magic-MIME + 跨站 404 + orphan `purged_at` 策略骨架  
- Object key 服务端生成、文件名不进 key  

---

# 27. Required Fixes Before Commit

1. **必须**：修复 **F-ACC-01**（gateway `trusted-proxies` + 确认追加行为，或 auth 忽略不可信 XFF）并补集成断言。  
2. **强烈建议（Commit 前）**：清理 **F-ACC-05** orphan mock / `userApi`。  
3. **建议**：修正 Execution Report/矩阵口径（F-ACC-02/03）；统一 soft-delete `deleted_at`（F-ACC-04）；users `onError`（F-ACC-08）。  
4. **不要**在本验收轮直接改业务功能范围（Menu 写 / AI LIVE / site-web）。

---

# 28. Safe Cleanup Candidates

- `contentMock` / `reservationMock` / `fileMock` / `userMock`  
- `operation/userApi` 与 `api/index` 导出  
- `siteMock` 中未接线的 CRUD（保留 runtime 或一并删 UI）  
- 过时 `apiMode` 注释  
- 可选：去掉 workspace 内层重复 Suspense  

---

# 29. Commit Split Recommendation

本轮**不 Commit**。按依赖（勿拆开强耦合 migration+code）：

1. **security-xff-fix** — gateway trusted-proxies / ClientIp 策略 + 测试（先于或紧贴 auth 限流提交）  
2. **security-auth-gate** — Redis 限流 / dummy hash / open-reg warn / ErrorCode 429（若与 1 同改可合并）  
3. **db-v5-v15 + admin-domains** — Flyway V5–V15 与 Site/User/Template/Appearance/Media/Content/Reservation 后端（强耦合，**勿按表硬拆**）  
4. **site-auth** — SITE login/refresh/logout/me + cookies/Redis  
5. **agent-ai-path** — HttpAgentClient + agent-services internal/blocked + SiteAi  
6. **admin-web-real-closure** — FORCE REAL APIs + REAL 页面三态 + lazy routes（可与命名/casing 大改分两笔：先 rename，再 REAL 接线）  
7. **mock-cleanup** — orphan mock / dead userApi  
8. **docs-audit** — audit/runbook/acceptance 报告  

若 rename/casing 与业务接线已缠在同一 working tree，允许：**rename 提交 → 业务提交**，但 migration 不得脱离对应 Service/Controller。

---

# 30. Final Recommendation

```text
NOT ACCEPTED
```

并列说明：

```text
BLOCKED ON HIGH: Auth client-IP / X-Forwarded-For trust chain under current gateway config
```

```text
AFTER F-ACC-01 (+ recommended cleanup): eligible for commit-split → then next product phase
```

**一句话**：Full Function Implementation 的业务纵向闭环**大体真实完成**，测试门禁复跑为绿；但 Phase 1 安全门的 IP 限流在现网关配置下可被伪造 XFF 绕过，故**不能**按 Execution Report 宣布验收通过或进入下一产品阶段。独立验收到此停止——不修复、不清理、不 Commit。

---

## Appendix A — Verification Commands（本轮已执行）

```bash
cd services/website-services && mvn -B -ntp test
# → BUILD SUCCESS (auth 66, admin 139, site-app 10, …)

cd services/website-services && mvn -B -ntp -Ppersistence-verify-pg test
# → BUILD SUCCESS; Testcontainers postgres:16-alpine; Flyway 15; admin 16 + site 4 verifyPg

cd apps/admin-web && pnpm lint && pnpm build
# → PASS; index-*.js 464.84 kB / gzip 141.41 kB

node scripts/check-imports.mjs          # broken imports: 0
node scripts/check-import-casing.mjs    # casing mismatches: 0
```

## Appendix B — Agents Used

| Agent | Role |
|-------|------|
| [codebase-explorer](f837a123-63ec-4862-aa89-6cef77233c54) | 后端域证据 |
| [codebase-explorer](5a67b8e0-cb6a-4be7-933f-fc577ca4dd89) | 前端/Mock/契约证据 |
| [architecture-planner](7a8d9667-0b67-424c-bd51-477b8e38b25a) | 设计语义（非新重构计划） |
| [integration-reviewer](36a943e7-5207-4f37-a69e-31831811d5ff) | 严重度终审（指出并经本轮复核确认 F-ACC-01） |

## Appendix C — Stop Condition

本文件生成后停止。未修复 Finding、未删除 Mock、未拆 Commit、未开始 Menu 写 / site-web / AI LIVE。
