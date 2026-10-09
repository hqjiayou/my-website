# ADMIN_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
```

前缀：`/api/admin/**`（经 gateway）。均需 Admin JWT + 对应 permission（`@PreAuthorize`）。

| 域 | 主要路径 | 前端 client |
|----|----------|-------------|
| Me / Menus | `/auth/me`、`/menus` | `rbacApi` |
| Roles / Permissions | `/roles`、`/permissions` | `rbacApi` |
| Users | `/users`、`/users/{id}/roles`… | `rbacApi`（**不是** legacy `userApi`） |
| Sites | `/sites` CRUD + `/status` | `siteApi` FORCE REAL |
| Templates | `/templates` list/get/by-code/status | `templateApi` |
| Appearance | `/sites/{id}/appearance` GET/PUT | `appearanceApi` |
| Media | `/sites/{id}/media` | `mediaApi` |
| Contents | `/sites/{id}/contents` | `contentApi` |
| Reservations | `/sites/{id}/reservations` | `reservationApi` |
| AI Prompt | `/sites/{id}/ai/prompt-profile` | `aiApi` FORCE REAL |
| AI Knowledge | `/sites/{id}/ai/knowledge-documents` | `aiApi` FORCE REAL |
| AI Conversations | `/sites/{id}/ai/conversations`（只读）；`.../{cid}/messages` 的 ASSISTANT 项含 `agentMeta{usage{requests,toolCalls,inputTokens,outputTokens}, tools[{name,ok}], skillsApplied[]}`（无 / 历史数据 / 损坏 → null） | `aiApi` FORCE REAL |
| AI Preview SSE | `POST /sites/{id}/ai/preview/chat/stream`（不落库）；body `{message, history?, skills?(≤16), todos?(≤20)}`；事件 `START`(`skillsApplied`/`skillsMissing`) / `TEXT_DELTA` / `TOOL_START` / `TOOL_RESULT`(`ok`) / `DONE`(`todos`/`usage`/`tools`) / `ERROR`（含 `AGENT_BUDGET_EXCEEDED`） | `streamPreviewChat` |
| AI Preview Skills | `GET /sites/{id}/ai/preview/skills`（`admin:ai-prompt:read`；agent-services 不可用 → 503） | `aiApi.listPreviewSkills` |
| AI Runtime | `GET /sites/{id}/ai/runtime`（`admin:ai-prompt:read`）→ `{reachable, errorCode, status{provider, providerStatus, fallbackProviders[], mcpStatus, mcpServers[{name,enabled}], limits{maxRequests,maxToolCalls,maxTotalTokens,toolTimeoutSeconds,historyMaxChars}}}`；不可达仍 200 且 `reachable=false`；不含凭证 / URL | `aiApi.getRuntime` |
| AI MCP ready | `GET /sites/{id}/ai/mcp-ready`（同权限，手动深检）→ `{reachable, errorCode, probe{ok, mcpStatus, servers[{name,ok,toolCount,error}], errorCode, message}}`；agent-services 不可达仍 200 且 `reachable=false`；MCP 不通时 `reachable=true` 且 `probe.ok=false`；不含 URL | `aiApi.probeMcp` |

细节字段以各 `Admin*Dtos` 与 admin-web `api/**` 为准。分页见 [`PAGINATION_CONTRACT.md`](./PAGINATION_CONTRACT.md)。
