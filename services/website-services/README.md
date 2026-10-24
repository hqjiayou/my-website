# website-services

`website-services` 是 Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。**阶段 4H.0（请求链路 + Token 错误体）**、**4H（Gateway 全链路）**、**4I（认证收口）** 已完成；**阶段 5A（Admin RBAC 详细设计）**、**5B（RBAC 权限校验底座）**、**5C（角色与权限管理）** 已完成（见 [**5A**](../../docs/website-services/设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md) / [**5B**](../../docs/website-services/设计文档/权限与后台管理/阶段5B-Admin-RBAC权限校验底座落地说明.md) / [**5C**](../../docs/website-services/设计文档/权限与后台管理/阶段5C-角色与权限管理接口落地说明.md)）。**现行认证 HTTP 契约**（网关 **`/api/auth/**`**、业务 **`/api/*/auth/me`**）已与实现对齐。**5D（管理员与角色绑定）** 已完成；**下一步：5E** 菜单权限与导航。**阶段 4F** 已将 **`auth`** 收敛为**单一可执行认证服务**（**`mvn -pl auth -am spring-boot:run`**），负责 **Login / Refresh / Logout**、**JWT 签发**、**Redis Refresh 会话**；**Admin / Site-app** 为 **OAuth2 Resource Server** 与业务 API。**阶段 4G** 已完成 **RS256 + JWKS**。设计见 [**阶段 4D**](../../docs/website-services/设计文档/认证与安全/阶段4D-Auth独立认证服务架构回归设计与迁移方案.md) · **落地**见 [**阶段 4F**](../../docs/website-services/设计文档/认证与安全/阶段4F-Auth单服务收敛与认证主链路上提落地说明.md) · **4G** 见 [**RS256/JWKS**](../../docs/website-services/设计文档/认证与安全/阶段4G-RS256与JWKS签名验签体系迁移说明.md)。**完整文档导航**：[`docs/website-services/README.md`](../../docs/website-services/README.md)。

除此之外：统一网关入口、多端可启动应用、基础设施、MyBatis-Plus、Flyway、OpenAPI。（[`架构设计`](../../docs/website-services/设计文档/基础架构/架构设计.md) §8）

## 认证 HTTP 契约（现行 · 对外以此为准）

以下路径经 **`gateway :8080`** 与**直连各服务**一致（网关 **`Path=/api/auth/**`** 须排在 **`/api/admin/**`、`/api/site/**` 之前）。

| 语义 | HTTP | 模块 |
|------|------|------|
| 管理端注册（首管理员 / 受限开放注册） | **`POST /api/auth/admin/register`** | **auth** |
| 管理端登录 | **`POST /api/auth/admin/login`** | **auth** |
| 刷新 Access（Refresh **HttpOnly Cookie**） | **`POST /api/auth/admin/refresh`** | **auth** |
| 登出（吊销 Refresh、清除 Cookie） | **`POST /api/auth/admin/logout`** | **auth** |
| 前台登录 | **`POST /api/auth/site/login`**（另有 refresh / logout） | **auth**（SITE JWT + `site_id`；Refresh Cookie `MYWEBSITE_SITE_REFRESH_TOKEN`） |
| 管理端当前用户上下文 | **`GET /api/admin/auth/me`**（需 Bearer） | **admin** |
| 前台当前用户上下文（占位） | **`GET /api/site/auth/me`**（需 Bearer） | **site-app** |

**Refresh Cookie（`mywebsite.security.token`）**：**`MYWEBSITE_REFRESH_TOKEN`**，**`Path=/api/auth/admin`**，`HttpOnly`，**`SameSite=Lax`**（可配置）；本地 **`SECURITY_REFRESH_COOKIE_SECURE=false`**，生产建议 **`Secure=true`**。

**管理端自助注册**：接口 **`POST /api/auth/admin/register`**。默认在 **PostgreSQL `admin_user` 中尚无未删除的记录**时可创建**第一个**管理员（bootstrap）；创建第二个管理员前须显式 **`security.auth.admin-open-registration-enabled=true`**（**默认 false**，一般不用于生产）。

**Access JWT**：载荷含 **`subject_type`**（**`SubjectType`**：`ADMIN` / `SITE`，与 **`aud`「面向谁」** 语义分离）；**`auth`** 内 **`JwtTokenServiceImpl.parseToken`** 将声明解析为 **`SubjectType`** 后与期望值 **枚举比对**，不再与 **`TokenAudience.name()`** 混绑。

**响应信封**：**Auth** Login/Refresh/Logout 成功路径仍为 **`HttpMessageBody`**，`code` **省略**（Jackson 不传 `null`），**message = "success"** 约定不变。**Admin / site-app** 作为 Resource Server：业务成功（如 Ping）使用带 **`code: 0`** 的约定；**401 / 403** 等安全拒绝走 **`HttpMessageBody`**，含 **`code`**（与 **`ErrorCode`** 一致）与 **i18n message**；**Bearer 无效**时另带 **`WWW-Authenticate: Bearer error="invalid_token"`**（详见 [**阶段4H.0**](../../docs/website-services/设计文档/认证与安全/阶段4H.0-请求链路日志与Token错误响应完善说明.md)）。

---

## 文档入口与阶段 TODO（唯一权威）

| 条目 | 状态 |
|------|------|
| 阶段 4F（Auth 单服务收敛 + 主链路上提） | **已完成** |
| 文档体系重整（`docs/website-services` 分类与总览） | **已完成** |
| 阶段 4G（RS256 + JWKS） | **已完成** |
| **阶段 4H.0**（请求链路日志 + **`X-Request-Id`** + Resource Server **401** JSON / **invalid_token**） | **已完成**（[**说明**](../../docs/website-services/设计文档/认证与安全/阶段4H.0-请求链路日志与Token错误响应完善说明.md)） |
| 阶段 4H（Gateway 全链路真实联调） | **已完成** |
| 阶段 4I（认证阶段收口：4H 尾巴、删兼容路径、Cookie/i18n/边界） | **已完成**（见 [**阶段4I 说明**](../../docs/website-services/联调说明/阶段4I-迁移清理收口.md)） |
| **认证契约现行版**（`/api/auth/*` + `/api/*/auth/me`，文档/网关一致） | **已完成**（2026-05；见本文 §认证 HTTP 契约） |
| 阶段 5A（Admin RBAC 权限体系详细设计） | **已完成**（见 [**阶段5A 说明**](../../docs/website-services/设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md)） |
| 阶段 5B（RBAC 权限校验底座） | **已完成**（`AdminPermissionChecker`、真实 `/auth/me`；Method Security 验证见 test `RbacTestController`；见 [**5B 说明**](../../docs/website-services/设计文档/权限与后台管理/阶段5B-Admin-RBAC权限校验底座落地说明.md) §7/§10） |
| 阶段 5C（角色与权限管理） | **已完成**（`/api/admin/roles`、`/api/admin/permissions`、角色-权限绑定、`V2` 种子；见 [**5C 说明**](../../docs/website-services/设计文档/权限与后台管理/阶段5C-角色与权限管理接口落地说明.md)） |
| 阶段 5C.1（RBAC 种子 PG 验证） | **已完成**（`my_website_rbac_seed_verify` + `AdminRbacSeedVerifyPgTest`；见 [**5C.1 说明**](../../docs/website-services/数据文档/阶段5C.1-RBAC种子真实PostgreSQL迁移验证.md)） |
| 阶段 5C.2（清理测试探针权限） | **已完成**（生产 `V2` 不含 `admin:rbac:check`；16 条生产权限） |
| 阶段 5D（管理员与角色绑定） | **已完成**（`/api/admin/users/{id}/roles`；见 [**5D 说明**](../../docs/website-services/设计文档/权限与后台管理/阶段5D-管理员与角色绑定接口落地说明.md)） |
| 阶段 5D.1（Admin 包结构 / 命名 / RBAC 链路审查收敛） | **已完成**（见 [**5D.1 综合说明**](../../docs/website-services/作業検査/阶段5D.1-Admin模块包结构命名与RBAC链路综合审查收敛.md)） |
| **单模块文件结构与 AI 开发约束规则 V1** | **已完成**（**仅文档**；见 [**结构规则 V1**](../../docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)） |
| 阶段 5E（菜单导航） | **待开始**（须遵守结构规则 V1） |

**勿在仓库另建第二份「后端 TODO 清单」。** 完整阶段史、产出与下一步：**[`docs/website-services/总览/后端开发路线图与阶段状态.md`](../../docs/website-services/总览/后端开发路线图与阶段状态.md)**；文档总导航：**[`docs/website-services/README.md`](../../docs/website-services/README.md)**。

## 快速理解

本工程当前有 **四个**可执行的 Spring Boot **应用**：

| 服务 | 模块 | 端口 | 职责 |
|------|------|------|------|
| Gateway | `gateway` | `8080` | 网关；**`/api/auth/**`** → **Auth(8083)**；`/api/admin/**` → Admin；`/api/site/**` → Site（**顺序**：`/api/auth/**` 须在 **`/api/admin/**`、`/api/site/**`** 之前） |
| Admin API | `admin` | `8081` | 后台**业务** API；**OAuth2 Resource Server** 校验 Access JWT |
| Site API | `site/site-app` | `8082` | 前台站点 API |
| Auth | **`auth`** | **`8083`**（`AUTH_APP_PORT`） | 统一认证：**`/api/auth/admin/login`**、**`/api/auth/admin/refresh`**、**`/api/auth/admin/logout`**、`GET /api/auth/ping`；**不设** Flyway（DDL 由 Admin/infra 迁移） |

## 模块结构

```text
website-services/
├── gateway
├── auth
├── admin
├── agent
├── common
├── infra
├── site
│   ├── site-app
│   ├── site-core
│   ├── site-car
│   └── site-cake
├── pom.xml
└── README.md
```

## 模块说明

### gateway

统一 API 网关，可独立启动。

- 端口：`8080`
- **`/api/auth/**`** → `http://localhost:8083`（Auth）；**`/api/admin/**`** → `http://localhost:8081`（Admin）；**`/api/site/**`** → `http://localhost:8082`（Site）
- 提供本地开发 CORS 配置
- **`X-Request-Id`**：网关未带则生成 UUID，写入**下游转发请求**与**响应头**（见 [**阶段4H.0**](../../docs/website-services/设计文档/认证与安全/阶段4H.0-请求链路日志与Token错误响应完善说明.md)）。
- 访问日志：**`RequestIdGatewayFilter`** 在请求结束时 **INFO** 输出 `gateway access requestId=… method=… path=… route=… status=… costMs=…`（不写 body、Authorization、Cookie、Token）。
- 不承载业务逻辑
- 不直连 PostgreSQL / Redis / MinIO
- **不**在网关校验 JWT（业务服务各自 Resource Server）
- **阶段 4H**：经 **:8080** 与 **Auth/Admin 多进程** 的真实联调结论见 [**阶段4H 联调说明**](../../docs/website-services/联调说明/阶段4H-Gateway唯一入口全链路真实联调.md)；路由 YAML 内注释强调 **`/api/auth/**` 必须在 `/api/admin/**`、`/api/site/**` 之前**。

### admin

后台管理 API，可独立启动。

- 端口：`8081`
- 依赖 `common`、`infra`、`agent`
- **认证编排（登录/刷新/登出）**：仅在 **`auth`（8083）**，路径 **`/api/auth/*`**；**不要**再假设 **`/api/admin/auth/login`** 等旧前缀。
- **业务上下文**：**`GET /api/admin/auth/me`**（需 Bearer）；JWT 验签走 **`AUTH_JWKS_URI`**。
- **`/api/admin/**`（业务）**：默认需要 **`Authorization: Bearer <accessToken>`**（Swagger **`adminBearer`**）；JWT 经 **`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`**（**`AUTH_JWKS_URI`**）验签，**不持有**签名密钥。
- `AdminSecurityConfig`、`AdminAuthenticationEntryPoint`、`AdminAccessDeniedHandler`、`AuthContextSynchronizingFilter` 保留。
- 健康探测：`GET /api/admin/ping`（需 Bearer）
- **阶段 3B**：首批后台 **`AdminUserEntity` 等** Mapper（见 [`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)）
- **阶段 4C 脚本**：`scripts/local/run-4c-http-verify.ps1` 已区分 **`$AuthBase`（默认 8083）** 与 **`$AdminBase`**；认证 HTTP 打 Auth，Ping 仍打 Admin。
- **阶段 4D～4G 路线图**：见 [**阶段4D 文档**](../../docs/website-services/设计文档/认证与安全/阶段4D-Auth独立认证服务架构回归设计与迁移方案.md)。

### site

`site` 是 Maven 聚合模块，不直接启动。

子模块：

- `site/site-app`
  - 前台站点 API 启动模块
  - 端口：`8082`
  - 当前提供 `GET /api/site/ping`；**`GET /api/site/auth/me`**（占位，需 Bearer）
  - 统一装配 `site-core`、`site-car`、`site-cake`
- `site/site-core`
  - 前台站点公共核心能力
  - 承载 `SiteContext`、当前站点上下文、站点配置读取能力
  - **阶段 3B**：首批站点域 **`SiteEntity` / `SiteUserEntity` / `SiteUserProfileEntity`** 及对应 **`Mapper`**（依赖 **`infra`** 基类；见 [`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)）
- `site/site-car`
  - 空模块，只有 `pom.xml`，没有业务代码
- `site/site-cake`
  - 空模块，只有 `pom.xml`，没有业务代码

### common

通用基础模块（详见 [`后端包结构与命名规范 V1`](../../docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md) §1）。

仅承载跨模块共用 **协议外壳与基础模型**：

- `ApiResponse`
- `ErrorCode`
- `BusinessException`（`messageKey` + `messageArgs`；由各应用 Handler + `LocalizedMessageService` 解析为自然语言，见 [后端多语言支持策略设计 V1](../../docs/website-services/设计文档/认证与安全/后端多语言支持策略设计V1.md)）
- `PageResult`（通用分页载荷；新业务若引入 `PageRequest` 须经多模块复用评估后再落 common）
- **`com.mywebsite.common.security`**：`JwtClaimConstants`、`SubjectType`、`SubjectCodes`、`TokenAudience`、`AuthenticatedUser`、`AuthContext`、`AuthenticatedUserUtils`（Resource Server 与网关侧共享身份契约）
- 通用常量

**禁止**下沉任何 **`admin`/`site`/业务板块**的 **Request / Response**，避免 common 沦为「全场 DTO 垃圾桶」。

### infra

基础设施接入模块。

- **国际化（阶段 4B.5）**：`Accept-Language`（BCP 47）解析、默认 **`ja-JP`**、`LocalizedMessageService`、成功响应 `ApiResponse` 写出前本地化；定案见 [`后端多语言支持策略设计 V1`](../../docs/website-services/设计文档/后端多语言支持策略设计V1.md)。
- PostgreSQL：通过 Spring Boot DataSource 自动配置
- Redis：通过 Spring Data Redis 自动配置
- MinIO / S3：提供 `MinioClient` Bean
- MyBatis-Plus：全局插件与基类（见 [`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)）
  - **`com.mywebsite.infra.entity.AbstractAuditableEntity`**（`@TableLogic`、`@Version`、公共审计列）
  - **`MybatisPlusInterceptor`** + **`OptimisticLockerInnerInterceptor`**（**`MybatisPlusInfraConfig`**）
  - **`AuditMetaObjectHandler`**（仅 `createdBy` / `updatedBy`；**`infra.handler`**）
  - **`StorageConfig`**（MinIO `MinioClient`，**`infra.config`**）
- Flyway：数据库迁移机制由可启动应用（如 `admin`）执行；脚本目录见下方「Flyway」
- 不承载业务用例逻辑；具体 **`XxxEntity` / `Mapper`** 放在 **`admin`** / **`site-core`** 等模块

### auth

统一认证 **可执行** 模块（**单模块**，不再有 `auth-core` / `auth-app` 拆分）。

- **`AuthApplication`**；默认端口 **`8083`**（**`AUTH_APP_PORT`**）。
- **承担**：**`POST /api/auth/admin/register`**（首管理员 bootstrap / 可配置开启开放注册）、**`POST /api/auth/admin/login`**、**`POST /api/auth/admin/refresh`**、**`POST /api/auth/admin/logout`**、`GET /api/auth/ping`、**`GET /.well-known/jwks.json`**、Access JWT **RS256** 签发（**`TokenService`/`JwtTokenServiceImpl`**，`subject_type` 与 **`SubjectType`** 显式校验）、Refresh Cookie（**`mywebsite.security.token`**）、**Redis** 刷新会话（**`RedisAdminRefreshSessionServiceImpl`** 等）；**`i18n/auth`** 承载 **`admin.auth.*`** 等认证文案。注册与安全策略：`security.auth.admin-bootstrap-registration-enabled`、`security.auth.admin-open-registration-enabled`、`security.auth.admin-register-min-password-length`。
- **直连** 与 admin 相同的 **`admin_user` 表**（本模块内自有 `entity`/`mapper`；**不**依赖 `admin` jar）。
- **显式关闭 Flyway**（避免与 admin 重复迁移）；使用 **`AUTH_DB_*`** 可选覆盖 **`DB_*`**。
- **启动**：`mvn -pl auth -am spring-boot:run`。

### agent

Java 后端未来调用 AI/Agent 服务的客户端层。

- 当前提供 `AgentClient.ping()`
- 暂不真实调用外部 AI API
- 不直接依赖 PostgreSQL / Redis / MinIO
- 如果未来需要读取业务数据、文件或缓存会话，由 `admin` / `site-app` 等上层应用模块编排

## 认证与用户模型边界

- **`auth`**：统一认证服务（4F）；后续 Site 登录仍走 **auth**，与 Admin realm 区分在 token/会话策略演进。**阶段 5A RBAC**：**暂不推进**。
- admin 侧用户体系由 `admin` 模块承载，后续包括 `admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission`。
- site 侧用户体系由 `site-core` 承载，后续包括 `site_user`、`site_user_profile`，必要时扩展 `site_user_auth`。
- 前台用户统一使用 `site_user`，通过 `site_id` 或 `site_code` 做多站点隔离。不要为 car/cake 分别建立 `car_user`、`cake_user`。

## 依赖关系

```text
gateway       -> Spring Cloud Gateway only

common
  ↑
infra         -> common
auth          -> common, infra
agent         -> common

site-core     -> common, infra
site-car      -> site-core
site-cake     -> site-core
site-app      -> common, infra, agent, site-core, site-car, site-cake

admin         -> common, infra, agent
```

约束：

- `gateway` 不依赖业务模块。
- `agent` 不直接依赖 `infra`，不做业务数据编排。
- `site` 是聚合模块，不承载 Java 代码。
- 可启动应用：`gateway`、`admin`、`site/site-app`、**`auth`**。

## 企业级基础框架 V1

### MyBatis-Plus

本工程采用 MyBatis-Plus 作为默认持久层框架，不引入 JPA / Hibernate。

约定：

- **企业级约定（基类、逻辑删除、乐观锁、审计填充、8 表归属）**：见 **[`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)**。**阶段 3B** 已落地首批 **8 表 `entity` + `mapper`**（无业务 CRUD、无 XML）。
- 默认 Mapper 放在各业务模块的 **`mapper`** 包下，并使用 `@Mapper` 标注。
- 复杂查询允许使用自定义 Mapper 方法与 XML SQL。
- XML Mapper 默认扫描 `classpath*:/mapper/**/*.xml`。

### Flyway

Flyway 是唯一数据库结构迁移机制。后续任何表结构变更必须通过 migration 文件提交，不允许只在本地手工改库。

**执行策略（阶段 2B 起）：**

- **脚本目录（唯一来源）**：`infra/src/main/resources/db/migration`
- **`site-app` 默认不执行 Flyway**（环境变量 `SITE_APP_FLYWAY_ENABLED`，默认 `false`），避免与 `admin` 共用同一库时出现重复 migration / `flyway_schema_history` 冲突。
- 本地联调建议：**先启动 `admin`** 完成迁移，再启动 `site-app`。说明见 `site/site-app/src/main/resources/db/README.md`。
- `admin` 侧开关：`FLYWAY_ENABLED`（默认 `true`）。

首批已落地脚本：`V1__init_platform_core_tables.sql`（其后另有 V2–V15+）。执行策略与设计边界见 [`数据库模型设计V1`](../../docs/website-services/设计文档/数据与持久层/数据库模型设计V1.md)；**字段级定义见 [`数据字典`](../../docs/website-services/数据字典/)**。当前完成度：[`docs/overview/CURRENT_STATE.md`](../../docs/overview/CURRENT_STATE.md)。

migration 命名使用 Flyway 标准格式：`V{version}__{description}.sql`。

### JWT 与认证边界（4F～4G + 现行契约）

- **签发**：仅在 **`auth`**（`TokenService`/`JwtTokenServiceImpl`、`AuthJwtKeyProperties`、`TokenProperties`、RSA PEM；**RS256**）。
- **载荷**：**`sub`** 为 **`SubjectCodes`**（如 `ADMIN_000001`）；**`subject_type`** 为 **`SubjectType`** 名称（**ADMIN** / **SITE**）；**`aud`** 为 **`security.jwt.audience` / `site-audience`**。解析令牌时 **`subject_type`** 与 **`SubjectType`** **枚举比对**，不与 **`TokenAudience`** 字符串偶然同名混为一谈。
- **Resource Server 验签**：**`admin` / `site-app`** 使用 **`jwk-set-uri`**（`**AUTH_JWKS_URI**`）与自定义 **`JwtDecoder`** 校验 **`iss`/`aud`/`subject_type`**（与各自受众一致）。
- **4G 后**：Admin **不再**使用 `SECURITY_JWT_SECRET`；Access JWT 仅由 Auth 用**私钥**签发，公钥经 **`GET /.well-known/jwks.json`** 发布。

### OpenAPI / Swagger

`admin`、`auth` 与 `site-app` 已接入 springdoc-openapi。`gateway` 暂不聚合文档。

本地访问：

- Admin Swagger UI：<http://localhost:8081/swagger-ui/index.html>
- Auth Swagger UI：<http://localhost:8083/swagger-ui/index.html>
- Site Swagger UI：<http://localhost:8082/swagger-ui/index.html>

### 模块内 Java 包结构

**Maven 边界 + 业务模块标准树 + AI 约束（必读）**：[单模块文件结构与AI开发约束规则V1](../../docs/website-services/设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)。**命名后缀**见 [后端包结构与命名规范V1](../../docs/website-services/设计文档/基础架构/后端包结构与命名规范V1.md)。

业务模块（admin、auth、site-core、site-app）按需使用：`config`、`constant`、`controller`、`dto`（**单层**，`XxxRequestDto` / `XxxResponseDto`）、`entity`、`mapper`、`properties`、`security`、`service` / `service.impl`、`handler`、`utils`。**禁止** `dto/request` 等子目录、`admin.rbac.*`、默认新增 manager/processor/facade 等中间层。gateway / common / infra / agent **不套用**上述业务树。

## 本地运行依赖

**本地基础设施统一使用既有 local-stack**（本仓库 **不再**附带 PostgreSQL / Redis 的 Compose）：典型映射为 **`local-postgres` → `localhost:5432`**、**`local-redis` → `localhost:6379`**、**`local-aistor` → `localhost:9000`**。请先在其 Compose 目录启动栈；**勿**与本仓库历史的第二套 Postgres/Redis 混用，以免 seed / JDBC 指向错误实例。**阶段 4B Admin 认证**主线依赖仍为 **PostgreSQL + Redis**；对象存储按现有 local-stack 或等价实例提供。

本地开发前请先确认基础设施已就绪：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- MinIO / AIStor API：`http://localhost:9000`
- MinIO / AIStor Console：以你的 local-stack 为准（常见 `9001`）

`admin` 与 `site-app` 的 **local profile**（`application-local.yml`）中，数据库 / Redis / MinIO / Admin JWT 使用占位符；**未设置时在 YAML 中有安全默认值**（含合法 jdbc URL），同时支持从 **同级或祖先目录的 `.env`** 注入（启动类会先加载 `.env` 为系统属性，见 `common` 模块 `LocalDotenvSupport`）。**请在 `.env` 中维护 `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`**（或与 **`DB_*`** 对齐），使 JDBC 与本机 Postgres 账号一致。**请先复制范本：**

```bash
cd services/website-services
cp .env.example .env
# 再按本机修改 .env（勿提交）
```

范本文件：**[`services/website-services/.env.example`](./.env.example)**（仓库跟踪）。**仅 Java 进程读取该 `.env`**；与 **local-stack** 自带的 Compose `.env` **分开维护**，变量不必强行同名复制。

其余常用键（节选，完整见 `.env.example`）：

- `POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD`（本地数据库主配置；可选 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 覆盖 JDBC）
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`MINIO_SECURE`
- `AUTH_APP_PORT`（默认 `8083`）
- `AUTH_DB_*`（可选；未设置时回退到 `DB_*` 再回退到 `POSTGRES_*`，供 **auth** 分域配置）
- **`AUTH_JWT_PRIVATE_KEY_PATH`**、**`AUTH_JWT_PUBLIC_KEY_PATH`**、**`AUTH_JWT_KEY_ID`**（Auth **RS256**）
- **`AUTH_JWKS_URI`**（Admin Resource Server 拉取 JWKS；默认 `http://localhost:8083/.well-known/jwks.json`）
- **`SECURITY_REFRESH_COOKIE_SECURE`**（Refresh Cookie）
- `FLYWAY_ENABLED`、`SITE_APP_FLYWAY_ENABLED`

在 `services/website-services/` 目录创建 `.env`（已在仓库根 `.gitignore` 中忽略 **`.env`**，**勿提交**）。本地可从 **`services/website-services`**、各应用子目录 **或仓库根目录 `my-website`** 启动：`LocalDotenvSupport` 会在 **`user.dir`** 及其祖先中查找 **`.env`**，或在每层尝试 **`services/website-services/.env`**（根目录启动时常用）。更多说明见 [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](../../docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md) §3。

Flyway 相关：

- `FLYWAY_ENABLED`：是否由 **admin** 执行迁移（默认 `true`）。
- `SITE_APP_FLYWAY_ENABLED`：是否由 **site-app** 执行迁移（默认 `false`，建议保持关闭）。

若日志出现 **`database "my_website_dev" does not exist`**：需在 **当前连接的 Postgres 实例**上手动 `CREATE DATABASE`，或调整 **local-stack** 初始化逻辑（取决于你如何创建库）；步骤见 [`scripts/README.md`](./scripts/README.md)。

## 构建顺序

首次启动或模块变更后，建议先在 `services/website-services` 根目录执行一次全量构建：

```bash
mvn clean install -DskipTests
```

构建成功后，`gateway`、`auth`、`admin`、`site-app` 可以分别独立启动（联调认证时 **需同时** 起 **auth** 与 **admin**；仅 `mvn -pl auth` 时请使用 **`mvn -pl auth -am`** 以带上 `common`/`infra`）。

运行测试：

```bash
mvn test
```

仅编译/测试单个模块时建议 **`mvn -pl <模块> -am`**，避免上游模块未安装导致 **`common.security`** 等测试源码找不到。

### 持久层真实 PostgreSQL 回归验证（可选）

在变更 Flyway、首批 Entity/Mapper 或持久层约定后，可在本地 Postgres **专用验证库**上运行 Mapper + Flyway 联通性回归（详见 **[持久层真实 PostgreSQL 验证说明](../../docs/website-services/数据文档/持久层真实PostgreSQL验证说明.md)**）。

```bash
mvn clean test -Ppersistence-verify-pg -pl admin,site/site-app -am
```

默认 **`mvn test`** 不包含上述验证（JUnit Tag **`verifyPg`** 由 Surefire 默认排除）。

## 推荐启动顺序

### 1. 启动本地基础设施

先确认 WSL + Docker 中的 PostgreSQL、Redis、MinIO / AIStor 已启动。

如果基础设施未启动，`admin` / **`auth`** / `site-app` 仍可能部分启动成功，但 `/actuator/health` 中数据库或 Redis 会显示 `DOWN`。

### 2. 启动 auth（认证服务）

```bash
mvn -pl auth -am spring-boot:run
```

验证探活：

```bash
curl http://localhost:8083/api/auth/ping
```

### 3. 启动 admin

```bash
mvn -pl admin -am spring-boot:run
```

`GET /api/admin/ping` 需要 Bearer（应先通过 **auth** 登录拿到 Access JWT）：

```bash
curl -i http://localhost:8081/api/admin/ping
```

### 4. 启动 site-app

```bash
mvn -pl site/site-app -am spring-boot:run
```

启动后验证：

```bash
curl http://localhost:8082/api/site/ping
```

### 5. 启动 gateway

```bash
mvn -pl gateway -am spring-boot:run
```

启动后通过网关验证（**ping 仍需 Bearer**；认证请求可走网关到 **auth**）：

```bash
curl -i http://localhost:8080/api/admin/ping
curl http://localhost:8080/api/site/ping
curl http://localhost:8080/api/auth/ping
```

说明：`gateway` 可以先启动，但推荐在本地联调时先启动 **`auth`、admin、`site-app`**，再启动 `gateway`，这样网关路由目标服务已经可用。

## 验证接口

- Auth 探活：<http://localhost:8083/api/auth/ping>
- Gateway → Auth：<http://localhost:8080/api/auth/ping>
- Gateway → Admin business：<http://localhost:8080/api/admin/ping>（需 Bearer）
- Gateway → Site：<http://localhost:8080/api/site/ping>
- Admin 直连：<http://localhost:8081/api/admin/ping>（需 Bearer）
- Site 直连：<http://localhost:8082/api/site/ping>
- Gateway health：<http://localhost:8080/actuator/health>
- Auth health：<http://localhost:8083/actuator/health>
- Admin health：<http://localhost:8081/actuator/health>
- Site health：<http://localhost:8082/actuator/health>

## 常见问题

### health 显示 DOWN

如果 PostgreSQL 或 Redis 未启动，`admin` 和 `site-app` 的 `/actuator/health` 会显示 `DOWN`。这通常是本地基础设施未就绪，不代表应用编译或启动失败。

### gateway 能启动但接口 502/连接失败

请先确认：

- `admin` 是否已启动在 `8081`
- `site-app` 是否已启动在 `8082`
- 访问路径是否以 `/api/admin/` 或 `/api/site/` 开头

### 修改 Maven 模块后 IDE 仍然报红

建议在 IDE 的 Maven 面板执行 Reload All Maven Projects。命令行可用以下命令确认真实构建结果：

```bash
mvn clean install -DskipTests
```

## 相关文档

### 文档归档约定（必读）

后端说明文档：**设计类正文**权威路径为 **`docs/website-services/设计文档/`**；**总导航**见 **[`docs/website-services/README.md`](../../docs/website-services/README.md)**；**当前完成度**见 [`docs/overview/CURRENT_STATE.md`](../../docs/overview/CURRENT_STATE.md)。数据字典权威路径：**`docs/website-services/数据字典/`**（顶层 `docs/数据字典/` 仅为跳转）。

### Java 持久层（阶段 3A / 3B）

| 文档 | 职责 |
| --- | --- |
| [Java 持久层设计 V1](../../docs/website-services/Java持久层设计V1.md) | **`XxxEntity`** 命名、**`AbstractAuditableEntity`**、MyBatis-Plus、审计/逻辑删除/乐观锁、Mapper 边界、8 表归属、包结构（**`entity` / `mapper` / `config` / `handler`**） |

### 数据库与数据文档

数据库相关维护 **双线**：

| 文档 | 职责 |
| --- | --- |
| [数据库模型设计 V1](../../docs/website-services/设计文档/数据与持久层/数据库模型设计V1.md) | 规范、蓝图、多站点与 RBAC 边界 |
| [持久层真实 PostgreSQL 验证说明](../../docs/website-services/数据文档/持久层真实PostgreSQL验证说明.md) | Flyway + Mapper / `persistence-verify-pg` |
| [数据字典索引](../../docs/website-services/数据字典/README.md) | 已落地表字段 / 索引 / 约束手册 |

数据字典分册：

- [平台与站点](../../docs/website-services/数据字典/平台与站点数据字典.md)
- [Admin 认证授权](../../docs/website-services/数据字典/Admin认证授权数据字典.md)
- [Site 用户](../../docs/website-services/数据字典/Site用户数据字典.md)

**约定**：表结构变更必须同步更新 [`数据字典`](../../docs/website-services/数据字典/) 与模型设计文档；冲突时以 Flyway schema 为准。

### 其他

- 总体架构：[整体架构说明](../../docs/website-services/设计文档/基础架构/整体架构说明.md)
- 后端架构：[架构设计](../../docs/website-services/设计文档/基础架构/架构设计.md)
- WSL/Docker：[WSL 与 Docker 环境配置说明](../../docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)
