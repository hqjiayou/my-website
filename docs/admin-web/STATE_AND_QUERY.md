# admin-web STATE_AND_QUERY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

- **Server state**：TanStack Query（各页 `queryKey` 尚未完全统一——非阻断）。  
- **Client state**：Zustand（auth、preferences、layout）。  
- 偏好：theme / font / density / language / sidebar → `config/preferences` + store；**后端持久化未实现**。  
- 写成功后 `invalidateQueries` 对应列表 key（如 `['sites']`）。  
