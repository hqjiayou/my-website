# agent-services — Python AI Execution Runtime（internal only）

**Python 3.13 + FastAPI + Pydantic AI 2.54**（`pydantic-ai-slim[mcp]`，禁止 LangChain / LiteLLM）。原则：**Java owns business truth, Python owns AI execution**（ADR-0005）。

- 设计：[`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](../../docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)
- 决策：ADR-0004（无 Provider 诚实失败）、ADR-0005（职责边界）

## 契约

内部路由（需 `X-Internal-Service-Token`，对端为 Java `HttpAgentClient`）：

- `GET /internal/v1/health` — `providerStatus: LIVE|BLOCKED`、`fallbackProviders`、`mcpStatus`/`mcpServers`（仅配置态）、`limits`（生效预算，null = 不限）
- `GET /internal/v1/mcp/ready` — 对 enabled MCP 短超时 `list_tools`；失败 **503** `MCP_UNAVAILABLE` + `servers` 明细（不含 URL）
- `POST /internal/v1/chat` — 成功 `{"ok":true,"data":{reply,conversationId,providerStatus:"LIVE",usage,tools,skillsApplied,skillsMissing,todos}}`（`tools` 只有 `{name,ok}`）；错误体为**顶层扁平** `{ok,errorCode,message[,providerStatus]}`；运行预算超限 → **422** `AGENT_BUDGET_EXCEEDED`
- `POST /internal/v1/chat/stream` — SSE：`START` / `TEXT_DELTA` / `TOOL_START` / `TOOL_RESULT`（`ok`，不含工具返回内容）/ `DONE`（`todos`/`usage`/`tools`）/ `ERROR`（预算超限时无 DONE）
- `GET /internal/v1/skills` — 技能目录（只元数据）
- 请求上限（纵深防御，§21.4）：`message`/history 单条 ≤16000 字符、history ≤50、skills ≤16、todos ≤20；超限或字段非法 → **400** `BAD_REQUEST`（扁平体，非 FastAPI 422）

公开探活：`GET /health`（无鉴权，只报进程存活）

## 两类失败（务必区分）

| 情形 | 行为 |
|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` 缺失 | **启动 fail-fast**（安全配置错误） |
| `AI_MODEL` / 凭证缺失 | 正常启动；health BLOCKED；chat 503 `EXTERNAL_DEPENDENCY_BLOCKED` |

## 环境变量

见 [`.env.example`](./.env.example)。`AI_MODEL` 使用 Pydantic AI model spec（`openai:…` / `azure:…` / `anthropic:…`），**无默认模型**。

## 本地开发

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 配置 token（必需）与 AI_MODEL（可选）
uv run agent-services
```

## 测试

```bash
uv run python -m pytest
```

测试全局 `ALLOW_MODEL_REQUESTS=False`，LIVE 路径用 `TestModel` / `FunctionModel`，不会触达真实 Provider。
平台行为评测集：`tests/test_evals_platform.py`（pydantic-evals）。

Windows 应用控制（Smart App Control）阻止 uv Python DLL 时，可在 Docker 中运行（独立 venv，不影响本地 `.venv`）：

```bash
docker run --rm -v "$PWD:/app" -w /app -e UV_PROJECT_ENVIRONMENT=/tmp/venv -e UV_LINK_MODE=copy \
  ghcr.io/astral-sh/uv:python3.13-bookworm-slim sh -c \
  "uv sync --frozen --all-extras --no-install-project && uv sync --frozen --all-extras && uv run --frozen --all-extras pytest -q -p no:cacheprovider"
```

## 部署（定型）

**internal only**：不经 gateway 暴露。Java `admin` 与 `site-app` 经 `agent` 模块的 `HttpAgentClient` 内网访问 `:3001`。

### Docker

```bash
cd services/agent-services
docker build -t agent-services:local .
docker run --rm -p 3001:3001 \
  -e AGENT_INTERNAL_SERVICE_TOKEN=CHANGE_ME \
  -e AI_MODEL=openai:gpt-4o-mini \
  -e AI_PROVIDER_API_KEY=sk-... \
  agent-services:local
```

### 探针

| 类型 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| Liveness | `GET /health` | 无 | 进程存活 |
| Readiness | `GET /internal/v1/health` | `X-Internal-Service-Token` | `providerStatus=LIVE` 表示可接 chat |

### Java 侧（admin 与 site-app）

`services/website-services/.env.example`：

- `AGENT_BASE_URL=http://agent-services:3001`（容器内 DNS 或宿主机地址）
- `AGENT_INTERNAL_SERVICE_TOKEN` 与 Python 侧一致

### CI

`.github/workflows/agent-services-ci.yml`：在 `dev` / `main` 上跑 pytest → Java 契约 IT → Docker build。分支说明见 `docs/operations/DEPLOYMENT.md`。

### 观测

设置 `OTEL_EXPORTER_OTLP_ENDPOINT` 后启用 FastAPI + Agent（run / 模型请求 / 工具调用）OTel span；未设置则 no-op。
Java 透传的 `X-Request-Id` 写入 server span 属性 `request.id` 与 agent run metadata。正文默认不入 span（`AGENT_OTEL_INCLUDE_CONTENT`）。

## 结构

```text
src/agent_services/
├── app.py / main.py / config.py
├── auth/internal_token.py
├── api/{errors,internal}.py
├── schemas/{chat,health,stream,todo,skills,retrieval}.py
├── agent/
│   ├── runtime.py / stream_runtime.py
│   ├── model_factory.py / prompt_builder.py
│   ├── toolset_factory.py          # business + todo + MCP
│   ├── business_tools.py / todo_tools.py / mcp_tools.py / skills.py
├── tools/business_api.py           # 回调 Java internal（样例业务，可选）
├── retrieval/                      # 派生索引（非业务 SoT）
├── observability/
└── testing/mcp_echo.py             # 平台 MCP smoke（echo/add）
skills/                             # SKILL.md
scripts/run_mcp_echo.py             # 可选 HTTP MCP echo
tests/
```

## MCP（平台）

- 配置：`MCP_SERVERS_JSON`（见 `.env.example`）。`transport` 仅 `http`/`sse`（url 以 `/sse` 结尾可省略）；`stdio` 及其它值跳过。health 只报配置态；深检用 `/internal/v1/mcp/ready`
- 连接失败：`503` + `errorCode=MCP_UNAVAILABLE`（不静默降级）
- CI：in-process FastMCP（`tests/test_mcp_tools.py`），不依赖外部 MCP 进程
- 本地 HTTP smoke：

```bash
uv run --extra dev python scripts/run_mcp_echo.py
# 另开终端配置 MCP_SERVERS_JSON 后启动 agent-services
```

## Skills（平台）

- 目录：`skills/{id}/SKILL.md`；可选 frontmatter `description` / `version`
- 默认：`AGENT_DEFAULT_SKILLS`；请求体 `skills[]`；未知 id：`AGENT_SKILLS_UNKNOWN_POLICY=ignore|reject`
- 目录 API：`GET /internal/v1/skills`（仅元数据）
- 响应/SSE：`skillsApplied` / `skillsMissing`
