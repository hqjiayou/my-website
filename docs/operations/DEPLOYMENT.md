# DEPLOYMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-09-01
```

当前仓库以本地/开发运行为主。部署时注意：

1. 仅暴露 **gateway**；auth / admin / site 内网。  
2. **agent-services 不经 gateway 暴露**；仅 Java `site-app` 内网访问。  
3. Flyway 向前迁移；不改已执行脚本。  
4. Auth 私钥与 JWKS 发布流程。  
5. `trusted-proxies` 配真实前置 LB CIDR。  
6. 关闭开放注册（除非显式 bootstrap）。  

## agent-services（Python AI Runtime）

| 项 | 定型值 |
|----|--------|
| 镜像 | `services/agent-services/Dockerfile`（Python 3.13-slim + uv） |
| 端口 | `3001`（`PORT`） |
| 网络 | 内网；调用方仅为 `site-app` 的 `HttpAgentClient` |
| Liveness | `GET /health`（无鉴权） |
| Readiness | `GET /internal/v1/health` + `X-Internal-Service-Token`；`providerStatus=LIVE` 方可接 chat |
| 必需密钥 | `AGENT_INTERNAL_SERVICE_TOKEN`（与 Java `AGENT_INTERNAL_SERVICE_TOKEN` 一致） |
| Provider | `AI_MODEL` + 各 Provider 凭证；缺失 → BLOCKED（503），**不**阻止进程启动 |
| RAG | `RETRIEVAL_DATABASE_URL` + `AI_EMBEDDING_MODEL`；缺失则检索 BLOCKED，纯问答仍可用 |
| Tools | `BUSINESS_API_BASE_URL` 指向 site-app **internal**（不经 gateway）；缺失则不注册业务工具 |
| Skills / Todo | `AGENT_DEFAULT_SKILLS` / `AGENT_ENABLE_TODO_TOOLS`；todo 状态经 chat 响应 `todos` 回传 |
| MCP | `MCP_SERVERS_JSON`；内网 URL；`async with agent` 管理连接 |
| 观测 | `OTEL_EXPORTER_OTLP_ENDPOINT` 设置后启用 OTLP HTTP trace |

### 最小运行示例

```bash
docker build -t agent-services:prod services/agent-services
docker run -d --name agent-services \
  -p 3001:3001 \
  -e AGENT_INTERNAL_SERVICE_TOKEN="$(openssl rand -hex 32)" \
  -e AI_MODEL=openai:gpt-4o-mini \
  -e AI_PROVIDER_API_KEY=sk-... \
  -e BUSINESS_API_BASE_URL=http://site-app:8082 \
  agent-services:prod
```

`BUSINESS_API_BASE_URL` 必须指向 site-app 内网地址（`/internal/**`），不要走 gateway。

检索库若启用：另设 `RETRIEVAL_DATABASE_URL`（专用用户，仅 `agent_retrieval` schema）。

Java `site-app` 配置：`AGENT_BASE_URL=http://<agent-host>:3001`，`AGENT_INTERNAL_SERVICE_TOKEN` 与上相同。

CI：`.github/workflows/agent-services-ci.yml`（pytest + Java 契约 IT + docker build）。
