# my-website

Language list: [`docs/README.md`](../README.md#项目介绍多语言). This page: English

A centrally managed collection of sites. One admin console (`apps/admin-web`), one Java backend (`services/website-services`), one Python AI execution service (`services/agent-services`), and one site-user client (`apps/agent-portal`). The public visitor frontend (former `apps/site-web`) was removed on 2026-10-28.

## Positioning

- One admin console manages every site in the collection.
- The site-user client is `apps/agent-portal` (login + AI chat). Site APIs live in Java `agent-app` (formerly site-app; renamed and merged on 2026-10-28).
- `apps/admin-web` is the console for both the platform and each site.
- `services/website-services` is the Java stack: a **shared gateway**, a **standalone auth service (`auth`)**, admin APIs, site APIs, and infra adapters. **JWT is RS256 + JWKS** (Auth signs; business services verify via JWKS).
- `services/agent-services` is the **Python 3.13 + FastAPI + Pydantic AI 2.54** AI Execution Runtime (internal only; not exposed through the gateway). Java holds business truth; Python only executes AI (ADR-0005). Without `AI_MODEL` the process still starts and calls fail honestly (ADR-0004).

## Layout

```text
my-website/
├── apps
│   ├── admin-web         # admin console
│   └── agent-portal      # site-user client
├── docs                  # three shelves: now / spec / history — see docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**What exists today:** [`docs/overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md). Start docs at [`docs/README.md`](../README.md); do not dig through old overviews. Open work is in GitHub Issues ([`docs/overview/ROADMAP.md`](../overview/ROADMAP.md) keeps Issue numbers only). Table dictionary: [`docs/website-services/数据字典/`](../website-services/数据字典/).

## Subprojects

### apps/agent-portal (site-user client)

Site-user client (login / AI chat). Public visitor frontend `apps/site-web/` was removed on 2026-10-28 — do not look for it.

Site APIs go through `website-services/gateway`.  
**Agent Auth (former Site Auth): backend done** (`/api/auth/agent/**` login / refresh / logout; `GET /api/agent/auth/me` on agent-app). **Frontend is wired in agent-portal** (see [`docs/overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md)).

### apps/admin-web

Admin console. Core verticals (Agent / User / Appearance / Media / Content / RBAC) and site AI management use real APIs. (Template and Reservation were removed on 2026-10-27.) Without `AI_MODEL`, preview fails honestly. Analytics / System and similar pages are still placeholders.

Docs:

- [`docs/admin-web/CURRENT_STATE.md`](../admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](../admin-web/开发规范.md)
- [`docs/contracts/`](../contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 Maven multi-module backend.

Runtime modules:

- `gateway`: shared API gateway, port `8080`
- `auth`: **shared auth** (Admin login / refresh / logout, JWT issuance), port `8083`
- `admin`: admin API (OAuth2 Resource Server + business), port `8081`
- `agent/agent-app`: site business API, port `8082`

Shared modules:

- `common`: shared responses, error codes, exceptions, paging; **`common.security`** thin JWT / identity contracts
- `infra`: PostgreSQL, Redis, MinIO / S3, MyBatis-Plus, Flyway, **system-message i18n**
- `agent/agent-core`: shared site entities and mappers (users, content, site AI) plus `HttpAgentClient` and Java↔Python contract DTOs (former site-core and agent client library merged here on 2026-10-28)

Docs:

- [`docs/website-services/README.md`](../website-services/README.md) (which shelf a doc belongs to)
- [`docs/website-services/ARCHITECTURE.md`](../website-services/ARCHITECTURE.md) (jump to architecture body)
- [`docs/website-services/数据字典/`](../website-services/数据字典/) (table dictionary; align with Flyway)
- [`services/website-services/README.md`](../../services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 AI Execution Runtime, default port `3001`. No LangChain / LiteLLM.

Internal APIs (require `X-Internal-Service-Token`; called only by Java `HttpAgentClient`):

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream` (SSE)

Public liveness: `GET /health` (process up only). Missing `AGENT_INTERNAL_SERVICE_TOKEN` refuses startup; missing `AI_MODEL` still starts, chat returns 503.

See [`services/agent-services/README.md`](../../services/agent-services/README.md) (former Agent design docs removed on 2026-10-29; check git history).

## Call graph

Recommended request path:

```text
admin-web ／ agent-portal ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（incl. GET /api/admin/auth/me）
                                              └──> /api/agent/**  → agent-app:8082（incl. GET /api/agent/auth/me）
```

All requests share one gateway. Login is on auth: `/api/auth/admin/**`, `/api/auth/agent/**`. `/me` is not on auth; it lives on the matching business service.

Internal Maven dependencies (not the HTTP topology):

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent-core（HttpAgentClient） -> services/agent-services
```

No service registry, discovery, message queue, or heavy gateway auth in this phase. `gateway` provides a single entry, CORS, request-id, and routing. `trusted-proxies` is `(?!)`; outbound `X-Forwarded-For` is the TCP peer. Auth rate limiting reads the last hop of that header. See [`docs/operations/SECURITY.md`](../operations/SECURITY.md).

## Startup

Full steps (local-stack account alignment, two `.env` files, JWT paths, health checks): [`docs/operations/LOCAL_DEVELOPMENT.md`](../operations/LOCAL_DEVELOPMENT.md).

Start local-stack first (current container names `local-dev-postgres-1` / `local-dev-redis-1`; see LOCAL_DEVELOPMENT §1). `services/website-services/.env` `POSTGRES_*`, `REDIS_PASSWORD`, and `MINIO_*` must match the containers; `.env.example` values `mywebsite` / `CHANGE_ME` will not reach the current DB. `services/agent-services/.env` is separate; `AGENT_INTERNAL_SERVICE_TOKEN` must match the Java side or the process refuses to start.

Start `website-services` (four terminals):

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl agent/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

Start frontends (two terminals):

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

Start `agent-services`:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # set AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## Local infrastructure

Backend local deps use the existing **local-stack** (do not spin up a second Postgres/Redis inside `website-services`):

- PostgreSQL: `local-dev-postgres-1` → `localhost:5432`
- Redis: `local-dev-redis-1` → `localhost:6379`
- MinIO: `local-dev-minio-1` → API `http://localhost:9000` (names follow the current local-stack / compose project `local-dev`; see LOCAL_DEVELOPMENT §1)

Set JDBC / Redis / MinIO in **`services/website-services/.env`** (template `.env.example`). Keep it separate from the **local-stack** `.env` and from **`services/agent-services/.env`**.

More in [`docs/operations/LOCAL_DEVELOPMENT.md`](../operations/LOCAL_DEVELOPMENT.md) (former WSL/Docker-only docs removed on 2026-10-29; see git history).
