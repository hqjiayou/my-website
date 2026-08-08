# website-services CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
source_of_truth: services/website-services
supersedes: 总览/当前功能完成度总览.md（作为完成度权威）
```

## 模块

| 模块 | 状态 | 说明 |
|------|------|------|
| gateway | PARTIAL | 路由/CORS/RequestId OK；已配 `trusted-proxies` + XFF append（生产需收紧；缺集成断言） |
| auth | PARTIAL / BACKEND_COMPLETE(site) | Admin+Site 会话；限流取 XFF 末段 |
| admin | COMPLETE_V1（主域） | Site/User/Template/Appearance/Media/Content/Reservation/RBAC |
| site-app | PARTIAL | Site AI 路径；业务前台仍扩展中 |
| common/infra | COMPLETE_V1 底座 | Flyway V1–V15 |
| agent (Java client) | COMPLETE_V1 path | 诚实失败 |

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
