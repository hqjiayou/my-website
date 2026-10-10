# PAGINATION_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
```

常用查询参数：`pageNum`、`pageSize`（1-based pageNum）。

响应 `PageResult<T>`：

- `records`  
- `total`  
- `pageNum`  
- `pageSize`  

部分列表若仍返回裸 `List`，以该 Controller 为准。新增列表用 `PageResult`。Java 类是 `com.mywebsite.common.page.PageResult`，字段与上表一致。站点列表默认 `pageSize` 20，上限 100（`AdminSiteConstants`）。
