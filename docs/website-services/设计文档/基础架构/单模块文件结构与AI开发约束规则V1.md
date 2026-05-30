# 单模块文件结构与 AI 开发约束规则 V1（website-services）

> **文档状态**：当前有效（**Cursor / Copilot / 人工开发必须遵守**）  
> **适用范围**：`services/website-services/` 全体 Java 模块  
> **与阶段关系**：**结构规则定稿**；**不**改变 5B～5D 已交付的业务行为  
> **最近同步**：2026-05-20  
> **权威级别**：与 [`后端包结构与命名规范V1.md`](./后端包结构与命名规范V1.md)、[`模块职责与依赖边界.md`](../../总览/模块职责与依赖边界.md) 并列；**冲突时以本文 Maven 边界 + 业务模块标准树为准**

---

## 0. 文档目的

1. 固定 **Maven 多模块** 职责边界，防止 Auth / Admin / Gateway 职责漂移。  
2. 固定 **单个业务模块内部包结构**，防止 `admin.rbac.*`、`dto/request` 等再次泛滥。  
3. 明确 **类应放在哪一层**，防止 AI 把一个功能拆到 manager / processor / support 等角落。  
4. 固定 **文件名与所在目录职责必须匹配**（见 §3），禁止用模糊命名逃避归属。  
5. 提供 **新增文件四问**，无清晰答案则禁止新增类。

**本轮**：仅规则与文档；**不**改业务代码、**不**移动文件、**不**改接口与数据库。

---

## 1. Maven 模块边界规则

### 1.1 gateway

**职责**

- 统一 HTTP 入口  
- 路由转发  
- CORS  
- RequestId  
- 网关级 Filter  
- 网关级错误处理（若有）

**禁止**

- 业务逻辑  
- 直连数据库 / Redis  
- Entity、Mapper、Service、业务 DTO  
- Maven 依赖 admin / auth / site 的**业务实现类**  
- 在网关聚合各服务 Swagger（各服务自暴露 springdoc）

---

### 1.2 auth

**职责**

- 认证主链路：登录、刷新 Token、登出  
- JWT 签发、JWKS  
- Refresh Session、Refresh Cookie  
- 认证相关 HTTP 与 i18n（`i18n/auth`）

**禁止**

- 后台角色 / 权限 / 菜单 / 站点配置管理  
- Maven 依赖 admin、site-* 的 Entity / Mapper / Service  

**说明**

- 可读 `admin_user` 等表；须在 **auth 模块内** 自持 `entity` / `mapper`  
- 跨服务身份契约使用 **`common.security`** + HTTP，**不** import `com.mywebsite.admin.*`

---

### 1.3 admin

**职责**

- 后台管理业务 API  
- 后台用户、角色、权限、菜单（5E）、站点配置  
- 用户-角色、角色-权限绑定  
- Admin 侧 RBAC 运行时校验（Resource Server + `AdminPermissionChecker`）  
- `GET /api/admin/auth/me`（业务上下文，**非** JWT 签发）

**禁止**

- `/api/auth/*` 认证主链路  
- JWT 签发  
- Maven 依赖 auth 内部实现  
- `com.mywebsite.admin.rbac.*` 等功能域深包  

---

### 1.4 site

**职责**

- 前台站点业务、`site-app` HTTP 入口  
- `site-core` 站点域共用 entity / mapper / 上下文  
- `site-car` / `site-cake` 行业骨架  

**禁止**

- 强依赖 admin 内部实现  
- 无业务时大量 manager / processor / adapter  

---

### 1.5 common

**职责**

- `ApiResponse`、`ErrorCode`、`BusinessException`、`PageResult`  
- `common.security` 身份契约  
- 少量真·跨模块工具  

**禁止**

- 业务 DTO / Entity / Mapper / Service（如 `AdminUserDto`、`LoginRequestDto`）

---

### 1.6 infra

**职责**

- Redis、MinIO、数据源、MyBatis-Plus 插件、Flyway **脚本目录**、i18n、`RequestLoggingFilter`、OAuth2 RS 辅助、`AbstractAuditableEntity`  

**禁止**

- 业务流程、业务 Entity、业务 Mapper、业务 DTO、业务 Service  

---

### 1.7 agent

**职责**

- AI / Agent 客户端封装  

**禁止**

- admin / auth / site 业务编排；反向污染业务模块  

---

### 1.8 依赖拓扑（摘要）

```text
gateway  → 仅 Gateway 技术栈

common
  ↑
infra    → common
auth     → common, infra
agent    → common

site-core → common, infra
site-app  → common, infra, agent, site-core, site-car, site-cake
admin     → common, infra, agent
```

**可执行进程**：gateway、auth、admin、site-app。

---

## 2. 业务模块标准包结构

**适用于**：`admin`、`auth`、`site-core`、`site-app` 中的业务部分。

```text
com.mywebsite.{module}
├── config
├── constant
├── controller
├── dto
├── entity
├── mapper
├── properties
├── security
├── service
│   └── impl
├── handler
└── utils
```

**说明**

1. **不要求创建空包**；有对应职责的类时才建目录。  
2. **禁止**为「看起来规范」新建无意义目录。  
3. **禁止**默认新增 manager、processor、facade、adapter、support、helper 等中间层（见 §10）。  
4. **非业务模块**（gateway、common、infra、agent）**不套用**本树（见 §15）。  
5. **目录与文件名后缀必须一致**（见 §3）；新增、移动、重命名均适用。

---

## 3. 文件名与文件夹职责匹配规则

**总则**（适用于业务模块及 gateway 等非业务模块中同类目录）：

- **文件名必须与所在文件夹职责匹配**；功能必须归纳到对应职责目录。  
- **禁止**为方便把类放到不匹配目录；**禁止**用模糊命名逃避职责归属。  
- **新增、移动、重命名** Java 文件前，须同时校验 **目录 + 类名后缀**（并与 §11「四问」一并执行）。

### 3.1 强制命名规则（目录 → 允许类型 → 文件名后缀）

| 目录 | 只允许 | 文件名必须以…结尾 | 示例 |
|------|--------|-------------------|------|
| **controller** | Controller | `Controller` | `AdminMenuController`、`AdminRoleController` |
| **service** | Service **接口** | `Service` | `AdminMenuService`、`AdminRoleManagementService` |
| **service.impl** | Service **实现** | `ServiceImpl` | `AdminMenuServiceImpl`、`AdminRoleManagementServiceImpl` |
| **mapper** | Mapper | `Mapper` | `AdminMenuMapper`、`AdminUserRoleMapper` |
| **entity** | 数据库实体 | `Entity` | `AdminMenuEntity`、`AdminRoleEntity` |
| **dto** | 数据传输对象 | `Dto`（可含 `RequestDto` / `ResponseDto` / `TreeDto` / `DetailDto` / `QueryDto` / `UpdateDto` / `ListItemDto`） | `AdminMenuTreeDto`、`AdminLoginRequestDto` |
| **config** | Spring / 框架配置类 | `Config` 或 `Configuration` | `AdminSecurityConfig`、`AuthJwtConfiguration` |
| **properties** | `@ConfigurationProperties` 绑定类 | `Properties` | `AuthJwtProperties`、`AdminAuthProperties` |
| **constant** | 常量类 | `Constants`、`MessageKeys`、`RedisKeys` 等**明确**后缀 | `AdminRbacConstants`、`AdminRbacMessageKeys` |
| **handler** | 异常 / HTTP 错误响应处理 | 体现异常处理职责（常见 `Handler`、`HttpStatus`） | `GlobalExceptionHandler`、`GlobalExceptionHttpStatus` |
| **security** | 认证鉴权链路 | 体现安全职责（`Filter`、`EntryPoint`、`AccessDeniedHandler`、`PermissionChecker` 等） | `AdminAccessDeniedHandler`、`AdminPermissionChecker` |
| **utils** | 纯静态工具 | `Utils` | `RefreshTokenHashUtils` |

**说明**

- **handler** 不放 Spring Security 的 `AuthenticationEntryPoint` / `AccessDeniedHandler`（归 **security**，见 §8）。  
- **security** 不放 RBAC 管理 CRUD、菜单管理、**业务保护规则**（如 `AdminRoleGuard` → **service**，见 §8、§16.4）。  
- **config** 下允许子包（如 `config/jwt`），类名仍须 `*Config` / `*Configuration`（见 §16.2）。  
- **聚合 DTO**（如 `AdminRbacDtos`）文件名仍以 `Dtos` 结尾，属 §16.1 书面例外。

### 3.2 禁止情况（错放目录）

| 禁止 | 正确归属 |
|------|----------|
| Controller 放在 `service` | `controller` |
| `ServiceImpl` 放在 `service` 根目录 | `service.impl` |
| Mapper 放在 `service` / `utils` | `mapper` |
| 业务 DTO 放在 `common` | 各业务模块 `dto` |
| 业务规则 / CRUD 放在 `security` | `service` / `controller` |
| 业务流程放在 `utils` | `service.impl` |
| 配置类放在 `service` | `config` / `properties` |
| 常量放在 `dto` | `constant` |

### 3.3 禁止模糊命名（与 §10 联动）

**默认禁止**新增以下类名模式（含作后缀）：

`Support`、`Helper`、`Manager`、`Processor`、`Facade`、`Adapter`、`Resolver`、`Context`、`Orchestrator`、`Coordinator`

**若必须使用**，须书面说明且回答：

1. 为什么不能归入现有 **controller / service / mapper / security / config**？  
2. 为什么文件名不能使用已有职责后缀（§3.1）？  
3. 是否被 **两个以上** 类或模块稳定复用？  
4. 是否符合 **当前 Maven 模块职责**（§1）？

### 3.4 功能归纳规则（先判层，再建文件）

| 职责 | 目录 |
|------|------|
| HTTP 入口 | `controller` |
| 业务主流程 | `service` + `service.impl` |
| 数据库访问 | `mapper` |
| 表结构映射 | `entity` |
| 请求 / 响应数据 | `dto` |
| 认证鉴权链路 | `security` |
| Spring / 框架装配 | `config` |
| 配置项绑定 | `properties` |
| 常量 | `constant` |
| 全局异常与 HTTP 状态映射 | `handler` |
| 纯静态、无状态、多类复用工具 | `utils` |

**说不清属于哪一层 → 默认不允许新增**；须重新分析职责后再定目录与文件名。

---

## 4. service / service.impl 规则

| 目录 | 内容 |
|------|------|
| `service` | **仅**服务接口 |
| `service.impl` | **仅**实现类，`XxxServiceImpl` |

**规则**

1. Controller **优先依赖** Service **接口**。  
2. **主流程在 ServiceImpl**；接口只定义能力。  
3. **禁止**接口与实现混放；**禁止**取消 `impl` 目录。  
4. **禁止**为拆分而拆分业务流程。  

**示例**

```text
service/AdminMenuService.java
service/impl/AdminMenuServiceImpl.java
```

---

## 5. DTO 规则

**唯一包**：`dto`（一级压平）。

**禁止子包**

- `dto/request`、`dto/response`、`dto/internal`、`dto/query`、`dto/command`

**命名后缀**

- `XxxRequestDto`、`XxxResponseDto`、`XxxDetailDto`、`XxxTreeDto`、`XxxQueryDto`、`XxxUpdateDto`、`XxxListItemDto`

**规则**

1. DTO **不放** common。  
2. DTO **不**承载业务逻辑；**不**等同 Entity。  
3. 新增 DTO **优先单文件单职责**。  
4. **聚合 DTO** 仅允许少量例外，须在 PR / 设计说明中写明原因（见 §16.1）。

---

## 6. Controller 规则

**只做**

- 接收 HTTP、参数校验、`@PreAuthorize` 等、调用 Service、返回 `ApiResponse` / `HttpMessageBody`

**禁止**

- 复杂业务、直访 Mapper、拼菜单树、权限主流程、直操作 DB/Redis、过重 DTO 组装  

---

## 7. Mapper 规则

**只做**数据库访问（单表 / 关联 / 分页 / 权限或菜单 SQL）。

**禁止**业务判断、权限决策、登录流程、菜单树组装、状态机。

---

## 8. security 包规则

**只放认证鉴权链路**

- `AuthenticationEntryPoint`、`AccessDeniedHandler`  
- `JwtDecoder` / `JwtAuthenticationConverter`（亦可与 `config` 配合装配，见 §16.2）  
- Filter、`PermissionChecker`、AuthContext 同步器  
- 运行时授权快照（若放本包，须非 HTTP 契约，见 §16.3）

**禁止**

- 角色/菜单 CRUD、用户-角色/角色-权限绑定、普通业务管理、**业务保护规则类**（应放 `service`，如 `AdminRoleGuard`）

**区分**

- **RBAC 管理** → admin 的 `controller` / `service`  
- **请求能否过、当前用户有什么权限** → `security` + `AdminPermissionChecker`

---

## 9. config / constant / properties / handler / utils

| 包 | 放什么 | 不放什么 |
|----|--------|----------|
| **config** | Spring、`SecurityFilterChain` 装配、OpenAPI、Bean | 业务流程 |
| **constant** | 模块常量、权限码、Redis Key、i18n message key | 复杂业务判断 |
| **properties** | `@ConfigurationProperties` | 普通 DTO |
| **handler** | `GlobalExceptionHandler`、HTTP 状态映射辅助 | Security EntryPoint / AccessDenied（归 **security**） |
| **utils** | 纯静态、无状态、无 `@Component` | 业务流程、「不好归类」的类 |

**utils 准入**：至少 **两个以上** 类稳定复用，且无 Spring 配置依赖。

---

## 10. 禁止新增的中间层

**默认禁止**目录或类名后缀：

`manager`、`processor`、`facade`、`adapter`、`support`、`helper`、`resolver`、`context`、`coordinator`、`orchestrator`

**例外须书面说明且满足其一**

1. 被 **两个以上业务模块** 稳定复用  
2. 框架 / 认证 / 异常等基础设施  
3. 单一 ServiceImpl 过长，拆出类有 **稳定单一职责**  
4. 明确接口 + 多实现必要  

---

## 11. 新增文件四问（AI 必答）

每次新增 Java 类前必须回答：

1. **为什么不能放在现有 ServiceImpl？**  
2. **是否真的被两个以上类稳定复用？**  
3. **是否会造成跨模块 Maven / import 依赖？**  
4. **是否符合当前模块职责边界（§1）？**  
5. **文件名后缀是否与目录匹配（§3）？**（移动 / 重命名时同样必答）

**答不清 → 不允许新增、移动或重命名文件。**

---

## 12. admin 模块后续规则

**保持按层分包**；**禁止** `admin.rbac.*`、`admin.menu.processor.*` 等。

**5E 菜单** 放入现有层级（示例）：

```text
controller/AdminMenuController
service/AdminMenuService
service/impl/AdminMenuServiceImpl
mapper/AdminMenuMapper
entity/AdminMenuEntity
dto/AdminMenuTreeDto
dto/AdminMenuRequestDto
dto/AdminMenuResponseDto
```

菜单树组装在 **`AdminMenuServiceImpl`**；`/auth/me` 的 menus 由 **`AdminAuthMeServiceImpl`** 调用 Menu 查询。

---

## 13. auth 模块后续规则

**认证基座**；多 Controller 分入口（Login / Refresh / Logout / JWKS / Register / Ping）**允许**，**禁止**无限膨胀小类。

**主流程落位**

| 能力 | Service | ServiceImpl |
|------|---------|-------------|
| 登录/注册编排 | `AdminAuthService` | `AdminAuthServiceImpl` |
| JWT 签发/解析 | `TokenService` | `JwtTokenServiceImpl` |
| Refresh 会话 | `AdminRefreshSessionService` | `RedisAdminRefreshSessionServiceImpl` 等 |
| Refresh Cookie | `AdminRefreshCookieService` | `AdminRefreshCookieServiceImpl` |

JWT 签发配置可保留 **`config/jwt`**（与 admin 的 `AdminJwtDecoderConfig` 对称，见 §16.2）。

---

## 14. site 模块后续规则

1. **site-app**：HTTP、config、security、handler、dto（压平）  
2. **site-core**：entity、mapper、`core.context`（站点上下文）  
3. **site-car / site-cake**：行业能力，依赖 site-core  
4. 有业务后再补 `service` / `impl`；**禁止**过早 manager / adapter  
5. **禁止** `dto/authme` 等子包（**后续收敛**，见 §17）

---

## 15. gateway / common / infra / agent（不套业务树）

### gateway

允许：`config`、`filter`、`handler`、`properties`、`GatewayApplication`、yml 路由  
禁止：`entity`、`mapper`、`service`、`service.impl`、业务 dto

### common

允许：`api`、`enums`、`exception`、`page`、`security`、`constant`、`utils`  
禁止：具体业务对象

### infra

允许：`config`、`web`、`i18n`、`entity`（基类）、`security.oauth2`、`service`（i18n 等基础设施）  
禁止：业务流程、业务 Entity/Mapper

### agent

允许：`client` 等  
禁止：业务编排

---

## 16. 当前代码允许的规范例外（记录，非鼓励扩散）

### 16.1 聚合 DTO

- **`admin.dto.AdminRbacDtos`**：多个 RBAC HTTP record 集中；**禁止**再建 `dto/rbac` 子包。  
- 5E 菜单：优先 `AdminMenuTreeDto` 等单文件；record 过多时可 `AdminMenuDtos` 聚合并 **文档说明**。

### 16.2 Jwt 配置包

- **auth**：`config/jwt/AuthJwtConfiguration`（签发 Bean）  
- **admin**：`config/AdminJwtDecoderConfig`（验签）  
- **不强制**迁入 `security`，除非 RS 组件显著增多。

### 16.3 内部授权快照

- **`security.AdminCurrentAuthorizationSnapshot`**：请求级 RBAC 快照，**非** REST DTO；**禁止**放回 `AdminRbacDtos`。

### 16.4 静态业务规则

- **`service.AdminRoleGuard`**：`final` + 静态方法；super_admin 保护；**非** Spring Bean；**禁止**复制多个 support 类。

---

## 17. 后续代码收敛建议（本轮不执行）

| 优先级 | 项 | 说明 |
|--------|-----|------|
| P2 | site-app `dto/authme/*` | 压平到 `dto/*Dto` |
| P2 | `handler/support/GlobalExceptionHttpStatus` | auth/site 与 admin 对齐到 `handler` 根 |
| P3 | admin `pom` 依赖 agent | Ping 已解耦，评估移除依赖 |
| P3 | 测试残留 `@MockBean AgentClient` | 清理无用 Mock |
| P3 | site 补 `SiteAuthMeService` | 有业务后再做 |

---

## 18. 相关文档索引

| 文档 | 关系 |
|------|------|
| [后端包结构与命名规范V1.md](./后端包结构与命名规范V1.md) | 命名后缀、common 边界、service.impl 细节 |
| [模块职责与依赖边界.md](../../总览/模块职责与依赖边界.md) | Maven 依赖表 |
| [架构设计.md](./架构设计.md) | 拓扑与运行方式 |
| [阶段5D.1 综合审查收敛](../../作業検査/阶段5D.1-Admin模块包结构命名与RBAC链路综合审查收敛.md) | 5D.1 代码收敛记录 |

---

## 19. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | §3 文件名与文件夹职责匹配；§11 四问增加第 5 条（后缀校验） |
| 2026-05-23 | V1 初版：Maven 边界 + 业务模块标准树 + AI 四问 + 例外与收敛建议 |
