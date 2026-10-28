# my-website

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

A centrally controlled collection of websites. One admin console (`apps/admin-web`) controls the collection. One Java service (`services/website-services`) serves it. One Python service (`services/agent-services`) runs AI. This workspace has no visitor-facing site app.

## What this repo is

- One admin console controls every site in the collection.
- This workspace contains the admin console only. Visitor-facing sites are not in the tree. Site APIs live in the Java `agent-app`.
- `apps/admin-web` is that admin console: platform control and per-site control.
- `services/website-services` is the Java service: a **shared gateway**, a **standalone auth service (`auth`)**, admin APIs, site APIs, and infrastructure. **JWT is RS256 + JWKS** (Auth signs; business services verify via JWKS).
- `services/agent-services` is the **Python 3.13 + FastAPI + Pydantic AI 2.54** AI Execution Runtime (internal only; it is not published through the gateway). Java owns business truth. Python only executes AI (ADR-0005). With no `AI_MODEL` the process still starts, and calls fail honestly (ADR-0004).

## Layout

```text
my-website/
├── apps
│   └── admin-web         # this workspace only has the admin app
├── docs                  # three shelves: now / spec / history — see docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**What exists today** is [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md). Start docs at [`docs/README.md`](docs/README.md). Open work is in GitHub Issues ([`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) only keeps issue numbers). Table-level dictionary: [`docs/website-services/数据字典/`](docs/website-services/数据字典/).

## Projects

### Visitor-facing sites

This workspace has no visitor-facing site app (`apps/site-web/` is not in the tree).

Site APIs are reached through `website-services/gateway`.  
**Site Auth: the backend is done** (`/api/auth/agent/**` for login / refresh / logout; `GET /api/agent/auth/me` on agent-app). **The frontend is not wired** (see [`docs/site-web/CURRENT_STATE.md`](docs/site-web/CURRENT_STATE.md)).

### apps/admin-web

Admin console. The main verticals (Site / User / Template / Appearance / Media / Content / Reservation / RBAC) and site AI management call real APIs. With no `AI_MODEL`, preview fails honestly. Analytics, System, and similar pages are still placeholders.

Docs:

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Maven multi-module backend on Java 21 and Spring Boot 3.5.14.

Running modules:

- `gateway`: shared API gateway, port `8080`
- `auth`: **shared authentication** (admin login / refresh / logout, JWT issuance), port `8083`
- `admin`: admin APIs (OAuth2 Resource Server plus business), port `8081`
- `site/agent-app`: site APIs, port `8082`

Shared and reserved modules:

- `common`: response envelope, error codes, exceptions, pagination; **`common.security`** is the lightweight JWT / identity contract
- `infra`: PostgreSQL, Redis, MinIO / S3, MyBatis-Plus, Flyway, **system-message i18n**
- `agent`: `HttpAgentClient` and the Java↔Python contract DTOs; it does not orchestrate business flows
- `site/agent-core`: shared site entities and mappers (users, content, reservations, site AI)
- `site/site-car`, `site/site-cake`: empty modules (`pom.xml` only), no business code

Docs:

- [`docs/website-services/README.md`](docs/website-services/README.md) (which shelf a doc belongs on)
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md) (jumps to the architecture text)
- [`docs/website-services/设计文档/基础架构/架构设计.md`](docs/website-services/设计文档/基础架构/架构设计.md)
- [`docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md`](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/) (table dictionary, aligned with Flyway)
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

AI Execution Runtime on Python 3.13, FastAPI, and Pydantic AI 2.54. Default port `3001`. LangChain and LiteLLM are out of scope.

Internal routes (require `X-Internal-Service-Token`; only Java `HttpAgentClient` calls them):

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream` (SSE)

Public probe: `GET /health` (process liveness only). Missing `AGENT_INTERNAL_SERVICE_TOKEN` refuses startup. Missing `AI_MODEL` still starts, and chat returns 503.

See [`services/agent-services/README.md`](services/agent-services/README.md) and [`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md).

## How the backend is called

Preferred request path:

```text
admin-web ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081 (includes GET /api/admin/auth/me)
                                              └──> /api/agent/**   → agent-app:8082 (includes GET /api/agent/auth/me)
```

Every request uses the same gateway. Login lives on auth: `/api/auth/admin/**` and `/api/auth/agent/**`. `/me` lives on the matching business service, on admin or agent-app.

Maven dependencies inside the build (this is not the HTTP topology):

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent (when needed) -> services/agent-services
```

This stage has no registry, service discovery, message queue, or gateway-side authorization. `gateway` is the single entry, plus CORS, request-id, and routing. It also enables `trusted-proxies` (default `.*`) and appends `X-Forwarded-For`. Auth rate limiting reads the last hop of that header. There is still no integration assertion for the client IP; see [GitHub Issue #1](https://github.com/hqjiayou/my-website/issues/1).

## Run

Full steps (local-stack credentials, the two `.env` files, JWT paths, health checks) are in [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md).

Start `local-postgres` and `local-redis` from local-stack first. `POSTGRES_*`, `REDIS_PASSWORD`, and `MINIO_*` in `services/website-services/.env` must match the containers. The `mywebsite` / `CHANGE_ME` values in `.env.example` will not log in to the current database. `services/agent-services/.env` is a separate file. `AGENT_INTERNAL_SERVICE_TOKEN` must match the Java side; if it is missing, the process refuses to start.

Start `website-services` (four terminals):

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl site/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

Start `admin-web`:

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

This workspace has no visitor-facing site app.

Start `agent-services`:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # set AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## Local infrastructure

Backend dependencies come from the existing **local-stack**. Do not start a second Postgres or Redis inside `website-services`.

- PostgreSQL: `local-postgres` → `localhost:5432`
- Redis: `local-redis` → `localhost:6379`
- MinIO / AIStor: `local-aistor` → API `http://localhost:9000` (console port follows the stack config)

JDBC, Redis, and MinIO are set in **`services/website-services/.env`** (template: `.env.example`). Keep that file separate from the `.env` under **local-stack**, and separate from **`services/agent-services/.env`**.

More detail: [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md) and [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md).
