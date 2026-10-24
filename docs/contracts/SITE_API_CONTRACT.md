# SITE_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
```

前缀：`/api/site/**`（业务）+ `/api/auth/site/**`（认证）。

| 能力 | 状态 |
|------|------|
| Site Auth login/refresh/logout/me | **BACKEND_COMPLETE** |
| site-web 对接 | **NOT_IN_TREE**（工作区无前台应用） |
| Site AI chat/stream | **BACKEND_COMPLETE**（会话落库；无 `AI_MODEL` 时诚实 503）。SSE 对终端用户只下发 `START`(`{}`) / `TEXT_DELTA` / `DONE`(`reply`,`providerStatus`,`citations`——仅本轮实际采用知识库 grounding 时出现，每项 `sourceId`/`chunkIndex`/`score`) / `CONVERSATION`(`conversationId`) / `ERROR`(`errorCode` ∈ `EXTERNAL_DEPENDENCY_BLOCKED`·`MCP_UNAVAILABLE`·`AGENT_BUDGET_EXCEEDED`·`SYSTEM_ERROR`)；工具事件、用量、skills、原始错误信息不下发 |
| 访客内容 / 预约公开 API | **没有**。管理端在 `/api/admin/sites/{id}/contents|reservations`。Agent 建预约走 `POST /internal/site/v1/sites/{siteId}/reservations` |

Audience 必须为 SITE；Admin token 不可调用 Site 受保护资源（反之亦然）。
