# Agent 服务技术选型与架构设计 V1

```yaml
artifact_type: SPEC
status: CURRENT   # Phase 0–5 + §19 MCP + §20 Skills 平台化（2026-09-05）
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

---

## 19. MCP 平台完善设计（Agent 基建，非业务）

```yaml
artifact_type: DESIGN
status: IMPLEMENTED
date: 2026-09-05
implemented: 2026-09-05
scope: services/agent-services MCP client 平台能力
non_goals: 业务 Tools 扩展、行业 Skills、site-web 聊天、admin MCP 管理 UI
```

### 19.1 背景与问题

Phase 5 已落地 **配置解析 + `MCPToolset` 注册 + `async with agent` 生命周期**，但缺口是「平台未验收」：

| 已有 | 缺口 |
|------|------|
| `MCP_SERVERS_JSON` → `build_mcp_toolsets` | 仅解析单测，**无真实/进程内 MCP 调用** |
| URL → `MCPToolset(url).prefixed(name)` | 无 headers / auth / enabled / 超时字段 |
| chat/stream 遇异常 → `ChatBlocked` / `ERROR` | MCP 专属失败语义与 health 不可见 |
| 依赖 `pydantic-ai-slim[mcp]` | 无 in-process fixture，CI 无法稳定验 MCP |
| 业务 Tools 样例已存在 | **本设计不扩业务**；MCP 与业务 Tools 正交 |

产品尚未定业务域；本阶段只把 **「外部工具总线」** 做成可配置、可测、可观测、失败诚实的平台能力。

### 19.2 目标 / 非目标

**目标（Must）**

1. **可测**：pytest 用 **in-process FastMCP**（无网络、无外部进程）验证 Agent 能发现并调用 MCP 工具。  
2. **配置完备**：规范化 `MCP_SERVERS_JSON` schema（向后兼容现有 `{name,url}`）。  
3. **失败诚实**：区分「未配置 / 配置无效 / 运行时不可达 / 工具执行失败」。  
4. **可观测**：internal health 暴露 MCP 配置态；SSE 已有 `TOOL_START`/`TOOL_RESULT` 须覆盖 MCP 工具名（含 prefix）。  
5. **边界清晰**：MCP ≠ 业务真相；禁止用 MCP 直连业务库或替代 Java internal Tools。

**非目标（本轮不做）**

- 预约/内容等业务 internal API 扩展  
- admin-web MCP 管理 UI / 动态热更新配置中心  
- Java chat 契约为 MCP 新增字段（MCP 纯 Python 配置驱动）  
- stdio 多进程 MCP 作为默认生产路径（可设计预留，实施可选）  
- Provider-native MCP（`native=True`）；本项目保持 **本地 MCP client（FastMCP）**  
- LangGraph / multi-agent / durable workflow  

### 19.3 架构位置

```text
                    ┌─────────────────────────────────────┐
                    │ Java（业务真相 / HttpAgentClient）   │
                    │ 不感知 MCP；不传 MCP 配置             │
                    └─────────────────┬───────────────────┘
                                      │ internal chat/stream
                                      ▼
┌─────────────────────────────────────────────────────────┐
│ agent-services（AI Execution，stateless）                │
│  toolset_factory:                                       │
│    [business?*] + [todo?] + [mcp_0..n]                  │
│         │                      │                        │
│         ▼                      ▼                        │
│   Java /internal/**      MCPToolset (FastMCP client)    │
│   （样例业务回调，        → 外部或 in-process MCP Server │
│    本轮不扩展）                                          │
└─────────────────────────────────────────────────────────┘
```

**原则（ADR-0005 延伸）**

| 通道 | 用途 | SoT |
|------|------|-----|
| Function Tools → Java internal | 站点业务读写 | Java + PostgreSQL |
| MCP | 外部/横切能力、平台演示、未来 SaaS | MCP Server 各自负责；**非**本仓业务 SoT |
| Skills | 指令模块 | 文件 `skills/*/SKILL.md` |
| Todo | 单次 run 编排 | 请求内内存；Java 可选回传 |

### 19.4 配置契约（`MCP_SERVERS_JSON`）

**形态**：JSON 数组。空 / 未设置 → `mcpStatus=DISABLED`，不注册 MCP。

**条目 schema（v1 扩展，兼容旧字段）**

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | 建议 | 工具前缀；缺省 `mcp{index}`；须 `[a-zA-Z0-9_-]+` |
| `url` | 与 `transport` 组合 | Streamable HTTP 推荐路径（如 `http://host:port/mcp`） |
| `enabled` | 否 | 默认 `true`；`false` 跳过 |
| `headers` | 否 | 静态 HTTP 头（如 `Authorization`）；**禁止**写入日志全文 |
| `toolPrefix` | 否 | 默认等于 `name`；传给 `.prefixed(...)` |
| `initTimeoutSeconds` | 否 | 连接/初始化超时；默认跟库（设计落码时对齐 FastMCP `init_timeout`） |
| `transport` | 否 | 显式提示：`http`（默认）\| `sse`（仅当 url 以 `/sse` 结尾或显式 sse） |

**兼容**：现有 `[{"name":"demo","url":"http://127.0.0.1:3999/mcp"}]` 必须继续有效。

**非法配置策略**

- JSON 无法解析 / 非数组 → **告警日志 + 跳过全部 MCP**（保持进程可起；与 Phase 5 一致），health 记 `mcpStatus=INVALID_CONFIG`。  
- 单条缺 url / name 非法 → **跳过该条**并告警，其余仍注册。  
- **不**因 MCP 配置错误导致进程 fail-fast（与 `AGENT_INTERNAL_SERVICE_TOKEN` 区分）。

**Stdio（预留，本轮可不实现）**

```json
{ "name": "local", "transport": "stdio", "command": "python", "args": ["-m", " somehow"] }
```

本轮若未实现：解析到 `stdio` → 告警跳过，文档标明 Future。

### 19.5 生命周期与调用语义

1. **构建期**（请求进入 `_prepare_execution`）：解析配置 → 构造 `MCPToolset` 列表（惰性连接，不在构建期强制 TCP）。  
2. **运行期**：存在 MCP toolset 时 `async with agent:` 建立会话、list tools、供模型调用；用 `entered` 标志区分「上下文进入失败 → `MCP_UNAVAILABLE`」与「进入后 run/stream 失败 → Provider/`EXTERNAL_DEPENDENCY_BLOCKED`」。  
3. **结束**：`async with` 退出释放连接（每请求独立；符合 Python stateless）。

**运行时失败分类**

| 情形 | chat | stream | 备注 |
|------|------|--------|------|
| MCP 未配置 | 正常（无 MCP 工具） | 同左 | `DISABLED` |
| 配置无效已跳过 | 同未配置 | 同左 | `INVALID_CONFIG` |
| `async with` / 连接失败 | `503` + `errorCode=MCP_UNAVAILABLE`（新建） | `ERROR` 事件同码 | Provider 仍可能 LIVE；**诚实暴露 MCP 依赖失败** |
| 单次 tool 执行失败 | 工具结果错误串回模型（不整轮 503） | `TOOL_RESULT` + 随后模型继续或结束 | 与 business tool 错误返回一致 |
| Provider 失败 | 现有 `EXTERNAL_DEPENDENCY_BLOCKED` | 现有 `ERROR` | 不变 |

**决策（已定）**：配置了 MCP 且本轮 toolsets 含 MCP 时，**连接失败 = 整轮失败（诚实）**，不静默降级成「假装没有 MCP」。未配置则完全不影响。

可选开关（本轮可做可缓）：`MCP_REQUIRED=false`（默认）保持上表；若未来需要「MCP 挂了仍纯问答」再加 soft-degrade，**默认不做静默降级**。

### 19.6 Health / 就绪

扩展 `GET /internal/v1/health`（**向后兼容**：新字段可选）：

```json
{
  "ok": true,
  "service": "agent-service",
  "provider": "openai:...",
  "providerStatus": "LIVE|BLOCKED",
  "mcpStatus": "DISABLED|CONFIGURED|INVALID_CONFIG",
  "mcpServers": [{ "name": "echo", "enabled": true }],
  "timestamp": "..."
}
```

- **不做** health 内实时 `list_tools` 探针（避免拖慢探活、耦合外部可用性）。  
- 运维若需深检：另增可选 `GET /internal/v1/mcp/ready`（本轮 Should）：对每个 enabled server 短超时尝试 list tools；失败返回 503 + 明细。CI/本地联调用；K8s 默认仍用现有 readiness（provider），MCP ready 作手动/可选探针。

Java `HttpAgentClient`：**本轮不强制解析新字段**（忽略未知 JSON 字段即可）；不改 Java 业务逻辑。

### 19.7 平台演示 MCP（非业务）

提供 **零业务语义** 的 echo 工具，仅证明总线可用：

| 工具 | 行为 |
|------|------|
| `echo` | 返回入参文本 |
| `add` | 两整数相加 |

**两种形态**

1. **In-process（Must，pytest）**  
   `tests/fixtures/mcp_echo.py`（或 `agent_services/testing/mcp_echo.py`）用 FastMCP 进程内 server → `MCPToolset(fastmcp_instance)`。  
   Agent 使用 `TestModel` / `FunctionModel` 触发 tool call，断言调用到 `echo_*` 前缀工具。  

2. **HTTP 可选进程（Should，本地手册）**  
   `scripts/run_mcp_echo.py` 起 Streamable HTTP（如 `:3999/mcp`），`.env.example` 给出 `MCP_SERVERS_JSON` 样例。  
   **不**进入默认 Docker Compose 强依赖；文档「如何本地验 MCP」一节即可。

工具名经 prefix 后形如：`echo_echo`、`echo_add`（prefix=`echo`）。Skill **不**强制绑定；可选极简 `skills/mcp-smoke/SKILL.md`（「需要时可调用 MCP echo/add 做连通性验证」）——可选，非 Must。

### 19.8 与现有 toolsets 组合规则

```text
build_execution_toolsets =
  business?          # BUSINESS_API_BASE_URL 有则挂（样例，不扩展）
  + todo?            # AGENT_ENABLE_TODO_TOOLS
  + mcp*             # MCP_SERVERS_JSON
```

- 多 MCP server：多个 prefixed toolset，**禁止**重名 `name`（冲突 → 后者跳过并告警）。  
- MCP 工具与 business/todo **平权**由模型选择；平台不做业务路由。  
- `site_id` **永不**注入 MCP URL/参数（防跨站）；MCP 若未来需要租户上下文，单开设计，本轮不做。

### 19.9 安全

| 风险 | 对策 |
|------|------|
| SSRF（模型改 URL） | URL **仅来自环境配置**，不接受请求体/前端 |
| 密钥泄露 | `headers` 不进日志、不进 SSE payload |
| 权限绕过业务 RBAC | MCP 不做业务写；业务仍走 Java Tools |
| 供应链 | 仅连接运维显式配置的 server；生产禁随意公网 MCP |
| Prompt 注入催促乱调 MCP | Skill/系统提示可限制；硬约束仍靠 MCP server 自身鉴权（headers） |

### 19.10 错误码与契约

| errorCode | HTTP | 含义 |
|-----------|------|------|
| `MCP_UNAVAILABLE` | 503 | 已配置且本轮启用 MCP，但连接/初始化失败 |
| `EXTERNAL_DEPENDENCY_BLOCKED` | 503 | Provider BLOCKED（不变） |
| `BAD_REQUEST` / `UNAUTHORIZED` | 400/401 | 不变 |

流式：`ERROR.data.errorCode = MCP_UNAVAILABLE`。  
Java `HttpAgentClient.mapErrorBody`：将 `MCP_UNAVAILABLE` **映射为** `EXTERNAL_DEPENDENCY_BLOCKED`（同属外部依赖 503；不新增 ErrorCode）。SSE 若已下发 `ERROR` 事件则原样转发 `MCP_UNAVAILABLE`。

### 19.11 测试策略

| 层级 | 内容 |
|------|------|
| Unit | schema 解析、enabled=false、重名、非法 JSON、缺 url |
| In-process IT | FastMCP echo + TestModel 走通 tool call；断言 TOOL 事件名带 prefix |
| 负面 | mock 连接失败 → `MCP_UNAVAILABLE`（可用坏 URL + 短 timeout，或注入假 toolset） |
| 回归 | 未配置 MCP 时既有 45+ 测试行为不变 |
| 非本轮 | 真实公网 MCP、Java Contract IT 强制起 MCP |

### 19.12 文档与运维

更新（实施时）：

- `.env.example`：完整 schema 注释 + echo 样例  
- `ENVIRONMENT_VARIABLES.md` / `DEPLOYMENT.md`：MCP 可选、失败语义  
- `agent-services/README.md`：结构图补 MCP；「本地 MCP smoke」步骤  
- 本文件 §19 状态 → `IMPLEMENTED` + 验收勾选  

### 19.13 实施落点（文件级）

| 区域 | 变更 |
|------|------|
| `agent/mcp_tools.py` | 结构化解析、`McpServerConfig`、headers/prefix/enabled、重名检测 |
| `schemas/health.py` + `api/internal.py` | `mcpStatus` / `mcpServers` |
| `api/errors.py` / chat·stream | `MCP_UNAVAILABLE` |
| `tests/fixtures/mcp_echo*` + `tests/test_mcp_*.py` | in-process 连通 |
| `scripts/run_mcp_echo.py`（可选） | HTTP smoke |
| `.env.example` / README / 运维文档 | 同步 |
| **不改** | admin/site 业务 Service、Flyway、admin-web、业务 Tools |

### 19.14 验收标准（实施完成定义）

- [x] 无 `MCP_SERVERS_JSON`：行为与现网一致；health `mcpStatus=DISABLED`
- [x] 合法配置 + in-process IT：模型可调用 prefixed MCP 工具；SSE `TOOL_*` 含 prefix（`test_mcp_tool_call_emits_tool_sse_events`）
- [x] 配置了 MCP 且连接失败：chat **与** stream 诚实失败 `MCP_UNAVAILABLE`（含 stream 单测）
- [x] 非法 JSON：进程可起；MCP 跳过；health `INVALID_CONFIG`
- [x] 文档与 `.env.example` 同步；**无新增业务 API**
- [x] pytest 全绿（含新增 MCP 用例；2026-09-05 修复后：**56 passed**）
- [x] Java `HttpAgentClient`：`MCP_UNAVAILABLE` → `EXTERNAL_DEPENDENCY_BLOCKED`

### 19.14.1 实施落点摘要

- `agent/mcp_tools.py`：`McpServerConfig` / `mcp_config_snapshot` / headers·prefix·enabled·重名
- `schemas/health.py`：`mcpStatus` / `mcpServers`
- `runtime.py` / `stream_runtime.py`：`async with agent` + `entered` → 连接失败 `MCP_UNAVAILABLE`
- Java：`HttpAgentClient` 将 `MCP_UNAVAILABLE` → `EXTERNAL_DEPENDENCY_BLOCKED`
- `testing/mcp_echo.py` + `scripts/run_mcp_echo.py` + `tests/test_mcp_tools.py`（含 stream 负面与 TOOL_*）
- dev 依赖：`fastmcp-slim[server]`（in-process / 本地 echo）

### 19.15 后续 Agent 基建队列（本设计之后）

1. Skills 平台化（加载策略、缺失行为、预览选 skill）— 仍非业务文案  
2. Todo 跨轮回传（Java 带上轮 `todos`）  
3. Preview 展示 `TOOL_*` / `todos`  
4. 契约 IT / evals 加固  
5. （更后）Phase 6 长任务  

### 19.16 决策摘要（已拍板）

1. **本阶段做 Agent 平台，不做新业务 Tools。**  
2. MCP 连接失败默认 **整轮诚实失败**，不静默降级。  
3. Health 报告配置态；深检探针可选、不阻塞默认 readiness。  
4. CI 以 **in-process FastMCP** 为准，不依赖外部 MCP 进程。  

---

## 20. Skills 平台化（Agent 基建，非业务文案）

```yaml
artifact_type: DESIGN
status: IMPLEMENTED
date: 2026-09-05
scope: skills 加载策略 / 缺失行为 / catalog / 预览传 id
```

### 20.1 目标

1. 规范化 `skills/{id}/SKILL.md`（可选 YAML frontmatter：`description` / `version`）  
2. 解析结果可观测：`skillsApplied` / `skillsMissing`  
3. 未知 id 策略：`AGENT_SKILLS_UNKNOWN_POLICY=ignore|reject`  
4. `GET /internal/v1/skills` 目录（只元数据，不返回正文）  
5. Admin Preview 请求可选 `skills[]` → `AgentChatRequest`（仍不直传正文）

### 20.2 非目标

- admin-web 技能选择 UI、行业 skill 文案扩写  
- Java 侧 skill 持久化表  
- Skills 热更新管理面  

### 20.3 合并规则

`default_skills` → `request.skills`，去重保序；只激活目录中存在的 id。

### 20.4 验收

- [x] frontmatter 解析 + mtime 缓存失效  
- [x] catalog API  
- [x] chat/stream 元数据；reject → 400  
- [x] Admin Preview 可传 `skills`  
- [x] pytest **54 passed**（2026-09-05）

### 20.5 后续队列

1. ~~Todo 跨轮回传（Java 带上轮 `todos`）~~ → §21  
2. ~~Preview 展示 `TOOL_*` / `todos` / `skillsApplied`~~ → §21  
3. ~~契约 IT 加固~~ → §21；evals 仍待定  
4. Phase 6 长任务  

---

## 21. Agent 平台闭环（Todo 跨轮 / 预览可观测 / 输入加固 / 契约）

```yaml
artifact_type: DESIGN
status: IMPLEMENTED
date: 2026-10-06
scope: 让 §18–§20 的 Python 能力在 Java + admin-web 端到端生效；不含任何业务工具
```

### 21.1 问题（代码现状）

| 能力 | Python | Java / admin-web 现状 |
|------|--------|----------------------|
| Todo | chat/`DONE` 回传 `todos` | `AgentChatResponse` 丢弃 `todos`；站点聊天与预览从不回传 → 只在单次 run 有效 |
| Skills | `skillsApplied`/`skillsMissing`、`GET /internal/v1/skills` | 无目录代理；预览前端不传 `skills`、不展示 |
| 工具事件 | `TOOL_START`/`TOOL_RESULT` | 预览前端只认 `TEXT_DELTA`/`ERROR` |
| 输入 | `ChatRequest` 无上限；校验失败返回 FastAPI 422 | Java 把 422 映射为 `SYSTEM_ERROR` |

### 21.2 Todo 跨轮回传

**真相源仍在 Java**（ADR-0005）；Python 每轮只接收 `todos[]`、返回最终快照。

| 通道 | 持久化 | 回传方式 |
|------|--------|----------|
| 站点聊天（site-app） | `site_ai_conversation.agent_todos`（Flyway V20，`TEXT` 存 JSON 数组；NULL = 无） | `prepare` 读出 → `AgentChatRequest.todos`；`recordExchange` 写回本轮快照（sync 读 `AgentChatResponse.todos`，stream 读 `DONE.todos`） |
| 后台预览（admin） | **不落库**（预览语义不变） | 前端保存上轮 `DONE.todos`，下一轮随请求 `todos[]` 回传；Java 校验后透传 |

规则：

- Python 本轮快照**覆盖** Java 存量；`DONE` 未带 `todos` 视为空列表（Python 只在非空时输出）→ 写 NULL。
- 选 `TEXT` 而非 `JSONB`：只做整体读写、不按字段查询；避免 site 模块引入 PG 专用 TypeHandler，H2 测试零差异。
- 解析失败（脏数据）→ 记 warn、按空列表处理，不阻断聊天。
- 站点聊天 **不**开放终端用户传 `skills`：只用平台默认 `AGENT_DEFAULT_SKILLS`（防止前台激活任意指令模块）。

### 21.3 预览可观测（admin-web）

- 新增 admin 代理 `GET /api/admin/sites/{siteId}/ai/preview/skills` → `AgentClient.listSkills()` → Python `/internal/v1/skills`；权限沿用 `admin:ai-prompt:read`；Python 不可用时抛 `EXTERNAL_DEPENDENCY_BLOCKED`。
- 预览请求体：`{ message, history?, skills?, todos? }`。
- 预览面板显示：skill 多选（来自目录）、本轮 `skillsApplied`/`skillsMissing`、工具调用列表（`TOOL_START` → 进行中，`TOOL_RESULT.ok` → 成功/失败）、当前 Todo 列表；「清空 Todo」按钮。
- 事件状态由 adapter 通过回调上报给面板，不改 assistant-ui 消息结构。

### 21.4 Python 加固

| 项 | 规则 |
|----|------|
| 输入上限 | `message ≤ 16000` 字符；`history ≤ 50` 条，单条 `≤ 16000`；`skills ≤ 16`，id `≤ 64`；`todos ≤ 20`，`id ≤ 64`、`title 1..200` |
| 校验错误 | `RequestValidationError` → **400** 扁平体 `{ok:false, errorCode:"BAD_REQUEST", message}`（Java 映射 `BAD_REQUEST`） |
| Todo 状态机 | `COMPLETED`/`CANCELLED` 为终态，不可再改；`add_todo` 上限 20、标题 ≤200；非法操作返回 `{"error": ...}`（**不**抛 `ModelRetry`：重试耗尽会使整轮 run 失败，一次 todo 误操作不应拖垮对话） |
| `TOOL_RESULT` | 增加 `ok: bool`：`RetryPromptPart`（参数校验失败 / MCP 工具报错）或返回体为含 `error` 键的 dict（平台工具约定）→ false，否则 true；**不**回传工具返回内容（防泄露/大 payload） |
| MCP health | 全部条目 `enabled:false` → `mcpStatus=DISABLED`（仍列出 `mcpServers`） |
| Schema 同步 | `ChatSuccessData` 补 `skillsApplied` / `skillsMissing` / `todos`，端点用模型序列化 |

上限是**纵深防御**：Java 侧仍按各自 DTO（站点 4000、预览 4000/20）先行校验。

### 21.5 契约

`AgentChatResponse(reply, conversationId, providerStatus, todos, skillsApplied)`（保留 3 参构造）。  
`HttpAgentClientPythonContractIT` 增补：`todos` 往返、`skills` 目录、`skillsApplied`、未知 skill 在 `ignore` 下 `skillsMissing`、超限请求 → `BAD_REQUEST`。

### 21.6 非目标

- 站点终端用户选择 skills；Todo 历史版本 / 审计表  
- 预览 Todo 持久化；工具返回内容展示  
- `/internal/v1/mcp/ready`（仍 Should，未排期）

### 21.7 验收

- [x] Python：上限 + 400 + Todo 终态 + `TOOL_RESULT.ok` + health DISABLED 语义 + schema 同步（测试已写；2026-10-06 本机 pytest 被 Windows 应用程序控制策略拦截，仅 `py_compile` 通过，**待 CI / 解除拦截后复跑**）
- [x] Java：V20 + 站点 todos 往返 + 预览 todos/skills 目录代理（`HttpAgentClientTest` 10、`AdminAiManagementMvcTest` 9、`SiteAiConversationTodosTest` 2、`SiteAiChatMvcTest` 3 全过）
- [x] admin-web：预览 skill 选择、事件面板、Todo 回传（`tsc` / eslint 通过）
- [x] 契约 IT 增补（编译通过；需 `AGENT_PYTHON_CONTRACT_IT=true`，CI 执行）；`CURRENT_STATE` / contracts 同步

---

## 22. Agent 平台能力补齐（运行预算 / 上下文 / 容错 / 观测 / 轨迹 / 运行时面板 / 评测）

```yaml
artifact_type: DESIGN
status: IMPLEMENTED
date: 2026-10-06
scope: 平台能力（非业务）；Python + Java + admin-web
baseline: pydantic-ai-slim 2.37 / pydantic-evals 2.37 / fastmcp-slim 4.0（以 .venv 源码核对 API）
```

### 22.1 业界主流 Agent 技术对照（2026）

| 技术 / 协议 | 主流做法 | 本项目现状 | 本轮决策 |
|-------------|----------|-----------|----------|
| 工具协议 MCP | 外部工具总线标准 | §19 已接（Streamable HTTP） | 保持 |
| Agent Skills | 目录化指令模块（SKILL.md + frontmatter） | §20 已接 | 保持 |
| 计划 / Todo | Agent 自维护任务列表 | §21 已端到端 | 保持 |
| **运行预算** | 请求数 / 工具调用数 / token 上限，防失控循环与成本爆炸 | 无上限（仅框架默认 request_limit=50） | **T1 做** |
| **工具超时** | 每个工具调用超时，超时视为失败可重试 | 无 | **T1 做** |
| **用量计量** | 每轮回传 token / 请求 / 工具次数，供计费与观测 | 无 | **T1 做** |
| **上下文管理** | 历史截断 / 压缩（history processor、compaction） | 只靠 Java 条数上限 20 | **T2 做**（字符预算截断；摘要压缩不做） |
| **模型容错** | Fallback 链 / 多 Provider 冗余 | 单模型 | **T3 做**（`FallbackModel`） |
| **可观测性** | OpenTelemetry GenAI 语义约定（agent run / model / tool span），Logfire / Langfuse / Phoenix 消费 | 只有 FastAPI HTTP span | **T4 做**（`Agent.instrument_all`，默认不录内容） |
| **运行轨迹** | 每条回复可追溯：用了哪些工具、skill、token | 预览仅前端临时显示；站点会话不留痕 | **T5 做**（Java 持久化 + admin 展示） |
| **运行时面板** | 管理端可见 Provider / MCP / Skills / 预算 | 无 | **T6 做** |
| **Evals** | 数据集 + evaluator 回归（pydantic-evals） | 只有 2 条基线 | **T7 做**（平台行为数据集） |
| Human-in-the-loop 工具审批 | `requires_approval` / DeferredToolRequests，人工批准后续跑 | 无 | **不做**：当前无带副作用的工具；审批状态须由 Java 持有，待业务工具出现再设计 |
| AG-UI 协议 | Agent↔前端标准事件流 | 自定义 SSE（Java 契约已固化） | **不做**：换协议需改 Java 转发与前端，收益低；事件语义已基本对齐（start/text/tool/done/error） |
| A2A 协议 | Agent 间互调 | 单 Agent | 不做（无多 Agent 需求） |
| Durable execution | Temporal / DBOS / Prefect 持久化长任务 | 无 | 不做（Phase 6） |
| 长期记忆 | 向量记忆 / 用户画像 | 无 | 不做：记忆即业务数据，须 Java 持有，待业务定义 |

### 22.2 Todo 执行规范（每项必须三段）

1. **事前分析**：读当前真实代码；确认依赖 API 在已安装版本存在；列出改动落点、契约变化、风险。  
2. **事中实施**：只改落点内文件；契约变化同步 Java DTO / 前端类型；新增配置写入 `.env.example` 与 `ENVIRONMENT_VARIABLES.md`。  
3. **事后检查**：对应层测试（pytest / JUnit / tsc+eslint）；回读 diff 核对边界（ADR-0004/0005、不引入业务）；在本节记录结果。

### 22.3 Todo 清单

| # | Todo | 层 | 契约变化 |
|---|------|----|----------|
| T1 | 运行预算 + 工具超时 + 用量回传 | Python / Java DTO | chat `usage`；DONE `usage`；新错误码 `AGENT_BUDGET_EXCEEDED` |
| T2 | 历史字符预算截断 | Python | 无（行为变化） |
| T3 | Fallback 模型链 | Python | health `fallbackProviders` |
| T4 | Agent OTel 埋点 + requestId 关联 | Python | 无 |
| T5 | 运行轨迹持久化与展示 | Python / Java（V21）/ admin-web | chat `tools`；admin 消息 `agentMeta` |
| T6 | Agent 运行时面板 | Java / admin-web | `GET /api/admin/sites/{id}/ai/runtime` |
| T7 | 平台行为评测集 | Python tests | 无 |

各项三段记录见 §22.4–§22.10。

### 22.4 T1 运行预算 + 工具超时 + 用量回传

**事前分析**
- 现状：`Agent(...)` 无 `usage_limits`（框架默认 `request_limit=50`）、无工具超时；`UsageLimitExceeded` 会落入通用 `except` 被误报为 `EXTERNAL_DEPENDENCY_BLOCKED`。
- API 核对（2.37 源码）：`UsageLimits` 为 kw-only dataclass（`request_limit` / `tool_calls_limit` / `total_tokens_limit`）；`Agent(tool_timeout=...)`；**`result.usage` 与 `stream.usage` 在 2.x 为 property**（非 1.x 的方法）。
- 落点：`agent/runtime.py`（`RunBudget`、`build_agent`、`build_run_kwargs`、`usage_to_api`）、`stream_runtime.py` 复用之、`config.py`、`api/internal.py`、`schemas/chat.py`。

**事中实施**
- 配置：`AGENT_MAX_REQUESTS=10`、`AGENT_MAX_TOOL_CALLS=20`、`AGENT_MAX_TOTAL_TOKENS=0`（不限）、`AGENT_TOOL_TIMEOUT_SECONDS=30`；`<=0` 关闭该维度。
- 超限 → `AGENT_BUDGET_EXCEEDED`：chat **422** 扁平体（不带 `providerStatus=BLOCKED`，因非依赖故障）；stream 为 `ERROR` 事件且**无 DONE**（已流出的部分文本不视为成功，Java 不落库）。Java `mapErrorBody` 将 `AGENT_BUDGET_EXCEEDED` 映射为 `ErrorCode.AGENT_BUDGET_EXCEEDED`（1008，HTTP 422；§24 起不再落到 `SYSTEM_ERROR`）。预览/站点 SSE 透传原始 `errorCode`；站点只透传 `errorCode` 不带 `message`（§23.1）。
- 工具超时：`runtime._TimeoutToolset`（`WrapperToolset` 子类，`anyio.fail_after` → `ModelRetry`）包装**所有**传入 toolset → 模型收到重试提示、`TOOL_RESULT.ok=false`。**不用** `Agent(tool_timeout=...)`：2.37 源码确认它只作用于 Agent 自身注册的函数工具，对 `toolsets=` 传入的 todo / MCP 无效（Docker 中实跑 pytest 暴露，§22.10）。同步工具在线程执行，超时不能中断线程本身。
- 成功体与 `DONE` 增加 `usage: {requests, toolCalls, inputTokens, outputTokens}`。
- `run_chat` / `stream_chat_events` 抽出共用 `build_agent` / `build_run_kwargs`，消除两处重复组装。

**事后检查**
- 新增 `tests/test_run_budget.py`（同步/流式超限、usage 字段、工具超时 ok=false、端点 422 扁平体、端点 usage）。
- 所有 `run_chat` / `stream_chat_events` 调用方均为关键字参数，`budget` 可选 → 旧调用不受影响（grep 核对）。
- 本机 pytest 受 Windows 应用控制策略拦截；改在 Docker（Linux）中实跑，首轮发现上述 `tool_timeout` 不生效缺陷并修复，复跑全绿（见 §22.10）。

### 22.5 T2 历史字符预算截断

**事前分析**
- 现状：Java 站点按条数取最近 20 条（`HISTORY_LIMIT`），每条最长 16000 字符；admin 预览历史来自前端；Python schema 上限 50 条。**总量不受控**，长会话可能撑爆上下文或抬高成本。
- 方案选择：不引入 tokenizer（各 Provider 不同、增加依赖），以字符为近似预算；不做 LLM 摘要（需额外模型调用且引入不可控输出，留待长期记忆一并设计）。
- 落点：`runtime.trim_history`，经 `RunBudget.history_max_chars` 注入 `build_run_kwargs` → 同步与流式共用。

**事中实施**
- `AGENT_HISTORY_MAX_CHARS=24000`（`<=0` 不限）。从最新往前累加，超出即丢弃更早项；再丢弃开头的非 USER 项（部分 Provider 拒绝 assistant 开头）。
- 截断仅影响本轮送模内容，Java 会话真相不变（Python 无状态）。

**事后检查**
- `test_trim_history_keeps_newest_within_budget_and_starts_with_user` 覆盖：恰好容纳、ASSISTANT 开头剥离、None 不限、预算小于单条 → 空、空输入。
- 默认 24000 远大于现有测试历史长度，旧用例不受影响。

### 22.6 T3 Fallback 模型链

**事前分析**
- 现状：单一 `AI_MODEL`；Provider 429/5xx 直接诚实失败，无冗余。
- 选型：Pydantic AI 原生 `FallbackModel(default, *fallbacks, fallback_on=(ModelAPIError,))`（2.37 源码核对）；**不引入 LiteLLM**。
- 关键约束（ADR-0004）：fallback 是冗余，不是默认值——**主模型 BLOCKED 时 fallback 不得顶替**，否则无配置也能“偷偷”跑起来。

**事中实施**
- `AI_FALLBACK_MODELS`：逗号分隔 spec；仅走 `infer_model`，凭证用各 Provider 原生环境变量（不复用主模型 `AI_PROVIDER_*`，避免把一个 key 发给另一家）。
- 与主模型相同的 spec 跳过；无效 spec 记 warning 剔除，不影响主模型。
- `ModelHandle.fallback_specs` → health `fallbackProviders`（只回 spec，不回凭证）。
- 仅 `ModelAPIError` 触发切换；工具异常 / 预算超限不切换。

**事后检查**
- `tests/test_model_factory.py`（monkeypatch `infer_model`，无需真实凭证）：无 fallback 返回原模型；重复/无效 spec 处理；主模型空或无效 → BLOCKED 且 `fallback_specs` 为空；health 字段。
- `conftest.make_settings` 显式置空 `ai_fallback_models`，防 shell 环境变量污染。

### 22.7 T4 Agent OTel 埋点 + requestId 关联

**事前分析**
- 现状：`setup_otel` 仅 FastAPI server span；Agent run / 模型请求 / 工具调用无 span；Java `HttpAgentClient` 已透传 `X-Request-Id`，Python 未使用。
- 选型：Pydantic AI 原生 `Agent.instrument_all(InstrumentationSettings(...))`（OTel GenAI 语义约定，2.37 源码核对 `include_content` / `tracer_provider`）；不引入 Logfire SDK（OTLP 任意后端可收）。
- 隐私：`include_content` 默认 True 会把 prompt / completion / 工具参数写进 span，含终端用户 PII 与 KB 内容 → **默认关闭**。

**事中实施**
- `otel._instrument_agents`：仅在 `OTEL_EXPORTER_OTLP_ENDPOINT` 启用时调用；`AGENT_OTEL_INCLUDE_CONTENT=true` 才记录正文（含 binary）。
- `observability/request_id.py`：纯 ASGI 中间件（不用 `BaseHTTPMiddleware`，避免干扰 SSE 流与断连取消）读取 `X-Request-Id`，白名单 `[A-Za-z0-9._:-]{1,128}` 校验后绑定 ContextVar，并写 server span 属性 `request.id`。
- `build_run_kwargs` 在已绑定时加 `metadata={"request_id": ...}` → agent run span 属性。

**事后检查**
- `tests/test_request_id.py`：合法 id 在模型调用时可见、请求结束后复位；非法 id 丢弃；未绑定时不带 metadata。
- 未开 OTel 时 `instrument_all` 不被调用，本地/测试零开销；`request_id` 模块不依赖 `agent_services` 内部模块，无循环导入。

### 22.8 T5 运行轨迹持久化与展示

**事前分析**
- 现状：工具轨迹只在 SSE 里瞬时出现（预览侧栏），sync chat 无轨迹；站点会话落库只有消息正文，后台无法回看“这一轮调了什么工具、花了多少”。
- 边界：Python 无状态 → 轨迹随响应返回，**持久化在 Java**（ADR-0005）；内容安全 → 只记工具名与成败，不记参数/返回（与 §21.4 TOOL_RESULT 一致）；终端用户不可见（仅后台观测）。
- 存储选型：`site_ai_message.agent_meta TEXT`（JSON），与 V20 `agent_todos` 同风格；不建独立 trace 表（无按字段查询需求，避免过度设计）。

**事中实施**
- Python：`runtime.tool_trace_from_messages(result.new_messages())`（跳过无 `tool_name` 的输出校验重试），`MAX_TOOL_TRACE=50`；`tool_succeeded` 抽为同步/流式共用；chat 成功体 `tools:[{name,ok}]`，DONE 同增 `tools`。
- Java agent：新增 `AgentRunMeta(usage, tools, skillsApplied)`（嵌套 `Usage` / `ToolCall`）；`AgentChatResponse` 第 5 参数改为 `runMeta`（`skillsApplied()` 保留为委托方法）；`HttpAgentClient.readRunMeta` 格式异常只丢元数据、不让成功回复失败。
- Flyway **V21** `site_ai_message.agent_meta`；site `recordExchange(..., runMeta)` 只写 ASSISTANT 行，序列化失败存 NULL 不回滚；stream 路径从 DONE 解析（显式关闭 unknown-property 失败）。
- admin：`AdminAiMessageDto.agentMeta`（损坏 → null + warn）；站点端 `SiteAiMessageDto` **不加**该字段。
- admin-web：`siteAiRunMeta.tsx`（`AiRunUsageLine` / `AiRunMetaSummary`）供会话面板与预览侧栏复用；预览 DONE 显示本轮用量；三语 i18n `aiWorkspace.runMeta.*`。

**事后检查**
- Python：`test_sync_reply_carries_tool_trace_without_content`、`test_stream_done_carries_tool_trace`。
- Java：`HttpAgentClientTest` 13/13（+usage/tools 映射、格式异常丢弃、422→SYSTEM_ERROR 不重试）；`SiteAiConversationTodosTest` 3/3（+agent_meta 仅 ASSISTANT）；`SiteAiChatMvcTest` 3/3；`AdminAiManagementMvcTest` 9/9（messages 含 `agentMeta`，未知字段容忍）。
- admin-web：`tsc --noEmit` 与相关文件 eslint 通过。
- H2 schema（site `schema-ai-h2.sql`、admin `rbac-h2-schema.sql`）已同步新列。

### 22.9 T6 Agent 运行时面板

**事前分析**
- 现状：运维要知道“当前用哪个模型、有没有 fallback、MCP 是否启用、预算多少”只能看部署环境变量；`AgentClient.ping()` 只回 providerStatus，且无调用方。
- 边界：只读、只元数据（spec 名 / server 名 / 数值），**不回凭证与 URL**；agent-services 全平台共享，但后台 AI 工作台按站点组织 → 路由挂在站点下、复用 `admin:ai-prompt:read`，不新增权限（无新写操作）。
- 不可达语义：面板的职责是“如实展示”，故 admin 不把不可达当作接口错误，而是 `reachable=false + errorCode`。

**事中实施**
- Python health 增 `limits{maxRequests,maxToolCalls,maxTotalTokens,toolTimeoutSeconds,historyMaxChars}`（直接取自 `run_budget_from_settings`，与执行同源；null = 不限）。
- Java agent：`AgentRuntimeStatus`（嵌套 `McpServer` / `Limits`，旧版 Python 缺字段 → 空列表 / `Limits.UNKNOWN`）；`AgentClient.runtimeStatus()`。
- admin：`AdminAiPreviewController` 类级映射改为 `/api/admin/sites/{siteId}/ai`（原 `/preview/skills`、`/preview/chat/stream` URL 不变），新增 `GET /runtime` → `AdminAiRuntimeDto(reachable, errorCode, status)`。
- admin-web：`siteAiRuntimePanel.tsx`（Provider+状态、Fallback、MCP servers、预算、skills 目录，可刷新）；`siteAiPage` 新增“运行时”tab（`aiPromptRead`）；三语 i18n `aiWorkspace.runtime.*`、`tabs.runtime`。

**事后检查**
- Python：`test_health_reports_effective_limits_with_null_for_unlimited`。
- Java：`HttpAgentClientTest` 15/15（+旧版 health 缺字段默认值、完整映射）；`AdminAiManagementMvcTest` 10/10（+代理成功、不可达 reachable=false 仍 200、无权限 403）。
- admin-web：`tsc --noEmit`、相关文件与 locales eslint 通过。

### 22.10 T7 平台行为评测集

**事前分析**
- 现状：`test_evals_baseline` 只锁“非空回复 / 历史被利用”两条；本轮新增的预算、截断、轨迹、todo 终态、skill 注入均无评测级回归。
- 选型：沿用 pydantic-evals（已在 dev extras），Case + Evaluator 结构可在未来接真实模型时复用同一数据集；本轮仍用 FunctionModel 保证确定性、不触达 Provider（不做业务内容）。

**事中实施**
- `tests/test_evals_platform.py`：`agent-platform-behavior` 数据集 7 个 Case——skill 指令进入模型上下文、todo 往返至 COMPLETED、终态 todo 重开失败可见（`tools` ok=false 且状态不变）、失控循环命中预算（`AGENT_BUDGET_EXCEEDED`）、预算内历史被利用、超预算历史被截断、Provider 异常诚实失败。
- `MatchesExpected`：只比较 `expected_output` 声明的字段（mapping 输出 → 每字段一条 assertion），同时断言无 task 失败、无 evaluator 异常。

**事后检查**
- 本机：Windows 应用控制阻止 uv Python 的 `_socket` DLL（os error 4551），pytest 无法运行。
- **Docker 实跑**（`ghcr.io/astral-sh/uv:python3.13-bookworm-slim`，独立 venv 不触碰本地 `.venv`）：首轮 78 passed / 1 failed —— 暴露 T1 `Agent(tool_timeout)` 对外部 toolset 无效；修复为 `_TimeoutToolset` 后 **79 passed**。命令见 agent-services README。
- `HttpAgentClientPythonContractIT` 需本机拉起 uv Python 进程，仍受同一策略限制，未执行（CI / 放开策略后运行）。

## 23. §22 复查与补强（2026-10-07）

对 §22 落地代码做逐层复查（Python → Java site/admin → admin-web），按“事前分析 / 事中实施 / 事后检查”处理发现项。

### 23.1 站点 SSE 运行轨迹与原始错误外泄（缺陷）

**事前分析**
- §22.8 约定运行轨迹“终端用户不可见”，但 `SiteAiServiceImpl.forwardStream` 将 Python 事件**原样**转发：`TOOL_START`/`TOOL_RESULT`（内部工具名、MCP prefix）、`START.skillsApplied`、`DONE.usage/tools/skillsApplied/todos` 全部到达终端用户；`ERROR.message` 为 Python `str(exc)`，可能含 Provider/MCP 地址或上游报错。
- 同步 chat 只返回 `reply/conversationId/providerStatus`，两条路径口径不一致；且站点 SSE 无任何测试。

**事中实施**
- `SiteAiServiceImpl.toClientEventData`：`TEXT_DELTA` 原样；`START` → `{}`；`DONE` → `{reply, providerStatus}`；`ERROR` → `{errorCode}`，仅白名单 `EXTERNAL_DEPENDENCY_BLOCKED` / `MCP_UNAVAILABLE` / `AGENT_BUDGET_EXCEEDED` 原样，其余 → `SYSTEM_ERROR`；工具事件与**未知事件默认不下发**（Python 新增事件不会自动外泄）。Java 落库仍读取完整 DONE。
- admin 预览保持原样透传（后台调试需要轨迹与诊断信息）。

**事后检查**
- 新增 `SiteAiChatStreamMvcTest` 3 例（轨迹剥离 + 仍有 CONVERSATION；ERROR 只留白名单码且无 message；未知码降级）；修复前断言 `doesNotContain("TOOL_")` 必然失败。site AI 相关 9/9 通过。

### 23.2 admin 预览错误不可见（体验缺陷）

**事前分析**
- adapter 遇 `ERROR` 仅 `throw new Error(code)`，页面未渲染错误 → 无 Provider（当前默认 BLOCKED）时预览只出现空白助手消息，`AGENT_BUDGET_EXCEEDED` 等也无提示。当前 assistant-ui 版本无可直接复用的错误 primitive。

**事中实施**
- `AiPreviewAgentEvent` 增加 `{type:'error', errorCode, message?}`；adapter 在抛错前上报（Java 推流前失败 → `REQUEST_FAILED` + Java 本地化 message；推流中 → 原始 `errorCode` + Python 诊断 message）。
- `SiteAiPreviewChat`：线程底部 `role="alert"` 错误条，已知码走 i18n（`aiWorkspace.preview.errors.*`，三语），下一轮 `START` 清除。

**事后检查**
- `tsc --noEmit`、相关文件与 locales eslint 通过。

### 23.3 `has_mcp_toolsets` 嵌套包装（加固）

**事前分析**
- 只下探一层 `wrapped`；§22 的 `_TimeoutToolset` 使结构变为 Timeout → Prefixed → MCP 三层。现调用点都传包装前列表，故当前无误，但若传入包装后列表会静默失去 `MCP_UNAVAILABLE` 区分。

**事中实施 / 事后检查**
- 改为沿 `wrapped` 链循环下探；`test_mcp_tools` 新增嵌套用例。Docker pytest **80 passed**。

### 23.4 复查结论（无需改动 / 记录）

- site `SiteAiMessageDto` 不含 `agentMeta`，终端用户历史接口不泄露 V21 列。
- ruff（默认规则外加仓库外配置）仅余风格项（多余 `noqa`、引号注解、`try-except-pass` 等，均为既有代码），无未定义名 / 未用导入；未改。
- `HttpAgentClientPythonContractIT` 仍未执行（同 §22.10 限制）。

## 24. P0 补强：预算错误码贯通 + 契约 IT 对齐 §22（2026-10-07）

### 24.1 `ErrorCode.AGENT_BUDGET_EXCEEDED` 端到端

**事前分析**
- Python 已诚实返回 422 + `AGENT_BUDGET_EXCEEDED`；站点 SSE 可透传该字符串。
- Java `mapErrorBody` 却落到 `SYSTEM_ERROR`（1000），sync chat / 运维无法区分超预算与系统故障；`common.ErrorCode` 无对应语义码。

**事中实施**
- `ErrorCode.AGENT_BUDGET_EXCEEDED(1008)` + 三语 `common.error.agent-budget-exceeded`。
- `HttpAgentClient.mapErrorBody`：仅当 `errorCode=AGENT_BUDGET_EXCEEDED` 映射（**不**把任意 HTTP 422 当成预算，避免 FastAPI 校验 422 误伤）。
- site/admin `GlobalExceptionHttpStatus` → HTTP **422** `UNPROCESSABLE_ENTITY`。
- site/admin 流式 `BusinessException` 映射含预算码；站点 sync MVC 覆盖 422 + code=1008。

**事后检查**
- `HttpAgentClientTest`：预算 → 1008、不重试；`SiteAiChatMvcTest` 4/4；admin MVC 既有套件通过。

### 24.2 契约 IT 对齐 §22

**事前分析**
- `HttpAgentClientPythonContractIT` 仅覆盖 LIVE chat / todos / skills / BAD_REQUEST / stream 事件名；无 `usage`/`tools`/`runMeta`、`runtimeStatus.limits`、预算超限、`MCP_UNAVAILABLE`。
- `runtimeStatus()` 对 health 的 `treeToValue` 遇 `ok`/`service`/`timestamp` 可能因 `FAIL_ON_UNKNOWN_PROPERTIES` 失败 → 改为 ignore unknown（与 `readRunMeta` 一致）。

**事中实施**
- 成功路径断言 `runMeta.usage.requests ≥ 1`、`tools` 非空（带 skills 的 todo 往返用例）、stream DONE 含 `usage`/`tools`。
- `runtimeStatus`：LIVE + 默认 limits（10/20/30/24000）+ mcp DISABLED。
- 短生命周期第二进程：`AGENT_MAX_REQUESTS=1` → `AGENT_BUDGET_EXCEEDED`；`MCP_SERVERS_JSON` 指向不可达 URL → `EXTERNAL_DEPENDENCY_BLOCKED`。

**事后检查**
- 本机 IT 仍因 Windows 应用控制 / 未设 `AGENT_PYTHON_CONTRACT_IT` **跳过**（11 skipped）；断言与进程编排已就绪，待 CI 执行。

## 25. P1：MCP transport 诚实化 + `/mcp/ready`（2026-10-08）

**事前分析**
- `transport` 写入 `McpServerConfig` 后 `build_mcp_toolsets` 仍 `MCPToolset(url)`，一律 Streamable HTTP；`stdio` 已跳过，其它未知值被当成 http。
- `/health` 按 §19.6 **不做** list_tools；深检端点仍未实现。

**事中实施**
- 仅 `http` / `sse` 注册。未写 `transport` 且 url 以 `/sse` 结尾 → sse；显式 `http` 不推断。`stdio` 与其它值告警跳过，不进入 CONFIGURED。
- `http` → `StreamableHttpTransport`；`sse` → `SSETransport`；headers 放在 Client 上。
- `GET /internal/v1/mcp/ready`：enabled server 短超时 `list_tools`（默认 3s，或条目 `initTimeoutSeconds`）。全通 200；任一条失败或配置非法 → **503** `MCP_UNAVAILABLE` + `servers[{name,ok,toolCount?,error?}]`。`error` 只含异常类型，不含 URL。DISABLED → 200。

**事后检查**
- Docker pytest **85 passed**（含 transport 跳过/推断、`/mcp/ready` 200 与 503 且响应不含 URL）。

## 26. 后台 MCP 深检代理（2026-10-08）

**事前分析**
- `/internal/v1/mcp/ready` 已存在，但 Java `mapErrorBody` 会把 503 收成 `EXTERNAL_DEPENDENCY_BLOCKED` 并丢掉 `servers`。运行时面板只显示配置态。

**事中实施**
- `HttpAgentClient.mcpReady()`：200 与 503 都解析探测体。
- Admin `GET /api/admin/sites/{siteId}/ai/mcp-ready`（`admin:ai-prompt:read`）：进程不可达 → `reachable=false`（仍 HTTP 200）；探测失败 → `reachable=true` 且 `probe.ok=false`。
- 运行时面板 MCP 卡片增加手动「探测」按钮，不在打开页时自动探活。

**事后检查**
- `HttpAgentClientTest` 覆盖 503 体与 DISABLED；`AdminAiManagementMvcTest` 覆盖代理与不可达。admin-web tsc / eslint。

## 27. 运行时升级至 pydantic-ai 2.54（2026-10-09）

**事前分析**
- 锁定版本为 `pydantic-ai-slim` / `pydantic-evals` / `pydantic-graph` **2.37.0**（2026-09-01）、`fastmcp-slim` **4.0.0**。PyPI 当前稳定版为 **2.54.0**（2026-10-02）与 fastmcp-slim **4.1.0**。
- 中间版本含两处安全修复：2.52 `web_fetch` 对恶意嵌套 HTML 的 CPU/内存消耗（本服务未注册该工具）；2.53 `ConcurrencyLimitedModel` 流式并发槽在取消后不释放（本服务未使用该包装）。升级同时吸收模型档案与框架缺陷修复。
- 对照 2.54 源码：`FunctionToolset(timeout=)` 只覆盖函数工具；`MCPToolset` 只有连接级 `init_timeout` / `read_timeout`。工具调用超时仍由 `_TimeoutToolset` 统一包装。`UsageLimits`、`result.usage` property、`FallbackModel`、`Agent.instrument_all` 的用法未变。
- 明确不引入：`pydantic-ai-harness` / CLAI、durable execution、长期记忆、Human-in-the-loop、历史摘要压缩（仍按 §22.1 / §22.5）。

**事中实施**
- `pyproject.toml`：`pydantic-ai-slim[mcp]>=2.54.0,<3`、`pydantic-evals>=2.54.0,<3`；包版本 `0.5.3`。
- `uv.lock`：上述三包 2.54.0，`fastmcp-slim` 4.1.0，`genai-prices` 0.1.9。执行代码无契约变化。

**事后检查**
- Docker（`ghcr.io/astral-sh/uv:python3.13-bookworm-slim`）pytest **86 passed**。
- 本机 pytest 仍被 Windows 应用控制拦截（`asyncio` / `_overlapped`），与 §22.10 相同。
