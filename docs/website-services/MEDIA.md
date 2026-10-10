# MEDIA

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

`AdminMediaController`：`/api/admin/sites/{siteId}/media` 的 list、upload、detail、preview、delete。object key 由服务端生成（ADR-0003）。MIME 以服务端检测为准。跨站访问按不存在处理。

`media_file.site_id` 必填。V13 `object_purged_at` 用来标记对象已从存储清掉，避免软删行反复进入清理批次。

运营「文件中心」同样是客户端聚合各站 media API（`OPERATION_FILES_CONNECTED = true`），没有单独的跨站后端。  
