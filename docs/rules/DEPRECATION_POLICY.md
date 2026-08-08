# DEPRECATION_POLICY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

## 文档

- 过时事实文档：顶部加 `Status: SUPERSEDED` + `Superseded by:`，移入 `archive/` 或 `implementation-history/`。  
- **不得**通过删除文档抹掉未解决问题。  
- **不得**改写历史 AUDIT / EXECUTION_REPORT 的 PASS/FAIL。  

## 代码

删除前必须搜索：imports、exports、dynamic import、routes、tests、docs、scripts、`.cursor` / AGENTS。  

无法证明无消费者 → **不删**，开 GitHub Issue。  

## 已知退役名（勿复活）

| 旧名 | 当前名 |
|------|--------|
| Reservation `COMPLETED` | `DONE` |
| Appearance `heroStyle` | `heroLayout` |
| Appearance `logoPlaceholder` | （v1 无此字段） |
| `operation/userApi` → `/site/users` | `rbacApi` 用户管理 |
| 统一 `USE_MOCK` 业务 API 模式 | 核心域 FORCE REAL |
