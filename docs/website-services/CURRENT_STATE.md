# website-services CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-25
source_of_truth: services/website-services
supersedes: 总览/当前功能完成度总览.md（作为完成度权威）
```

## 模块

| 模块 | 状态 | 说明 |
|------|------|------|
| gateway | PARTIAL | 路由/CORS/RequestId OK；已配 `trusted-proxies` + XFF append（生产需收紧；缺集成断言） |
| auth | PARTIAL / BACKEND_COMPLETE(site) | Admin+Site 会话；停用或已删账号的 Refresh 吊销；限流取 XFF 末段 |
| admin | COMPLETE_V1（主域）+ 站点 AI 管理 | Site/User/Template/Appearance/Media/Content/Reservation/RBAC；删站点同时软删 Prompt/KB/会话并 best-effort 清检索索引；平台模型入口 `ai_model_entry`（`/api/admin/ai/model-entries`，响应不含 key 正文；启用的默认入口注入预览）；Prompt/KB/会话/预览（预览可传 `skills`/`todos`，`locale` 取当前请求 `Accept-Language`（缺省 `ja-JP`），`GET .../ai/preview/skills` 代理技能目录；会话消息含 `agentMeta`；`GET .../ai/runtime` Agent 运行时快照（含 `harness` 三个平台文件是否存在，旧版全 false）；`GET .../ai/mcp-ready` 手动 MCP 深检；KB 纯文本上传 `POST .../ai/knowledge-documents/upload`，仅 `.txt`/`.md`、UTF-8、≤1MB，走 `createDocument`） |
| site-app | PARTIAL | Site AI chat/stream（启用的默认模型入口随请求注入，没有则 Python 回退 `AI_MODEL`；入口构建成功后挂进程 `AI_FALLBACK_MODELS`（仅 Provider API 错误时切换，备用凭证不使用入口 apiKey），入口本身 BLOCKED 时不顶替；送模历史按字符预算裁剪，超预算时把被裁掉的前缀摘要进系统提示，摘要失败则只裁剪，不回写会话，未做摘要落库与跨轮复用；本地 profile 把 `businessApiBaseUrl` 指到本机 site-app，对话可调站点资料读取和建预约，Compose 用 `AGENT_BUSINESS_API_BASE_URL=http://site:8082`；向量索引没有命中时，用知识库原文子串匹配片段；会话级 Todo 快照 `site_ai_conversation.agent_todos` 跨轮回传；ASSISTANT 消息落 `site_ai_message.agent_meta` 运行元数据（V21，不对终端用户返回）；SSE 对终端用户白名单重写（只含回复文本、`citations` 与错误码，工具轨迹/用量/原始错误不下发）；非流式 chat 同步返回 `citations`；终端用户不可指定 skills；`locale` 同样取请求 `Accept-Language`；`POST /{siteId}/ai/knowledge/search` 知识检索：余弦低于 `RETRIEVAL_MIN_SCORE`（默认 0.35）的向量命中丢弃，`0` 关闭，小于 0、大于 1 或非数字则 Python 启动失败；代码型 token 如 `XJ-900` 在嵌入与向量查询成功后按 site_id 做字面量匹配，分数取余弦与覆盖率的较大值；未做中文/日文专有名词词法、BM25、rerank）；internal Tools（建预约可读 `Idempotency-Key`，命中则回既有单）；业务前台仍扩展中 |
| common/infra | COMPLETE_V1 底座 | Flyway 当前至 V24（`ai_model_entry` 可按站点覆盖全站默认，可选 embedding spec；api_key 在配置了 `AI_MODEL_ENTRY_SECRET` 时 AES-GCM 存储） |
| agent (Java client) | COMPLETE_V1 contract | 扁平错误体（`MCP_UNAVAILABLE` → `EXTERNAL_DEPENDENCY_BLOCKED`；`AGENT_BUDGET_EXCEEDED` → ErrorCode 1008）；`runMeta`/`runtimeStatus`；SSE 转发；`listSkills()`；`searchRetrieval` → `POST /internal/v1/retrieval/search`；chat 成功体含 `citations`；契约 IT（含预算/MCP 短进程用例，CI 启用） |

## 域完成度（后端）

与 [`../overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md) 一致。特别强调：

- **Site Auth = BACKEND_COMPLETE**（旧「Site 登录未开始」文档已过时）  
- **Menu 写 = DEFERRED_BY_DESIGN**  
- **Reservation status = DONE**（非 COMPLETED）  
- **Appearance = heroLayout**  

## 仍有效的 SPEC（勿归档）

- `设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`  
- `设计文档/基础架构/后端包结构与命名规范V1.md`  
- `设计文档/基础架构/架构设计.md`  
- `设计文档/数据与持久层/*`（与 Flyway 冲突时以 Flyway 为准）  
- `设计文档/权限与后台管理/阶段5A–5E*`（5E 写操作仍 deferred）  
- `数据字典/*`  

阶段联调记录、作業検査、过时完成度总览 → `docs/archive/` / `implementation-history/`。
