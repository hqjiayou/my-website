# PAGINATION_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
```

常用查询参数：`pageNum`、`pageSize`（1-based pageNum）。

响应 `PageResult<T>`：

- `records`  
- `total`  
- `pageNum`  
- `pageSize`  

部分旧/简单列表 API 可能返回裸 `List`（如部分用户列表）——以具体 Controller 为准；新增列表优先 `PageResult`。
