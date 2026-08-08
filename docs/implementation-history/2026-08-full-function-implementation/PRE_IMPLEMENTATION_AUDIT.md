> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/overview/CURRENT_STATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# Full Functional Implementation & Design Audit — my-website

> 本轮为**只读**审计。基于当前真实代码（Controller / Service / Mapper / Entity / Flyway / Test / 前端 API Client / Page / Mock / Router），文档仅作辅助背景；代码与文档冲突时以代码为准。
>
> 调查方式：`codebase-explorer`（后端清单）+ `codebase-explorer`（前端 / Mock 清单）+ `architecture-planner`（设计与 2026 实践评审）+ 审计者本人对 auth / RBAC / 安全链路 / DB migration / 前端数据来源层的一手复核。
>
> 统计口径贯穿全文：**功能 = 有独立业务意义的能力**，不以 Controller 方法数或 Page 文件数凑数。同一业务功能可包含多个 endpoint。

---

## 1. Executive Summary

- **产品级业务功能总数（统计口径见 §23）：35。**
- **后端真实完成（COMPLETE，含真实逻辑 + DB + 权限 + 测试）：11。**
- **后端占位 / 骨架（PLACEHOLDER / SKELETON）：1（站点前台登录 501 占位；site 前台业务为骨架）。**
- **后端完全未实现（NOT_IMPLEMENTED）：23。**
- **前端 Mock 域（ACTIVE_MOCK，写不持久化）：约 12 个域。**
- **前端 Placeholder / 空壳（EmptyModulePage / 只读占位 / toast 占位 / 硬编码 KPI）：约 12 处。**
- **真正端到端闭环功能（真实 UI + 真实 API + DB 持久化 + 测试）：6。**
  即：管理员登录/会话、当前用户上下文(`/admin/auth/me`)、角色管理 CRUD、角色↔权限绑定、权限只读列表、菜单树只读。
- **当前最大功能风险**：
  1. **认证链缺少限流 / 账号锁定**（`/api/auth/admin/login|register|refresh` 无防暴力）——这是全项目唯一暴露在真实攻击面上的**真实**安全短板（MEDIUM）。
  2. **“假闭环”误导**：新建站点、外观保存在 UI 上表现为成功（toast + invalidate），但仅写内存 mock，刷新即丢失，易被误判为“功能已完成”。
  3. **权限种子承诺了未落地能力**：`admin:user:*`、`admin:site:*`、`admin:dashboard:view`、`admin:menu:create/update/…` 已入库但无对应 endpoint。
  4. **前后端契约漂移**：除 auth/rbac 外，前端所有 `httpService` 契约指向不存在的 `/site/**`、`/agent/**`。

**一句话结论**：这是一个**认证 + RBAC 内核已达生产级质量、其余业务域尚处 Mock / 骨架原型阶段**的多站点平台。真实短板不是“代码写得差”，而是“绝大多数业务后端尚未开工”，且前端已提前铺好一层指向空后端的契约与假闭环。

---

## 2. Functional Architecture Map

```
                          ┌─────────────── apps/admin-web (React 19) ───────────────┐
                          │  Router(全同步) → Guards(Auth/Guest/Permission)          │
                          │  TanStack Query + Zustand(authStore/preferences)        │
                          └───────────────┬───────────────────────┬─────────────────┘
                                          │ REAL_API               │ ACTIVE_MOCK(内存)
                                          │                        │  (http 分支指向不存在端点)
                              /api (gateway 8080: 路由/CORS/RequestId, 不鉴权)
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                            ▼                            ▼
     auth-service(8083)           admin-service(8081)          site-app(8082)  [SKELETON]
     login/refresh/logout         RBAC: role/perm/menu(读)      SitePing / SiteAuthMe(占位)
     register/JWKS                user-role / auth/me           /site/** 业务：无
     RS256 + Refresh 旋转+重放     OAuth2 RS(iss/aud/subject)
              │                            │
              └──────────── PostgreSQL (Flyway V1–V4, 8 张表) ───────────┘
                     site / admin_user / admin_role / admin_permission /
                     admin_user_role / admin_role_permission / site_user / site_user_profile
              Redis: admin refresh 会话 (hash + 旋转 index + 重放 index)

     agent (Java jar): AgentClient.ping() → 硬编码常量  [STUB]
     agent-services (TS/Fastify 3001): chat/reservation/site-config → 全 mock, 无鉴权  [ACTIVE MOCK]

  前端 Mock 域(无真实后端): site / template / appearance / content / reservation /
                           file / ai / analytics / system / user
```

**真实数据流只有一条闭环**：`admin-web → gateway → auth/admin service → PostgreSQL/Redis`（仅 Auth 与 RBAC）。其余前端域的数据流终止于浏览器内存中的 mock store。

---

## 3. Backend Feature Inventory

Status：COMPLETE=端到端+测试；IMPLEMENTED=有真实逻辑（前端接入未定）；PARTIAL；BACKEND_ONLY=后端有前端无；SKELETON=仅骨架；PLACEHOLDER=占位常量；NOT_IMPLEMENTED=无实现。

| ID | Domain | Feature | API | Service | DB | Permission | Tests | Status |
|----|--------|---------|-----|---------|----|-----------|-------|--------|
| A1 | Auth | Admin Login | `POST /api/auth/admin/login` | ✅ BCrypt+状态校验+签发 | admin_user | permitAll | ✅ happy/invalid/i18n/JWT | COMPLETE |
| A2 | Auth | Refresh(旋转+重放吊销) | `POST /api/auth/admin/refresh` | ✅ | Redis/内存 | permitAll | ✅ happy/replay/unknown/no-cookie | COMPLETE |
| A3 | Auth | Logout | `POST /api/auth/admin/logout` | ✅ 吊销+清cookie(幂等) | Redis/内存 | permitAll | ✅ | COMPLETE |
| A4 | Auth | Register(bootstrap/open门控) | `POST /api/auth/admin/register` | ✅ 通道门控+密码策略+唯一 | admin_user | permitAll | ✅ | COMPLETE |
| A5 | Auth | JWKS 公钥 | `GET /.well-known/jwks.json` | ✅ RS256 公钥集 | — | public | ✅ | COMPLETE |
| A6 | Auth | Site(前台)Login | `POST /api/auth/site/login` | ❌ 返回 501 常量 | — | permitAll | — | PLACEHOLDER |
| R1 | RBAC | Role CRUD | `GET/POST/PUT/DELETE /api/admin/roles[/{id}]` | ✅ 校验/唯一/super保护/在用拒删 | admin_role | 4 role 码 | ✅ 全覆盖 | COMPLETE |
| R2 | RBAC | Role↔Permission 绑定 | `GET/PUT /api/admin/roles/{id}/permissions` | ✅ 事务替换+ACTIVE校验+super保护 | admin_role_permission | role:permission:view/update | ✅ | COMPLETE |
| R3 | RBAC | Permission 列表(只读) | `GET /api/admin/permissions` | ✅ 过滤查询 | admin_permission | permission:list | ✅ | COMPLETE(只读) |
| R4 | RBAC | User↔Role 绑定 | `GET/PUT /api/admin/users/{id}/roles` | ✅ 事务替换+自改禁止+super保护 | admin_user_role | user:role:view/update | ✅ 全覆盖 | COMPLETE(后端)/前端缺 UI |
| R5 | RBAC | Menu 树(只读) | `GET /api/admin/menus` | ✅ 组装树 | admin_permission(MENU) | menu:list | ✅ | COMPLETE(只读) |
| R6 | RBAC | 当前用户上下文 | `GET /api/admin/auth/me` | ✅ 用户+角色+权限+可见菜单(请求级快照) | 多表 | isAuthenticated | ✅ 含角色变更即时生效 | COMPLETE |
| R7 | RBAC | Menu 写(create/update/delete/sort/bind) | ❌ 无 Controller | ❌ | admin_permission | menu:* **已种子** | — | NOT_IMPLEMENTED |
| R8 | RBAC | Admin 用户账号 CRUD | ❌ 无 Controller | ❌(仅 RBAC 关联) | admin_user | user:list/detail **已种子** | — | NOT_IMPLEMENTED |
| R9 | RBAC | Dashboard 数据 | ❌ 无 Controller | ❌ | — | dashboard:view **已种子** | — | NOT_IMPLEMENTED |
| S1 | Site | Ping(含 agent 探活) | `GET /api/site/ping` | ⚠️ 调 `agentClient.ping()`→硬编码 | — | permitAll | smoke | IMPLEMENTED(依赖 stub) |
| S2 | Site | 前台用户上下文 | `GET /api/site/auth/me` | ❌ 硬编码 ACTIVE/SITE_USER/[] | 不查库 | authenticated | — | PLACEHOLDER |
| S3 | Site | 站点管理 CRUD | ❌ 无 Controller/Service | 仅 Entity+Mapper | site | site:list/detail **已种子** | 持久层PG测试 | NOT_IMPLEMENTED(表已备) |
| S4 | Site | 前台业务(car/cake) | ❌ | 仅实体+Mapper | site_user/profile | — | 持久层PG测试 | SKELETON |
| G1 | Gateway | 路由/CORS/RequestId | 全局 | ✅ | — | — | ✅ | COMPLETE(横切) |
| X1 | Agent(Java) | AgentClient | 接口 | ❌ 返回常量 | — | — | — | HARDCODED STUB |
| X2 | agent-services(TS) | chat/reservation/site-config | Fastify 路由 | ❌ 全 mock | 无 | 无鉴权 | — | ACTIVE MOCK |

**后端完全不存在的业务域（无 Controller / 无 Service / 无表）**：Content/CMS、Reservation（Java 侧）、File/Media（仅 MinIO 配置，无表无上传接口）、Template、Appearance/Theme、AI/Chat（Java 侧）、Analytics、System Settings、多域名(site_domain)、审计日志。

---

## 4. Backend Completion

| 层 | 结论 |
|----|------|
| API | Auth/RBAC endpoint 完整；请求/响应统一 `HttpMessageBody`；HTTP 语义正确（401/403/409/501）；Jakarta `@Valid` + `@RequestBody` 校验。 |
| Domain Logic | Auth/RBAC 有真实业务逻辑（无常量占位）：登录校验、refresh 旋转、角色唯一/在用保护、事务性权限替换、自改禁止、super_admin 短路。 |
| Persistence | 8 张表 + 触发器 + 软删 partial unique + 乐观锁 `@Version` + FK `ON DELETE RESTRICT`；Flyway 幂等种子。仅覆盖 site 主数据 + admin RBAC + site_user。 |
| Authorization | Method Security `@PreAuthorize("@adminPermissionChecker.hasPermission(...)")` + Resource Server 三重 JWT 校验(iss/aud=ADMIN/subject_type=ADMIN)。 |
| Test | Auth MockMvc（i18n 参数化/重放/流程）、RBAC MockMvc（401/403/happy/冲突/保护）、Testcontainers PG 持久层、种子校验 PG。Admin RBAC MVC 用 H2 schema（有独立 PG 校验补足）。 |

**未完成域**：所有 NOT_IMPLEMENTED 项在 API / Service / DB 三层均缺席（Site 管理与 Admin 用户仅 DB 表存在）。

---

## 5. Backend Logic Issues

| # | Feature | File / Symbol | Evidence | Current Behavior | Expected/Better | Impact | Severity | Confidence |
|---|---------|---------------|----------|------------------|-----------------|--------|----------|------------|
| L1 | Register 并发 | `AdminAuthServiceImpl.ensureRegistrationChannelOpen` | `selectCount==0` 判定 bootstrap | 两个并发 bootstrap 均见 0，可各建一个首账号（同名被唯一索引挡，不同名可双开） | bootstrap 用唯一约束/串行化或首账号后强制关闭 | 极低（仅初始化窗口） | LOW | MEDIUM |
| L2 | 权限码 vs endpoint | `V2/V4` seed vs 无 Controller (R7/R8/R9) | `admin:user:*`、`admin:site:*`、`admin:dashboard:view`、`admin:menu:create/…` 已入库 | 权限体系“承诺”了未落地功能 | 种子与 endpoint 同步演进，或标注为“预留” | 中：前端若按码渲染将指向死接口 | MEDIUM | HIGH |
| L3 | Site 前台鉴权链断裂 | `AuthSiteLoginController`(501) + `SiteAuthMeController`(硬编码) | 无任何路径签发 SITE 受众 token，但 `/site/auth/me` 需 authenticated | 前台鉴权整体不可用（占位） | 前台业务开工时统一设计 site 登录 → SITE token → auth/me | 中：site 前台域尚不可用 | MEDIUM | HIGH |
| L4 | Site Ping 依赖 stub | `SitePingController` → `DefaultAgentClient.ping()` | 返回硬编码 `"agent module ready"` | 探活不反映真实 agent 状态 | agent 真实化后替换 | 低 | LOW | HIGH |

> 未发现事务、软删绕过、乐观锁误用等既存逻辑缺陷；RBAC 的删除/替换语义正确（软删 + 在用保护 + 事务替换）。

---

## 6. Backend Security Review

**总体：无 CRITICAL / HIGH 真实可利用漏洞。** 认证与授权内核达到生产级：

- JWT：RS256 + `kid` + JWKS 公钥分发；Resource Server 校验 `iss` + `aud=ADMIN` + `subject_type=ADMIN` 三重 `JwtClaimValidator`。
- Refresh：不透明随机 token，**仅存 SHA-256 哈希**，HttpOnly + Secure(默认) + SameSite=Lax，path 限定 `/api/auth/admin`；每次刷新**旋转**并把旧 token 写入 replay index，重放即**吊销整会话**（RFC 6819 / OAuth refresh-rotation BCP）。
- 授权：`@PreAuthorize` + `AdminPermissionChecker`（super_admin 短路），请求级权限快照（角色变更即时生效，已测）；user↔role 替换禁止“自改”、保护 super_admin。
- 数据：软删 + 乐观锁 + FK RESTRICT + partial unique。

| # | 类型 | File/Symbol | 攻击路径/缺口 | Severity | Confidence |
|---|------|-------------|--------------|----------|------------|
| SEC1 | 缺限流/账号锁定 | `AuthSecurityConfig`(auth 链 permitAll) + gateway 不限流 | 对 `/login`、`/register`、`/refresh` 在线暴力破解 / 注册滥用无任何速率约束或失败锁定 | **MEDIUM**（真实威胁面，非纵深） | HIGH |
| SEC2 | 误配放大 | `application.yml` `admin-open-registration-enabled` | 若误置 `true`，任意人可自注册管理员 | MEDIUM（条件性；默认 false + bootstrap 仅零管理员，已有 console.error 类告警缺失） | HIGH |
| SEC3 | 用户名枚举时间侧信道 | `AdminAuthServiceImpl.login` | user==null 立即拒绝，命中用户才 `matches`，响应时间差异可枚举用户名（错误消息本身已统一，无消息侧信道） | LOW / DEFENSE_IN_DEPTH | HIGH |
| SEC4 | bootstrap 竞态 | 见 L1 | 同上 | LOW / DEFENSE_IN_DEPTH | MEDIUM |
| SEC5 | agent-services 无鉴权 | `agent-services/src/routes/*` | 独立 Node 服务全 mock、无鉴权；当前不在真实数据流中 | LOW（尚未接入生产） | HIGH |

**结论**：唯一需在生产前处理的真实项是 **SEC1（认证限流 + 账号锁定）**；SEC2 是运维误配防护；SEC3/SEC4 属纵深防御。

---

## 7. Backend 2026 Practice Review

| 领域 | 现状 | 评级 |
|------|------|------|
| Java 21 + Spring Boot 3.5 + OAuth2 Resource Server（JWKS/RS256 + `JwtClaimValidator` + `@EnableMethodSecurity`） | 现代标准 | GOOD CURRENT PRACTICE |
| Refresh 旋转 + 重放吊销 + 哈希存储 + HttpOnly/SameSite | 优于常见实现 | GOOD CURRENT PRACTICE |
| MyBatis-Plus `@TableLogic` / `@Version` / partial unique / 触发器 | 规范 | GOOD |
| Flyway 集中 infra + 幂等种子 + 开发/生产分离 | 规范 | GOOD |
| 测试：Auth/RBAC MockMvc + Testcontainers PG 持久层 + 种子 PG 校验 | 全面 | GOOD |
| Admin RBAC MVC 用 H2（非 Testcontainers） | 有 PG 校验补足，方言差异风险可控 | ACCEPTABLE |
| Controller/Service/Mapper 职责边界（HTTP / 业务 / 持久） | 清晰、无空转发 Helper | GOOD |
| BCrypt 密码哈希 | 可用（Argon2id 更前沿，但非必须） | ACCEPTABLE |
| 认证限流 / 账号锁定 | 缺失 | ACTUAL PROBLEM（生产前补） |
| record/immutable DTO、结构化日志 + requestId(MDC) | 已具备 | GOOD |
| site / agent 业务域 | 占位/全 mock | MODERNIZATION OPPORTUNITY（尚未开工，非债务） |

> 不建议因“有更新 API”而重写任何 auth/RBAC 代码。

---

## 8. Frontend Feature Inventory

Data Source：REAL_API / ACTIVE_MOCK（内存，写不持久）/ HARDCODED / EMPTY_SHELL。

| Domain | Feature | Page | Data Source | 后端存在 | Match |
|--------|---------|------|-------------|---------|-------|
| Auth | 登录/记住用户名/密码显隐 | `login/loginPage.tsx` | REAL_API | 是 | MATCH |
| Auth | 会话恢复/401 刷新/登出 | `stores/authStore.ts`+`utils/http/axios.ts` | REAL_API | 是 | MATCH |
| RBAC | 角色列表/增改删/停启用 | `roles/roleListPage.tsx`+`roleEditPage.tsx` | REAL_API | 是 | MATCH |
| RBAC | 角色权限分配 | `roles/rolePermissionPage.tsx` | REAL_API | 是 | MATCH |
| RBAC | 权限列表 | `permissions/permissionListPage.tsx` | REAL_API | 是 | MATCH |
| RBAC | 菜单树(只读) | `menus/menuTreePage.tsx` | REAL_API | 是 | PARTIAL(无写) |
| RBAC | 用户↔角色分配 | — 无专用页 | — | 是(后端) | BACKEND_ONLY |
| System | 用户管理 | `SystemPlaceholderPage`(“功能开发中”) | EMPTY_SHELL | 否 | FRONTEND_ONLY |
| Dashboard | KPI/趋势/提醒 | `dashboard/dashboardPage.tsx` | HARDCODED | 否 | FRONTEND_ONLY |
| Site | 站点列表 | `apps/sites/sitesPage.tsx` | ACTIVE_MOCK | 否 | MOCK_ONLY |
| Site | 新建站点向导(4步) | `apps/sites/siteCreatePage.tsx` | ACTIVE_MOCK(假闭环) | 否 | MOCK_ONLY |
| Site | 工作区概览/发布清单 | `siteOverviewPage.tsx` | ACTIVE_MOCK | 否 | MOCK_ONLY |
| Site | 基础/主题/域名“设置” | `siteBasicPage/siteThemePage/siteDomainPage.tsx` | ACTIVE_MOCK(只读展示) | 否 | MOCK_ONLY |
| Site | 内容管理 | `siteContentsPage.tsx` | ACTIVE_MOCK(读)+toast占位(写) | 否 | MOCK_ONLY |
| Site | 预约/AI/文件(工作区) | `siteReservationsPage/siteAiPage/siteFilesPage.tsx` | EMPTY_SHELL | 否 | FRONTEND_ONLY |
| Template | 列表/详情 | `apps/templates/*.tsx` | ACTIVE_MOCK | 否 | MOCK_ONLY |
| Appearance | 风格设置(主题/布局/区块+保存+预览) | `apps/styleSettingsPage.tsx` | ACTIVE_MOCK(假闭环) | 否 | MOCK_ONLY |
| Operation | 内容/预约/文件 列表 | `operation/*.tsx` | ACTIVE_MOCK(只读) | 否 | MOCK_ONLY |
| AI | 设置/提示词/知识库/会话 | `ai/*.tsx` | ACTIVE_MOCK(只读) | 否 | MOCK_ONLY |
| Config | 参数配置 | `config/paramsConfigPage.tsx` | EMPTY_SHELL | 否 | FRONTEND_ONLY |
| Governance | 数据分析/团队/系统设置 | `analytics/teamPage/systemSettingsPage.tsx` | EMPTY_SHELL | 否 | FRONTEND_ONLY |
| Governance | 开发者中心 | `developer/developerPage.tsx` | HARDCODED+ACTIVE_MOCK | 否 | MOCK_ONLY |

**site-web（cake-web/car-web）**：静态营销单页骨架，无 router、无 API client、无后端调用 → DemoOnly / UI Shell。

---

## 9. Frontend Completion

- **UI**：RBAC 页面完整（`DataTable` 三态、分页、搜索、`PermissionGate` 按钮级权限）；标准不统一——`permissionListPage` 有完整 loading/empty/error，而 `sitesPage`/operation/*/ai/* 仅 `data=[]` 默认值、缺 error/empty 态。
- **API**：仅 auth/rbac 真实；其余 mock。
- **Contract**：真实域与后端 DTO 一致；mock 域 `httpService` 契约指向不存在端点。
- **State**：`authStore`(Zustand) 存会话/身份（合理），业务数据走 TanStack Query（未把 server state 误存 Zustand）。
- **Form**：RHF + Zod 用于登录、角色编辑；mock 域多为只读或占位。
- **Permission**：路由权限映射仅覆盖少数 RBAC 路由；业务路由仅校验登录态。
- **Error**：401/刷新在 axios 拦截器统一处理；mutation onError 走全局 `handleApiError`。
- **Persistence**：仅 RBAC 真持久；site 创建、外观保存为内存假闭环。
- **i18n**：i18next 覆盖良好，enum 经 `enumLabel`/`formatResourceType` 转文案。
- **A11y**：基础可用（label/语义），未专项审计。

---

## 10. Backend ↔ Frontend Mapping

| Domain | Frontend Feature | Backend Feature | API | Current Data Source | Match |
|--------|------------------|-----------------|-----|---------------------|-------|
| Auth | 登录/会话 | A1/A2/A3/R6 | `/auth/admin/*`,`/admin/auth/me` | REAL_API | MATCH |
| RBAC | 角色/权限/菜单读 | R1/R2/R3/R5 | `/admin/*` | REAL_API | MATCH |
| RBAC | 用户↔角色 | R4 | `/admin/users/{id}/roles` | 无前端页 | BACKEND_ONLY |
| Auth | 管理员注册 | A4 | `/auth/admin/register` | 无前端页(仅 API) | BACKEND_ONLY |
| Site | 站点管理/创建 | — (S3 仅表) | `/site/sites*`(不存在) | ACTIVE_MOCK | MOCK_ONLY / FRONTEND_ONLY |
| Template/Appearance/Content/Reservation/File | 各页 | — | `/site/*`(不存在) | ACTIVE_MOCK | MOCK_ONLY |
| AI | 4 页 | — | `/agent/*`(不存在) | ACTIVE_MOCK | MOCK_ONLY |
| Analytics | 空壳 | — | `/site/analytics`(不存在) | 死代码 | MOCK_ONLY |
| System/Team/Params | 空壳 | — | — | EMPTY_SHELL | FRONTEND_ONLY |
| Menu 写 | 页面标注“待 5E.3” | R7(未实现) | — | 无 | 双缺 |

---

## 11. Mock Inventory

所有 mock 服务模式一致：`mocks/db/*` 静态种子 → 复制进内存 store → `mockDelay(~300ms)` → 写操作仅改内存（刷新回初始）。开关 `src/config/apiMode.ts`（dev 默认 mock，prod fail-safe 默认真实）。

| Mock 域 | 当前用途 | 真实后端 | 真实 API | 应否 API 化 | API 化前置条件 |
|---------|---------|---------|---------|-------------|----------------|
| site | 列表/创建/更新/运行状态 | 否（仅 site 表） | 否(`/site/sites`) | **BACKEND_FIRST** | 先实现 Site Controller/Service（表已备） |
| template | 列表/详情 | 否 | 否 | BACKEND_FIRST | 需 template 表 + 接口 |
| appearance | 风格读/写 | 否 | 否 | BACKEND_FIRST + CONTRACT_FIRST | 需 appearance 表；字段模型需定稿 |
| content | 列表/创建/更新 | 否 | 否 | BACKEND_FIRST + CONTRACT_FIRST | 需 CMS 模型 |
| reservation | 列表 | 否(Java)；TS mock | 否 | BACKEND_FIRST | 需 reservation 表 + 接口 |
| file | 列表 | 否(仅 MinIO 配置) | 否 | CONTRACT_FIRST | 需存储元数据表 + 上传接口 |
| ai | 配置/提示词/知识库/会话 | 否(agent 骨架) | 否 | BACKEND_FIRST | agent-services 尚未真实化 |
| analytics | 分析汇总 | 否 | 否 | **REMOVE**（死代码，无页面引用） | 需求确立后再重建 |
| system | 参数/开发者资料 | 否 | 否 | KEEP_MOCK_TEMPORARILY | 低优先 |
| user | 站点级用户/角色 | 否 | 否 | KEEP_MOCK_TEMPORARILY / CAUTION | **与 admin RBAC 是两套模型，勿混淆** |

---

## 12. Fake / Placeholder Features

- **EmptyModulePage 空壳（4）**：analytics、team、systemSettings、paramsConfig。
- **工作区占位页（不请求数据，3）**：siteReservations、siteAi、siteFiles。
- **只读伪装“设置页”（无表单/无保存，3）**：siteBasic(`pendingSave`)、siteTheme(`themeFuture`)、siteDomain(`domainFuture`)。
- **按钮无持久化（toast.info 占位）**：siteContents（新增/编辑/预览）、styleSettings（预览站点）、templateDetail（editorPlaceholder）。
- **假 KPI/图表（HARDCODED）**：dashboard（KPI 来自 i18n 常量，趋势图为空 dashed div，无图表库）；siteOverview（KPI 来自 mock，新站点恒 0）。
- **系统占位组件**：`systemPlaceholderPage`（硬编码“功能开发中”，被 `/system/users` 复用）。
- **死代码 / 未接线**：`analyticsApi` 全链路无引用；`contentApi.create/update`、`siteApi.updateSite` 等 mock 写方法 UI 未接线。
- **假闭环（成功但不持久）**：新建站点、外观保存——mutation 成功 + toast + invalidate，但仅内存，刷新即丢。

分类小结：REAL FEATURE = Auth/RBAC；PARTIAL = 菜单只读、内容只读列表；FAKE/PLACEHOLDER = 上述 toast/空壳/只读设置；DEMO ONLY = site-web、dashboard KPI；UI SHELL = EmptyModulePage 组。

---

## 13. Functional Logic Issues

| # | Feature | Evidence | Impact | Severity | Confidence |
|---|---------|----------|--------|----------|------------|
| FL1 | 假闭环误导 | siteCreate / styleSettings 写内存 mock | 用户/开发者误判“已保存”，刷新丢失 | MEDIUM | HIGH |
| FL2 | 死代码 | `analyticsApi` 无页面引用 | 维护噪音、类型泄漏 | LOW | HIGH |
| FL3 | 重复请求 | `SiteContentsPage`(`['sites',id,'contents-page']`) 与 `SiteWorkspaceLayout`(`['sites',id]`) 各拉一次同站点 | 冗余请求（mock 下无害，真实下浪费） | LOW | MEDIUM |
| FL4 | 列表页缺三态 | sites/templates/operation/ai 页仅默认空数组 | 真实 API 化后无 error/empty 反馈 | LOW→MEDIUM(接真实后) | HIGH |
| FL5 | 概念混淆风险 | mock `user/role` vs 真实 admin RBAC | 迁移时可能把站点用户误当后台角色 | MEDIUM(迁移期) | HIGH |

---

## 14. Requirement / Implementation Drift

- **IMPLEMENTATION_DRIFT（权限种子 vs endpoint）**：`admin:user:*`/`admin:site:*`/`admin:dashboard:view`/`admin:menu:create/…` 已入库但无接口（承诺未兑现）。
- **IMPLEMENTATION_DRIFT（前端契约 vs 后端）**：所有 mock 域 `httpService` 指向不存在的 `/site/**`、`/agent/**`。
- **REQUIREMENT_DRIFT（菜单双维护）**：侧栏来自后端 `/admin/auth/me`，顶栏标题来自前端静态 `navigation/menu.ts`，需手工保持一致。
- **文档漂移**：本轮以代码为准；文档若声称 site/content/AI 等“已实现”，与代码冲突，以代码为准（未实现）。

---

## 15. API Contract Issues

- **真实契约一致**：auth/rbac 前后端 DTO 与 `HttpMessageBody<T>` 包装一致，`unwrapData` 正确。
- **虚构契约**：mock 域的 `xxxHttpService` 定义了后端不存在的端点与形状；这些是“前端单方面约定”，未经后端确认，属 CONTRACT_FIRST 待办（迁移前必须以真实后端 DTO 为准，不得直接沿用 mock 形状落库）。
- **ID 类型风险**：真实 RBAC 用 `long/BIGINT`；mock 用字符串 id（如 `createMockId('site')`）。未来 Site 后端应使用 BIGINT，前端需对齐类型，避免 mock 字符串 id 泄漏进真实契约。

---

## 16. Authorization Review

| 面 | 结论 | 评级 |
|----|------|------|
| 后端 Method Security | `@PreAuthorize` 全覆盖受保护 endpoint；权限码与种子一致；super_admin 短路 | GOOD（权威授权在后端） |
| Resource Server 验签 | iss + aud=ADMIN + subject_type=ADMIN 三重校验 | GOOD |
| 对象级/自改保护 | user↔role 替换禁止自改、保护 super_admin；角色在用拒删 | GOOD |
| 前端路由守卫 | 仅覆盖少数 RBAC 路由，业务路由只校验登录态 | **DEFENSE_IN_DEPTH / UX**（后端权威 + 业务路由无真实后端，非越权漏洞） |
| 菜单可见性 vs 授权 | 菜单来自后端可见性；能否调 API 由后端权限决定，二者已分离 | GOOD |

**重要判定**：前端路由权限覆盖不全**不构成越权漏洞**——后端做最终授权，且未受保护的业务路由背后并无真实后端可越权访问。记为 `DEFENSE_IN_DEPTH / UX`。

---

## 17. Data Persistence Review

- **真持久**：admin_user / admin_role / admin_permission / 关联表（RBAC 全链路）；Redis refresh 会话。
- **不持久（内存 mock）**：site 创建、appearance 保存、以及全部 mock 域写操作。
- **表已备但无写路径**：`site`（有表、有 Mapper、无 Service/Controller）、`admin_user`（仅 RBAC 关联，无账号 CRUD）、`site_user`/`site_user_profile`（骨架）。
- **完整性机制**：软删（`deleted` + partial unique）、乐观锁（`@Version`）、FK RESTRICT、触发器维护 `updated_at`——已就位，等待业务 Service 使用。

---

## 18. Error / Edge Case Review

- **后端**：统一异常 → `HttpMessageBody` + i18n 消息；401/403/409/501 语义正确；refresh 失败清 cookie；logout 幂等。覆盖 invalid/permission/replay/冲突等边界（有测试）。
- **前端**：401/刷新队列在 axios 拦截器统一；mutation onError 全局处理。**短板**：mock 列表页普遍缺 empty/error/retry UI；`isError` 未渲染。真实 API 化时必须补齐。

---

## 19. Frontend 2026 Practice Review

| 项 | 现状 | 评级 |
|----|------|------|
| server state 归属 | 业务走 TanStack Query，未误存 Zustand | GOOD |
| 认证错误统一处理 | axios 拦截器 + 全局 handleApiError | GOOD |
| RHF + Zod | 登录/角色编辑用 zodResolver | GOOD |
| i18n | enum 经 label 转换，不裸露 code | GOOD |
| 类型 | 未见 `any` 滥用，类型集中 | GOOD |
| 路由懒加载/代码分割 | **全同步 import，无 lazy/Suspense** | ACTUAL PROBLEM |
| 列表三态一致性 | RBAC 完整，业务页缺失，标准不统一 | ACTUAL PROBLEM |
| 死代码 | `analyticsApi` 无引用 | ACTUAL PROBLEM(小) |
| query key 约定 | 命名不统一 + 同站点重复请求 | MODERNIZATION OPPORTUNITY |
| 菜单双数据源 | 后端菜单 + 前端静态导航双维护 | MODERNIZATION OPPORTUNITY |
| UI 偏好 localStorage+Zustand | 无后端偏好持久化需求下合理 | ACCEPTABLE / KEEP |

---

## 20. Security Findings（汇总与定级）

| ID | Finding | Severity | 说明 |
|----|---------|----------|------|
| SEC1 | 认证链无限流/账号锁定 | **MEDIUM** | 唯一真实攻击面（暴力破解/注册滥用），生产前必补 |
| SEC2 | open-registration 误配可自注册管理员 | MEDIUM(条件) | 默认安全；建议启动时对 `open-registration=true` 显式告警 |
| SEC3 | 登录用户名枚举时间侧信道 | LOW / DEFENSE_IN_DEPTH | 建议 user==null 时执行 dummy hash |
| SEC4 | bootstrap 注册竞态 | LOW / DEFENSE_IN_DEPTH | 影响仅初始化窗口 |
| SEC5 | agent-services 无鉴权(全 mock) | LOW | 尚未接入真实数据流 |
| SEC6 | 前端业务路由守卫不全 | DEFENSE_IN_DEPTH / UX | 非越权漏洞（后端权威 + 无真实后端） |

**无 CRITICAL / HIGH。**

---

## 21. Design Findings

| 域 | 判定 | 依据 |
|----|------|------|
| Auth / Session | **GOOD DESIGN** | RS256+JWKS、refresh 旋转+重放、HttpOnly cookie |
| RBAC / 权限快照 | **GOOD DESIGN** | 请求级快照即时生效、super_admin 短路、事务替换 |
| Menu（读） | ACCEPTABLE | 菜单存 `admin_permission(MENU)`，不另起平行架构；写操作待补 |
| Gateway 不鉴权 | **GOOD DESIGN** | 各 RS 独立验签，边界清晰 |
| Site 管理 | UNDER-DESIGN / BACKEND_FIRST | 表已备，Service/Controller 缺席 |
| Site 前台 | UNDER-DESIGN | 登录 501 + auth/me 占位，鉴权链未通 |
| Content/Reservation/File/Template/Appearance/AI/Analytics | NOT STARTED | 无表无接口，前端 mock 先行 |
| Admin 用户管理 | IMPLEMENTATION_DRIFT | 权限码已种子、表已备、无接口 |
| 前端偏好 | ACCEPTABLE / KEEP | localStorage + Zustand，无后端需求下合理 |
| Router / 权限映射 | DESIGN ISSUE(小) | 懒加载缺失 + 业务路由守卫不全（纵深） |
| Mock 层策略(USE_MOCK) | **GOOD DESIGN** | prod fail-safe（默认真实），整体服务替换清晰 |
| 菜单双数据源 | DESIGN ISSUE(小) | 后端菜单 + 前端静态导航双维护 |
| agent-services(TS 独立服务) | REQUIREMENT_DRIFT(待定) | 与 Java agent stub 并存，职责边界需明确 |

**值得改的设计（含 Better-Design 结构）**：

1. **认证限流 + 账号锁定**
   - Current：auth 链 permitAll，无速率/锁定。
   - Proposed：gateway 或 auth 层加基于 IP+用户名的滑动窗口限流 + 连续失败锁定（可用 Redis 计数，复用现有 Redis）。
   - Benefit：消除唯一真实攻击面。Migration Cost：低（已有 Redis/gateway）。Risk：低。**Worth：SHOULD CHANGE。**

2. **去掉“假闭环写”**
   - Current：siteCreate/appearance 保存写内存，UI 显示成功。
   - Proposed：真实后端就绪前，将这些写操作显式标注为“原型/未持久”，或改为草稿本地态并明确提示。
   - Benefit：消除“已完成”误判。Cost：低。Risk：低。**Worth：WORTH CONSIDERING。**

3. **删除死代码 analyticsApi**
   - Current：定义但无引用。Proposed：REMOVE，待需求确立重建。Benefit：降噪。Cost：极低。Risk：无。**Worth：SHOULD CHANGE（但属整改，本轮不执行）。**

4. **路由懒加载**
   - Current：全同步 import。Proposed：`React.lazy` + Suspense 按路由分割。Benefit：首屏体积/加载。Cost：低。Risk：低。**Worth：WORTH CONSIDERING。**

5. **菜单单一数据源**
   - Current：后端菜单 + 前端静态导航双维护。Proposed：顶栏标题也从后端菜单派生，前端静态仅兜底。Benefit：消除漂移。Cost：中。Risk：低。**Worth：WORTH CONSIDERING。**

---

## 22. Completion Matrix

维度评分：0 / 0.5 / 1（N/A 不计入）。Completion 分档：0% / 25% / 50% / 75% / 100%。

| Domain | Feature | Backend | DB | API | Frontend | Mock | Security | Tests | E2E | Completion |
|--------|---------|---------|----|----|----------|------|----------|-------|-----|-----------|
| Auth | Admin 登录/会话 | 1 | 1 | 1 | 1 | N/A | 1 | 1 | 1 | **100%** |
| Auth | 管理员注册(bootstrap) | 1 | 1 | 1 | 0 | N/A | 0.5 | 1 | 0 | **75%** |
| Auth | Site 前台登录 | 0 | N/A | 0 | 0 | N/A | N/A | 0 | 0 | **0%** |
| RBAC | 角色 CRUD | 1 | 1 | 1 | 1 | N/A | 1 | 1 | 1 | **100%** |
| RBAC | 角色↔权限 | 1 | 1 | 1 | 1 | N/A | 1 | 1 | 1 | **100%** |
| RBAC | 权限列表(只读) | 1 | 1 | 1 | 1 | N/A | 1 | 1 | 1 | **100%** |
| RBAC | 用户↔角色 | 1 | 1 | 1 | 0 | N/A | 1 | 1 | 0 | **75%** |
| RBAC | 菜单树(只读) | 1 | 1 | 1 | 1 | N/A | 1 | 1 | 1 | **100%** |
| RBAC | 菜单写 | 0 | 0.5 | 0 | 0 | N/A | N/A | 0 | 0 | **0%** |
| Admin | 用户账号 CRUD | 0 | 0.5 | 0 | 0 | N/A | N/A | 0 | 0 | **0%** |
| Dashboard | KPI/趋势 | 0 | 0 | 0 | 0.5(壳) | 0 | N/A | 0 | 0 | **0%** |
| Site | 站点管理 CRUD | 0 | 0.5 | 0 | 0.5(mock) | 1 | N/A | 0 | 0 | **25%** |
| Site | 新建站点向导 | 0 | 0.5 | 0 | 0.5 | 1 | N/A | 0 | 0 | **25%** |
| Site | 概览/发布清单 | 0 | 0 | 0 | 0.5 | 1 | N/A | 0 | 0 | **25%** |
| Site | 基础/主题/域名 | 0 | 0 | 0 | 0.5(只读) | 1 | N/A | 0 | 0 | **25%** |
| Appearance | 风格设置 | 0 | 0 | 0 | 0.5(假闭环) | 1 | N/A | 0 | 0 | **25%** |
| Template | 列表/详情 | 0 | 0 | 0 | 0.5 | 1 | N/A | 0 | 0 | **25%** |
| Content | CMS | 0 | 0 | 0 | 0.5(读) | 1 | N/A | 0 | 0 | **25%** |
| Reservation | 预约 | 0 | 0 | 0 | 0.5(读) | 1 | N/A | 0 | 0 | **25%** |
| File | 文件 | 0 | 0 | 0 | 0.5(读) | 1 | N/A | 0 | 0 | **25%** |
| AI | 配置/提示/KB/会话 | 0 | 0 | 0 | 0.5(读) | 1 | N/A | 0 | 0 | **25%** |
| Analytics | 分析 | 0 | 0 | 0 | 0(壳/死码) | 0.5 | N/A | 0 | 0 | **0%** |
| System/Team/Params | 治理 | 0 | 0 | 0 | 0(壳) | 0 | N/A | 0 | 0 | **0%** |

---

## 23. Feature Statistics

**统计口径**：以产品级业务功能为单位（union of 前端页面意图 + 后端能力 + mock），共 **35** 项。子能力（如角色的增/改/删）折叠进其父功能。基础设施（gateway/ping/JWKS 单列在后端矩阵，不计入 35 的产品功能主口径，但 Auth 会话作为一项计入）。

**Backend**（口径：35 项产品功能中，Auth 的 login / refresh / logout / register / JWKS 各计为一项独立功能）
- 总业务功能数：35
- COMPLETE：**11** = Auth 5（login, refresh, logout, register, JWKS）+ RBAC 6（角色 CRUD, 角色↔权限, 权限列表, 用户↔角色, 菜单树读, auth-me）。逐项对应矩阵 A1,A2,A3,A4,A5,R1,R2,R3,R4,R5,R6。
- PARTIAL：0
- SKELETON / PLACEHOLDER：1（Site 前台登录 501；SiteAuthMe / site 前台业务为骨架，合并计 1）
- NOT_IMPLEMENTED：23（= 35 − 11 − 1；Menu 写、Admin 用户 CRUD、Dashboard、Site 管理及各 mock 业务域）

> 注意两种计数粒度：**后端功能矩阵（§3）** 以 endpoint/service 行计，COMPLETE 行 = 11；本节以 **35 项产品功能** 计，Auth 拆为 5 项后 COMPLETE 亦 = 11，二者数值一致但口径不同。若把 login/refresh/logout 折为单一“会话”功能，则 Auth 计 3 项、产品功能总数相应降为 33、COMPLETE 降为 9——本报告统一采用“Auth 拆 5 项 / 总数 35 / COMPLETE 11”口径。

**Frontend**
- 总业务功能数：35
- REAL_API：6（登录/会话、角色、角色权限、权限、菜单读；auth-me 为支撑）
- MOCK_ONLY：约 12（site/template/appearance/content/reservation/file/ai×4/developer）
- PLACEHOLDER / EMPTY_SHELL：约 12（user/analytics/team/system/params/dashboard/site 工作区预约·AI·文件/site 只读设置组）
- BACKEND_MISSING（前端有、后端无）：约 12（= MOCK_ONLY 集合）
- BACKEND_ONLY（后端有、前端无）：2（用户↔角色分配、管理员注册）

**End-to-End（真实 UI + 真实 API + DB 持久 + 测试）：6**
= 管理员登录/会话、当前用户上下文、角色 CRUD、角色↔权限、权限只读列表、菜单树只读。

> 若把“会话/登录”和“当前用户上下文”视为同一闭环，则 E2E 为 5–6，取 **6**（含 auth-me 独立价值）。

---

## 24. Priority Backlog

| Priority | Domain | Feature | Problem | Recommended Action |
|----------|--------|---------|---------|--------------------|
| P0 | Auth | 认证限流/账号锁定 | 无防暴力（SEC1） | gateway/auth 层加 Redis 滑窗限流 + 失败锁定 |
| P1 | Site | 站点管理 CRUD 后端 | 表已备无 Service/Controller | 实现 Site Controller/Service（复用软删/乐观锁）→ 前端接真实 API |
| P1 | RBAC | 用户↔角色前端页 | 后端完整无 UI | 补管理员-角色分配页面 |
| P1 | Admin | 用户账号 CRUD | 权限码已种子无接口 | 实现 admin_user 账号管理接口 |
| P1 | RBAC | 菜单写操作 | 权限码已种子无接口 | 在现有 Menu 结构上扩展写（勿另起平行架构） |
| P2 | Frontend | 路由懒加载 + 列表三态 | 首屏体积/无 error 态 | React.lazy + 统一 DataTable 三态 |
| P2 | Governance | 假闭环/死代码 | siteCreate/appearance 假闭环、analyticsApi 死码 | 标注原型态 / 移除死码 |
| P2 | Nav | 菜单单一数据源 | 双维护漂移 | 顶栏标题从后端菜单派生 |
| P3 | Site 前台 | site 登录/auth-me | 501 + 占位 | 前台业务开工时统一设计 |
| P3 | AI/Content/File/Template/Reservation | 各业务后端 | 无表无接口 | 按产品优先级逐域 BACKEND_FIRST |

---

## 25. Mock → Real API Roadmap

- **MIGRATE_NOW：0**（除 RBAC 外无真实后端，任何 mock 域都无法“立即接口化”）。
- **BACKEND_FIRST**：site（表已备，最优先）、template、reservation、ai、content。前置：先建后端 Service/Controller（及缺失的表）。
- **CONTRACT_FIRST**：appearance（字段模型复杂需定稿）、file（存储元数据模型 + 上传契约）、content（CMS 模型）。前置：以真实后端 DTO 定稿，勿沿用 mock 形状。
- **KEEP_MOCK_TEMPORARILY**：system、user（站点级）、developer——仍属原型/低优先。
- **REMOVE**：analytics（`analyticsApi` 死代码，无页面引用）。

---

## 26. Modernization Opportunities（仅列有实际收益者）

| 机会 | Current | 2026 Recommended | Actual Benefit | Migration Cost | Risk |
|------|---------|------------------|----------------|----------------|------|
| 认证限流/锁定 | 无 | Redis 滑窗限流 + 失败锁定（复用现有 Redis） | 消除真实暴力破解面 | 低 | 低 |
| 路由懒加载 | 全同步 import | React.lazy + Suspense 分割 | 首屏体积/加载 | 低 | 低 |
| 列表三态统一 | RBAC 有、业务缺 | 统一 `DataTable` loading/empty/error | 真实 API 化后一致体验 | 中 | 低 |
| 菜单单一数据源 | 后端 + 前端静态双维护 | 顶栏从后端菜单派生 | 消除漂移 | 中 | 低 |
| Mock 契约对齐 | httpService 指向虚构端点 | 以真实 DTO 定契约再迁移 | 降低迁移返工 | 中 | 中 |
| （可选）Argon2id | BCrypt | Argon2id | 更强哈希 | 中（需迁移策略） | 中 |

> 不建议为“更新”而重写 auth/RBAC/持久层。

---

## 27. Things That Should NOT Be Changed（KEEP）

- **JWT 方案**：RS256 + kid + JWKS + iss/aud/subject_type 三重校验。**KEEP。**
- **Refresh 旋转 + 重放吊销 + SHA-256 存储 + HttpOnly/SameSite cookie。KEEP。**
- **RBAC 请求级权限快照模型**（角色变更即时生效、super_admin 短路）。**KEEP。**
- **gateway 不鉴权、各 RS 独立验签的边界。KEEP。**
- **Flyway 单一 schema 源 + 幂等种子 + 开发/生产分离。KEEP。**
- **软删（partial unique）+ 乐观锁（@Version）+ FK RESTRICT + 触发器。KEEP。**
- **`USE_MOCK` 生产 fail-safe（默认真实）+ 服务整体替换的 mock 策略。KEEP。**
- **UI 偏好走 localStorage + Zustand（无后端偏好需求下）。KEEP。**
- **Menu 存于 `admin_permission(MENU)`、不另起平行 Menu 架构的决定。KEEP。**
- **DTO/Controller/Service/Mapper 职责边界与“不过度拆分”实践。KEEP。**

---

## 28. Recommended Functional Roadmap（基于真实调查重排）

**Phase 1 — Security / Correctness**
- 认证限流 + 账号锁定（P0，SEC1）。
- open-registration 启动告警（SEC2）；登录 dummy-hash 抹平时间侧信道（SEC3，可选）。
- 明确标注/收敛“假闭环写”，移除 `analyticsApi` 死代码。

**Phase 2 — Complete Existing Backend Capabilities**
- 补 **用户↔角色分配前端页**（后端已完整）。
- 在现有结构上扩展 **Menu 写** 与 **Admin 用户账号 CRUD**（权限码/表已备）。

**Phase 3 — Mock → Real API（首选 Site）**
- 实现 **Site 管理 CRUD 后端**（表已备，最短路径），前端 `siteApi` 从 mock 切真实；顺带打通 site 列表/创建/概览闭环。

**Phase 4 — Complete Missing Backend**
- 按产品优先级逐域 BACKEND_FIRST：template → reservation → content → file → ai/appearance（appearance/file/content 走 CONTRACT_FIRST 定稿字段）。

**Phase 5 — Frontend Functional Closure**
- 各真实 API 页补齐 loading/empty/error/retry 三态；统一 query key；菜单单一数据源。

**Phase 6 — Modernization**
- 路由懒加载/代码分割；（可选）Argon2id；观测性（metrics/tracing）按需。

**下一阶段最值得开发**：**Site 管理 CRUD 后端**（表已备、契约清晰、能立刻把一条真实业务闭环从 0 拉到 1），并行做 **认证限流**（安全必修）。

---

## 29. Final Assessment（逐条回答）

1. **当前到底有多少业务功能？** 产品级约 **35** 项（见 §23 口径）。
2. **真正完全实现多少？** 端到端闭环 **6** 项（Auth 会话、auth-me、角色 CRUD、角色↔权限、权限只读、菜单只读）；后端 COMPLETE **11**。
3. **哪些只是 UI / Mock？** 约 12 个 mock 域 + 约 12 处空壳/占位（site/template/appearance/content/reservation/file/ai/analytics/system/user/dashboard/治理页）。
4. **哪些后端已有但前端没接？** 用户↔角色分配、管理员注册（BACKEND_ONLY）。
5. **哪些前端已有但后端不存在？** site/template/appearance/content/reservation/file/ai/analytics/system/user 全部（契约指向虚构 `/site|/agent/**`）。
6. **哪些 Mock 应立即接口化？** **无（MIGRATE_NOW=0）**——除 RBAC 外无真实后端。
7. **哪些 Mock 现在应继续保留？** system、user（站点级）、developer 等原型域（KEEP_MOCK_TEMPORARILY）；analytics 应 REMOVE。
8. **是否存在真实逻辑错误？** 无严重逻辑缺陷；存在假闭环、死代码、重复请求、bootstrap 竞态等中低问题（§13）。
9. **是否存在安全漏洞？** 无 CRITICAL/HIGH；唯一真实项是认证无限流/锁定（MEDIUM，SEC1）。
10. **是否存在权限漏洞？** 无越权漏洞；后端授权权威且完整。前端路由守卫不全属 DEFENSE_IN_DEPTH/UX。
11. **是否存在数据一致性问题？** 真实链路（RBAC）一致性机制完备；mock 域不落库（设计使然，非一致性缺陷）。
12. **是否存在明显设计错误？** 无重大设计错误；小问题为路由懒加载缺失、菜单双数据源、权限种子超前于 endpoint。
13. **是否符合 2026 主流工程实践？** 后端 auth/RBAC/持久层/测试 = GOOD；前端 server-state/表单/i18n = GOOD；短板为限流、前端懒加载与三态一致性。
14. **哪些“现代化”值得做？** 认证限流、路由懒加载、列表三态统一、菜单单源、mock 契约对齐（§26）。
15. **哪些代码不应该动？** JWT/refresh/RBAC 快照/gateway 边界/Flyway 单源/软删乐观锁/USE_MOCK fail-safe（§27）。
16. **下一阶段最值得开发什么？** **Site 管理 CRUD 后端** + 并行 **认证限流**。

---

### Evidence & Confidence 说明

- 本报告所有“真实/占位/mock”判定均来自对 Controller / Service / Mapper / Flyway / Test / 前端 API Client / Page / Mock / Router 的直接阅读；关键 auth/RBAC/安全/数据来源层由审计者一手复核。
- 未运行构建或测试；“测试覆盖”指存在对应断言，非执行通过（Confidence 相应标注）。
- 无证据处标 UNKNOWN；A11y 未做专项审计（标为未覆盖）。

### Final Validation（独立只读复核）

由 `integration-reviewer` 独立只读复核（交叉核实 `apps/admin-web/src/api/*`、`services/website-services/**/controller/*`、Flyway V1–V4、`AdminAuthServiceImpl`、`RedisAdminRefreshSessionServiceImpl`）：

- 复核结论：**PASS_WITH_NOTES**。
- 实质判定（真实 / 占位 / mock、BACKEND_ONLY 映射、安全定级、KEEP / Modernization / Flyway 单源）经一手代码核实**全部成立**，无功能重复统计、无 mock 误判为真实、无 placeholder 误判为完成、无安全夸大。
- 唯一需修正项：§23 功能计数口径混用（已在本次修订中统一为“Auth 拆 5 项 / 总数 35 / COMPLETE 11”，并显式标注与 §3 endpoint 粒度的换算关系）。
- 结论：允许标记完成。

**状态：`AUDIT COMPLETE`**（严格只读；本轮仅生成本报告文件，未修改任何业务代码 / DB / migration / 前端 / mock / docs / 依赖，未 commit / push）。
