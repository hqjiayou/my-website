# website-services

`website-services` 是 Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。当前阶段保持收紧架构：统一网关入口、后台应用、前台站点应用、基础设施模块与可扩展的站点业务模块。

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
- 后续承载后台用户与权限体系，例如 `admin_user`、`admin_role`、`admin_permission`

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
  - 后续承载统一前台用户体系：`site_user`、`site_user_profile`
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
- 不放业务逻辑、DAO、Mapper 或业务实体

### auth

公共认证基础能力模块。

- Token / JWT 抽象预留
- 认证上下文
- 当前登录用户抽象
- 不定义具体 `admin_user` 或 `site_user`

### agent

Java 后端未来调用 AI/Agent 服务的客户端层。

- 当前提供 `AgentClient.ping()`
- 暂不真实调用外部 AI API

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
agent         -> common, infra
config        -> common, infra

site-core     -> common, auth, config
site-car      -> site-core
site-cake     -> site-core
site-app      -> common, infra, agent, auth, config, site-core, site-car, site-cake

admin         -> common, infra, agent, auth, config
```

约束：

- `gateway` 不依赖业务模块。
- `site` 是聚合模块，不承载 Java 代码。
- 只有 `gateway`、`admin`、`site/site-app` 是可启动应用。
- library 模块不要配置 Spring Boot repackage。

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

## 构建顺序

首次启动或模块变更后，建议先在 `services/website-services` 根目录执行一次全量构建：

```bash
mvn clean install -DskipTests
```

构建成功后，`admin`、`site-app`、`gateway` 可以分别独立启动。

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

- 总体架构：`doc/整体架构说明.md`
- 后端架构：`doc/website-services/架构设计.md`
