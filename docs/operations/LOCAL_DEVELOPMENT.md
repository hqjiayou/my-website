# LOCAL_DEVELOPMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-29
```

本地全栈启动的**单一权威**。命令、端口、参数默认值都以当前代码为准（出处见文末对照）；环境变量全表见 [`ENVIRONMENT_VARIABLES.md`](./ENVIRONMENT_VARIABLES.md)，本文不复制默认值。自架部署（根 `docker-compose.yml`）不是本地开发路径，见 [`SELF_HOSTING.md`](./SELF_HOSTING.md)。

## 0. 前置要求

| 工具 | 版本 | 出处 |
|------|------|------|
| JDK | 21 | `services/website-services/pom.xml` 的 `java.version`；`services/website-services/Dockerfile` builder 为 `maven:3.9-eclipse-temurin-21` |
| Maven | 3.9 | 同上 Dockerfile |
| Node | 22 | `.github/workflows/*-ci.yml` 的 `setup-node` |
| pnpm | 10 | 同上 `pnpm/action-setup` 的 `version: 10` |
| Python | 3.13（`>=3.13,<3.14`） | `services/agent-services/pyproject.toml` 的 `requires-python` |
| uv | 任意新版 | Python 依赖与运行入口 |
| Docker | — | 仓库外 local-stack、E2E 栈，以及按需的 proxy-services；Java/Python/前端进程都跑在宿主机 |

## 1. 基础设施（仓库外 local-stack）

PostgreSQL、Redis、MinIO 由仓库外的 **local-stack** 提供，本仓库不附带本地 infra Compose（根 `docker-compose.yml` 是自架部署栈，不要拿来当本地开发栈）。

当前环境（compose 项目 `local-dev`）的容器与宿主机端点：

| 容器 | 宿主机 |
|------|--------|
| `local-dev-postgres-1` | `localhost:5432`，库名以容器 `POSTGRES_DB` 为准（常见 `my_website_dev`） |
| `local-dev-redis-1` | `localhost:6379` |
| `local-dev-minio-1` | API `http://localhost:9000` |

> ⚠️ 旧文档与种子脚本里的默认容器名 `local-postgres` / `local-redis` 与当前环境对不上。用到容器名的地方（尤其种子脚本，见 §6）必须显式指定 `local-dev-postgres-1`，见 §6 的正确用法。

先确认三个容器在跑。账号以**正在运行的容器环境变量**为准（`POSTGRES_USER` / `POSTGRES_PASSWORD` / `REDIS_PASSWORD` / MinIO root），不要假设 `.env.example` 里的 `mywebsite` / `CHANGE_ME` 能登录。对不上时 Java 服务会在 Flyway / 连接阶段报 `password authentication failed`。

MinIO 起不来时四个 Java 进程仍可启动，只是对象存储功能不可用。

CLI 代理池进程是 [`services/proxy-services/`](../../services/proxy-services/README.md)，见 §4.1。

## 2. 两份 `.env`（互不读取）

| 文件 | 谁读 | 要点 |
|------|------|------|
| `services/website-services/.env` | Java（`LocalDotenvSupport`，从 `user.dir` 向上找） | `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 与 local-stack 一致。`AGENT_INTERNAL_SERVICE_TOKEN` 与 Python 相同 |
| `services/agent-services/.env` | Python（cwd 下的 `.env`） | **不**读 Java 那份。缺 `AGENT_INTERNAL_SERVICE_TOKEN` 则进程拒绝启动 |

Java 侧：`cp services/website-services/.env.example services/website-services/.env` 后，把数据库 / Redis / MinIO 改成与容器一致。

JWT：`application-local.yml` 默认 `./secrets/local/auth-private.pem` 与 `auth-public.pem`（相对 `auth` 模块目录，找不到时再试父目录 `services/website-services/secrets/local/`）。`.env` 里不要把 `AUTH_JWT_PRIVATE_KEY_PATH` / `AUTH_JWT_PUBLIC_KEY_PATH` 写成 `CHANGE_ME`，否则 auth 报 `RSA key file not found`。未设置这两项即用默认路径。

## 3. Java 后端（四个进程）

在 `services/website-services` 下执行。首次或模块变更后先构建一次：

```bash
cd services/website-services
mvn clean install -DskipTests
```

四个终端分别启动（建议顺序 auth → admin → agent-app → gateway）：

```bash
mvn -pl auth -am spring-boot:run           # auth：8083
mvn -pl admin -am spring-boot:run          # admin：8081
mvn -pl agent/agent-app -am spring-boot:run # agent-app：8082
mvn -pl gateway -am spring-boot:run        # gateway：8080
```

| 服务 | 端口 | 出处 |
|------|------|------|
| gateway | 8080 | `gateway/src/main/resources/application.yml` |
| admin | 8081 | `admin/src/main/resources/application.yml` |
| agent-app | 8082 | `agent/agent-app/src/main/resources/application.yml` |
| auth | 8083（`AUTH_APP_PORT` 可覆盖） | `auth/src/main/resources/application.yml` |

Flyway：表结构只由 **admin** 执行（`FLYWAY_ENABLED` 默认 `true`，当前迁移至 **V35**）。agent-app 默认不跑（代码里变量名仍是历史名 `AGENT_APP_FLYWAY_ENABLED`，默认 `false`）；auth 的 local profile 关闭。所以先起 admin 把迁移跑完，再起其余服务。改 `.env` 后必须重启对应进程。

gateway 路由（`gateway/src/main/resources/application.yml`，顺序敏感）：`/api/auth/**` → auth:8083、`/api/admin/**` → admin:8081、`/api/agent/**` → agent-app:8082。

## 4. Python agent-services

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env
# AGENT_INTERNAL_SERVICE_TOKEN 与 website-services/.env 相同
uv run agent-services
```

入口是 `agent_services.main:run`（`pyproject.toml` 的 `[project.scripts]`），默认端口 `3001`（`PORT` 可改，`UVICORN_WORKERS` 默认 1）。无 `AI_MODEL` 且 Java 也没给模型入口时进程可起，health 为 BLOCKED，chat 返回 503（ADR-0004）。

## 4.1 proxy-services

CLI 代理进程，源码直接在 [`services/proxy-services/`](../../services/proxy-services/README.md)。不进 Java。本地：

```bash
cd services/proxy-services
docker compose up -d --build
```

| 项 | 值 |
|----|----|
| 进程名 | `proxy-services` |
| API | `http://127.0.0.1:8317` |
| 管理口令 | `PROXY_INTERNAL_SERVICE_TOKEN`，本地默认 `（本地自设随机值，无默认值）`（与 `services/proxy-services/.env` 同名；Compose 注入为 `MANAGEMENT_PASSWORD`） |

后台页 `/agent/api`（agent 菜单里的 API 管理）点新增后选择 GPT CLI（Codex）、Claude、Gemini（Antigravity）或 Muse（Meta）的图标并跳转登录，Cursor 没有登录入口。登录成功的账号成为面板，可选中查看成功/失败次数、额度和放出的模型。调用地址是 `http://127.0.0.1:8317/v1`，客户端 key 在同一页签发。请求里的 `model` 决定走哪个已登录账号。对话用模型入口 CLI 记录，`baseUrl` 填该地址，`apiKey` 填页面上任意一把客户端 key。自架 Compose 里 admin 用 `http://proxy-services:8317`。

## 5. 前端（两个）

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

| 前端 | 端口 | 出处 |
|------|------|------|
| admin-web | 5173（Vite 默认，`vite.config.ts` 未设 `server.port`） | `apps/admin-web/vite.config.ts`（proxy `/api` → `http://localhost:8080`） |
| agent-portal | 5174（`vite.config.ts` 显式设置） | `apps/agent-portal/vite.config.ts`（proxy `/api` → `http://localhost:8080`） |

两边开发时都经 proxy 走 gateway `:8080`，不直连后端。agent-portal 优先用登录会话里的站点信息调 `/api/agent/{agentId}/ai/**`；`VITE_PORTAL_AGENT_ID` 只是会话缺失时的后备（正整数），见 [`ENVIRONMENT_VARIABLES.md`](./ENVIRONMENT_VARIABLES.md)。

## 6. 种子（初始管理员）

在 `services/website-services` 下执行。**必须显式指定当前环境的容器名**（脚本默认值 `local-postgres` 是错的）：

```powershell
# Windows PowerShell
.\scripts\local\run-seed-admin-hqjiayou-super.ps1 -Container local-dev-postgres-1
```

```bash
# bash / WSL
POSTGRES_CONTAINER=local-dev-postgres-1 ./scripts/local/run-seed-admin-hqjiayou-super.sh
```

成功后可用 `hqjiayou` / `123456` 登录管理端（super_admin，**仅本地**，首次登录后应改密码）。另有演示站点种子 `scripts/local/run-seed-site-demo.ps1` / `.sh`，容器参数指定方式相同。脚本细节见 [`services/website-services/scripts/README.md`](../../services/website-services/scripts/README.md)。

## 7. 验收（活着的检查）

```text
GET http://localhost:8083/actuator/health   # auth：db、redis UP
GET http://localhost:8081/actuator/health   # admin：db UP
GET http://localhost:8082/actuator/health   # agent-app：db、redis UP
GET http://localhost:8080/actuator/health   # gateway UP（无注册中心时 discoveryClient 为 UNKNOWN，整体仍可 UP）
GET http://localhost:3001/health            # agent-services 进程存活（无鉴权）
GET http://localhost:8317/v1/models         # proxy-services（未带客户端 key 时 401 也表示进程在）
GET http://localhost:3001/internal/v1/health # 需请求头 X-Internal-Service-Token；看 providerStatus / retrieval 状态
GET http://localhost:8082/api/agent/ping    # agent-app 直连探活（gateway 经 http://localhost:8080/api/agent/ping）
```

## 8. 停止 / 清理

- 四个 Java 进程、Python、两个前端：在各自终端 `Ctrl-C`。
- local-stack：用它自己的 compose 停止（不在本仓库）。
- 需要重建 Java 产物时：`mvn clean`（在 `services/website-services`）。

## 9. 可选联调

契约集成测试（需先起 Python，见 §4）：

```bash
cd services/website-services
AGENT_PYTHON_CONTRACT_IT=true mvn -pl agent/agent-core test -Dtest=HttpAgentClientPythonContractIT
```

浏览器 E2E（Playwright，独立一次性栈）：见 [`../testing/E2E_GOLDEN_PATHS.md`](../testing/E2E_GOLDEN_PATHS.md) 与 `apps/admin-web/e2e/`。

WSL / Docker 细节以本篇与 `docker-compose.yml` 为准（原 WSL 专项文档已于 2026-10-29 移除，见 git 历史）。
