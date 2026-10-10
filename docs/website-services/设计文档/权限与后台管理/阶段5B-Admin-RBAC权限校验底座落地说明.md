# 阶段 5B：Admin RBAC 权限校验底座落地说明

> **文档状态**：5B 已落地。Method Security 与权限校验仍是后台接口的基础。  
> **最近核对**：2026-10-10  
> **其后已完成**：5C 角色与权限、5D 用户角色、5E 菜单读与 `/auth/me` 的 menus。**仍未做**：5E.3 菜单写。

阶段 **5C** 已在 5B 权限校验底座之上实现角色与权限管理接口；5B 的 `AdminPermissionChecker` 与 Method Security 作为后续所有后台管理接口权限控制的基础。

本文档用于：**记录 5B 已落地事实**、**划定本阶段边界**、**约束后续 5C～5E**，并作为 **代码合并后的自查清单**。阅读与审查时，以本文 + 5A 为准，不以口头约定或零散注释为准。

---

## 1. RBAC 当前阶段的设计边界

### 1.1 阶段在整体路线中的位置

```text
认证阶段（4F～4I）已关闭
  → 5A：RBAC 详细设计（原则仍有效；接口已在后续阶段落地）
  → 5B：权限校验底座（已落地）
  → 5C：角色与权限管理（已落地）
  → 5D：用户与角色绑定（已落地）
  → 5E：菜单读与 /auth/me.menus（已落地）；菜单写未做
```

### 1.2 5B 在 RBAC 中的职责（一句话）

**在 Admin 服务内，基于已登录管理员身份，从数据库加载角色与 `permission_code`，并通过 Method Security 拒绝越权；不向 JWT 写入权限，不在 Auth 做授权。**

### 1.3 架构边界（固定，5B 不得破坏）

```text
gateway  → 路由、RequestId、访问日志（不验签、不做 RBAC）
auth     → 登录 / Refresh / Logout / JWT 签发 / JWKS（不做 RBAC）
admin    → Resource Server + RBAC 查询 + PermissionChecker + 方法级校验
common   → SubjectCodes、AuthContext、协议与 ErrorCode
infra    → Servlet 日志、i18n、MyBatis 公共列约定
```

### 1.4 数据边界

- RBAC 表已在 **V1 Flyway** 落地：`admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission`。
- **5B 不新增 migration**；测试数据仅 **test scope** SQL（`admin/src/test/resources/rbac/`）。
- 权限类型语义沿用 **`admin_permission.resource_type`**（`MENU` / `ACTION`），见 5A §5。

---

## 2. 硬性约束（审查时必须满足）

### 2.1 Auth 不参与 RBAC

**Auth 只负责认证，不负责权限。** 以下在 **auth 模块** 中 **禁止** 出现（审查时用 §12 自查命令核对）：

| 禁止项 | 说明 |
|--------|------|
| 查询 `admin_role` | 角色归属 **admin** |
| 查询 `admin_permission` | 权限点归属 **admin** |
| 查询 `admin_user_role` / `admin_role_permission` | 关联表归属 **admin** |
| 判断后台接口权限 | 无 `PermissionChecker`、无 `@PreAuthorize` 业务权限 |
| 向 JWT 写入 `permissions` / 角色列表 | 见 §2.3 |
| Maven 依赖 **admin** 业务 jar 做授权 | Auth 仅可读 `admin_user` 等表做**登录校验**（自持 Mapper） |

**允许**：Auth 读 `admin_user` 校验口令、签发 Access JWT（`sub` + `subject_type` + `aud` 等）。

### 2.2 Admin 负责后台授权

**Admin** 负责本阶段全部 RBAC 运行时能力：

| 能力 | 5B 实现类 / 入口 |
|------|------------------|
| 当前用户角色查询 | `AdminPermissionQueryService` → `AdminRbacQueryMapper.selectRoleCodesByUserId` |
| 当前用户权限查询 | 同上 → `selectPermissionCodesByUserId` 或 super_admin 时 `selectAllActivePermissionCodes` |
| 权限判断 | `AdminPermissionChecker`（Bean 名 **`adminPermissionChecker`**） |
| Method Security | `AdminSecurityConfig`（`@EnableMethodSecurity`）+ Controller 上 `@PreAuthorize` |
| 当前用户权限信息 HTTP | `GET /api/admin/auth/me` |

**分层约定**：

- **Controller**、**Security 配置**、**`AdminPermissionChecker`**：**不得**直接注入或调用 `AdminRbacQueryMapper`（及 RBAC 查询 Mapper）。
- 权限数据访问统一收口到 **`AdminPermissionQueryService`**。
- **`AdminPermissionQueryServiceImpl`** 可按职责调用 **`AdminRbacQueryMapper`**、`AdminUserMapper` 等。

### 2.3 JWT 只表达身份

| 项 | 5B 事实 |
|----|---------|
| JWT 是否携带完整 `permissions` | **否**；禁止在代码中 `jwt.getClaim("permissions")` 或等价逻辑做授权 |
| 身份入口 | Access JWT **`sub`** → `SubjectCodes.parseAdminUserId` → **`AuthContext`** / `AuthenticatedUser` |
| `userCode` 展示 | `SubjectCodes.formatAdminSubject(userId)`，形如 **`ADMIN_000100`**（与 5A 文档示例 `admin:42` 不同，以 **代码为准**） |
| 授权数据来源 | `admin_user.id` → JOIN `admin_user_role` → `admin_role` → `admin_role_permission` → `admin_permission` |
| 有效数据过滤 | 用户 **ACTIVE**；角色/权限/绑定 **未删除** 且 **status = ACTIVE**（常量 `AdminRbacConstants.STATUS_ACTIVE`） |

**签发侧（auth）**：`JwtTokenServiceImpl` 仅写入 `subject_type`、`sub`、`aud`、`iss`、`jti` 等；历史 claim `admin_user_id` / `username` 已 **deprecated**。

**验签侧（admin）**：`AdminJwtDecoderConfig` 校验 `iss` / `aud` / `subject_type=ADMIN`；**不**从 JWT 解析 permissions。

### 2.4 Method Security 是主方案（非 URL 绑权限）

| 层级 | 5B 行为 |
|------|---------|
| **URL（`AdminSecurityConfig`）** | Swagger / Actuator 放行；**其余 `authenticated()`**；**不**按路径配置 `admin:user:list` |
| **方法级** | **`@PreAuthorize("@adminPermissionChecker.hasPermission('...')")`** 为推荐形式 |
| **403 语义** | `AccessDeniedException` / `AuthorizationDeniedException` → `GlobalExceptionHandler` → **`auth.access.denied`**（**1003**）；**不**新增 `admin.permission.denied` |

推荐写法（后续业务接口照抄）：

```java
@PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:list')")
```

依赖：**`spring-boot-starter-aop`**（`admin/pom.xml`）。

---

## 3. 本阶段实现清单（5B 已完成）

| # | 交付项 | 状态 | 代码 / 测试位置 |
|---|--------|------|-----------------|
| 1 | `AdminPermissionQueryService` + `Impl` | ✅ | `rbac/service/`、`rbac/service/impl/AdminPermissionQueryServiceImpl` |
| 2 | `AdminRbacQueryMapper`（有效角色/权限 SQL） | ✅ | `admin/.../rbac/mapper/` |
| 3 | `AdminCurrentAuthorizationSnapshot` | ✅ | `admin/.../security/AdminCurrentAuthorizationSnapshot`（**5D.1** 自 DTO 迁出） |
| 4 | `CurrentAdminAuthorizationHolder`（请求级缓存） | ✅ | `admin/.../rbac/security/` |
| 5 | `AdminPermissionChecker` | ✅ | Bean 名 **`adminPermissionChecker`** |
| 6 | `@EnableMethodSecurity` | ✅ | `AdminSecurityConfig` |
| 7 | `GET /api/admin/auth/me` 真实 roles/permissions | ✅ | `AdminAuthMeController`；`menus=[]` |
| 8 | `super_admin` 短路 | ✅ | Checker 全 true；me 返回全量 ACTIVE permission |
| 9 | test scope 权限探针（**非 main**） | ✅ | `RbacTestController` → `GET /api/admin/test/rbac/check` |
| 10 | 403 与 i18n | ✅ | `GlobalExceptionHandler` + `auth.access.denied` |
| 11 | 单元 / MVC 测试 | ✅ | `AdminPermissionQueryServiceTest`、`AdminPermissionCheckerTest`、`AdminRbacSecurityMvcTest` |
| 12 | test scope H2 数据 | ✅ | `admin/src/test/resources/rbac/rbac-h2-*.sql` |

**未改动的合理保留**：

- `GET /api/admin/ping`：仅需 **登录**，无额外 `@PreAuthorize`（与 5A「URL 只 authenticated」一致）。

---

## 4. 本阶段明确不做

以下若在 5B PR 中出现，视为 **越界**，应退回或标为下一阶段的独立 PR：

| 不做项 | 归属阶段 |
|--------|----------|
| Flyway migration（含生产 RBAC 种子） | **5C**（或 5C 数据脚本约定） |
| Redis 权限缓存 `rbac:admin:user:{userId}:permissions` | 5B+ / 5C 变更失效策略 |
| 角色 CRUD（`/api/admin/roles`） | **5C** |
| 权限点后台创建 / 随意改 `permission_code` | **5C**（推荐只读 + 种子） |
| 角色-权限 PUT | **5C** |
| 用户-角色 PUT | **5D** |
| `/auth/me` 菜单树 `menus` 非空 | **5E** |
| 菜单管理 CRUD、前端路由配置接口 | **5E** |
| Site RBAC | 不在 Admin RBAC 路线内 |
| Auth / Gateway 路由或 JWT 契约变更 | 认证阶段已关闭 |
| 自定义 `@RequirePermission` 注解框架 | 5B 刻意用标准 `@PreAuthorize` + Bean |
| URL 层 `authorizeHttpRequests` 按 permission 匹配 | 违反 5A/5B 主方案 |
| **main** 中 `/api/admin/rbac/check` 等临时探针接口 | 5B 仅用 **test scope** `RbacTestController` |

---

## 5. 权限加载与校验（实现事实）

### 5.1 流程

```text
Authorization: Bearer <access>
  → Admin OAuth2 Resource Server（JWKS）
  → AuthContextSynchronizingFilter
  → AuthContext（TokenAudience.ADMIN, subject=ADMIN_xxxxxx）
  → @PreAuthorize 触发
  → CurrentAdminAuthorizationHolder（@RequestScope，单请求只查库一次）
  → AdminPermissionQueryServiceImpl（`rbac/service/impl`）
       0. JWT sub / userCode（ADMIN_xxxxxx）→ parseAdminUserId → admin_user.id（表无 user_code 列，userCode 由 SubjectCodes 编码）
       1. admin_user 存在且 status=ACTIVE
       2. selectRoleCodesByUserId
       3. 若含 super_admin → selectAllActivePermissionCodes
          否则 → selectPermissionCodesByUserId
  → AdminPermissionChecker.hasPermission(...)
```

### 5.2 核心类索引

| 类 | 包路径 |
|----|--------|
| `AdminRbacConstants` | `com.mywebsite.admin.constant` |
| `AdminRbacQueryMapper` | `com.mywebsite.admin.mapper` |
| `AdminPermissionQueryService` | `com.mywebsite.admin.service` |
| `AdminPermissionQueryServiceImpl` | `com.mywebsite.admin.service.impl` |
| `AdminPermissionChecker` | `com.mywebsite.admin.security` |
| `AdminSecurityConfig`（含 `@EnableMethodSecurity`） | `com.mywebsite.admin.config` |
| `RbacTestController`（**test only**） | `com.mywebsite.admin.testsupport` |

### 5.3 `GET /api/admin/auth/me` 响应契约（5B）

```json
{
  "message": "success",
  "data": {
    "user": { "userCode": "ADMIN_000100", "username": "...", "displayName": "...", "avatarUrl": null, "status": "ACTIVE" },
    "roles": ["editor"],
    "permissions": ["admin:menu:system", "admin:rbac:check", "admin:user:list"],
    "menus": []
  }
}
```

- **成功**：`message = "success"`（`HttpMessageBody`，Admin 业务接口可无 `code` 字段，与 README 一致）。
- **`permissions`**：含 **MENU + ACTION** 全部有效 `permission_code`。
- **`menus`**：**5B 恒为 `[]`**。

### 5.4 `super_admin`

| 场景 | 行为 |
|------|------|
| `AdminPermissionChecker` | 角色列表含 **`super_admin`** → 任意 `hasPermission` → **true** |
| `/auth/me` | **roles** 仍含 `super_admin`；**permissions** = 库中全部 **ACTIVE** 的 `permission_code` |
| DB 绑定 | **不要求** `admin_role_permission` 绑满所有权限行 |

**数据说明**：生产库若尚无 RBAC 种子，`/auth/me` 的 **permissions** 可能为空数组，但 **super_admin** 在 **Checker** 中仍短路通过；种子由 **5C** Flyway 负责，**5B 不新增生产 migration**。

### 5.5 后续缓存扩展点（仅设计，5B 未实现）

在 **`AdminPermissionQueryServiceImpl`** 增加 cache-aside；key 建议：

`rbac:admin:user:{userId}:permissions`

角色/权限变更时删除对应用户 key（**5C** 写路径负责失效）。

---

## 6. 后续 5C / 5D / 5E 如何承接

| 阶段 | 在 5B 之上增加什么 | 5B 组件是否复用 |
|------|-------------------|-----------------|
| **5C** | 角色 CRUD；`GET /api/admin/permissions` 只读；`PUT /api/admin/roles/{id}/permissions`；**Flyway V2 种子**（16 条生产权限） | **复用** QueryService + Checker；管理接口加 `@PreAuthorize` |
| **5D** | `GET/PUT /api/admin/users/{id}/roles`；bootstrap 用户绑 `super_admin` | 绑定写入 `admin_user_role` 后，5B 查询自动生效 |
| **5E** | MENU 树构建；`/auth/me.menus` 非空；可选 V2 `parent_id` / `admin_menu` | **复用** 权限查询；扩展 me 组装逻辑，**不**改 Checker 核心 |

**原则**：5C～5E **只增数据和 HTTP 管理面**，**不**把 RBAC 判权迁回 Auth，**不**把 permissions 塞进 JWT。

---

## 7. 代码完成后按文档自查

审查人或作者在合并前，建议按下列项 **逐项打勾**（可复制到 PR 描述）。

### 7.1 模块边界（Auth 零 RBAC）

在仓库根目录执行（PowerShell / bash 均可）：

```bash
# auth 模块不得出现 RBAC 表/权限判断（允许 admin_user 登录）
rg -n "admin_role|admin_permission|admin_user_role|admin_role_permission|PermissionChecker|PreAuthorize" services/website-services/auth/src/main
# 期望：无匹配（或仅注释/文档字符串，无业务逻辑）

# auth 签发不得写入 permissions
rg -n "permissions" services/website-services/auth/src/main/java
# 期望：无将 permissions 写入 JWT 的代码
```

### 7.2 Admin 必须包含 5B 核心

```bash
rg -l "AdminPermissionQueryService|AdminPermissionChecker|AdminRbacQueryMapper|EnableMethodSecurity" services/website-services/admin/src/main
# 期望：命中 service / security / config / controller
```

### 7.3 JWT 不做授权依据

```bash
# admin 主代码不得从 JWT 读取 permissions 做判权
rg -n "getClaim.*permission|permissions.*getClaim" services/website-services/admin/src/main
# 期望：无匹配
```

- [ ] 业务判权**不**依赖 JWT 中的 `permissions` / 角色列表 claim

### 7.4 权限数据访问分层（Mapper 收口）

**约定**：

- **Controller**、**Security 配置**、**`AdminPermissionChecker`**：**不**直接访问 Mapper。
- 权限数据访问统一收口到 **`AdminPermissionQueryService`**。
- **`AdminPermissionQueryServiceImpl`** 可按职责调用 **`AdminRbacQueryMapper`**（及 `AdminUserMapper` 等）。

```bash
# Controller 不得直接依赖 RBAC Query Mapper
rg -n "AdminRbacQueryMapper" services/website-services/admin/src/main/java/com/mywebsite/admin/controller
# 期望：无匹配

# Security 配置、PermissionChecker 不得直接依赖 RBAC Query Mapper
rg -n "AdminRbacQueryMapper" services/website-services/admin/src/main/java/com/mywebsite/admin/config
rg -n "AdminRbacQueryMapper" services/website-services/admin/src/main/java/com/mywebsite/admin/security
# 期望：无匹配（Checker 仅依赖 CurrentAdminAuthorizationHolder / QueryService）

# QueryService 实现层允许使用 Mapper
rg -l "AdminRbacQueryMapper" services/website-services/admin/src/main/java/com/mywebsite/admin/service
# 期望：命中 AdminPermissionQueryServiceImpl（仅此一类为宜）
```

- [ ] `AdminAuthMeController` 仅注入 **`AdminPermissionQueryService`**（及 `AdminUserMapper` 读用户展示字段），**不**注入 `AdminRbacQueryMapper`

### 7.5 Method Security 主方案

```bash
rg -n "hasAuthority\\(|hasRole\\(" services/website-services/admin/src/main/java/com/mywebsite/admin/config/AdminSecurityConfig.java
# 期望：无按权限码配置 URL 的规则

rg -n "PreAuthorize" services/website-services/admin/src/main/java
# 期望：main 中至少 AdminAuthMeController（@PreAuthorize isAuthenticated）；test 中 RbacTestController
```

打开 **`AdminSecurityConfig`** 目视确认：

- [ ] `anyRequest().authenticated()`（除 Swagger/Actuator）
- [ ] **无** `.requestMatchers(...).hasAuthority(...)` 绑业务权限码

### 7.6 HTTP 与错误体

- [ ] `GET /api/admin/test/rbac/check`（test scope）无权限 → **403**，`code=1003`，`message` 来自 **`auth.access.denied`**
- [ ] 无 Bearer → **401**（`auth.unauthorized` / token invalid 语义不变）
- [ ] `GET /api/admin/auth/me` 返回 DB 真实 **roles/permissions**，**menus=[]**

### 7.7 构建与测试

```bash
cd services/website-services
mvn -q -DskipTests validate
mvn -q test
```

- [ ] 全量通过
- [ ] 尤其：`AdminPermissionQueryServiceTest`、`AdminPermissionCheckerTest`、`AdminRbacSecurityMvcTest`

### 7.8 禁止项快速核对

- [ ] **无** 新增 `infra/.../db/migration/V2*`（5B）
- [ ] **无** `auth` 模块 RBAC 查询
- [ ] **无** Gateway JWT 权限校验
- [ ] **无** 自定义 `@RequirePermission`（5B 范围）
- [ ] **无** main 源码 `AdminRbacCheckController` / `/api/admin/rbac/check`

---

## 8. 测试范围（5B 交付）

| 测试类 | 覆盖点 |
|--------|--------|
| `AdminPermissionQueryServiceTest` | `loadForUserCode`、editor、super_admin 全量、无角色、DISABLED 角色 |
| `AdminPermissionCheckerTest` | 有/无权限、super_admin、未登录、**非 ADMIN audience** |
| `AdminRbacSecurityMvcTest` | `@Import(RbacTestController)`；401/403/200；`/auth/me` 真实字段 |

数据脚本：**`admin/src/test/resources/rbac/rbac-h2-schema.sql`**、**`rbac-h2-data.sql`**（仅测试，**非**生产 Flyway）。

---

## 9. Method Security 验证（test scope，非 main）

| 方法 | 路径 | 权限码 | 位置 |
|------|------|--------|------|
| GET | `/api/admin/test/rbac/check` | `admin:rbac:check` | **`admin/src/test/java/.../RbacTestController`** |

- **不**进入 `src/main`；生产路由无此路径。
- `AdminRbacSecurityMvcTest` 通过 `@Import(RbacTestController.class)` 注册。
- 正式业务接口的 `@PreAuthorize` 从 **5C / 5D** 起逐步接入。

---

## 10. 5B 实现自查清单（§17，审查输出用）

> 下列为 **2026-05-21** 落地复核结果；后续 PR 须重新执行 §7 命令并更新本节。

| # | 检查项 | 结果 |
|---|--------|------|
| 1 | Auth 模块没有新增 RBAC 查询或权限判断 | **通过**（`auth/src/main` 无 RBAC 关键字） |
| 2 | JWT 中没有新增 permissions | **通过** |
| 3 | `AdminSecurityConfig` 仍只做 authenticated | **通过** |
| 4 | 已启用 Method Security | **通过**（`AdminSecurityConfig`） |
| 5 | 已新增 `AdminPermissionQueryService` | **通过** |
| 6 | 已新增 `AdminPermissionChecker` | **通过**（`adminPermissionChecker`） |
| 7 | Controller / Security / Checker 不直接访问 Mapper | **通过** |
| 8 | 权限数据收口到 `AdminPermissionQueryService` | **通过** |
| 9 | `PermissionChecker` 支持 super_admin 短路 | **通过** |
| 10 | 权限加载基于 JWT sub / userCode → `admin_user` | **通过**（`loadForUserCode`） |
| 11 | `/api/admin/auth/me` 返回真实 roles | **通过** |
| 12 | `/api/admin/auth/me` 返回真实 permissions | **通过** |
| 13 | menus 在 5B 保持 `[]` | **通过** |
| 14 | 没有新增生产 Flyway migration | **通过**（无 `V2*`） |
| 15 | 没有角色 / 权限 CRUD、用户角色绑定 | **通过** |
| 16 | 测试：无权限 403、有权限 200、super_admin 200、无 Bearer 401 | **通过** |
| 17 | **无** main 临时 `/api/admin/rbac/check` | **通过**（已删除；仅 test `/api/admin/test/rbac/check`） |
| 18 | `mvn -DskipTests validate` | **通过** |
| 19 | `mvn test` | **通过** |

---

## 11. 关闭标准（5B 阶段）

当且仅当同时满足：

1. §3 实现清单全部为 ✅  
2. §4 未做项未混入本 PR  
3. §7 与 §10 自查可打勾  
4. `mvn test` 通过  
5. 路线图 / README 已标 **5B 完成**

则 **5B 关闭**，可启动 **5C**。

---

## 12. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | 初版：5B 落地摘要 |
| 2026-05-20 | 扩充：阶段边界、硬性约束、实现/不做清单、5C～5E 承接、§7 自查清单（权威审查依据） |
| 2026-05-20 | §2.2 / §7.4：修正 Mapper 分层表述（QueryServiceImpl 可调 Mapper）；§7 小节编号顺延 |
| 2026-05-21 | 对齐最终 5B 要求：Impl 迁至 `service.impl`、`loadForUserCode`、删除 main 探针、test `RbacTestController`、§10 自查结果表 |
