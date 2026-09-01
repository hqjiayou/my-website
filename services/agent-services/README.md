# agent-services — Python AI Execution Runtime（internal only）

**Python 3.13 + FastAPI + Pydantic AI V2**。原则：**Java owns business truth, Python owns AI execution**（ADR-0005）。

- 设计：[`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](../../docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)
- 决策：ADR-0004（无 Provider 诚实失败）、ADR-0005（职责边界）

## 契约

内部路由（需 `X-Internal-Service-Token`，对端为 Java `HttpAgentClient`）：

- `GET /internal/v1/health` — `providerStatus: LIVE|BLOCKED`
- `POST /internal/v1/chat` — 成功 `{"ok":true,"data":{reply,conversationId,providerStatus:"LIVE"}}`；错误体为**顶层扁平** `{ok,errorCode,message[,providerStatus]}`

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

## 部署（定型）

**internal only**：不经 gateway 暴露；仅 Java `site-app`（`HttpAgentClient`）内网访问 `:3001`。

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

### Java 侧（site-app）

`services/website-services/.env.example`：

- `AGENT_BASE_URL=http://agent-services:3001`（容器内 DNS 或宿主机地址）
- `AGENT_INTERNAL_SERVICE_TOKEN` 与 Python 侧一致

### CI

`.github/workflows/agent-services-ci.yml`：pytest → Java 契约 IT → Docker build。

### 观测

设置 `OTEL_EXPORTER_OTLP_ENDPOINT` 后启用 FastAPI OTel；未设置则 no-op。

## 结构

```text
src/agent_services/
├── app.py / main.py / config.py
├── auth/internal_token.py     # constant-time 校验 + 启动期 fail-fast
├── api/{errors,internal}.py   # 扁平错误契约 + /internal/v1/*
├── schemas/{chat,health}.py
└── agent/
    ├── runtime.py             # Pydantic AI Agent.run（single-turn）
    ├── model_factory.py       # AI_MODEL spec → Model | BLOCKED
    └── prompt_builder.py
```

`rag/`、`tools/`、`graph/` 等目录在对应 Phase 需求出现后再建。
