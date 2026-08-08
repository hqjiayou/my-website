> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/overview/CURRENT_STATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# Cursor Agent / Rules 架构调查报告

> **阶段**：仅调查与报告（Phase 1）  
> **产物唯一允许写入**：本文件  
> **调查日期**：2026-08-08  
> **仓库状态标记**：`WORK IN PROGRESS`（工作区存在大量未提交改动，尤其是 `apps/admin-web` 与部分 `website-services` / `.cursor`）

---

# 1. Executive Summary

**CURRENT ARCHITECTURE（事实）**：`my-website` 是无多站点平台 Monorepo，活跃面主要是 `apps/admin-web`（React/Vite 总后台）与 `services/website-services`（Java 21 / Spring Boot 3.5.14 多模块：gateway / auth / admin / site / common / infra / agent）。前台 `site-web/{car,cake}-web` 与 `agent-services` 仍为骨架或 mock。无根级 pnpm workspace，无 `packages/` / `libs/`。

**当前 Cursor 配置**：初代 `.cursor/agents`（5 个：codebase-explorer、architecture-planner、admin-frontend、admin-backend、integration-reviewer）+ `.cursor/rules`（3 个：`backend-structure`、`admin-web-router`、`admin-web-styles`）+ 根 `AGENTS.md`（仅指向后端结构文档）。Agent 与 Rule 均有未提交/未跟踪状态。

**最大问题**：

1. **文档与代码漂移**：`AGENTS.md` 与 `backend-structure.mdc` 仍写「5E 菜单未实现」，但代码与完成度总览显示 **5E.1–5E.2 菜单只读已落地**。
2. **Agent / Rule 职责边界模糊**：五个 Agent 大量复制「修改边界 / 禁止过度拆分 / 注释规则」等内容，与 Rule、结构总纲重复维护。
3. **Rule 覆盖不完整**：无真正项目级 `alwaysApply` 规则；前端仅有窄 glob 的 router/styles；缺少面向 `apps/admin-web/src/**` 的实现硬约束（API/mock、权限、偏好等）。
4. **WIP 前端 pattern 不稳定**：命名迁移、双轨组件、默认 mock 与真实 RBAC 并存，Rules/Agents 若按 WIP 写死易过时。

**推荐方向（本阶段仅建议，不执行）**：保留 5 个 Agent 角色分工，但 **瘦身 Agent 正文、把硬约束收敛到少量 Rules**；修正过时 5E 表述；新增一条极薄的 project-wide Rule；扩展一条 admin-web 实现 Rule（或合并现有两条窄 Rule 后再补缺口）；**暂不**为 site-web / agent-services / 独立 test-security-database reviewer 建 Agent。

**Complexity Assessment：`BALANCED`**（角色数量合理，但正文重复偏多，需收敛而非再拆更多角色）。

---

# 2. Repository Baseline

| 项 | 值 |
|----|-----|
| Root | `D:/forCode/code/my-website` |
| Repository name | `my-website` |
| Branch | `dev` |
| HEAD | `61ad50489755e6a7beec0e07ef4be6cc13127bb0`（`admin-services调整`） |
| Working Tree | **脏**：`WORK IN PROGRESS` |
| Modified / staged 重点 | `apps/admin-web/**` 大规模重命名与 layout/router/RBAC UI；`services/website-services/admin/**` 菜单相关；多份 docs；`.cursor/rules/*` 已暂存 |
| Untracked 重点 | `.cursor/agents/**`；`docs/admin-web/开发规范.md` |
| Monorepo workspace | **NOT PRESENT**（无根 `package.json`、无 `pnpm-workspace.yaml`） |
| Frontend package manager | 各应用自管 **pnpm**（`apps/admin-web/pnpm-lock.yaml` 等） |
| Maven parent / reactor | `services/website-services/pom.xml` → modules: `gateway`, `common`, `infra`, `agent`, `auth`, `admin`, `site` |

**说明**：下列 Pattern 结论优先基于当前磁盘代码；WIP 变更已标注，**不**自动视为长期稳定规范。

---

# 3. Project Map

```text
my-website/
├── AGENTS.md                          # AI 后端入口约束（过时 5E 表述）
├── README.md                          # 仓库总览（部分路径漂移）
├── .cursor/
│   ├── agents/                        # 5 个初代 Agent（untracked）
│   └── rules/                         # 3 个 Rule
├── apps/
│   ├── admin-web/                     # 总后台（活跃，WIP）
│   └── site-web/
│       ├── car-web/                   # 汽修前台骨架
│       └── cake-web/                  # 蛋糕前台骨架
├── docs/
│   ├── admin-web/                     # 前台后台规范与设计
│   ├── site-web/
│   ├── website-services/              # 后端权威文档体系
│   └── cursor/                        # 本报告目录（本阶段新建）
└── services/
    ├── agent-services/                # Node/Fastify AI mock :3001
    └── website-services/              # Java Maven 多模块主干
        ├── gateway/                   # :8080 统一入口
        ├── auth/                      # :8083 认证
        ├── admin/                     # :8081 后台 API + 默认 Flyway
        ├── site/                      # site-app :8082 + core/car/cake
        ├── common/                    # 横切契约
        ├── infra/                     # Flyway SQL / 基础设施
        ├── agent/                     # Java Agent 客户端桩
        ├── scripts/                   # 本地 seed / 运维脚本
        └── secrets/                   # 本地密钥目录（不写入报告秘密值）
```

| 类别 | 目录 | 职责 |
|------|------|------|
| Frontend | `apps/admin-web` | 管理后台 UI |
| Frontend | `apps/site-web/*` | 行业前台 UI 骨架 |
| Backend | `services/website-services/*` | 平台 API / 网关 / 认证 / 数据 |
| Backend (Node) | `services/agent-services` | AI 编排 mock |
| 数据库 | `infra/.../db/migration` | Flyway 唯一脚本源 |
| 测试 | 各模块 `src/test`；admin H2 RBAC fixtures | 单元/MVC/可选 PG verify |
| 文档 | `docs/**` | 架构、数据字典、联调、作业检查 |
| 基础设施说明 | README + local-stack 外置 | Postgres/Redis/MinIO 不在本仓 Compose |
| Cursor | `.cursor/**` | Agents / Rules |
| **不存在** | `packages/`, `libs/`, 根 `doc/` | README 仍提及部分过时路径 |

---

# 4. Technology Stack

## 4.1 admin-web

| 项 | 技术 | Confidence |
|----|------|------------|
| Framework | React 19 | HIGH |
| Language | TypeScript | HIGH |
| Build | Vite 8 | HIGH |
| Router | react-router-dom 7 | HIGH |
| UI | Tailwind CSS 4 + lucide-react + 自建 admin 组件 | HIGH |
| State | zustand + @tanstack/react-query | HIGH |
| HTTP | axios（集中封装 `httpClient`） | HIGH |
| Form | react-hook-form + zod | HIGH |
| Table | 自建 `DataTable`；依赖含 `@tanstack/react-table` **但未见业务使用** | HIGH |
| i18n | i18next / react-i18next（zh-CN / en-US / ja-JP） | HIGH |
| Lint | ESLint 10 + typescript-eslint | HIGH |
| Formatting | 仓库级 Prettier **NOT PRESENT**（未见根/应用 prettier 配置为硬事实） | MEDIUM |
| Permission | `/me` permissions + `PermissionGuard` / `PermissionGate` | HIGH |
| Theme | CSS tokens + `data-*`（theme/font/density/primary） | HIGH |
| Package manager | pnpm | HIGH |

## 4.2 site-web/car-web & cake-web

| 项 | 技术 | Confidence |
|----|------|------------|
| Framework | React 19 + Vite | HIGH |
| HTTP / Router / i18n 业务接后端 | 基本未接入（无 axios 业务调用） | HIGH |

## 4.3 website-services

| 项 | 技术 | Confidence |
|----|------|------------|
| Java | 21 | HIGH |
| Spring Boot | 3.5.14 | HIGH |
| Build | Maven 多模块 | HIGH |
| Cloud Gateway | Spring Cloud Gateway（gateway 模块） | HIGH |
| DB | PostgreSQL（本地 local-stack） | HIGH |
| ORM | MyBatis-Plus 3.5.16 | HIGH |
| Migration | Flyway（脚本在 infra；默认 admin 执行） | HIGH |
| Redis | 用于 auth Refresh 等 | HIGH |
| Security | auth 签发 JWT RS256；admin/site Resource Server + JWKS | HIGH |
| Validation | Jakarta Validation | HIGH |
| OpenAPI | springdoc-openapi 2.8.17（admin/auth/site 有注解/配置） | HIGH |
| Testing | Spring MVC tests + H2 `@Sql` + 可选 PG verify | HIGH |

## 4.4 agent-services

| 项 | 技术 | Confidence |
|----|------|------------|
| Runtime | Node + TypeScript + Fastify 5 | HIGH |
| 现状 | mock 路由（health / site config / reservations / ai chat） | HIGH |

---

# 5. Application / Service Responsibilities

| 单元 | 职责（CURRENT） | Evidence | Confidence |
|------|-----------------|----------|------------|
| admin-web | 总后台：登录、RBAC UI、布局、大量业务页（多数 mock） | `apps/admin-web/src/**`；`authApi`/`rbacApi` | HIGH |
| car-web / cake-web | 行业落地页 UI 骨架 | `apps/site-web/*/package.json`；无业务 API | HIGH |
| gateway | 统一 HTTP 入口、CORS、RequestId、路径转发 | `gateway/.../application.yml` | HIGH |
| auth | Admin 登录/刷新/登出/注册、JWKS；Site login 501 | auth controllers；`AuthApplication` 排除 Flyway | HIGH |
| admin | Resource Server + RBAC/角色/权限/用户角色/菜单只读 + `/auth/me` | `Admin*Controller` | HIGH |
| site-app | 前台入口骨架（ping / auth/me 占位） | site-app controllers | HIGH |
| site-core | 站点核心 jar | site-core 源码 | HIGH |
| site-car / site-cake | **空壳**（仅 pom） | 无 Java 源 | HIGH |
| common | ApiResponse/ErrorCode/BusinessException/security 契约等 | `common/src` | HIGH |
| infra | Flyway SQL、MyBatis/Redis/i18n/OAuth2 RS 辅助 | `infra/.../db/migration` | HIGH |
| website-services/agent | AgentClient stub | `DefaultAgentClient` | HIGH |
| agent-services | 独立 Node mock 服务 | `services/agent-services/src` | HIGH |

---

# 6. Current Architecture

**只描述现状，不含改进建议。**

```text
Browser
  └─ admin-web (Vite)  baseURL=/api  ──proxy──► gateway:8080
                                                  ├─ /api/auth/**  → auth:8083
                                                  ├─ /api/admin/** → admin:8081
                                                  └─ /api/site/**  → site-app:8082

Authentication: auth 验密并签发 Access JWT（无 roles/permissions）+ Refresh Cookie
Authorization:  admin 验签后查库；@PreAuthorize + AdminPermissionChecker；/me 返回 roles/permissions/menus

Data: PostgreSQL 同库；Flyway 脚本在 infra；默认由 admin 执行
AI:   Java agent 模块 stub；agent-services 独立 mock；未硬接线打通
```

- 前端类型与后端 DTO **各自维护**（无共享 packages）。
- admin-web 业务域默认 `VITE_USE_MOCK !== 'false'` → 内存 mock。
- gateway **不做** JWT 业务鉴权。

---

# 7. Dependency Direction

## 当前实际 Maven 依赖

```text
gateway → common
common  （叶子）
infra   → common
agent   → common
auth    → common, infra
admin   → common, infra
site-core → common, infra
site-car/cake → site-core
site-app → common, infra, agent, site-core, site-car, site-cake
```

## 允许方向（HARD RULE 来源：边界文档 + `backend-structure.mdc` + pom）

- admin / auth / site-* → common、infra（按模块）
- site-app → agent、site-*
- gateway → 网关栈；**代码实际还依赖 common**（RequestId 契约）

## 已明确禁止方向

- admin ↛ auth 实现包；auth ↛ admin 业务包
- common / infra ↛ 业务模块
- agent ↛ infra
- gateway ↛ 业务模块 / DB / Redis
- gateway 不做鉴权业务

## 当前违规方向

- **未发现** admin↔auth Maven 互依赖等硬违规（HIGH）。
- **软漂移**：边界文档写 gateway「可以依赖」仅 Gateway/Reactor，代码依赖 `common`（见 Documentation Drift）。

---

# 8. Existing Code Patterns

## Frontend Patterns

| Pattern | 归类 | Evidence |
|---------|------|----------|
| 页面不直连 axios，走 `api/*` | EXISTING PATTERN（抽样） | 页面 import `@/api`；axios 仅 `utils/http/axios.ts` |
| auth/rbac 真实 HTTP；业务 API `USE_MOCK` 门控 | PROJECT FACT | `authApi.ts`/`rbacApi.ts` vs `siteApi.ts` 等 |
| 静态 Route 表 + 动态菜单 | EXISTING PATTERN + Rule | `adminRoutes.tsx`；`admin-web-router.mdc` |
| zustand（auth/prefs）+ react-query | EXISTING PATTERN | `stores/*`；`queryClient.ts` |
| 偏好集中 localStorage | PROJECT FACT | `config/preferences.ts`；`adminPreferencesStore.ts` |
| camelCase 文件名迁移 | WIP PATTERN | git rename 大量 `*-page.tsx`→`*Page.tsx` |
| 双轨列表头组件 | WIP / 不一致 | `PageHeader` vs `AdminPageHeader`/`DataTable` |
| 业务类型仍出自 `mocks/types` | EXISTING PATTERN（债） | api 再导出 mock types |

## Backend Patterns

| Pattern | 归类 | Evidence |
|---------|------|----------|
| 业务模块标准包树 controller/service/impl/mapper/dto/entity | HARD RULE + FACT | 结构总纲；admin 包结构 |
| Controller 薄、ServiceImpl 主流程、Mapper 仅 SQL | HARD RULE + FACT | Controllers + Services |
| DTO 不放 common | HARD RULE | `backend-structure.mdc` |
| 禁止模糊 Support/Helper/Manager… | HARD RULE | Rule + Agents |
| `@PreAuthorize` + PermissionChecker | PROJECT FACT | admin controllers |
| 写操作 `@Transactional` | EXISTING PATTERN | user-role / role-permission |
| 双轨响应：`HttpMessageBody` 为主，ping 仍 `ApiResponse` | EXISTING PATTERN | controllers |
| auth/admin 各有 `AdminUserEntity` 映射同表 | PROJECT FACT | 两模块 entity |

## Database Patterns

| Pattern | 归类 | Evidence |
|---------|------|----------|
| Flyway 仅在 infra 维护脚本 | PROJECT FACT | `infra/.../db/migration/V1–V4` |
| admin 默认执行；auth/site 默认不执行 | PROJECT FACT | application yml |
| 生产种子含 RBAC/菜单，不含开发用户密码 | PROJECT FACT | V2/V4；seed 在 scripts/local |
| H2 测试 SQL 可 DELETE | PROJECT FACT（仅 test） | `rbac-h2-data.sql` |

## Testing Patterns

| Pattern | 归类 | Evidence |
|---------|------|----------|
| admin MVC + `@Sql` H2 | EXISTING PATTERN | `*MvcTest.java` |
| 可选 PostgreSQL verify | EXISTING PATTERN | `AdminRbacSeedVerifyPgTest` |
| admin-web 自动化测试 | **弱 / UNKNOWN 覆盖** | package.json 无 test script |

## Documentation Patterns

| Pattern | 归类 | Evidence |
|---------|------|----------|
| `docs/website-services` 分「总览 / 设计文档 / 数据字典 / 联调 / 作业検査」 | PROJECT FACT | docs 树 |
| 阶段文档带状态头 | EXISTING PATTERN | 完成度总览等 |
| 根 README 路径偶发过时 | DOCUMENTATION DRIFT | 见 §25 |

---

# 9. Historical Feature Re-Verification

## 9.1 admin-web

| 项 | 状态 | Evidence |
|----|------|----------|
| Admin Layout | IMPLEMENTED（WIP） | `layouts/index.tsx` |
| Header | PARTIAL | 外观/用户/登出 OK；搜索无逻辑 |
| Sidebar | IMPLEMENTED | `menus` → `buildSidebarEntries` |
| MainContainer | IMPLEMENTED | `mainContainer.tsx` |
| Router | IMPLEMENTED | `router/index.tsx` + routes/guards |
| Menu source | IMPLEMENTED | `/admin/auth/me` menus |
| Permission source | IMPLEMENTED | me.permissions |
| RBAC UI users | NOT IMPLEMENTED | `SystemPlaceholderPage` 用户管理 |
| RBAC UI roles | IMPLEMENTED（WIP UX） | role*Page + rbacApi |
| RBAC UI permissions | IMPLEMENTED | permissionListPage |
| RBAC UI menus | PARTIAL | menuTreePage 只读 |
| API service layer | PARTIAL | auth/rbac 真；业务 mock 门控 |
| mock data | ACTIVE MOCK | `mocks/services/*` |
| Promise.resolve fake | MOCK（mockDelay 等价） | mocks utils |
| hardcoded UI | HARDCODED | Dashboard 假统计；Header 搜索 |
| loading/empty/error | PARTIAL | RBAC 较好；部分运营页弱 |
| 401 | IMPLEMENTED | axios refresh + SessionBridge |
| 403 | PARTIAL | ForbiddenPage；ROUTE_PERMISSIONS 覆盖窄 |
| i18n | PARTIAL | 三语底座；roles/menus 等硬编码中文残留 |
| theme light/dark/system | IMPLEMENTED | preferences + theme.css |
| font size / density / primary | IMPLEMENTED | font.css / theme.css dataset |
| local UI preferences | IMPLEMENTED | localStorage |
| backend preference persistence | NOT IMPLEMENTED | preferences.ts 仅注释未来接口 |
| localStorage fallback | IMPLEMENTED | load/save + 旧 locale key |

## 9.2 website-services

| 项 | 状态 | Evidence |
|----|------|----------|
| gateway 统一入口 | IMPLEMENTED（路由层） | application.yml 路由 |
| auth 职责 | IMPLEMENTED（认证） | login/refresh/jwks；无 RBAC CRUD |
| admin 职责 | IMPLEMENTED（后台+RBAC） | roles/permissions/menus/me |
| common | IMPLEMENTED（横切契约） | 无业务 Entity |
| admin 直接认证签发 | 否 | 不签发 JWT |
| auth 承载 admin 业务 | 否 | 仅 admin_user 凭证 |
| 跨模块泄漏 | 低：双写 AdminUserEntity | 有意重复映射 |

## 9.3 RBAC

见 §16 调用链摘要。总体：**后端底座完整；前端路由授权 PARTIAL；菜单写操作未实现**。

## 9.4 UI Preferences

本地完整；后端持久化未实现。

## 9.5 Mock To Real API

auth/rbac：**REAL**；站点/运营/AI/外观等：**ACTIVE MOCK**（默认）。

## 9.6 Database / Flyway

V1–V4 存在；开发用户靠 scripts seed；Flyway 无清理业务表；H2 test 含 DELETE。

---

# 10. Mock Inventory

| Module | File | Type | Consumer | Real API | Backend | Database | Still Used | Recommendation |
|--------|------|------|----------|----------|---------|----------|------------|----------------|
| admin-web | `mocks/services/siteMock.ts` | ACTIVE MOCK | siteApi | No | No CRUD | N/A | Yes（默认） | 保留至真实站点 API |
| admin-web | `mocks/services/templateMock.ts` | ACTIVE MOCK | templateApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/appearanceMock.ts` | ACTIVE MOCK | appearanceApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/contentMock.ts` | ACTIVE MOCK | contentApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/reservationMock.ts` | ACTIVE MOCK | reservationApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/fileMock.ts` | ACTIVE MOCK | fileApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/aiMock.ts` | ACTIVE MOCK | aiApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/analyticsMock.ts` | ACTIVE MOCK | analyticsApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/systemMock.ts` | ACTIVE MOCK | systemApi | No | No | N/A | Yes | 同上 |
| admin-web | `mocks/services/userMock.ts` | LEGACY MOCK / 弱引用 | userApi（无页面消费） | No（用户管理另轨） | 仅 user-role API | admin_user 等 | Weak | 清理或对齐用户管理 |
| admin-web | `mocks/db/*` | ACTIVE MOCK | mock services | N/A | N/A | N/A | Yes | 随 mock 保留 |
| admin-web | `mocks/types/*` | ACTIVE MOCK types | api 再导出 | N/A | N/A | N/A | Yes | 迁出 mock（建议，非本阶段） |
| admin-web | Dashboard i18n 假统计 | DEMO DATA | dashboardPage | No | No | N/A | Yes | 标注或替换 |
| website-services | `scripts/local/seed-admin-*.sql` | DEV SEED | 本地脚本 | N/A | N/A | PG | 手动启用 | 保持与 Flyway 分离 |
| website-services | `admin/.../rbac-h2-*.sql` | TEST FIXTURE | MVC tests | N/A | N/A | H2 | Yes | 勿当业务 mock |
| website-services | Flyway V2/V4 INSERT | DEV/PROD SEED（平台） | Flyway | N/A | N/A | PG | Yes | 非测试用户；保留 |
| agent-services | Fastify mock routes | ACTIVE MOCK | 独立服务 | Partial 设计 | Java stub | N/A | Yes | 独立演进 |
| auth/rbac API | — | REAL DATA | admin-web | Yes | Yes | Yes | Yes | 维持 |

---

# 11. API Contract Review

| API | Frontend | Backend | DB | Status | Drift |
|-----|----------|---------|----|--------|-------|
| `POST /auth/admin/login` | `authApi` AdminLoginInput | `AdminLoginRequestDto` / Response | `admin_user` | CONTRACT MATCH | — |
| `GET /admin/auth/me` | `AdminAuthMeDto` + menus | `AdminAuthMeDto` | user/role/permission | CONTRACT MATCH | — |
| `/admin/roles` CRUD | `RoleDto` / rbacApi | AdminRole*Dto | admin_role* | CONTRACT MATCH | — |
| `GET /admin/permissions` | `PermissionDto` | `AdminPermissionItemDto` | admin_permission | CONTRACT MATCH | 列表字段子集 |
| `GET /admin/menus` | `MenuNodeDto[]` | `AdminMenuNodeDto` | admin_permission MENU | PARTIAL | 形状 MATCH；语义为**全量树**，与 `/me.menus`（按用户过滤）不同，易混 |
| 业务 `/site/*` CRUD | *Api + mock | site-app 无对等 CRUD | 无对应业务表 CRUD | CONTRACT DRIFT / 未实现 | 关 mock 会打不存在的后端 |

---

# 12. Database / Flyway Review

| 项 | 结论 |
|----|------|
| migrations | `infra/.../db/migration`：V1 建表；V2 RBAC 种子；V3 MENU 列；V4 菜单导航种子 |
| dev seed | `scripts/local/seed-admin-hqjiayou-super.sql` + ps1/sh；**不在** Flyway |
| test seed | H2 `rbac-h2-schema.sql` + `rbac-h2-data.sql` |
| H2 destructive | **含 DELETE**（test resources only） |
| Flyway destructive | **无** DELETE/TRUNCATE 清理业务表 |
| PostgreSQL local | 外置 local-stack `local-postgres:5432`；凭据来自 env（报告不输出秘密值） |
| Docker/volume | **本仓无 Compose**；依赖外部 local-stack |
| admin user init | 需手动/脚本 seed；Flyway **不含**测试管理员密码 |
| production migration 测试数据 | 含平台权限/菜单种子；**不含**测试用户账号 |
| Entity/Mapper/migration | 抽查 MENU 字段与 V3 对齐（HIGH） |
| 数据字典 | 位于 `docs/website-services/数据字典/`（根 README 旧路径漂移） |

---

# 13. Existing Cursor Agents

| Agent | Scope | Trigger | Responsibility | Problem | Recommendation |
|-------|-------|---------|----------------|---------|----------------|
| codebase-explorer | 全仓只读 | 定位/盘点/实现 vs mock | 探索与证据输出 | 与 planner 边界分析有重叠文案；正文含大量“不要过度拆分”Rule 内容 | **KEEP** + **MODIFY**（瘦身，指向 Rules） |
| architecture-planner | 全仓规划 | 范围不清时先规划 | 方案/边界/验收；默认不写代码 | 与 explorer/reviewer 重复边界原则；引用过时 5E 语境间接风险 | **KEEP** + **MODIFY** |
| admin-frontend | `apps/admin-web` | 后台 UI 功能 | React 实现；不改 Java/DB | 复制 Rule；缺少对 mock 默认值/路由权限缺口的显式提醒 | **KEEP** + **MODIFY** |
| admin-backend | website-services 侧重 admin+Flyway | 后台 API/迁移 | Java 实现与测试 | description 写 “admin-web real API work” 易越前端界；结构 Rule 大段重复；5E 表述过时 | **KEEP** + **MODIFY** |
| integration-reviewer | 全仓审查 | 改完/合并前 | 只读集成审查 | 检查清单与 Agents 原则重复；无独立价值问题 | **KEEP** + **MODIFY** |

**未存在且评估后不建议新建为长期 Agent**：test-reviewer、security-reviewer、database-reviewer、debug-agent、site-web agent、agent-services agent（成熟度不足 / 职责可并入现有角色）。

---

# 14. Existing Cursor Rules

| Rule | Glob | Always Apply | Purpose | Problem | Recommendation |
|------|------|--------------|---------|---------|----------------|
| `backend-structure.mdc` | `services/website-services/**/*.java` | false | Maven 边界、包树、命名、分层 | 「5E 菜单未实现前勿提前拆层」过时；与结构总纲/Agents 重复但作为短硬约束仍有价值 | **KEEP** + **MODIFY**（更新 5E 状态） |
| `admin-web-router.mdc` | `apps/admin-web/src/router/**/*` | false | router 目录职能 | 范围正确但窄；不覆盖 pages/api 权限映射习惯 | **KEEP**（或 MERGE 进更宽 frontend rule） |
| `admin-web-styles.mdc` | `apps/admin-web/src/assets/styles/**/*` | false | globals/theme/font 边界 | 范围正确；与 router 无冲突 | **KEEP** |
| （缺失）project-wide | — | — | 修改边界 / 证据优先 / 不过度拆分 | 目前只散落在 Agents + AGENTS.md | **CREATE** |
| （缺失）admin-web 实现 | — | — | API/mock、权限、偏好、页面约束 | 仅靠 Agent 正文，编辑非 router/styles 文件时 Rule 不触发 | **CREATE** |
| AGENTS.md | always（仓库指引） | N/A | 指向后端文档 | 5E 过时；几乎无前端指引 | **MODIFY**（文档，非本阶段执行） |

**alwaysApply 审查**：当前三条 Rule 均为 `alwaysApply: false`，技术栈隔离正确。**缺少**真正适合 alwaysApply 的极薄项目规则。

---

# 15. Fact Classification

## A. PROJECT FACT

- Monorepo：`apps` + `services` + `docs`；无 packages/libs。
- gateway:8080 为推荐统一入口；auth/admin/site 可直连。
- JWT 不含权限；admin 查库授权。
- Flyway V1–V4 在 infra；默认 admin 执行。
- admin-web 默认业务 mock；auth/rbac 真 API。
- 工作区 WIP 大量前端重构。

## B. HARD RULE

- 结构总纲 V1 + `backend-structure.mdc`：模块边界、包树、后缀、禁止模糊抽象。
- admin-web-router / styles：目录职能硬约束。
- Agents / AGENTS 要求改 Java 前读结构文档。

## C. EXISTING PATTERN

- FE：api 分层、zustand+RQ、静态路由+动态菜单、偏好 localStorage。
- BE：薄 Controller、ServiceImpl 主流程、springdoc 注解、H2 MVC 测试。
- 注释：关键边界处有模块注释（如 preferences）；非逐行注释为主。

## D. IMPROVEMENT SUGGESTION

- 瘦身 Agents，避免与 Rules 双写。
- 新增 project + admin-web 实现 Rules。
- 修正 5E / README 路径漂移。
- 扩展 `ROUTE_PERMISSIONS`；用户管理页对接后端。
- mock types 迁出；清理弱引用 userMock。
- **不要**仅为“最佳实践”新建 test/security/db Agent。

---

# 16. Problems Found

## Critical

| ID | 问题 | Evidence |
|----|------|----------|
| C1 | Agent/文档引导「5E 未实现」可能阻止正确扩展已存在的 Menu API | `AGENTS.md`；`backend-structure.mdc`「5E 菜单（未实现前…）」vs `AdminMenuController` + 完成度总览 5E.1–5E.2 |

## High

| ID | 问题 | Evidence |
|----|------|----------|
| H1 | Agent 与 Rule 大量重复「边界/拆分/注释」，维护成本高且易漂移 | 五个 `.cursor/agents/*.md` 与三条 rules / 总纲 |
| H2 | 无 admin-web 宽 glob Rule：改 pages/api 时硬约束不自动挂载 | 仅 router/styles globs |
| H3 | AUTHORIZATION DRIFT：前端路由权限映射覆盖远窄于后端 ACTION | `router/permission/constants.ts` 仅 roles/permissions/menus |
| H4 | 默认 mock + 真实 auth 混用，易误判后端完成度 | `VITE_USE_MOCK !== 'false'` |

## Medium

| ID | 问题 | Evidence |
|----|------|----------|
| M1 | README / 完成度总览路径与脚本引用漂移 | 根 README `docs/数据字典`、`doc/`；总览引用已删 `run-4h-*.ps1`（git status） |
| M2 | gateway 依赖文档措辞 vs 实际依赖 common | 边界文档 vs gateway pom |
| M3 | 双轨 UI 组件与 mock types 耦合 | PageHeader vs Admin*；mocks/types |
| M4 | admin-backend description 暗示可做 admin-web API 工作，边界易糊 | agent frontmatter |

## Low

| ID | 问题 | Evidence |
|----|------|----------|
| L1 | `@tanstack/react-table` 未使用 | package.json vs 代码 |
| L2 | Header 搜索为假控件 | header.tsx |
| L3 | Agents 仍 untracked，协作可见性差 | git status `?? .cursor/agents/` |

---

# 17. Recommended Agent Architecture

| Agent | Scope | Trigger | Responsibility | Recommendation | Evidence |
|-------|-------|---------|----------------|----------------|----------|
| codebase-explorer | 全仓只读 | 定位/审计/实现状态 | 证据型探索 | KEEP+MODIFY | 当前活跃开发需盘点 mock/真 API |
| architecture-planner | 全仓方案 | 范围/归属不清 | 规划不默认写码 | KEEP+MODIFY | 前后端+DB 边界复杂 |
| admin-frontend | admin-web | UI/体验/mock→API | 前端实现 | KEEP+MODIFY | 主活跃前端 |
| admin-backend | website-services（主 admin+infra Flyway；auth/gateway/common 仅必要时） | 后台 API/RBAC/迁移 | 后端实现 | KEEP+MODIFY | 主活跃后端 |
| integration-reviewer | 已完成改动 | 合并前 | 只读审查 | KEEP+MODIFY | 契约/边界检查刚需 |
| site-web / agent / test / security / db / debug | — | — | — | **不 CREATE**（现并入上表） | 成熟度或重叠不足 |

### 推荐 Agent 定义摘要

#### codebase-explorer — KEEP + MODIFY

- **Purpose**：只读定位与现状盘点  
- **Forbidden**：改文件  
- **MODIFY**：删除与 Rule 重复的长文规范；保留输出格式与证据要求；明确重新扫描、禁止旧结论  

#### architecture-planner — KEEP + MODIFY

- **Purpose**：最小合理方案与修改边界  
- **Forbidden**：默认大规模改码、管理其他 agent  
- **MODIFY**：方案输出格式保留；硬约束改为「遵守对应 Rules + 结构总纲」引用  

#### admin-frontend — KEEP + MODIFY

- **Purpose**：admin-web 实现  
- **Forbidden**：除非任务明确要求，不改 Java/auth/gateway/DB/RBAC 模型  
- **MODIFY**：补充提醒：区分 ACTIVE MOCK vs REAL API；扩展路由权限时与后端 ACTION 对齐；不要把 WIP 命名当永久真理  

#### admin-backend — KEEP + MODIFY

- **Purpose**：admin（及必要的 infra migration / 明确必要的 auth|gateway|common）实现  
- **Forbidden**：把 RBAC 业务塞进 auth；gateway 加业务；Flyway 塞开发用户密码  
- **MODIFY**：更新 5E：只读已存在，写操作按 5E.3；description 去掉易越界的 admin-web 表述或改为「为 admin-web 提供契约」  

#### integration-reviewer — KEEP + MODIFY

- **Purpose**：合并前只读审查  
- **MODIFY**：清单保留；删除与 Rule 重复的说教；强调 CONTRACT / AUTHORIZATION / Flyway 安全 / mock 残留  

---

# 18. Recommended Rules Architecture

| Rule | Glob | Always Apply | Purpose | Recommendation | Evidence |
|------|------|--------------|---------|----------------|----------|
| `project.mdc`（建议名） | — | **true** | 证据优先、修改边界、不过度拆分、必要注释、禁止把建议当硬规则 | **CREATE** | 五 Agent 重复同一原则；适合全局 |
| `backend-structure.mdc` | `services/website-services/**/*.java` | false | 后端结构硬约束 | **KEEP+MODIFY** | 已有且与总纲对齐；修 5E |
| `admin-web.mdc`（建议名） | `apps/admin-web/src/**/*` | false | API/mock、权限、偏好集中、禁止页面直连 HTTP、复用组件 | **CREATE** | 真实代码 pattern；现有 Rule 不覆盖 |
| `admin-web-router.mdc` | `router/**` | false | router 目录职能 | **KEEP**（或 MERGE 进 admin-web.mdc 的一节） | 已与目录一致 |
| `admin-web-styles.mdc` | `assets/styles/**` | false | 样式三层 | **KEEP** | 已与 README/代码一致 |

**不建议**：为 database/security/testing 各建独立 alwaysApply 长 Rule；不建议把结构总纲全文贴进 Rule。

### Rule 建议细节

#### CREATE `project.mdc`（alwaysApply: true）

- 修改前先读相关代码；结论要有路径证据  
- 只改任务所需模块；禁止无关重构  
- 职责清晰优先、实现相对集中；禁止无意义 Helper/Adapter 拆分  
- 必要处注释（边界/安全/兼容）；禁止逐行废话注释  
- Improvement Suggestion ≠ Hard Rule  

#### MODIFY `backend-structure.mdc`

- 将「5E 未实现前勿提前拆层」改为：「5E.1–5E.2 只读已落地；写操作（5E.3）在现有 Menu* 类型上扩展，禁止另起 processor 包」  

#### CREATE `admin-web.mdc`

- 页面禁止直接 axios/fetch  
- 明确区分 mock 门控 API 与真实 auth/rbac  
- 权限码与展示文案分离；路由权限需显式登记  
- UI 偏好集中 preferences store，禁止拆一堆 theme/density hooks  
- 指向 router/styles 细则，避免复制  

#### KEEP router / styles

- 窄 glob 继续有效；若合并进 `admin-web.mdc`，需保证编辑 styles/router 时约束仍清晰（合并收益有限，**优先 KEEP 分文件**）。

---

# 19. Agent / Rule Responsibility Matrix

| 关注点 | 谁负责 |
|--------|--------|
| 谁来做探索 / 规划 / 实现 / 审查 | **Agent** |
| Maven/包树/后缀/模块禁止项 | **Rule**（backend-structure）+ 结构总纲文档 |
| router / styles 目录职能 | **Rule** |
| API 不直连、mock 边界、偏好集中 | **Rule**（建议 admin-web） |
| 全局修改边界、反过度拆分、注释原则 | **Rule**（建议 project alwaysApply） |
| 具体任务实施方案与文件清单 | **architecture-planner** |
| 改完后契约/边界/测试是否够 | **integration-reviewer** |
| 长篇设计背景 | **docs/**，不塞进 Rule/Agent |

**禁止**：Agent 与 Rule 双份维护同一大段条文。

---

# 20. Recommended Files To Keep

| 文件 | 原因 |
|------|------|
| `.cursor/agents/codebase-explorer.md` | 角色必要 |
| `.cursor/agents/architecture-planner.md` | 角色必要 |
| `.cursor/agents/admin-frontend.md` | 角色必要 |
| `.cursor/agents/admin-backend.md` | 角色必要 |
| `.cursor/agents/integration-reviewer.md` | 角色必要 |
| `.cursor/rules/backend-structure.mdc` | 后端硬约束短版有效 |
| `.cursor/rules/admin-web-router.mdc` | glob 精确 |
| `.cursor/rules/admin-web-styles.mdc` | glob 精确 |
| `AGENTS.md` | 仓库入口；需内容修正 |
| 结构总纲 / 命名规范 / 模块边界 docs | 权威长文，Rule 只摘要 |

---

# 21. Recommended Files To Modify

| 文件 | 修改方向 | 原因 | Evidence |
|------|----------|------|----------|
| 五个 agents | 删重复规范，改为引用 Rules；收紧 description | H1 | agents 正文重叠 |
| `backend-structure.mdc` | 更新 5E 状态 | C1 | Menu API 已存在 |
| `AGENTS.md` | 更新 5E；可补一句前端 Rules 指针 | C1 | 与完成度总览冲突 |
| `admin-backend.md` frontmatter | 避免“改 admin-web”歧义 | M4 | description 原文 |

**本阶段不实际修改。**

---

# 22. Recommended Files To Create

| 文件 | Purpose | Scope | Reason | Evidence |
|------|---------|-------|--------|----------|
| `.cursor/rules/project.mdc` | 全局薄约束 | alwaysApply | 消除 Agent 重复、统一边界 | 五 Agent 同文 |
| `.cursor/rules/admin-web.mdc` | 前端实现硬约束 | `apps/admin-web/src/**` | 编辑页面/API 时自动关联 | mock/api/permission 真实 pattern |

可选（非必须）：`docs/cursor/README.md` 指向本调查与后续重构记录——**非本阶段要求**。

---

# 23. Recommended Files To Remove / Merge

| 文件 | Remove / Merge | 原因 | 风险 |
|------|----------------|------|------|
| 不建议删除五个 Agent | — | 角色边界仍成立 | 删除会导致任务路由真空 |
| `admin-web-router` + `styles` | **暂不强制 MERGE** | glob 不同、冲突少；合并收益低 | 合并后 styles 编辑可能淹没在大 Rule |
| 不建议新建后又删除的临时 Agent | — | — | — |

---

# 24. Final Recommended `.cursor` Structure

```text
.cursor/
├── agents/
│   ├── codebase-explorer.md      # KEEP+MODIFY（瘦身）
│   ├── architecture-planner.md   # KEEP+MODIFY
│   ├── admin-frontend.md         # KEEP+MODIFY
│   ├── admin-backend.md          # KEEP+MODIFY
│   └── integration-reviewer.md   # KEEP+MODIFY
└── rules/
    ├── project.mdc               # CREATE（alwaysApply: true）
    ├── backend-structure.mdc     # KEEP+MODIFY
    ├── admin-web.mdc             # CREATE（globs: apps/admin-web/src/**）
    ├── admin-web-router.mdc      # KEEP
    └── admin-web-styles.mdc      # KEEP
```

说明：不引入 site-web / agent-services 专用 Agent/Rule，直至该表面有稳定业务 pattern。

---

# 25. Documentation Drift

| Document | Current Code | Difference | Severity |
|----------|--------------|------------|----------|
| `AGENTS.md`「5E 菜单未实现」 | `AdminMenuController` + V3/V4 + `/me.menus` | 只读菜单已落地；写操作未做 | Critical（对 AI 行为） |
| `backend-structure.mdc` 5E 段落 | 同上 | 仍按「未实现」约束 | Critical |
| 根 `README.md` `docs/数据字典`、`doc/` | 字典在 `docs/website-services/数据字典/`；无 `doc/` | 路径过时 | Medium |
| `docs/.../模块职责与依赖边界.md` gateway「仅 Gateway/Reactor」 | gateway → common | 「可以依赖」列过严 | Medium（软） |
| `当前功能完成度总览.md` 引用 `run-4h-gateway-auth-flow-verify.ps1` | git 显示该脚本已删 | 联调脚本路径可能失效 | Medium |
| `docs/admin-web/设计文档/*` 部分路径 | 实际 `docs/admin-web/设计文档/` vs README 写 `docs/admin-web/架构设计.md` | 入口路径不一致 | Low–Medium |
| Agents 未跟踪 | 磁盘已有 agents | 协作/评审不可见 | Low |

---

# 26. Evidence Index

| Finding | Evidence | Confidence |
|---------|----------|------------|
| 无根 workspace / packages | `Test-Path` package.json/pnpm-workspace/packages/libs = False | HIGH |
| Maven 模块列表 | `services/website-services/pom.xml` modules | HIGH |
| gateway 路由 | `gateway/.../application.yml` `/api/auth|admin|site` | HIGH |
| Flyway 位置与版本 | `infra/.../db/migration/V1–V4` | HIGH |
| H2 DELETE | `admin/src/test/resources/rbac/rbac-h2-data.sql` | HIGH |
| 5E 只读已实现 | `AdminMenuController.java`；完成度总览「5E.1–5E.2」 | HIGH |
| AGENTS 过时 | `AGENTS.md` L16 | HIGH |
| 默认 mock | `VITE_USE_MOCK !== 'false'` in `*Api.ts` | HIGH |
| 真实 rbac/auth API | `authApi.ts` / `rbacApi.ts` | HIGH |
| 路由权限窄 | `router/permission/constants.ts` | HIGH |
| 偏好仅本地 | `config/preferences.ts` 注释 + localStorage | HIGH |
| Cursor agents 初代 5 个 | `.cursor/agents/*.md` | HIGH |
| Cursor rules 3 个 | `.cursor/rules/*.mdc` | HIGH |
| springdoc 存在 | parent pom + controller 注解 | HIGH |
| site-car/cake 空壳 | 仅 pom，无 java | HIGH |
| WIP | `git status` 大量 admin-web rename + untracked agents | HIGH |

---

# 27. Investigation Uncertainties

| Unknown Item | Why Unknown | What Would Confirm It |
|--------------|-------------|------------------------|
| admin-web 是否配置 Prettier | 未见明确 prettier 配置文件抽查完整 | 搜索 `.prettierrc*` / package scripts |
| site-web 未来路由/i18n 目标形态 | 仅有骨架，文档分散 | 产品阶段文档或首个真实 API 落地 |
| Java agent → agent-services 最终协议 | 仅 stub | 设计文档 + 真实 HTTP client 实现 |
| 生产环境是否强制只走 gateway | 代码允许直连端口 | 部署/Ingress 配置（本仓未含完整部署清单） |
| `@tanstack/react-table` 是否计划使用 | 依赖在但未用 | 团队确认或后续 PR |
| 完成度总览「最近同步 2026-05-23」是否漏更 5E 之后项 | 文档日期旧但正文已写 5E.1–5E.2 | 对照路线图文档全量 diff |

---

# 28. Complexity Assessment

**结论：`BALANCED`**

- **不是 TOO SIMPLE**：已有探索/规划/前后端实现/审查分工，匹配当前双活跃面（admin-web + website-services）。  
- **不是 TOO COMPLEX**：尚未膨胀出 test/security/db/site/agent 等多余长期 Agent；Rule 文件也少。  
- **主要复杂度来自内容重复与过时表述**，应通过瘦身与补齐 1–2 条 Rule 解决，而不是继续加角色。

---

# 29. Final Recommendation

1. **当前 Cursor 架构是否适合项目？**  
   **基本适合**（角色切分对），但作为初代：**规范重复、5E 过时、前端 Rule 覆盖不足**，需要在下一阶段小步重构，而不是推倒重来。

2. **保留**  
   五个 Agent；`backend-structure`、`admin-web-router`、`admin-web-styles`。

3. **修改**  
   瘦身全部 Agent；更新 `backend-structure` 与 `AGENTS.md` 的 5E 表述；收紧 admin-backend description。

4. **合并**  
   暂不强制合并 router/styles Rules；优先把重复原则从 Agents 挪到 Rules。

5. **新增**  
   `project.mdc`（alwaysApply）+ `admin-web.mdc`（宽 glob）。

6. **移除**  
   无必须删除的 Agent/Rule 文件。

7. **下一阶段推荐执行顺序（待用户确认后再做）**  
   1. 修正 `AGENTS.md` + `backend-structure.mdc` 过时 5E（低风险高收益）  
   2. 新建 `project.mdc`  
   3. 新建 `admin-web.mdc`（基于本报告 EXISTING PATTERN，避免把 WIP 写死）  
   4. 瘦身五个 Agent，改为引用 Rules  
   5. （可选）文档路径漂移修复（README / 完成度总览脚本链接）——属 docs，非 Cursor 必做  
   6. **不要**在本轮创建 site-web/agent/test/security/database Agent  

---

**Stop Condition**：本报告已写入 `docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md`。本阶段结束，不执行 Phase 2（不修改 `.cursor/agents`、`.cursor/rules`、业务代码，不 Commit/Push）。
