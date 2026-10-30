# ENVIRONMENT_VARIABLES

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-30
```

环境变量的**全表权威**：默认值逐项取自当前代码的占位符 / Settings 定义（出处列）。其他文档只引用本文，不复制默认值。本地怎么配、怎么起见 [`LOCAL_DEVELOPMENT.md`](./LOCAL_DEVELOPMENT.md)。

读取方总览：Java 只读 `services/website-services/.env`；Python 只读进程 cwd 下的 `services/agent-services/.env`；前端读各自构建/启动时的 `VITE_*`；根 `docker-compose.yml`（自架）读仓库根 `.env`。`POSTGRES_*` / `REDIS_PASSWORD` / `MINIO_*` 必须与正在运行的 local-stack 容器一致；`.env.example` 中的 `mywebsite` / `CHANGE_ME` 是占位。`AGENT_INTERNAL_SERVICE_TOKEN` 在 Java 与 Python 两边必须相同。

## 1. Java：数据库 / Redis（auth、admin、agent-app 共用占位符）

出处：各服务 `src/main/resources/application.yml` / `application-local.yml`。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `POSTGRES_DB` | 否 | `my_website_dev` | 经 `DB_URL` 占位符嵌套引用 |
| `POSTGRES_USER` | 否 | `mywebsite` | 经 `DB_USERNAME` 回退引用 |
| `POSTGRES_PASSWORD` | **是**（本地须与容器一致） | `CHANGE_ME`（占位） | 经 `DB_PASSWORD` 回退引用 |
| `DB_URL` | 否 | `jdbc:postgresql://localhost:5432/${POSTGRES_DB}` | 直接给 JDBC 时优先 |
| `DB_USERNAME` | 否 | 回退 `POSTGRES_USER` | |
| `DB_PASSWORD` | 否 | 回退 `POSTGRES_PASSWORD` | |
| `AUTH_DB_URL` / `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` | 否 | 回退 `DB_*` 同上 | 仅 auth 使用（`auth/application.yml`） |
| `REDIS_HOST` | 否 | `localhost` | |
| `REDIS_PORT` | 否 | `6379` | |
| `REDIS_PASSWORD` | **是**（本地须与容器一致） | `CHANGE_ME`（占位） | |
| `AUTH_REDIS_HOST` / `AUTH_REDIS_PORT` / `AUTH_REDIS_PASSWORD` | 否 | 回退 `REDIS_*` | 仅 auth 使用 |

## 2. Java：JWT / JWKS / 端口（auth）

出处：`auth/src/main/resources/application.yml`、`application-local.yml`。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `AUTH_APP_PORT` | 否 | `8083` | auth 监听端口 |
| `AUTH_JWT_PRIVATE_KEY_PATH` | 否 | `./secrets/local/auth-private.pem` | 相对 auth 模块目录；不要写 `CHANGE_ME`（见 LOCAL_DEVELOPMENT §2） |
| `AUTH_JWT_PUBLIC_KEY_PATH` | 否 | `./secrets/local/auth-public.pem` | |
| `AUTH_JWT_KEY_ID` | 否 | `auth-local-rs256-key-1` | |
| `AUTH_JWKS_URI` | 否 | `http://localhost:8083/.well-known/jwks.json` | 业务服务验签拉取地址 |
| `SECURITY_REFRESH_COOKIE_SECURE` | 否 | `true`（`application.yml`）；local profile 为 `false` | refresh cookie 是否只走 HTTPS |

## 3. Java：Flyway

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `FLYWAY_ENABLED` | 否 | `true` | admin 执行迁移（当前至 V35）；auth 的 local profile 关闭 |
| `AGENT_APP_FLYWAY_ENABLED` | 否 | `false` | **agent-app 读的仍是这个历史变量名**（`agent/agent-app/src/main/resources/application.yml`）。注意根 compose 里另设的 `AGENT_APP_FLYWAY_ENABLED` 目前没有代码消费者，不要靠它控制 agent-app |

## 4. Java：对象存储 MinIO

出处：`admin` / `agent-app` 的 `application(-local).yml`。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `MINIO_ENDPOINT` | 否 | `http://localhost:9000` | |
| `MINIO_ACCESS_KEY` | **是**（用存储时） | `CHANGE_ME`（占位；個別模块默认空） | 须与 local-stack MinIO root 一致 |
| `MINIO_SECRET_KEY` | **是**（用存储时） | `CHANGE_ME`（占位；個別模块默认空） | |
| `MINIO_BUCKET` | 否 | `uploads` | |
| `MINIO_SECURE` | 否 | `false` | 是否 HTTPS 访问 MinIO |

## 5. Java：调 Python（admin、agent-app）与模型入口

出处：`admin` / `agent/agent-app` 的 `application.yml`，及 `application-local.yml`。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `AGENT_BASE_URL` | 否 | `http://127.0.0.1:3001` | Java → agent-services 根地址 |
| `AGENT_BUSINESS_API_BASE_URL` | 否 | `http://127.0.0.1:8082`（根 compose 为 `http://agent-service:8082`） | Java 随对话传给 Python 的 agent-app internal 根地址；空则沿用 Python `BUSINESS_API_BASE_URL` |
| `AGENT_INTERNAL_SERVICE_TOKEN` | **是** | 本地占位默认 | 与 Python 同名变量必须一致 |
| `AGENT_CONNECT_TIMEOUT_MS` | 否 | `2000` | HttpAgentClient |
| `AGENT_READ_TIMEOUT_MS` | 否 | `120000` | 须大于工具超时 30s，并与站点 SSE 120s 对齐；POST 读超时不重试，避免对话副作用执行两次 |
| `AGENT_RETRY_MAX_ATTEMPTS` | 否 | `1` | |
| `AI_MODEL_ENTRY_SECRET` | 否 | `local-dev-model-entry-secret`（`application-local.yml`） | 模型入口 api_key 的 AES-GCM 口令；生产必须换成独立口令。空则明文兼容旧行。模型入口双模式（KEY/CLI，Flyway V35）由后台「模型入口」维护，不走环境变量 |
| `PROXY_SERVICES_BASE_URL` | 否 | `http://127.0.0.1:8317`（根 compose 的 admin 为 `http://proxy-services:8317`） | admin 访问 proxy-services 的根地址 |
| `PROXY_INTERNAL_SERVICE_TOKEN` | 否 | 空；`application-local.yml` 与本地 Compose 默认 `（本地自设随机值，无默认值）` | 与 `services/proxy-services/.env` 同名变量相同，形式与 `AGENT_INTERNAL_SERVICE_TOKEN` 一致。Compose 把它注入进程为 `MANAGEMENT_PASSWORD`。空则后台只能看进程是否可达 |

## 6. Gateway

出处：`gateway/src/main/resources/application.yml`、`application-prod.yml`。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES` | 否，且不要设置 | `(?!)`（写在 `application.yml` 与 `application-prod.yml`） | 覆盖该值会让匹配到的对端用转发头第一段替换 TCP 地址。不是 `(?!)` 时 `ClientIpGatewayFilter` 拒绝启动。口径见 [`SECURITY.md`](./SECURITY.md) |

## 7. Python：agent-services

出处：`services/agent-services/src/agent_services/config.py`（`Settings`，别名即变量名）与 `observability/otel.py`。`.env` 在进程 cwd。

| 变量 | 必填 | 代码默认 | 说明 |
|------|------|----------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` | **是** | 空 → 启动 fail-fast | 与 Java 同名变量一致 |
| `PORT` | 否 | `3001` | |
| `UVICORN_WORKERS` | 否 | `1` | |
| `AI_MODEL` | 否 | 空 | 进程回退 spec（如 `google:gemini-3-flash-preview`）。Java `ai_model_entry` 有启用的默认入口时，对话用该入口的 spec 和 key；都没有 → BLOCKED（chat 503，ADR-0004） |
| `AI_PROVIDER_API_KEY` | 视 Provider | 空 | OpenAI-compatible / Google 直配凭证。未直配且嵌入模型是 Google 时，读同一份 `.env` 里的 `GEMINI_API_KEY` 或 `GOOGLE_API_KEY` |
| `AI_PROVIDER_BASE_URL` | 否 | 空 | 直配时传给 Provider |
| `AI_FALLBACK_MODELS` | 否 | 空 | 逗号分隔 fallback spec。**只挂进程主模型**（无入口路径）；Java 模型入口的备援来自请求带入的入口链，不读此变量。仅 Provider API 错误时切换。主模型或入口本身 BLOCKED 时不顶替。备用凭证走各 Provider 原生变量，不复用入口 apiKey |
| `AI_ALLOW_PROCESS_MODEL` | 否 | `false` | 请求未带 Java 模型入口时，是否允许用进程 `AI_MODEL` 顶替作答。默认关：入口驱动的对话（门户/试聊/OCR）没选到入口即 BLOCKED，不许环境模型静默顶上（Issue #145 F1）。仅本机调试可设 `true` |
| `RETRIEVAL_DATABASE_URL` | 否 | 空 | 独立 `agent_retrieval` 库（专用 DB 用户，禁止读 Java 业务表）；缺失 → 检索 BLOCKED（问答仍可用） |
| `AI_EMBEDDING_MODEL` | 否 | 空 | 例 `openai:text-embedding-3-small`；缺失 → 检索 BLOCKED |
| `AI_EMBEDDING_DIMENSIONS` | 否 | `1536` | 遗留 `chunks` 表的向量维度（如 gemini-embedding-001 可设 768）。同维度的其它嵌入模型共用这张表，查询按模型名过滤；维度不同则另建 `chunks_d<维度>`，不删除已有类 |
| `RETRIEVAL_MIN_SCORE` | 否 | `0.35` | 余弦门槛；低于此分的向量命中不返回。`0` 关闭；越界或非数字 → 启动 fail-fast |
| `RETRIEVAL_SCORE_RATIO_FLOOR` | 否 | `0.7` | 与最高分比值的下限（`config.py`） |
| `RETRIEVAL_MAX_CHUNKS_PER_SOURCE` | 否 | `2` | 每来源最多返回块数 |
| `RETRIEVAL_HNSW_EF_SEARCH` | 否 | `40` | HNSW 检索参数 |
| `RETRIEVAL_RERANK_ENABLED` | 否 | `true` | |
| `RETRIEVAL_RERANK_TIMEOUT_SECONDS` | 否 | `4.0` | |
| `RETRIEVAL_RERANK_MAX_CANDIDATES` | 否 | `12` | |
| `RETRIEVAL_RERANK_CUTOFF` | 否 | `true` | |
| `BUSINESS_API_BASE_URL` | 否 | 空 | Tools 回调 Java agent-app internal（如 `http://127.0.0.1:8082`）；缺失 → 不注册业务工具 |
| `AGENT_SKILLS_DIR` | 否 | 空（源码 `skills/`） | Docker 默认 `/app/skills`（根 compose） |
| `AGENT_HARNESS_DIR` | 否 | 空（源码 `harness/`） | 平台 background / guardrails / tool guidance 目录 |
| `AGENT_DEFAULT_SKILLS` | 否 | `orchestration` | 逗号分隔默认 skill id |
| `AGENT_SKILLS_UNKNOWN_POLICY` | 否 | `ignore` | 或 `reject`；未知 skill id 时记 `skillsMissing` 或 400 |
| `AGENT_SKILLS_MAX_INSTRUCTION_CHARS` | 否 | `12000` | |
| `AGENT_ENABLE_TODO_TOOLS` | 否 | `true` | `false` 关闭 todo 编排工具 |
| `AGENT_SUBAGENT_ENABLED` | 否 | `true` | |
| `AGENT_SUBAGENT_MAX_PER_RUN` | 否 | `4` | |
| `AGENT_SUBAGENT_TIMEOUT_SECONDS` | 否 | `60` | |
| `AGENT_SUBAGENT_MAX_REQUESTS` | 否 | `6` | |
| `AGENT_MEMORY_EXTRACTION_ENABLED` | 否 | `true` | |
| `MCP_SERVERS_JSON` | 否 | 空 | MCP 客户端配置 JSON 数组；缺失 → `mcpStatus=DISABLED`。字段：`enabled`/`headers`/`toolPrefix`/`initTimeoutSeconds`/`transport`（`http` 默认，`sse` 或 url 以 `/sse` 结尾；`stdio` 及其它值跳过并告警）。非法 JSON → `INVALID_CONFIG`（进程可起）。已配置但连接失败 → chat `503 MCP_UNAVAILABLE`。深检 `GET /internal/v1/mcp/ready` |
| `AGENT_MAX_REQUESTS` | 否 | `10` | 单轮最大模型请求数；`<=0` 不限。超限 → chat `422`，`errorCode=AGENT_BUDGET_EXCEEDED` |
| `AGENT_MAX_TOOL_CALLS` | 否 | `20` | 单轮最大成功工具调用数；`<=0` 不限 |
| `AGENT_MAX_TOTAL_TOKENS` | 否 | `0`（不限） | |
| `AGENT_TOOL_TIMEOUT_SECONDS` | 否 | `30` | todo / MCP 均生效；超时 → 模型收到重试提示 |
| `AGENT_HISTORY_MAX_CHARS` | 否 | `24000` | 送模历史字符预算；超出时把被裁前缀摘要进本轮系统提示，不回写会话 |
| `AGENT_OTEL_INCLUDE_CONTENT` | 否 | `false`（`otel.py` 读环境） | `true` 时 Agent span 记录 prompt/completion/工具参数正文（含 PII，仅排障） |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 否 | 空 | 设置后启用 OTLP trace |
| `OTEL_SERVICE_NAME` | 否 | `agent-services`（`otel.py`） | OTel resource 属性 |
| `OTEL_DEPLOYMENT_ENVIRONMENT` | 否 | `local`（Settings 别名；`otel.py` 侧缺省 `unknown`） | 部署环境标识 |
| `AGENT_CONTRACT_USE_TEST_MODEL` | 否 | `false` | **仅契约 IT**；注入 TestModel，生产勿设 |

注：旧版文档里 `RETRIEVAL_MIN_SCORE` 条目下关于代码型 token 字面量匹配的长段行为描述属于检索实现细节，已移出参数表（实现以 `config.py` 与检索代码为准）。

## 8. 前端

出处：`apps/admin-web/src`、`apps/agent-portal/src` 的 `import.meta.env` 消费点。

| 变量 | 哪个应用 | 必填 | 代码默认 | 说明 |
|------|----------|------|----------|------|
| `VITE_API_BASE_URL` | admin-web | 否 | `/api`（`src/utils/http/axios.ts`、`src/api/aiApi.ts` 的 `?? '/api'`） | API 基地址；dev 时经 vite proxy 走 gateway |
| `VITE_SITE_WEB_BASE_URL` | admin-web | 否 | 空（回退 `localhost:5174` 的 agent-portal 地址） | 「访问站点」链接用的已部署前台地址（`src/utils/agentWebUrl.ts`）。变量名是历史名，指向的是 agent-portal，不是已删除的 site-web |
| `VITE_API_BASE_URL` | agent-portal | 否 | 空（同源相对路径，`src/config/env.ts`） | |
| `VITE_PORTAL_AGENT_ID` | agent-portal | 否 | 空 | 正整数；仅作登录会话缺失时的后备站点 ID（`/api/agent/{agentId}/ai/**`）。根 compose 部署时它是 `web` 的 build arg，改值必须重建镜像 |
| `VITE_USE_MOCK` | admin-web | — | — | **已无消费者**，不因该变量切换 mock / real（见 `../rules/MOCK_POLICY.md`） |

## 9. 根 compose（自架部署）读的 `.env`

出处：仓库根 `docker-compose.yml`。只列与启动直接相关者，全量以该文件 `${}` 引用为准：`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`、`REDIS_PASSWORD`、`AGENT_INTERNAL_SERVICE_TOKEN`、`AI_MODEL`、`AI_PROVIDER_API_KEY`、`RETRIEVAL_DATABASE_URL`、`AGENT_RETRIEVAL_DB` / `AGENT_RETRIEVAL_USER` / `AGENT_RETRIEVAL_PASSWORD`、`AI_EMBEDDING_MODEL` / `AI_EMBEDDING_DIMENSIONS`（默认 `1536`）、`AI_MODEL_ENTRY_SECRET`（默认 `local-dev-model-entry-secret`）、`PROXY_INTERNAL_SERVICE_TOKEN`（无默认，Compose 以 `${...:?}` 强制要求，注入 proxy-services 为 `MANAGEMENT_PASSWORD`）、`AUTH_JWT_PRIVATE_KEY_PATH` / `AUTH_JWT_PUBLIC_KEY_PATH` / `AUTH_JWT_KEY_ID`、`SECURITY_REFRESH_COOKIE_SECURE`（默认 `false`）、`FLYWAY_ENABLED`（默认 `true`）、`VITE_PORTAL_AGENT_ID`（默认空）。细节见 [`SELF_HOSTING.md`](./SELF_HOSTING.md)。
