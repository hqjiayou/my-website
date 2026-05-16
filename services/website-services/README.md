# website-services

`website-services` 是 Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。当前阶段进入企业级基础框架 V1：统一网关入口、后台应用、前台站点应用、基础设施模块、JWT 认证骨架、MyBatis-Plus、Flyway、OpenAPI 与 **按 Maven 边界 + 模块内常规 Spring Boot 包结构**（见 [`架构设计`](../../docs/website-services/架构设计.md) §8）。

## 快速理解

本工程当前有三个可启动服务：

| 服务 | 模块 | 端口 | 职责 |
|------|------|------|------|
| Gateway | `gateway` | `8080` | 统一 API 入口，转发 admin/site 请求 |
| Admin API | `admin` | `8081` | 后台管理后端 |
| Site API | `site/site-app` | `8082` | 前台站点后端 |

本工程还有若干普通 jar 模块，用于沉淀公共能力、基础设施与后续业务扩展。不要直接启动这些 jar 模块。

## 模块结构

```text
website-services/
├── gateway
├── admin
├── agent
├── auth
├── common
├── config
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
- `/api/admin/**` 转发到 `http://localhost:8081`
- `/api/site/**` 转发到 `http://localhost:8082`
- 提供本地开发 CORS 配置
- 提供基础 request-id 预留与请求日志
- 不承载业务逻辑
- 不直连 PostgreSQL / Redis / MinIO

### admin

后台管理 API，可独立启动。

- 端口：`8081`
- 依赖 `common`、`infra`、`agent`、`auth`、`config`
- 当前提供 `GET /api/admin/ping`
- **阶段 3B**：首批后台 RBAC 表 **`AdminUserEntity` 等 5 个 Entity + 5 Mapper**（见 [`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)；**未**依赖 `site-core`）

### site

`site` 是 Maven 聚合模块，不直接启动。

子模块：

- `site/site-app`
  - 前台站点 API 启动模块
  - 端口：`8082`
  - 当前提供 `GET /api/site/ping`
  - 统一装配 `site-core`、`site-car`、`site-cake`
- `site/site-core`
  - 前台站点公共核心能力
  - 承载 `SiteContext`、当前站点上下文、站点配置读取能力
  - **阶段 3B**：首批站点域 **`SiteEntity` / `SiteUserEntity` / `SiteUserProfileEntity`** 及对应 **`Mapper`**（依赖 **`infra`** 基类；见 [`Java 持久层设计 V1`](../../docs/website-services/Java持久层设计V1.md)）
- `site/site-car`
  - 汽车站点业务模块骨架
  - 当前只保留清晰包结构，不实现具体业务
- `site/site-cake`
  - 蛋糕站点业务模块骨架
  - 当前只保留清晰包结构，不实现具体业务

### common

通用基础模块。

- `ApiResponse`
- `ErrorCode`
- `BusinessException`
- `PageResult`
- 通用常量

### infra

基础设施接入模块。

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

公共认证基础能力模块。

- JWT 抽象预留
- Token audience 区分 `ADMIN` / `SITE`
- 认证上下文
- 当前登录用户抽象
- 不定义具体 `admin_user` 或 `site_user`

### agent

Java 后端未来调用 AI/Agent 服务的客户端层。

- 当前提供 `AgentClient.ping()`
- 暂不真实调用外部 AI API
- 不直接依赖 PostgreSQL / Redis / MinIO
- 如果未来需要读取业务数据、文件或缓存会话，由 `admin` / `site-app` 等上层应用模块编排

### config

网站配置业务模块预留。

- 主题
- 页面布局
- SEO
- Brand / Logo
- 页脚等配置能力

## 认证与用户模型边界

- `auth` 只负责公共认证能力，不直接定义 `admin_user`、`site_user`、角色或权限实体。
- admin 侧用户体系由 `admin` 模块承载，后续包括 `admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission`。
- site 侧用户体系由 `site-core` 承载，后续包括 `site_user`、`site_user_profile`，必要时扩展 `site_user_auth`。
- 前台用户统一使用 `site_user`，通过 `site_id` 或 `site_code` 做多站点隔离。不要为 car/cake 分别建立 `car_user`、`cake_user`。

## 依赖关系

```text
gateway       -> Spring Cloud Gateway only

common
  ↑
infra         -> common
auth          -> common
agent         -> common
config        -> common, infra

site-core     -> common, infra, auth, config
site-car      -> site-core
site-cake     -> site-core
site-app      -> common, infra, agent, auth, config, site-core, site-car, site-cake

admin         -> common, infra, agent, auth, config
```

约束：

- `gateway` 不依赖业务模块。
- `agent` 不直接依赖 `infra`，不做业务数据编排。
- `site` 是聚合模块，不承载 Java 代码。
- 只有 `gateway`、`admin`、`site/site-app` 是可启动应用。
- library 模块不要配置 Spring Boot repackage。

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

- **脚本目录（唯一来源）**：`admin/src/main/resources/db/migration`
- **`site-app` 默认不执行 Flyway**（环境变量 `SITE_APP_FLYWAY_ENABLED`，默认 `false`），避免与 `admin` 共用同一库时出现重复 migration / `flyway_schema_history` 冲突。
- 本地联调建议：**先启动 `admin`** 完成迁移，再启动 `site-app`。说明见 `site/site-app/src/main/resources/db/README.md`。
- `admin` 侧开关：`FLYWAY_ENABLED`（默认 `true`）。

首批已落地脚本：`V1__init_platform_core_tables.sql`（8 张表，无种子数据）。执行策略与设计边界见 [`docs/website-services/数据库模型设计V1.md`](../../docs/website-services/数据库模型设计V1.md) §8；**字段级定义见 [`docs/数据字典/`](../../docs/数据字典/)**。

migration 命名使用 Flyway 标准格式：`V{version}__{description}.sql`。

### JWT 认证骨架

认证策略采用 JWT 作为主模型，Admin / Site 认证逻辑隔离。

当前只提供基础抽象：

- `TokenAudience.ADMIN`
- `TokenAudience.SITE`
- `TokenClaims`
- `AuthenticatedUser`
- `AuthContext`
- `JwtProperties`

本阶段不实现登录接口、权限系统、完整 JWT 签发流程，也不把具体用户实体放入 `auth` 模块。Redis 未来用于 refresh token、主动失效、登出等扩展能力。

### OpenAPI / Swagger

`admin` 与 `site-app` 已接入 springdoc-openapi。`gateway` 暂不聚合文档。

本地访问：

- Admin Swagger UI：<http://localhost:8081/swagger-ui/index.html>
- Admin OpenAPI JSON：<http://localhost:8081/v3/api-docs>
- Site Swagger UI：<http://localhost:8082/swagger-ui/index.html>
- Site OpenAPI JSON：<http://localhost:8082/v3/api-docs>

### 模块内 Java 包结构

主要边界由 Maven 多模块承担；模块内采用 **Spring Boot 常规包名**（`config`、`controller`、`handler`、`mapper`、`entity`、`dto`、`exception` 等），数据库映射统一 **`XxxEntity`**，配置类统一 **`XxxConfig`**；**不**再使用 `interfaces` / `application` / `domain` / `infrastructure` / `po` 作为一级分层包。详见 [`架构设计`](../../docs/website-services/架构设计.md) §8。

## 本地运行依赖

本地开发前请先启动 WSL + Docker 中的基础设施：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- MinIO / AIStor API：`http://localhost:9000`
- MinIO / AIStor Console：`http://localhost:9001`

`admin` 与 `site-app` 的本地配置使用 `application-local.yml`，敏感信息使用环境变量占位：

- `DB_URL`，默认 `jdbc:postgresql://localhost:5432/my_website_dev`
- `DB_USERNAME` / `DB_PASSWORD`
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`
- `MINIO_ENDPOINT`，默认 `http://localhost:9000`
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET`

在 `services/website-services/` 目录创建 `.env`（已在仓库根 `.gitignore` 中忽略，**勿提交**）。`docker compose` 会自动读取同级 `.env`。**变量名范本**（与本文档前文 `application-local.yml` 占位一致）：`POSTGRES_*`（Compose 拉起库）、`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`、`REDIS_*`、`MINIO_*`、`FLYWAY_ENABLED` / `SITE_APP_FLYWAY_ENABLED`。详细可复制段落见 [`docs/website-services/WSL与Docker环境配置说明.md`](../../docs/website-services/WSL与Docker环境配置说明.md) §3.0。

Flyway 相关：

- `FLYWAY_ENABLED`：是否由 **admin** 执行迁移（默认 `true`）。
- `SITE_APP_FLYWAY_ENABLED`：是否由 **site-app** 执行迁移（默认 `false`，建议保持关闭）。

## 构建顺序

首次启动或模块变更后，建议先在 `services/website-services` 根目录执行一次全量构建：

```bash
mvn clean install -DskipTests
```

构建成功后，`admin`、`site-app`、`gateway` 可以分别独立启动。

运行测试：

```bash
mvn test
```

## 推荐启动顺序

### 1. 启动本地基础设施

先确认 WSL + Docker 中的 PostgreSQL、Redis、MinIO / AIStor 已启动。

如果基础设施未启动，`admin` 和 `site-app` 仍可能启动成功，但 `/actuator/health` 中数据库或 Redis 会显示 `DOWN`。

### 2. 启动 admin

```bash
mvn -pl admin -am spring-boot:run
```

启动后验证：

```bash
curl http://localhost:8081/api/admin/ping
```

### 3. 启动 site-app

```bash
mvn -pl site/site-app -am spring-boot:run
```

启动后验证：

```bash
curl http://localhost:8082/api/site/ping
```

### 4. 启动 gateway

```bash
mvn -pl gateway -am spring-boot:run
```

启动后通过网关验证：

```bash
curl http://localhost:8080/api/admin/ping
curl http://localhost:8080/api/site/ping
```

说明：`gateway` 可以先启动，但推荐在本地联调时先启动 `admin` 和 `site-app`，再启动 `gateway`，这样网关路由目标服务已经可用。

## 验证接口

- Gateway 转发 admin：<http://localhost:8080/api/admin/ping>
- Gateway 转发 site：<http://localhost:8080/api/site/ping>
- Admin 直连：<http://localhost:8081/api/admin/ping>
- Site 直连：<http://localhost:8082/api/site/ping>
- Gateway health：<http://localhost:8080/actuator/health>
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

后端说明文档权威路径：**`docs/website-services/`**（仓库根目录）。数据库数据字典权威路径：**`docs/数据字典/`**。不要在 `services/website-services/docs/` 撰写或存放权威正文（该目录仅保留跳转 [`README`](docs/README.md)）。

### Java 持久层（阶段 3A / 3B）

| 文档 | 职责 |
| --- | --- |
| [Java 持久层设计 V1](../../docs/website-services/Java持久层设计V1.md) | **`XxxEntity`** 命名、**`AbstractAuditableEntity`**、MyBatis-Plus、审计/逻辑删除/乐观锁、Mapper 边界、8 表归属、包结构（**`entity` / `mapper` / `config` / `handler`**） |

### 数据库与数据文档

数据库相关维护 **双线**：

| 文档 | 职责 |
| --- | --- |
| [数据库模型设计 V1](../../docs/website-services/数据库模型设计V1.md) | 规范、蓝图、多站点与 RBAC 边界、暂缓表与阶段说明（含 Flyway §8） |
| [数据字典索引](../../docs/数据字典/README.md) | 已落地表的 **字段 / 索引 / 唯一约束 / 外键** 可查手册（按业务域拆分） |

数据字典分册（首批 8 表）：

- [平台与站点](../../docs/数据字典/平台与站点数据字典.md)（`site`）
- [Admin 认证授权](../../docs/数据字典/Admin认证授权数据字典.md)（`admin_*` RBAC）
- [Site 用户](../../docs/数据字典/Site用户数据字典.md)（`site_user`、`site_user_profile`）

**约定**：凡新增或变更表结构（含字段、索引、唯一约束、外键）或新增 Flyway migration，**必须同步更新** [`docs/数据字典/`](../../docs/数据字典/) 对应条目及 [`数据库模型设计 V1`](../../docs/website-services/数据库模型设计V1.md) 中有字段口径的章节，禁止仅写代码或仅改 SQL。详见 [`docs/数据字典/README.md`](../../docs/数据字典/README.md) 与设计文档 §9。

### 其他

- 总体架构：[整体架构说明](../../docs/整体架构说明.md)
- 后端架构：[架构设计](../../docs/website-services/架构设计.md)
- WSL/Docker：[WSL 与 Docker 环境配置说明](../../docs/website-services/WSL与Docker环境配置说明.md)
