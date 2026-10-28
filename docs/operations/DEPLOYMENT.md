# DEPLOYMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-28
```

当前仓库以本地/开发运行为主。部署时注意：

1. 仅暴露 **gateway**；auth（:8083）/ admin / agent-app 只在内网，不得把 auth 端口直接映射到公网。  
2. **agent-services 不经 gateway 暴露**；Java `admin` 与 `agent-app` 内网访问。  
3. Flyway 向前迁移；不改已执行脚本。  
4. Auth 私钥与 JWKS 发布流程。  
5. 生产 gateway 启用 **prod profile**：`trusted-proxies` 由环境变量 `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES` 提供前置 LB 对端 IP 的正则（未设置则启动失败，勿用 dev 默认 `.*`）；且网络层须保证 gateway 只能被前置 LB 到达。口径见 [`SECURITY.md`](./SECURITY.md) 的 Client IP / XFF 节。  
6. 关闭开放注册（除非显式 bootstrap）。  

## agent-services（Python AI Runtime）

| 项 | 定型值 |
|----|--------|
| 镜像 | `services/agent-services/Dockerfile`（Python 3.13-slim + uv） |
| 端口 | `3001`（`PORT`） |
| 网络 | 内网；`admin` 与 `site-app` 经 `HttpAgentClient` 调用 |
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

Java `admin` 与 `site-app` 配置：`AGENT_BASE_URL=http://<agent-host>:3001`，`AGENT_INTERNAL_SERVICE_TOKEN` 与上相同。

## 分支与 CI

日常集成分支是 `dev`，发版把 `dev` 合并到 `main`。不要把日常提交直接推到 `main`。GitHub 默认分支应为 `dev`。

约定是经 Pull Request 合入 `dev`，发版再合入 `main`，并看对应检查。私有仓库在当前 GitHub 方案下不能开启分支保护（需要 Pro，或把仓库改为 public），所以这项约定现在还不能在服务器上强制。

Actions 有两种触发。指向 `dev` / `main` 的 Pull Request 按整个 PR 的改动匹配路径。`push` 按本次提交改到的路径匹配，功能分支还没开 PR 时也会跑。同一条分支上新的一次运行会取消该事件里尚未结束的上一次。Actions 页也可以手动跑这三条工作流。

| 工作流 | 路径 | 检查 |
|--------|------|------|
| `.github/workflows/backend-ci.yml` | `services/website-services/**` | Java 快速测试 + PostgreSQL 契约测试 |
| `.github/workflows/agent-services-ci.yml` | `services/agent-services/**` 或 `services/website-services/agent/**` | pytest，然后 Java 契约 IT 与 docker build 并行 |
| `.github/workflows/admin-web-ci.yml` | `apps/admin-web/**` | `pnpm lint`、导入检查、`pnpm build` |
