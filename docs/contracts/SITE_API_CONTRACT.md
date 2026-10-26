# SITE_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-24
```

前缀：`/api/site/**`（业务）+ `/api/auth/site/**`（认证）。

| 能力 | 状态 |
|------|------|
| Site Auth login/refresh/logout/me | **BACKEND_COMPLETE** |
| site-web 对接 | **IN_TREE**（`apps/site-web` 只接下方匿名公开内容；Site Auth 前端仍未接） |
| Site AI chat/stream | **BACKEND_COMPLETE**（会话落库；无 `AI_MODEL` 时诚实 503）。SSE 对终端用户只下发 `START`(`{}`) / `TEXT_DELTA` / `DONE`(`reply`,`providerStatus`,`citations`——仅本轮实际采用知识库 grounding 时出现，每项 `sourceId`/`chunkIndex`/`score`) / `CONVERSATION`(`conversationId`) / `ERROR`(`errorCode` ∈ `EXTERNAL_DEPENDENCY_BLOCKED`·`MCP_UNAVAILABLE`·`AGENT_BUDGET_EXCEEDED`·`SYSTEM_ERROR`)；工具事件、用量、skills、原始错误信息不下发。请求可带 `attachments`（`{mediaType,data}`，`image/jpeg|image/png|image/webp|image/gif`，每条 ≤3、单张解码后 ≤2MiB、合计 ≤5MiB）；`message` 可空白仅当 attachments 非空。历史消息带回 `attachments`。会话管理见下 |
| 访客内容 / 预约公开 API | 内容公开读见下方「公开内容」。预约公开 API 仍没有。管理端在 `/api/admin/sites/{id}/contents|reservations`。Agent 建预约走 `POST /internal/site/v1/sites/{siteId}/reservations` |

Audience 必须为 SITE；Admin token 不可调用 Site 受保护资源（反之亦然）。

### Site AI 会话

与 `GET /api/site/{siteId}/ai/conversations` 同级。不存在或不属于当前站点用户时为 NOT_FOUND，且不删除任何数据。

| 方法 | 路径 | 请求 / 响应 |
|------|------|-------------|
| PATCH | `/api/site/{siteId}/ai/conversations/{conversationId}` | 请求体 `title`（非空白，最长 60）；成功返回更新后的会话 |
| DELETE | `/api/site/{siteId}/ai/conversations/{conversationId}` | 无请求体；成功时 `data` 为 null |

### 公开内容

匿名，只允许 GET。站点不存在或非 ACTIVE 为 404。内容只回 PUBLISHED；不存在、不属于该站或未发布一律 404。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/site/public/{siteCode}` | 匿名。站点摘要：siteCode、siteName、siteType、description |
| GET | `/api/site/public/{siteCode}/contents` | 匿名。只回 PUBLISHED 列表：id、type、title、coverMediaId、publishedAt（不含 body） |
| GET | `/api/site/public/{siteCode}/contents/{contentId}` | 匿名。只回该站 PUBLISHED 详情（含 body） |
