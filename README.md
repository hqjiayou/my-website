# my-website

一个“多站点网站合集平台”代码仓库骨架。第一阶段聚焦汽车维修与蛋糕烘焙站点模板，后续可扩展到美容院、诊所、中古车店、行政书士事务所等行业站点。

## 项目定位

- 用一套平台同时承载多个行业站点，不是单个官网。
- `apps/site-web` 是前台子站点集合目录，内部按行业拆分独立站点。
- `apps/admin-web` 负责总后台与站点后台管理。
- `services/website-services` 是当前 Java 后端主干，包含**统一网关**、**独立认证服务（`auth`）**、后台 API、前台 API、基础设施接入与后续业务模块骨架；**JWT 为 RS256 + JWKS（Auth 签发，业务服务 JWKS 验签）**。
- `services/agent-services` 是 Node.js + Fastify 的 AI/Agent 编排服务骨架，当前仍以 mock 能力为主。

## 目录结构

```text
my-website/
├── apps
│   ├── admin-web
│   └── site-web
│       ├── car-web
│       └── cake-web
├── docs
│   ├── website-services    # Java 后端：架构、数据库模型、环境说明等（权威）
│   ├── 数据字典             # 表级数据字典（权威）
│   └── 整体架构说明.md
├── services
│   ├── agent-services
│   └── website-services
└── doc                       # 兼容跳转占位（正文权威均在 docs/；见 doc/README.md）
```

设计与数据文档请以 **`docs/`** 为准：`docs/website-services/`、`docs/数据字典/`。若仍有指向 `doc/` 的旧书签或外链，同名路径下现为 **一页跳转说明**，并非正文副本。

## 子项目职责

### apps/site-web

前台站点集合目录，当前包含：

- `car-web`：汽车维修行业站点
- `cake-web`：蛋糕烘焙行业站点

前台站点后续优先通过 `website-services/gateway` 访问前台 API。

### apps/admin-web

后台管理系统，负责多站点平台的管理界面。当前包含站点、模板、内容、AI、运营、系统等管理页面骨架。

专项文档：

- `docs/admin-web/架构设计.md`
- `docs/admin-web/API设计.md`

### services/website-services

Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。

核心运行模块：

- `gateway`：统一 API 网关，端口 `8080`
- `auth`：**统一认证服务**（Admin 登录/刷新/登出、JWT 签发等），端口 `8083`
- `admin`：后台管理 API（OAuth2 Resource Server + 业务），端口 `8081`
- `site/site-app`：前台站点 API，端口 `8082`

基础与预留模块：

- `common`：通用响应、错误码、异常、分页；**`common.security`** JWT/身份轻量契约
- `infra`：PostgreSQL、Redis、MinIO / S3 接入、MyBatis-Plus、**系统消息 i18n** 等
- `agent`：对接 AI/Agent 运行服务的 Java 客户端层
- `site/site-core`：前台站点核心能力与统一 site 用户体系预留
- `site/site-car`、`site/site-cake`：行业业务模块骨架，当前不实现具体业务

专项文档：

- [`docs/website-services/README.md`](docs/website-services/README.md)（**文档总导航**）
- [`docs/website-services/架构设计.md`](docs/website-services/架构设计.md)（入口；正文：[设计文档/基础架构/架构设计.md](docs/website-services/设计文档/基础架构/架构设计.md)）
- [`docs/website-services/Java持久层设计V1.md`](docs/website-services/Java持久层设计V1.md)（入口；正文：[设计文档/数据与持久层/Java持久层设计V1.md](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)）
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/)（表级字典，与 Flyway 对齐）
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Node.js + Fastify + TypeScript 的 AI/Agent 编排服务，默认端口 `3001`。

当前提供 mock 接口：

- `GET /health`
- `GET /api/site/:domain/config`
- `POST /api/site/:siteId/reservations`
- `POST /api/site/:siteId/ai/chat`

## 后端调用关系

推荐请求路径：

```text
admin-web ─┐
           ├──> website-services/gateway:8080 ──> /api/auth/**           → auth:8083（登录 / 刷新 / 登出等）
           │                                 ──> /api/admin/**           → admin:8081（业务 API，含 **`GET /api/admin/auth/me`**）
site-web  ─┘                                 └──> /api/site/**           → site-app:8082（含 **`GET /api/site/auth/me`**）
```

内部 Maven 依赖关系（非 HTTP 拓扑）：

```text
admin / site-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent（按需） -> services/agent-services
```

当前阶段不引入注册中心、服务发现、消息队列或复杂网关鉴权。`gateway` 只做统一入口、CORS、基础 request-id 与路由。

## 启动命令

启动本地基础设施后，启动 `website-services`：

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl site/site-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

启动 `admin-web`：

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

启动前台站点：

```bash
cd apps/site-web/car-web
pnpm install
pnpm dev
```

```bash
cd apps/site-web/cake-web
pnpm install
pnpm dev
```

启动 `agent-services`：

```bash
cd services/agent-services
pnpm install
pnpm dev
```

## 本地基础设施

后端本地依赖统一使用既有 **local-stack**（不在 `website-services` 内再起第二套 Postgres/Redis）：

- PostgreSQL：`local-postgres` → `localhost:5432`
- Redis：`local-redis` → `localhost:6379`
- MinIO / AIStor：`local-aistor` → API `http://localhost:9000`（控制台端口以栈配置为准）

应用在 **`services/website-services/.env`**（范本 `.env.example`）中配置 JDBC / Redis / MinIO 等；与 **local-stack** 目录下的 `.env` **分开维护**。

更多说明见 [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。
