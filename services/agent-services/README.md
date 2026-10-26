# agent-services — Python AI Execution Runtime（internal only）

**Python 3.13 + FastAPI + Pydantic AI 2.54**（`pydantic-ai-slim[google,mcp]`，禁止 LangChain / LiteLLM）。原则：**Java owns business truth, Python owns AI execution**（ADR-0005）。

- 设计：[`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](../../docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)
- 决策：ADR-0004（无 Provider 诚实失败）、ADR-0005（职责边界）

## 契约

内部路由（需 `X-Internal-Service-Token`，对端为 Java `HttpAgentClient`）：

- `GET /internal/v1/health` — `providerStatus: LIVE|BLOCKED`、`fallbackProviders`、`mcpStatus`/`mcpServers`（仅配置态）、`limits`（生效预算，null = 不限）
- `GET /internal/v1/mcp/ready` — 对 enabled MCP 短超时 `list_tools`；失败 **503** `MCP_UNAVAILABLE` + `servers` 明细（不含 URL）
- `POST /internal/v1/chat` — 成功 `{"ok":true,"data":{reply,conversationId,providerStatus:"LIVE",usage,tools,skillsApplied,skillsMissing,todos}}`（`tools` 只有 `{name,ok}`）；本轮采用了检索片段时 `data` 另含 `citations`（`sourceId`/`chunkIndex`/`score`，按 score 降序，最多 5 条；未采用或检索降级时不出现该字段）；错误体为**顶层扁平** `{ok,errorCode,message[,providerStatus]}`；运行预算超限 → **422** `AGENT_BUDGET_EXCEEDED`
- `POST /internal/v1/chat/stream` — SSE：`START` / `TEXT_DELTA` / `TOOL_START` / `TOOL_RESULT`（`ok`，不含工具返回内容）/ `DONE`（`todos`/`usage`/`tools`，采用检索片段时另含 `citations`）/ `ERROR`（预算超限时无 DONE）
- `GET /internal/v1/skills` — 技能目录（只元数据）
- `PUT /internal/v1/retrieval/documents` — 写入派生索引文档（`siteId`、`sourceId`、`title`、`content`）；成功 `{"ok":true,"data":{"chunks"}}`
- `DELETE /internal/v1/retrieval/documents` — 删除派生索引文档（query `siteId`、`sourceId`）；成功 `{"ok":true,"data":null}`
- `POST /internal/v1/retrieval/search` — `{siteId, query, topK?}` → `{hits:[{sourceId,chunkIndex,score,content}], degraded}`；嵌入或向量库失败时 **200**、`hits=[]`、`degraded=true`（不回 500）。`query` 上限与文档 `content` 相同；`topK` 缺省 4，大于 20 夹制到 20

业务工具（`BUSINESS_API_BASE_URL` 配置时）：`get_site_info` / `list_site_contents` 只读回调 Java internal API。预约工具与 `reservation-assistant` 技能已于 2026-10-27 随预约功能下架移除。

- 请求上限（纵深防御，§21.4）：`message`/history 单条 ≤16000 字符、history ≤50、skills ≤16、todos ≤20；超限或字段非法 → **400** `BAD_REQUEST`（扁平体，非 FastAPI 422）

公开探活：`GET /health`（无鉴权，只报进程存活）

## 两类失败（务必区分）

| 情形 | 行为 |
|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` 缺失 | **启动 fail-fast**（安全配置错误） |
| `AI_MODEL` / 凭证缺失 | 正常启动；health BLOCKED；chat 503 `EXTERNAL_DEPENDENCY_BLOCKED` |

## 环境变量

见 [`.env.example`](./.env.example)。`AI_MODEL` 使用 Pydantic AI model spec（`openai:…` / `azure:…` / `anthropic:…` / `google:…`），**无默认模型**。

Gemini 是正式支援路徑：`AI_MODEL=google:<model>`，舊別名 `google-gla:<model>`。對話與嵌入的直配憑證都用 `AI_PROVIDER_API_KEY`；若同時設定 `AI_PROVIDER_BASE_URL`，會傳給 Google Provider。未直配金鑰時，Provider 依原生規則讀取 `GOOGLE_API_KEY` 或 `GEMINI_API_KEY`。嵌入範例：`AI_EMBEDDING_MODEL=google:gemini-embedding-001`，`AI_EMBEDDING_DIMENSIONS` 必須與向量欄位一致（`gemini-embedding-001` 可設 `768`）。已實測對話範例為 `google:gemini-3-flash-preview`，部署時仍須明示 `AI_MODEL`。

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

Gemini 部署示例（已實測模型名；金鑰也可改放 `GOOGLE_API_KEY` 或 `GEMINI_API_KEY`，不要寫進程式）：

```bash
docker run --rm -p 3001:3001 \
  -e AGENT_INTERNAL_SERVICE_TOKEN=CHANGE_ME \
  -e AI_MODEL=google:gemini-3-flash-preview \
  -e AI_PROVIDER_API_KEY=... \
  -e AI_EMBEDDING_MODEL=google:gemini-embedding-001 \
  -e AI_EMBEDDING_DIMENSIONS=768 \
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

`.github/workflows/agent-services-ci.yml`：改到 `agent-services` 或 Java `agent` 客户端时跑 pytest，通过后 Java 契约 IT 与 Docker build 并行。指向 `dev` / `main` 的 Pull Request 按整个 PR 匹配路径。分支说明见 `docs/operations/DEPLOYMENT.md`。

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
harness/                            # background / guardrails / tool guidance
scripts/run_mcp_echo.py             # 可选 HTTP MCP echo
tests/
```

## MCP（平台）

- 配置：`MCP_SERVERS_JSON`（见 `.env.example`）。`transport`：`http`（默认；url 以 `/sse` 结尾则视为 `sse`）、`sse`（MCP 2026-07-28 已弃用，解析时 warning，连接不变）、`stdio`（`command`，可选 `args` / `env` / `cwd`）。其它值跳过。health 只报配置态；深检 `GET /internal/v1/mcp/ready` 对 stdio 同样 `list_tools`
- stdio 以本进程权限启动本地命令，只配置运维信任的 server。`env` 只传条目里的变量；MCP SDK 再并上默认可继承的一小套变量（不是整份父进程环境）
- 连接失败：`503` + `errorCode=MCP_UNAVAILABLE`（不静默降级）
- CI：in-process FastMCP（`tests/test_mcp_tools.py`），不依赖外部 MCP 进程
- 本地 HTTP smoke：

```bash
uv run --extra dev python scripts/run_mcp_echo.py
# 另开终端配置 MCP_SERVERS_JSON 后启动 agent-services
```

## Harness（平台）

一次请求组装成只读 harness，同步和流式都把这份系统提示送给模型：

- `harness/background.md`、`guardrails.md`、`tool_guidance.md`（`AGENT_HARNESS_DIR`，镜像里是 `/app/harness`）。单文件超过 4000 字时截断并标明 `[... truncated]`
- 已解析的 skills 正文；未知 id 记在 `skills_missing`
- 本轮函数工具的名字和说明。MCP 另列服务器名，不写 URL，也不混进函数工具清单
- 送模历史按字符预算裁成 `memory`，同步和流式都用这份结果
- 预算、工具超时、历史字符上限和技能注入上限记在 guardrails 上，并写进提示里的 Run limits；超限仍由运行时强制失败
- `GET /internal/v1/health` 的 `harness` 只报告三个平台文件在不在

不在请求之间保存 harness，也不根据回复改写下一轮提示。

## Skills（平台）

- 目录：`skills/{id}/SKILL.md`。YAML frontmatter：`name`（与目录名一致，小写连字符，≤64）与 `description`（≤1024）；可选 `license`、`compatibility`、`metadata`、`allowed-tools`（只进 catalog，不做工具门控）。缺 `name` 回退目录名；缺 `description` 用正文首段。`version` 仍解析，但不是标准字段
- 默认：`AGENT_DEFAULT_SKILLS`；请求体 `skills[]`；未知 id：`AGENT_SKILLS_UNKNOWN_POLICY=ignore|reject`
- 注入上限：`AGENT_SKILLS_MAX_INSTRUCTION_CHARS`（默认 12000，须为正整数）。超出则按解析顺序整段跳过，并在提示末尾标记；第一个 skill 单独超限时截断该段
- 目录 API：`GET /internal/v1/skills`（仅元数据）
- 响应/SSE：`skillsApplied` / `skillsMissing`
