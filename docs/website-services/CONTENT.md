# CONTENT

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

站点内容在 admin：`AdminContentController` `/api/admin/sites/{siteId}/contents`，含 CRUD 与 status。表是 `site_content`，不是蓝图里的 `content_article`。类型与状态见《数据库模型设计 V1》文首。

封面只存 `cover_media_id`，必须同站且媒体为 READY。

运营「内容中心」没有单独的跨站后端。`apps/admin-web` 的 `listOperationContents` 把各站 REAL API 在客户端聚合，`OPERATION_CONTENTS_CONNECTED = true`。
