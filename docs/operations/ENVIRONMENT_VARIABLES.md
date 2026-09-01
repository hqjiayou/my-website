# ENVIRONMENT_VARIABLES

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-09-01
```

权威列表以各服务 `.env.example` / `application.yml` / `services/website-services/README.md` 为准。

## website-services（Java）

| 变量 | 域 |
|------|-----|
| DB / Redis / JWT / JWKS URI | website-services |
| `AGENT_BASE_URL` | site-app → agent-services 根地址（默认 `http://127.0.0.1:3001`） |
| `AGENT_INTERNAL_SERVICE_TOKEN` | site-app 与 agent-services 共享内部鉴权 |
| `AGENT_CONNECT_TIMEOUT_MS` / `AGENT_READ_TIMEOUT_MS` / `AGENT_RETRY_MAX_ATTEMPTS` | HttpAgentClient 超时与重试 |

## agent-services（Python）

| 变量 | 必需 | 说明 |
|------|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` | **是** | 缺失 → 启动 fail-fast |
| `AI_MODEL` | 否 | Pydantic AI spec；缺失 → BLOCKED |
| `AI_PROVIDER_API_KEY` / `AI_PROVIDER_BASE_URL` | 视 Provider | OpenAI-compatible 直配 |
| `RETRIEVAL_DATABASE_URL` | 否 | Phase 3 派生索引；缺失 → 检索 BLOCKED（问答仍可用） |
| `AI_EMBEDDING_MODEL` | 否 | 例 `openai:text-embedding-3-small`；缺失 → 检索 BLOCKED |
| `AI_EMBEDDING_DIMENSIONS` | 否 | 默认 1536 |
| `BUSINESS_API_BASE_URL` | 否 | Phase 4 Tools 回调 Java site-app internal（如 `http://127.0.0.1:8082`）；缺失 → 不注册业务工具 |
| `AGENT_SKILLS_DIR` | 否 | Skills 目录；Docker 默认 `/app/skills` |
| `AGENT_DEFAULT_SKILLS` | 否 | 逗号分隔默认 skill id（如 `orchestration`） |
| `AGENT_ENABLE_TODO_TOOLS` | 否 | 默认 `true`；`false` 关闭 todo 编排工具 |
| `MCP_SERVERS_JSON` | 否 | JSON `[{"name":"x","url":"http://..."}]`；缺失则不注册 MCP |
| `PORT` | 否 | 默认 3001 |
| `UVICORN_WORKERS` | 否 | 默认 1 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 否 | 设置后启用 OTLP trace |
| `OTEL_SERVICE_NAME` / `OTEL_DEPLOYMENT_ENVIRONMENT` | 否 | OTel resource 属性 |
| `AGENT_CONTRACT_USE_TEST_MODEL` | 否 | **仅契约 IT**；注入 TestModel，生产勿设 |

## admin-web

| 变量 | 域 |
|------|-----|
| `VITE_USE_MOCK` | admin-web（勿用于核心域与站点 AI 管理假闭环） |

生产网关应配置可信代理 CIDR（XFF）；见 SECURITY。
