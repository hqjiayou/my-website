# ADMIN_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
```

前缀：`/api/admin/**`（经 gateway）。均需 Admin JWT + 对应 permission（`@PreAuthorize`）。

| 域 | 主要路径 | 前端 client |
|----|----------|-------------|
| Me / Menus | `GET /auth/me`、`GET /menus`（无菜单写） | `rbacApi` |
| Roles / Permissions | `/roles` CRUD 与权限绑定；`GET /permissions`（不在后台创建权限码） | `rbacApi` |
| Users | `/users`、`/users/{id}/roles`… | `rbacApi`（**不是** legacy `userApi`） |
| Sites | `/sites` CRUD + `/status` | `siteApi` FORCE REAL |
| Templates | `/templates` list/get/by-code/status | `templateApi` |
| Appearance | `/sites/{id}/appearance` GET/PUT | `appearanceApi` |
| Media | `/sites/{id}/media` | `mediaApi` |
| Contents | `/sites/{id}/contents` | `contentApi` |
| Reservations | `/sites/{id}/reservations` | `reservationApi` |
| AI Model Entry | `/ai/model-entries`（平台级，不按站点）GET/POST、`/{id}` PUT/DELETE。响应不含 `apiKey`，只有 `apiKeyConfigured` 与 `apiKeyHint`；更新时 `apiKey` 留空保留原密钥。权限 `admin:ai-model:read` / `admin:ai-model:update`。启用且 `defaultEntry` 的那一条由 Java 注入站点对话与预览；没有则 Python 回退 `AI_MODEL` | `aiApi` FORCE REAL |
| AI Prompt | `/sites/{id}/ai/prompt-profile` | `aiApi` FORCE REAL |
| AI Knowledge | `/sites/{id}/ai/knowledge-documents` | `aiApi` FORCE REAL |
| AI Conversations | `/sites/{id}/ai/conversations`（只读）；`.../{cid}/messages` 的 ASSISTANT 项含 `agentMeta{usage{requests,toolCalls,inputTokens,outputTokens}, tools[{name,ok}], skillsApplied[]}`（无 / 历史数据 / 损坏 → null） | `aiApi` FORCE REAL |
| AI Preview SSE | `POST /sites/{id}/ai/preview/chat/stream`（不落库）；body `{message, history?, skills?(≤16), todos?(≤20)}`；事件 `START`(`skillsApplied`/`skillsMissing`) / `TEXT_DELTA` / `TOOL_START` / `TOOL_RESULT`(`ok`) / `DONE`(`todos`/`usage`/`tools`) / `ERROR`（含 `AGENT_BUDGET_EXCEEDED`） | `streamPreviewChat` |
| AI Preview Skills | `GET /sites/{id}/ai/preview/skills`（`admin:ai-prompt:read`；agent-services 不可用 → 503） | `aiApi.listPreviewSkills` |
| AI Runtime | `GET /sites/{id}/ai/runtime`（`admin:ai-prompt:read`）→ `{reachable, errorCode, status{provider, providerStatus, fallbackProviders[], mcpStatus, mcpServers[{name,enabled}], limits{maxRequests,maxToolCalls,maxTotalTokens,toolTimeoutSeconds,historyMaxChars}, retrievalStatus, retrievalBlockedReason, harness{background,guardrails,toolGuidance}}`；`harness` 只表示三个平台文件是否存在，旧版未回报时为全 false；不可达仍 200 且 `reachable=false`；不含凭证 / URL | `aiApi.getRuntime` |
| AI MCP ready | `GET /sites/{id}/ai/mcp-ready`（同权限，手动深检）→ `{reachable, errorCode, probe{ok, mcpStatus, servers[{name,ok,toolCount,error}], errorCode, message}}`；agent-services 不可达仍 200 且 `reachable=false`；MCP 不通时 `reachable=true` 且 `probe.ok=false`；不含 URL | `aiApi.probeMcp` |

细节字段以各 `Admin*Dtos` 与 admin-web `api/**` 为准。分页见 [`PAGINATION_CONTRACT.md`](./PAGINATION_CONTRACT.md)。
