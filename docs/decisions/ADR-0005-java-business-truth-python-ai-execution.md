# ADR-0005 Java owns business truth, Python owns AI execution

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-09-01
```

## Context

`agent-services` 用 Python（FastAPI + Pydantic AI V2）承载 AI 能力。若 Python 侧再保存会话 / Prompt 配置 / 业务数据，将与 Java + PostgreSQL 形成双真相；若 Python 直连业务库，将绕过 RBAC / 校验 / 事务。

## Decision

1. **Java 持有全部业务真相**：用户、RBAC、Site、Prompt 配置、Conversation/Message、知识库主数据、业务数据。
2. **Python 只做 AI Execution**：模型调用、Agent loop、Tool 调度、RAG retrieval、AI telemetry；business request 之间 **stateless**。
3. `promptProfile.systemPrompt` 为 **Java → Python trusted internal field**，不接受前端直传。
4. Tools 一律回调 Java internal API；Python 禁止直连业务 PostgreSQL。
5. RAG 仅允许独立 `agent_retrieval` derived store（非 SoT）；`site_id` 过滤由 Java trusted context 提供并在 retrieval 层强制注入。
6. 内部安全：`AGENT_INTERNAL_SERVICE_TOKEN` 缺失 → **启动 fail-fast**（安全配置错误）；与 Provider 缺失（BLOCKED，ADR-0004）严格区分。

## Consequences

- Conversation 历史由 Java 加载后随请求传入（Phase 2 起 multi-turn）。
- Provider 保持 neutral（Pydantic AI model spec）；Azure Foundry 可为第一生产 Provider，但不进 Agent Domain Model。
- 契约以 Java `HttpAgentClient` 为准：错误体必须**顶层扁平** `errorCode` / `providerStatus`。
