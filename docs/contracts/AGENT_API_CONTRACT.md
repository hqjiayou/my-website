# AGENT_API_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-26
```

前缀：`/api/agent/**`（业务）+ `/api/auth/agent/**`（认证）。

| 能力 | 状态 |
|------|------|
| Site Auth login/refresh/logout/me | **BACKEND_COMPLETE** |
| site-web 对接 | **IN_TREE**（`apps/site-web` 只接下方匿名公开内容；Site Auth 前端仍未接） |
| Site AI chat/stream | **BACKEND_COMPLETE**（会话落库；无 `AI_MODEL` 时诚实 503）。SSE 对终端用户下发 `START`(`{}`) / `TEXT_DELTA` / `THINKING`(`delta`，provider 产出思考片段时才有) / `TOOL_START`(`toolName`,`toolCallId`) / `TOOL_RESULT`(`toolName`,`toolCallId`,`ok`；知识搜索另带安全摘要 `summary{query,hitCount,sources[]}`，不带片段正文) / `DONE`(`reply`,`providerStatus`,`citations`——仅本轮实际采用知识库 grounding 时出现，每项 `sourceId`/`chunkIndex`/`score`) / `CONVERSATION`(`conversationId`) / `ERROR`(`errorCode` ∈ `EXTERNAL_DEPENDENCY_BLOCKED`·`MCP_UNAVAILABLE`·`AGENT_BUDGET_EXCEEDED`·`SYSTEM_ERROR`)；工具返回正文、用量、skills、原始错误信息不下发。知识检索由 agent 经 `search_site_knowledge` 工具在 run 内执行，runtime 不再预取注入（Java `knowledgeContext` 片段仅在 Python 检索服务缺失时作兜底）。请求可带 `attachments`（`{mediaType,data}`，`image/jpeg|image/png|image/webp|image/gif`，每条 ≤3、单张解码后 ≤2MiB、合计 ≤5MiB）；请求另可带 `documents`（`{fileName,mediaType,data}`，扩展名限 `pdf|docx|xlsx|pptx|txt|md`，≤3 份、单份解码后 ≤10MiB）：站点层抽取纯文本（超 60,000 字截断并标 `truncated`）后作为本轮文档上下文经 internal 契约 `documents[]{name,text,truncated}` 交 agent，原文不落库；抽取失败或无可抽取文本（如扫描版 PDF，聊天路径不做 OCR）以 `BAD_REQUEST` 拒绝整轮。`message` 可空白仅当 attachments 或 documents 非空。历史消息带回 `attachments`；文档轮的 attachments 只含描述项（`kind=DOCUMENT`、`fileName`、派生 `mediaType`、无 `data`），历史回放不重送文档。消息列表 DTO 的 ASSISTANT 项另含 `citations`（`sourceId`/`chunkIndex`/`score`；无引用或 `agentMeta` 无法解析时为 null）与 `process` 过程摘要（`tools[]{name,ok}`＋`searches[]{query,hitCount}`，自 `agentMeta` 回放，与实时事件同一白名单；无过程记录时为 null，旧消息只有 `tools`）。请求可带 `mode`（`KEY` 直连 API key / `CLI` 经 CLI 代理池；空 = 不分模式整链）：带 mode 时服务端按模式过滤入口组链（主用 = 该模式默认入口，备援封闭在模式内，模式内无默认即整链不带、回退进程模型），非法值 `BAD_REQUEST`。`GET /{agentId}/ai/model-modes` 返回两种模式的可用性（该模式链非空）与主用模型名 `label`，供 Portal 双模式切换呈现。会话管理见下 |
| 访客内容公开 API | 内容公开读见下方「公开内容」。预约功能已下架（2026-10-27，V30）：无公开、管理或 internal 预约端点，internal 仅剩站点资料与已发布内容读取 |

Audience 必须为 SITE；Admin token 不可调用 Site 受保护资源（反之亦然）。

### Site AI 会话

与 `GET /api/agent/{siteId}/ai/conversations` 同级。不存在或不属于当前站点用户时为 NOT_FOUND，且不删除任何数据。

| 方法 | 路径 | 请求 / 响应 |
|------|------|-------------|
| PATCH | `/api/agent/{siteId}/ai/conversations/{conversationId}` | 请求体 `title`（非空白，最长 60）；成功返回更新后的会话 |
| DELETE | `/api/agent/{siteId}/ai/conversations/{conversationId}` | 无请求体；成功时 `data` 为 null |
| DELETE | `/api/agent/{siteId}/ai/conversations/{conversationId}/messages/{messageId}` | 硬删该消息及其之后全部消息（编辑/重新生成的真截断）；会话或消息不存在/非本人为 NOT_FOUND；截断后 `lastMessageAt` 回退、Todo 快照清空；成功时 `data` 为 null |
| POST | `/api/agent/{siteId}/ai/conversations/{conversationId}/share` | 创建或刷新分享（每会话至多一条；已有则复用 token 并把快照更新为当前内容）；快照只含消息正文与附件计数/文件名，不含图片数据、引用与过程；返回 `token` |
| GET | `/api/agent/{siteId}/ai/conversations/{conversationId}/share` | 当前分享的 `token`；未分享时 `data` 为 null |
| DELETE | `/api/agent/{siteId}/ai/conversations/{conversationId}/share` | 撤销分享（物理删行，token 立即失效）；成功时 `data` 为 null |
| GET | `/api/agent/public/ai/shares/{token}` | **公开**（permitAll，仅 GET）：凭 token 读分享快照（`title`/`sharedAt`/`messages[]{role,content,createdAt,imageCount,documentNames}`）；token 不存在为 NOT_FOUND。会话硬删时分享行级联删除、链接失效 |
| GET | `/api/agent/{siteId}/ai/search?q=` | 跨会话全文搜索（本站点＋本人作用域）：消息正文 ILIKE 匹配（`%`/`_` 已转义），按消息 id 倒序最多 30 条；每条含 `conversationId`/`conversationTitle`/`messageId`/`role`/`snippet`（命中词 ±30 字）/`createdAt`；空白查询返回空数组 |

### Site AI 记忆

`GET /api/agent/{siteId}/ai/memories` 返回当前用户记忆（`id`、`content`、`createdAt`，时间倒序）；`DELETE /api/agent/{siteId}/ai/memories/{memoryId}` 删除自己的一条（非本人 NOT_FOUND，成功 `data` 为 null）；`DELETE /api/agent/{siteId}/ai/memories` 清空自己的（成功 `data` 为 null）。每用户最多 50 条，超出时先删最旧再写入。

站点 chat 与 chat/stream：服务端注入该用户最近 20 条记忆，并在成功回复后把本轮抽取结果去重落库；抽取为空或缺失时不写入。终端 SSE 不下发抽取结果。

### 公开内容

匿名，只允许 GET。站点不存在或非 ACTIVE 为 404。内容只回 PUBLISHED；不存在、不属于该站或未发布一律 404。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/public/{siteCode}` | 匿名。站点摘要：siteCode、siteName、siteType、description |
| GET | `/api/agent/public/{siteCode}/contents` | 匿名。只回 PUBLISHED 列表：id、type、title、coverMediaId、publishedAt（不含 body） |
| GET | `/api/agent/public/{siteCode}/contents/{contentId}` | 匿名。只回该站 PUBLISHED 详情（含 body） |
