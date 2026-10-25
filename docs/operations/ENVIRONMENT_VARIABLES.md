# ENVIRONMENT_VARIABLES

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-10
```

权威列表以各服务 `.env.example` / `application.yml` / `services/website-services/README.md` 为准。本地怎么配、怎么起见 [`LOCAL_DEVELOPMENT.md`](./LOCAL_DEVELOPMENT.md)。

Java 只读 `services/website-services/.env`。Python 只读进程 cwd 下的 `services/agent-services/.env`。`POSTGRES_*` / `REDIS_PASSWORD` / `MINIO_*` 必须与正在运行的 local-stack 容器一致；`.env.example` 中的 `mywebsite` / `CHANGE_ME` 是占位。`AGENT_INTERNAL_SERVICE_TOKEN` 两边必须相同。

## website-services（Java）

| 变量 | 域 |
|------|-----|
| DB / Redis / JWT / JWKS URI | website-services |
| `AGENT_BASE_URL` | admin / site-app → agent-services 根地址（默认 `http://127.0.0.1:3001`） |
| `AGENT_BUSINESS_API_BASE_URL` | Java 随对话传给 Python 的 site-app internal 根地址。本地默认 `http://127.0.0.1:8082`；Compose 为 `http://site:8082`。空则沿用 Python `BUSINESS_API_BASE_URL` |
| `AI_MODEL_ENTRY_SECRET` | 模型入口 api_key 的 AES-GCM 口令。本地默认 `local-dev-model-entry-secret`；生产必须换成独立口令。空则明文兼容旧行 |
| `AGENT_INTERNAL_SERVICE_TOKEN` | admin / site-app 与 agent-services 共享内部鉴权 |
| `AGENT_CONNECT_TIMEOUT_MS` / `AGENT_READ_TIMEOUT_MS` / `AGENT_RETRY_MAX_ATTEMPTS` | HttpAgentClient 超时与重试。读超时默认 120000（须大于工具超时 30s，并与站点 SSE 120s 对齐）。POST 读超时不重试，避免对话副作用执行两次 |

## agent-services（Python）

| 变量 | 必需 | 说明 |
|------|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` | **是** | 缺失 → 启动 fail-fast |
| `AI_MODEL` | 否 | 进程回退 spec。Java `ai_model_entry` 有启用的默认入口时，对话用该入口的 spec 和 key；都没有 → BLOCKED |
| `AI_PROVIDER_API_KEY` / `AI_PROVIDER_BASE_URL` | 视 Provider | OpenAI-compatible 直配 |
| `RETRIEVAL_DATABASE_URL` | 否 | Phase 3 派生索引；缺失 → 检索 BLOCKED（问答仍可用） |
| `AI_EMBEDDING_MODEL` | 否 | 例 `openai:text-embedding-3-small`；缺失 → 检索 BLOCKED |
| `AI_EMBEDDING_DIMENSIONS` | 否 | 默认 1536 |
| `BUSINESS_API_BASE_URL` | 否 | Phase 4 Tools 回调 Java site-app internal（如 `http://127.0.0.1:8082`）；缺失 → 不注册业务工具 |
| `AGENT_SKILLS_DIR` | 否 | Skills 目录；Docker 默认 `/app/skills` |
| `AGENT_HARNESS_DIR` | 否 | 平台 background / guardrails / tool guidance 目录；Docker 默认 `/app/harness`。空则用源码 `harness/` |
| `AGENT_DEFAULT_SKILLS` | 否 | 逗号分隔默认 skill id（如 `orchestration`） |
| `AGENT_SKILLS_UNKNOWN_POLICY` | 否 | `ignore`（默认）或 `reject`；未知 skill id 时记 `skillsMissing` 或 400 |
| `AGENT_ENABLE_TODO_TOOLS` | 否 | 默认 `true`；`false` 关闭 todo 编排工具 |
| `MCP_SERVERS_JSON` | 否 | MCP 客户端配置 JSON 数组；缺失 → `mcpStatus=DISABLED`。字段：`enabled`/`headers`/`toolPrefix`/`initTimeoutSeconds`/`transport`（`http` 默认，`sse` 或 url 以 `/sse` 结尾；`stdio` 及其它值跳过并告警）。非法 JSON → `INVALID_CONFIG`（进程可起）。已配置但连接失败 → chat `503 MCP_UNAVAILABLE`。深检 `GET /internal/v1/mcp/ready`（不进默认 health） |
| `AGENT_MAX_REQUESTS` | 否 | 单轮最大模型请求数，默认 10；`<=0` 不限。超限 → chat `422` / stream `ERROR`，`errorCode=AGENT_BUDGET_EXCEEDED` |
| `AGENT_MAX_TOOL_CALLS` | 否 | 单轮最大成功工具调用数，默认 20；`<=0` 不限 |
| `AGENT_MAX_TOTAL_TOKENS` | 否 | 单轮最大总 tokens，默认 0（不限） |
| `AGENT_TOOL_TIMEOUT_SECONDS` | 否 | 单次工具调用超时，默认 30；todo / MCP 均生效；超时 → 模型收到重试提示、`TOOL_RESULT.ok=false` |
| `AGENT_HISTORY_MAX_CHARS` | 否 | 送模历史字符预算，默认 24000；从最新往前保留并保证 USER 开头；`<=0` 不限 |
| `AI_FALLBACK_MODELS` | 否 | 逗号分隔 fallback spec；仅主模型 Provider API 错误时切换；主模型 BLOCKED 时不生效；凭证走各 Provider 原生变量 |
| `AGENT_OTEL_INCLUDE_CONTENT` | 否 | 默认 `false`；`true` 时 Agent span 记录 prompt/completion/工具参数正文（含 PII，仅排障） |
| `PORT` | 否 | 默认 3001 |
| `UVICORN_WORKERS` | 否 | 默认 1 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 否 | 设置后启用 OTLP trace（FastAPI + Agent run / 模型请求 / 工具调用 span；`X-Request-Id` 写入 span 属性） |
| `OTEL_SERVICE_NAME` / `OTEL_DEPLOYMENT_ENVIRONMENT` | 否 | OTel resource 属性 |
| `AGENT_CONTRACT_USE_TEST_MODEL` | 否 | **仅契约 IT**；注入 TestModel，生产勿设 |

## admin-web

| 变量 | 域 |
|------|-----|
| `VITE_USE_MOCK` | 已无消费者。admin-web 不因该变量切换 mock / real |

生产网关应配置可信代理 CIDR（XFF）；见 SECURITY。
