# API_CHANGE_POLICY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

1. 改 API 前先读 `docs/contracts/` 与现有 Controller/DTO/前端 consumer。  
2. 字段名、枚举、路径、错误码以代码为准同步 contracts。  
3. 破坏性变更：先扩展、再迁移 consumer、再删除旧字段；禁止静默改枚举语义（如 `DONE` ↔ `COMPLETED`）。  
4. 新权限码必须有 Flyway 种子 + `@PreAuthorize` + 前端映射（若有 UI）。  
5. 禁止修改已执行 Flyway；新变更新增 migration。  
