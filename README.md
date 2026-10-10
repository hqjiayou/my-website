# my-website

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

一个多站点网站合集平台。平台能力按行业站点扩展（汽车维修、蛋糕烘焙，以及后续美容院、诊所、中古车店、行政书士事务所等）。当前工作区没有行业前台应用。

## 项目定位

- 用一套平台同时承载多个行业站点，不是单个官网。
- 行业前台不在当前工作区。恢复后放在 `apps/site-web/`，按行业拆成独立站点。
- `apps/admin-web` 负责总后台与站点后台管理。
- `services/website-services` 是当前 Java 后端主干，包含**统一网关**、**独立认证服务（`auth`）**、后台 API、前台 API、基础设施接入与后续业务模块骨架；**JWT 为 RS256 + JWKS（Auth 签发，业务服务 JWKS 验签）**。
- `services/agent-services` 是 **Python 3.13 + FastAPI + Pydantic AI 2.54** 的 AI Execution Runtime（internal only，不经 gateway 暴露）。Java 持有业务真相，Python 只做 AI 执行（ADR-0005）。没有 `AI_MODEL` 时进程仍启动，调用诚实失败（ADR-0004）。

## 目录结构

```text
my-website/
├── apps
│   └── admin-web         # 当前工作区只有管理后台
├── docs                  # 三格：现在 / 规格 / 历史，见 docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**现在是什么**看 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文档先看 [`docs/README.md`](docs/README.md)，不要从旧总览翻起。未完成工作在 GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 号）。表级字典在 [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 子项目职责

### 行业前台

当前工作区没有行业前台应用（原先的 `car-web`、`cake-web` 骨架已移除，`apps/site-web/` 也不在树里）。

恢复后的前台通过 `website-services/gateway` 访问前台 API。  
**Site Auth：后端已完成（`/api/auth/site/**` 登录 / 刷新 / 登出，`GET /api/site/auth/me` 在 site-app），前端尚未对接**（见 [`docs/site-web/CURRENT_STATE.md`](docs/site-web/CURRENT_STATE.md)）。

### apps/admin-web

后台管理系统。主纵向域（Site / User / Template / Appearance / Media / Content / Reservation / RBAC）与站点 AI 管理为真实 API。无 `AI_MODEL` 时预览诚实失败。Analytics / System 等页仍为占位。

专项文档：

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。

核心运行模块：

- `gateway`：统一 API 网关，端口 `8080`
- `auth`：**统一认证服务**（Admin 登录/刷新/登出、JWT 签发等），端口 `8083`
- `admin`：后台管理 API（OAuth2 Resource Server + 业务），端口 `8081`
- `site/site-app`：前台站点 API，端口 `8082`

基础与预留模块：

- `common`：通用响应、错误码、异常、分页；**`common.security`** JWT/身份轻量契约
- `infra`：PostgreSQL、Redis、MinIO / S3 接入、MyBatis-Plus、Flyway、**系统消息 i18n** 等
- `agent`：`HttpAgentClient` 与 Java↔Python 契约 DTO，不做业务编排
- `site/site-core`：站点共享实体与 Mapper（用户、内容、预约、站点 AI）
- `site/site-car`、`site/site-cake`：行业模块骨架，当前没有具体业务

专项文档：

- [`docs/website-services/README.md`](docs/website-services/README.md)（文档落在哪一格）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（跳转到架构正文）
- [`docs/website-services/设计文档/基础架构/架构设计.md`](docs/website-services/设计文档/基础架构/架构设计.md)
- [`docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md`](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/)（表级字典，与 Flyway 对齐）
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 的 AI Execution Runtime，默认端口 `3001`。禁止 LangChain / LiteLLM。

内部接口（需 `X-Internal-Service-Token`，只由 Java `HttpAgentClient` 调用）：

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream`（SSE）

公开探活：`GET /health`（只表示进程存活）。缺 `AGENT_INTERNAL_SERVICE_TOKEN` 时拒绝启动；缺 `AI_MODEL` 时仍启动，chat 返回 503。

详见 [`services/agent-services/README.md`](services/agent-services/README.md) 与设计文档 [`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)。

## 后端调用关系

推荐请求路径：

```text
admin-web ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（含 GET /api/admin/auth/me）
                                              └──> /api/site/**   → site-app:8082（含 GET /api/site/auth/me）
```

行业前台恢复后走同一条 gateway。登录在 auth：`/api/auth/admin/**`、`/api/auth/site/**`。`/me` 不在 auth，而在对应业务服务。

内部 Maven 依赖关系（非 HTTP 拓扑）：

```text
admin / site-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent（按需） -> services/agent-services
```

当前阶段不引入注册中心、服务发现、消息队列或复杂网关鉴权。`gateway` 做统一入口、CORS、request-id 与路由，并打开 `trusted-proxies`（默认 `.*`）与 `X-Forwarded-For` 追加。auth 限流读这个头的最后一段。客户端 IP 还没有集成断言，见 [GitHub Issue #1](https://github.com/hqjiayou/my-website/issues/1)。

## 启动命令

完整步骤（local-stack 账号对齐、两份 `.env`、JWT 路径、健康检查）见 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先启动 local-stack 的 `local-postgres` / `local-redis`。`services/website-services/.env` 的 `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 必须与容器一致；`.env.example` 的 `mywebsite` / `CHANGE_ME` 不能直接拿来连当前库。`services/agent-services/.env` 单独一份，`AGENT_INTERNAL_SERVICE_TOKEN` 与 Java 侧相同，缺了进程会拒绝启动。

启动 `website-services`（四个终端）：

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

工作区没有 site-web 前台应用，不要启动 `car-web` / `cake-web`。

启动 `agent-services`：

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 配置 AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## 本地基础设施

后端本地依赖统一使用既有 **local-stack**（不在 `website-services` 内再起第二套 Postgres/Redis）：

- PostgreSQL：`local-postgres` → `localhost:5432`
- Redis：`local-redis` → `localhost:6379`
- MinIO / AIStor：`local-aistor` → API `http://localhost:9000`（控制台端口以栈配置为准）

应用在 **`services/website-services/.env`**（范本 `.env.example`）中配置 JDBC / Redis / MinIO 等；与 **local-stack** 目录下的 `.env` **分开维护**，也与 **`services/agent-services/.env`** 分开。

更多说明见 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md) 与 [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。
