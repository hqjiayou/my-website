# Agent 服务技术选型与架构设计 V1

```yaml
artifact_type: SPEC
status: CURRENT   # 2026-09-01 Phase 0–5 主干验收（见 §12–§18）
owner: platform
last_verified: 2026-09-01
source_of_truth: services/agent-services（代码优先）+ services/website-services/agent（Java 契约）
related:
  - docs/decisions/ADR-0004-ai-must-fail-honestly-without-provider.md
  - docs/decisions/ADR-0005-java-business-truth-python-ai-execution.md
```

> 本版取代早前 Node/Fastify 骨架方案与首版 Python 草案。核心结论：
> **Java owns business truth. Python owns AI execution.**

---

## 1. 定位

`agent-services` 是 **纯 AI Execution Runtime**：模型调用、Agent loop、Tool 调度（未来）、RAG retrieval（未来）、AI telemetry。仅被 Java `website-services` 经 internal HTTP 调用，不对公网暴露。

Java 持有：用户、RBAC、Site、Prompt 配置、Conversation、业务数据（全部 Source of Truth）。

## 2. 最终技术栈（2026-09 定稿）

| 环节 | 最终选择 | 引入时机 |
|------|----------|---------:|
| Python | **3.13**（`>=3.13,<3.14` + `.python-version` + `uv.lock`） | ✅ |
| 包管理 | **uv + pyproject.toml** | ✅ |
| HTTP | **FastAPI + Uvicorn** | ✅ |
| 配置 | **pydantic-settings** | ✅ |
| Agent Runtime | **Pydantic AI V2**（2026-06 稳定发布） | ✅ |
| Provider 抽象 | **直接用 Pydantic AI Model/Provider**，不自建 Protocol | ✅ |
| Azure | Pydantic AI AzureProvider / Foundry endpoint（部署配置，不进 Domain） | 按部署 |
| LiteLLM / LangChain / OpenAI Agents SDK / MS Agent Framework | **不使用** | ❌ |
| LangGraph | **Architecture Option，非 Roadmap Dependency**（触发条件见 §11） | ⏳ |
| Streaming | **SSE**（自定义 `AgentStreamEvent`，不透传 Pydantic AI 原始事件） | ✅ |
| Conversation / Prompt 管理 | **Java + PostgreSQL**（Python stateless between business requests） | ✅ |
| RAG | pgvector **Derived Retrieval Store**（Java KB = SoT） | ✅ |
| Tools | **Pydantic AI Function Tools → Java internal API** | ✅ |
| MCP / Multi-Agent / Durable Workflow | MCP + Skills + Todo 编排（Phase 5） | ✅ |
| Observability | **OpenTelemetry**（Pydantic AI 原生；Logfire 仅可选后端） | ✅基础 |
| Testing | **pytest + TestModel/FunctionModel** + Java↔Python 契约集成测试 | ✅ |
| AI Evals | Pydantic Evals | ✅基线 |
| Frontend Chat | **assistant-ui + 自定义 Java transport**（不引 Vercel AI SDK） | ✅ admin 预览 |

### 明确否决项（含理由摘要）

- ❌ 自建 `providers/base.py` Provider Protocol —— Pydantic AI 已有 Model/Provider 两层抽象，再包一层是抽象叠罗汉
- ❌ 默认 `gpt-4o-mini` —— 隐式绑定 OpenAI；无配置必须 BLOCKED，不自动选模型
- ❌ Python 保存 Conversation/session —— 避免双真相；历史由 Java 加载后随请求传入
- ❌ Python 直连业务 PostgreSQL —— RAG 仅允许独立 `agent_retrieval` schema（derived data）
- ❌ 初期 MCP / LlamaIndex / LiteLLM —— 触发条件未满足
- ❌ 把 internal-token 缺失当 Provider BLOCKED —— 那是 SECURITY CONFIGURATION ERROR，见 §7

## 3. 职责边界

```text
                        Java
        ┌────────────────┼─────────────────┐
      Auth             RBAC              Site
        │                                  │
    User/Role                      Prompt / Config
                                   Conversation
                                   Business Data
                                           ▼
                                     AgentClient ── internal contract ──▶ Python Agent
                                                        ┌────────┼─────────┐
                                                     Prompt     RAG      Tools
                                                    execution retrieval execution
                                                        └────────┼─────────┘
                                                                 ▼
                                                                LLM
```

硬约束：

1. Python **stateless between business requests**（Phase 3 例外仅 derived retrieval index）
2. `promptProfile.systemPrompt` 是 **Java → Python trusted internal field**，永不接受前端直传
3. Tools 一律回调 Java internal API，禁止 Python 直连业务表
4. RAG `site_id` 过滤由 Java trusted context 提供、Python retrieval 层强制注入，**不交给模型决定**（安全不变量）

## 4. 项目结构（Phase 0，按职责收敛）

```text
services/agent-services/
├── pyproject.toml / uv.lock / .python-version
├── .env.example / README.md
├── src/agent_services/
│   ├── main.py            # uvicorn 入口
│   ├── app.py             # FastAPI factory（启动期安全校验）
│   ├── config.py          # Settings
│   ├── auth/internal_token.py
│   ├── api/
│   │   ├── errors.py      # InternalApiError → 扁平 JSON（契约保证）
│   │   └── internal.py    # /internal/v1/*
│   ├── schemas/{chat,health}.py
│   └── agent/
│       ├── runtime.py         # chat 执行（Pydantic AI Agent.run）
│       ├── model_factory.py   # AI_MODEL spec → Pydantic AI Model | BLOCKED
│       └── prompt_builder.py
└── tests/
    ├── test_auth.py
    ├── test_internal_api.py   # 契约测试（对照 HttpAgentClient）
    └── test_runtime.py
```

**现在不建**：`rag/`、`tools/`、`graph/`、`mcp/`、`memory/`、`providers/`、`adapters/`、`workflows/`。功能真实出现再建。

## 5. HTTP 契约（Phase 0 = SINGLE-TURN）

所有响应体均为**顶层扁平 JSON**（Java `mapErrorBody` 读取顶层 `errorCode` / `providerStatus`）。

### `GET /internal/v1/health`（需 token）

200：`{"ok":true,"service":"agent-service","provider":"<AI_MODEL spec|blocked>","providerStatus":"LIVE|BLOCKED","timestamp":"ISO-8601"}`

### `POST /internal/v1/chat`（需 token）

请求：`{"siteId","message","locale?","conversationId?","promptProfile?{systemPrompt,temperature}"}`

- 200：`{"ok":true,"data":{"reply","conversationId","providerStatus":"LIVE"}}`
- 400：`{"ok":false,"errorCode":"BAD_REQUEST","message"}`
- 401：`{"ok":false,"errorCode":"UNAUTHORIZED","message"}`
- 503：`{"ok":false,"errorCode":"EXTERNAL_DEPENDENCY_BLOCKED","message","providerStatus":"BLOCKED"}`

### 与 Java `HttpAgentClient` 对照记录（2026-09-01）

| Java 侧行为（代码事实） | Python 侧义务 | 状态 |
|---|---|---|
| Header `X-Internal-Service-Token`（`AgentHttpHeaders`） | 同名 header 校验、constant-time compare | ✅ |
| chat 成功要求 `data.reply` 非空且 `data.providerStatus == "LIVE"`，否则抛 BLOCKED | 成功体固定 LIVE；不得伪造 | ✅ |
| `mapErrorBody` 读**顶层** `errorCode`/`providerStatus` | 错误体必须扁平；**首版 Python 用 `HTTPException(detail=…)` 产生 `{"detail":{…}}` 嵌套 → Java 会把 503 误映射为 SYSTEM_ERROR，已修复为扁平 JSONResponse** | ✅（漂移已修复） |
| 502/504 有限重试；503/4xx 不重试 | blocked 一律 503，不用 502/504 | ✅ |
| 401/403 → Java 映射 SYSTEM_ERROR（不外泄细节） | 401 扁平体即可 | ✅ |
| `X-Request-Id` 透传（MDC） | 暂不强制消费；OTel 接入时映射 trace | Phase 1 |

### Streaming（Phase 1 预告）

自定义事件契约 `AgentStreamEvent`：`START | TEXT_DELTA | TOOL_START | TOOL_RESULT | DONE | ERROR`。
禁止把 Pydantic AI 原始 stream event 透传给 Java/前端（其 minor release 允许新增事件类型，不算 breaking）。

## 6. 配置（Provider-neutral）

| 变量 | 必需 | 语义 |
|------|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` | **是** | 缺失 → **启动 fail-fast**（安全配置错误） |
| `AI_MODEL` | 否 | Pydantic AI model spec：`openai:gpt-…` / `azure:…` / `anthropic:…`；缺失 → BLOCKED |
| `AI_PROVIDER_API_KEY` | 视 Provider | OpenAI-compatible 直配用 |
| `AI_PROVIDER_BASE_URL` | 否 | OpenAI-compatible 端点（本地/代理/Foundry compatible） |
| `PORT` | 否 | 默认 3001 |

**无默认模型**。Azure 是 Provider/Deployment Platform，不进 Agent Domain Model。

## 7. 安全语义（两类失败必须分开）

| 情形 | 行为 | 理由 |
|------|------|------|
| `AGENT_INTERNAL_SERVICE_TOKEN` 未配置 | **启动 fail-fast**（进程拒绝启动） | SECURITY CONFIGURATION ERROR，不是外部依赖问题 |
| `AI_MODEL` / credentials 未配置 | 正常启动；health `providerStatus=BLOCKED`；chat 503 | ADR-0004 诚实失败 |

其余：token constant-time compare；secret 只来自 env；永不写日志；所有 internal 端点受保护。未来 Azure 可升级为 Managed Identity/Entra，届时废除 shared secret（本地不提前引入）。

## 8. Retry 所有权

- Java → Python chat：**不自动重试**（可能已产生计费调用）；health GET 可重试 —— 与现有 `HttpAgentClient`（仅 502/504 重试，503/4xx 不重试）一致
- Python → Provider：交给 Provider SDK / Pydantic AI 的有限 transient retry（429/网络/5xx，遵循 Retry-After）；4xx 不重试；stream 已输出后不重试

## 9. 观测与日志

- OpenTelemetry 全链：Java `traceparent` → FastAPI → Pydantic AI → Provider；Logfire 仅为可选后端
- 默认日志字段：requestId/traceId/siteId/model/provider/latency/token count/status/tool name/error type
- **默认不记录**：完整 prompt、完整 RAG context、完整回复、API key、internal token；AI debugging dataset 需 sampling/redaction/retention/permission 后另启

## 10. 测试体系（四层）

1. pytest：HTTP/业务单测
2. **TestModel / FunctionModel**：conftest 全局 `ALLOW_MODEL_REQUESTS=False`，杜绝测试误调真实 Provider
3. **Java ↔ Python 契约集成测试**：真实启动 Python service，用 `HttpAgentClient` 打 health/chat/auth/blocked/live-mock/timeout/error-mapping（Phase 0 先以 Python 侧契约测试锁形状，Java 侧集成测试在 website-services 增补）
4. Pydantic Evals：Prompt/RAG 成型后（不替代单测）

## 11. Phase 路线与触发条件

| Phase | 内容 |
|-------|------|
| **0** | Python 3.13 + uv + FastAPI + Pydantic AI V2；token fail-fast；provider-neutral 配置；Blocked/LIVE；single-turn chat；health；契约测试；OTel 基础 |
| **1** | SSE（AgentStreamEvent）→ Java 转发 → assistant-ui（自定义 Java transport） |
| **2** | Java Conversation/Message/Prompt/AI Config 表 + CRUD；请求携带 `history` → MULTI-TURN；Pydantic Evals 基线 |
| **3** | Java KB CRUD（SoT）；`agent_retrieval` derived index（pgvector：chunks/embeddings/site_id/source_id/metadata；独立 schema/db user，禁止读 Java 业务表）；site_id 强制过滤 |
| **4** | Pydantic Function Tools → Java internal API（Reservation/Site/Content） |
| **5** | 仅当需要：MCP（几十个工具/第三方系统）、multi-agent composition |
| **6** | 仅当具体 workflow 出现：LangGraph 或 Pydantic durable + Temporal/Restate |

LangGraph 引入触发条件（满足其一才评估，且先问 Pydantic AI 自身够不够）：明确 Graph State / 长任务 checkpoint-resume / HITL / 复杂分支循环 / 节点级失败恢复。

## 12. Phase 0 需求分析与实施记录

### 需求

1. 与 Java `HttpAgentClient` 契约完全兼容（含扁平错误体修复）
2. token 缺失 fail-fast 与 provider BLOCKED 语义分离
3. `AI_MODEL` provider-neutral spec，无默认模型
4. 去掉自建 Provider 抽象层，目录收敛为 `agent/{runtime,model_factory,prompt_builder}`
5. 测试层 1+2 落地（TestModel/FunctionModel + `ALLOW_MODEL_REQUESTS=False`）

### 验收标准

- [x] pytest 全绿（auth / internal_api 契约 / runtime）
- [x] 契约测试断言错误体**顶层** `errorCode`/`providerStatus`
- [x] 无 `AI_MODEL` → health BLOCKED + chat 503；配置 spec 后 health LIVE（TestModel 注入验证 chat 200）
- [x] 无 token → 进程启动失败
- [x] `uv.lock` + `.python-version`（3.13）提交

### 实施记录（2026-09-01）

- 测试：`18 passed`（Python 3.13.15；`tests/test_auth.py` 6 项、`tests/test_internal_api.py` 6 项、`tests/test_runtime.py` 6 项）
- 契约漂移修复：首版 Python 用 `HTTPException(detail=…)` 产生 `{"detail":{…}}` 嵌套错误体，Java `mapErrorBody` 只读顶层字段，503 会被误映射 `SYSTEM_ERROR`；已改为 `InternalApiError` → 扁平 `JSONResponse` 并由契约测试锁定
- 目录收敛：删除自建 `providers/` 抽象层与 `api/routes/` 嵌套，改为 `agent/{runtime,model_factory,prompt_builder}` + `api/{errors,internal}`
- 环境备注：本机 Windows 应用控制策略阻止 uv 隔离构建的临时 venv，`pyproject [tool.uv] no-build-isolation-package` 已固化 workaround（构建依赖 hatchling/editables 入 dev extras）
- 遗留（非 Phase 0 范围）：~~Java 侧 `HttpAgentClient` ↔ 真实 Python 进程的集成契约测试~~ **已完成**（`HttpAgentClientPythonContractIT`）；OTel exporter 配置见 `docs/operations/DEPLOYMENT.md`

## 13. 部署定型记录（2026-09-01）

### 需求

1. Docker 镜像可构建、可运行（非 root、`/health` HEALTHCHECK）
2. CI：pytest + Java↔Python 契约 IT + docker build
3. 运维文档：`DEPLOYMENT.md` / `LOCAL_DEVELOPMENT.md` / `ENVIRONMENT_VARIABLES.md` 补充 agent 清单
4. 修正文档漂移（`AGENTS.md`、`整体架构说明.md` §3.4 仍写 Node/mock）

### 实施

- **HttpAgentClient 修复**：`JdkClientHttpRequestFactory` 在 Windows/部分环境下 POST body 未写出 → FastAPI 422；改用 `BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory)` + Jackson 消息转换器
- **契约 IT**：`HttpAgentClientPythonContractIT` 4 项全绿（health / auth / chat / stream）
- **Docker**：`[tool.uv.extra-build-dependencies]` 补 hatchling；`Dockerfile` 多阶段构建
- **SSE Phase 1（Python 侧）**：`POST /internal/v1/chat/stream` + `AgentStreamEvent` + `test_stream_api.py`
- **测试隔离**：`make_settings()` 显式 `agent_contract_use_test_model=False`，避免 shell 残留 env 污染 BLOCKED 测试

### 验收

- [x] `uv run python -m pytest` 全绿（20 项）
- [x] `AGENT_PYTHON_CONTRACT_IT=true mvn -pl agent test -Dtest=HttpAgentClientPythonContractIT` 全绿
- [x] `docker build` 成功
- [x] 部署/环境变量 runbook 更新

### 明确不在部署定型轮

- skills / MCP / todo 编排（Phase 5+；未到触发条件）

---

## 14. Phase 1 实施记录（SSE + assistant-ui）

### 需求

1. Python `POST /internal/v1/chat/stream` 输出 `AgentStreamEvent`（不透传 Pydantic AI 原始事件）
2. Java `HttpAgentClient` 转发 SSE；site-app 对 SITE JWT 用户落库聊天；admin 预览 **不落库**
3. admin-web 使用 **assistant-ui + 自定义 Java transport**，禁止 `@assistant-ui/ai-sdk` / Vercel AI SDK
4. 前端预览请求体仅 `message` + `history`（role 闭集 USER/ASSISTANT）；`systemPrompt` 由 Java 从 `site_ai_prompt_profile` 注入

### 验收

- [x] site-app `POST /api/site/{siteId}/ai/chat/stream`
- [x] admin `POST /api/admin/sites/{siteId}/ai/preview/chat/stream`（权限 `admin:ai-prompt:read`）
- [x] admin-web `SiteAiPreviewChat`：`useLocalRuntime` + `createJavaPreviewAdapter`

site-web 仍为骨架，无 SITE JWT 聊天页；P1 前端落在 **admin 站点 AI 预览**。

---

## 15. Phase 2 实施记录（Conversation / Prompt / MULTI-TURN / Evals）

### 需求

1. Java + PostgreSQL 为 Conversation / Message / Prompt 真相源；Python business request 之间 stateless
2. 请求携带 `history` → MULTI-TURN
3. admin 只读会话列表与消息；权限 `admin:ai-conversation:list`（Flyway V19）
4. Pydantic Evals 基线（不替代单测）

### 验收

- [x] site-app 会话 CRUD 路径（用户侧写，admin 只读）
- [x] admin `GET /api/admin/sites/{siteId}/ai/conversations` 与 `.../messages`
- [x] admin Prompt `GET/PUT .../prompt-profile`
- [x] admin-web Prompt / 会话页 FORCE REAL（站点选择器或工作区 tabs）

---

## 16. Phase 3 实施记录（KB SoT + 派生索引）

### 需求

1. Java KB CRUD 为 SoT；写后 best-effort 同步 Python 索引
2. Python 仅写独立 `agent_retrieval` schema；`site_id` 强制过滤
3. 缺失 `RETRIEVAL_DATABASE_URL` / `AI_EMBEDDING_MODEL` → 检索 BLOCKED，纯问答仍可用

### 验收

- [x] Flyway V18 `site_ai_knowledge_*` + `admin:ai-kb:*`
- [x] admin ` /api/admin/sites/{siteId}/ai/knowledge-documents`
- [x] Python `retrieval/` + admin-web 知识库页 FORCE REAL

---

## 17. Phase 4 实施记录（Function Tools → Java internal）

### 需求

1. Pydantic AI Function Tools 只回调 Java site-app `/internal/**`
2. `BUSINESS_API_BASE_URL` 缺失 → 不注册业务工具（纯问答仍可用）
3. gateway **不**转发 internal；Tools 直连 site-app

### 验收

- [x] Python `business_tools`（Site / Content / Reservation）
- [x] site-app internal token 校验
- [x] pytest `test_business_tools.py`

### 本轮明确不做

- Phase 6 LangGraph / durable workflow
- site-web 终端用户聊天 UI
- multi-agent composition（独立 agent 编排，非 todo 步骤）

---

## 18. Phase 5 实施记录（MCP / Skills / Todo 编排）

### 需求

1. **MCP**：配置驱动 `MCPToolset`（Pydantic AI V2 + fastmcp client），与 business/todo toolsets 组合；`async with agent` 管理生命周期
2. **Skills**：`skills/{id}/SKILL.md` 指令模块；请求 `skills[]` + 默认 `AGENT_DEFAULT_SKILLS` 注入 system prompt（Java 可传 id，内容不从前端直传）
3. **Todo 编排**：单次 run 内 `list/add/update/complete_todo` 工具；初始 `todos[]` 可由 Java 传入；完成态 `todos` 随 chat/stream `DONE` 回传
4. SSE：映射 `FunctionToolCallEvent` / `FunctionToolResultEvent` → `TOOL_START` / `TOOL_RESULT`；`TEXT_DELTA` 同时带 `text` 与 `delta`

### 验收

- [x] `agent/mcp_tools.py` + `MCP_SERVERS_JSON` 配置
- [x] `agent/skills.py` + 内置 `orchestration` / `reservation-assistant` skills
- [x] `agent/todo_tools.py` + `AGENT_ENABLE_TODO_TOOLS`
- [x] `AgentChatRequest` 扩展 `skills` / `todos`（向后兼容）
- [x] pytest **45 passed**（含 skills / todo / mcp 单测）
- [x] Docker 镜像 COPY `skills/` + `AGENT_SKILLS_DIR`

### 明确不在 Phase 5

- LangGraph / Temporal / 长任务 checkpoint
- admin-web MCP 管理 UI
- Java 侧 skills/todos 持久化表（可由后续请求体传入，无新表）
