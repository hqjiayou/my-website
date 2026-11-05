# MEDIA

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-11-05
```

`AdminMediaController`：`/api/admin/agents/{agentId}/media` 的 list、upload、detail、preview、delete，以及平台化（#212 / V57）新增的 `POST /{id}/reference`、`DELETE /{id}/reference`、`PUT /{id}/scope`。object key 由服务端生成（ADR-0003）。MIME 以服务端检测为准。

归属语义（V57 起）：媒体文件归平台，`media_file.site_id` 为上传来源站留痕（必填、非归属）。可见范围由 `media_file.scope` 决定：`AGENT`（默认，存量行为不变）仅来源站与引用站可见；`PLATFORM` 平台内任意站可见。文件对某站可见 ⇔ 该站为来源站 ∨ `scope = PLATFORM` ∨ 存在 `media_reference(media_id, agent_id)` 引用行；不可见一律按不存在处理（404，不泄露归属）。

列表 `GET /` 带 `view` 参数：`library`（默认）＝来源站文件＋本站已引用文件；`platform`＝全部 `PLATFORM` 文件。列表项带 `scope` 与 `referenced`（是否经引用可见），详情带 `scope` 与 `referenceCount`（被引用站数）。

引用：`POST /{id}/reference` 为本站建引用行，文件须经 `PLATFORM` 对本站可见（来源站本人、重复引用均为幂等成功；`AGENT` 私有且非来源站 → 404）。引用只建关系行、不复制对象，对象始终只存一份。`DELETE /{id}/reference` 删本站引用（来源站本人 → 400；无引用 → 404）。`PUT /{id}/scope` 仅来源站可改（他人 404），取值仅 `AGENT` / `PLATFORM`（非法 → 400）。

删除拦截顺序：先既有内容引用拦截（`cover_media_id` → 409 `admin.media.referenced-by-content`），再跨站引用拦截（存在他人 `media_reference` → 409 `admin.media.referenced-by-agents`）。删站级联：站点软删时清掉指向其文件的全部引用行与该站发出的全部引用行，避免死站引用永久阻塞他站文件删除。

V13 `object_purged_at` 用来标记对象已从存储清掉，避免软删行反复进入清理批次。

运营「文件中心」同样是客户端聚合各站 media API（`OPERATION_FILES_CONNECTED = true`），没有单独的跨站后端。
