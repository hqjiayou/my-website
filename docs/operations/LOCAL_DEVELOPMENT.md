# LOCAL_DEVELOPMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-10
```

本地一次拉起全栈的入口。端口、模块边界以仓库根 [`README.md`](../../README.md) 与代码为准。细节环境变量见 [`ENVIRONMENT_VARIABLES.md`](./ENVIRONMENT_VARIABLES.md)。

## 1. 基础设施

PostgreSQL、Redis、MinIO / AIStor 由仓库外的 **local-stack** 提供。本仓库不再附带第二套 Compose。

| 容器 | 宿主机 |
|------|--------|
| `local-postgres` | `localhost:5432`，库名以容器 `POSTGRES_DB` 为准（常见 `my_website_dev`） |
| `local-redis` | `localhost:6379` |
| `local-aistor` | API `http://localhost:9000` |

先确认 `local-postgres`、`local-redis` 在跑。账号以**正在运行的容器环境变量**为准（`POSTGRES_USER` / `POSTGRES_PASSWORD` / `REDIS_PASSWORD` / MinIO root），不要假设 `.env.example` 里的 `mywebsite` / `CHANGE_ME` 能登录。对不上时，`admin` / `site-app` 会在 Flyway 失败：`password authentication failed`。

`local-aistor` 若因挂载失败起不来，下面四个 Java 进程仍可启动；对象存储功能不可用。

## 2. 两份 `.env`（互不读取）

| 文件 | 谁读 | 要点 |
|------|------|------|
| `services/website-services/.env` | Java（`LocalDotenvSupport`，从 `user.dir` 向上找） | `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 与 local-stack 一致。`AGENT_INTERNAL_SERVICE_TOKEN` 与 Python 相同 |
| `services/agent-services/.env` | Python（cwd 下的 `.env`） | **不**读 Java 那份。缺 `AGENT_INTERNAL_SERVICE_TOKEN` 则进程拒绝启动 |

Java 侧：`cp services/website-services/.env.example .env` 后，把数据库 / Redis / MinIO 改成与容器一致。

JWT：`application-local.yml` 默认 `./secrets/local/auth-private.pem` 与 `auth-public.pem`（相对 `auth` 模块目录，找不到时再试父目录 `services/website-services/secrets/local/`）。`.env` 里不要把 `AUTH_JWT_PRIVATE_KEY_PATH` / `AUTH_JWT_PUBLIC_KEY_PATH` 写成 `CHANGE_ME`，否则 auth 报 `RSA key file not found`。未设置这两项即用默认路径。

Python 侧：

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env
# AGENT_INTERNAL_SERVICE_TOKEN 与 website-services/.env 相同
uv run agent-services
```

无 `AI_MODEL` 时进程可起，health 为 BLOCKED，chat 返回 503（ADR-0004）。

## 3. 启动顺序

四个 Java 进程分四个终端，在 `services/website-services` 下执行。首次或模块变更后先 `mvn clean install -DskipTests`。

```bash
mvn -pl auth -am spring-boot:run          # 8083
mvn -pl admin -am spring-boot:run         # 8081
mvn -pl site/site-app -am spring-boot:run # 8082
mvn -pl gateway -am spring-boot:run       # 8080
```

建议先 auth、admin、site-app，再 gateway。改 `.env` 后必须重启对应进程。

前端：

```bash
cd apps/admin-web && pnpm install && pnpm dev
```

admin-web 走 gateway `:8080`，不读取 `VITE_USE_MOCK`。工作区没有 site-web 前台应用，不要再启动 `car-web` / `cake-web`。

## 4. 验收

```text
GET http://localhost:8083/actuator/health   # auth：db、redis UP
GET http://localhost:8081/actuator/health   # admin：db UP
GET http://localhost:8082/actuator/health   # site-app：db、redis UP
GET http://localhost:8080/actuator/health   # gateway UP（无注册中心时 discoveryClient 为 UNKNOWN，整体仍可 UP）
GET http://localhost:3001/health            # agent-services 进程存活
```

## 5. 可选（agent 联调）

- `RETRIEVAL_DATABASE_URL`：独立 `agent_retrieval` 库（专用 DB 用户，禁止读 Java 业务表）
- `AI_EMBEDDING_MODEL`：嵌入模型 spec
- `BUSINESS_API_BASE_URL`：site-app internal，如 `http://127.0.0.1:8082`，不经 gateway

契约集成测试（可选，需 uv）：

```bash
cd services/website-services
AGENT_PYTHON_CONTRACT_IT=true mvn -pl agent test -Dtest=HttpAgentClientPythonContractIT
```

WSL / Docker 细节：[`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](../website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。
