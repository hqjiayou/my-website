# website-services 文档总入口

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

**当前完成度权威**：[`CURRENT_STATE.md`](./CURRENT_STATE.md) 与全栈 [`../overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md)。

运行命令与 `.env`：[`../../services/website-services/README.md`](../../services/website-services/README.md)。

---

## 阅读顺序（Agent / 开发）

1. [`CURRENT_STATE.md`](./CURRENT_STATE.md)  
2. [`ARCHITECTURE.md`](./ARCHITECTURE.md) → 基础架构 SPEC  
3. 域摘要：[`AUTH.md`](./AUTH.md) [`RBAC.md`](./RBAC.md) [`SITE.md`](./SITE.md) [`MEDIA.md`](./MEDIA.md) [`CONTENT.md`](./CONTENT.md) [`RESERVATION.md`](./RESERVATION.md) [`PERSISTENCE.md`](./PERSISTENCE.md)  
4. 契约：[`../contracts/`](../contracts/)  
5. 包结构硬约束：[`设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](./设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md)

---

## 目录

| 路径 | 角色 |
|------|------|
| `CURRENT_STATE.md` / 域 `*.md` | CURRENT 摘要 |
| `总览/` | 架构总览、模块边界（完成度/路线图已 stub→overview） |
| `设计文档/` | 长期 SPEC（基础架构、数据、认证、RBAC） |
| `数据字典/` | 表级字典 |
| `数据文档/` | 持久层验证 RUNBOOK（阶段核验已归档） |
| `归档/` | 本树内旧归档说明 |
| `../archive/website-services/` | 联调 / 作業検査 / 过时总览 |

历史阶段联调与审查记录**不是**当前 SoT。
