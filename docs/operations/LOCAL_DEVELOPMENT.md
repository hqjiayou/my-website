# LOCAL_DEVELOPMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-09-01
```

## 后端

详见 [`../../services/website-services/README.md`](../../services/website-services/README.md)（`.env`、端口、启动顺序）。

推荐外部入口：**gateway :8080**。

WSL/Docker：`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`。

### agent-services（与 site-app 联调）

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 配置 AGENT_INTERNAL_SERVICE_TOKEN（必需）
uv run agent-services  # :3001
```

`services/website-services/.env` 对齐：

- `AGENT_BASE_URL=http://127.0.0.1:3001`
- `AGENT_INTERNAL_SERVICE_TOKEN` 与 Python 侧相同

无 `AI_MODEL` 时 health 为 BLOCKED、chat 返回 503（预期行为，ADR-0004）。

契约集成测试（可选，需 uv）：

```bash
# website-services
AGENT_PYTHON_CONTRACT_IT=true mvn -pl agent test -Dtest=HttpAgentClientPythonContractIT
```

## 前端 admin-web

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

核心域、运营聚合与站点 AI 管理 FORCE REAL——本地需可用的 gateway/auth/admin。admin-web 不读取 `VITE_USE_MOCK`。

Phase 3/4 可选：

- `RETRIEVAL_DATABASE_URL`：独立 `agent_retrieval` 库（专用 DB 用户，禁止读 Java 业务表）
- `AI_EMBEDDING_MODEL`：嵌入模型 spec
- `BUSINESS_API_BASE_URL`：site-app internal 根地址（默认开发 `http://127.0.0.1:8082`），**不经 gateway**

## site-web

各子应用独立 README；认证对接尚未完成。
