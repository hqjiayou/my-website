# website-services 文档

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-10
```

全库分格见 [`../README.md`](../README.md)。本目录只列后端文件落在哪一格。运行命令在 [`../../services/website-services/README.md`](../../services/website-services/README.md)，不在这里复制一份。

## 现在

[`CURRENT_STATE.md`](./CURRENT_STATE.md) 以及域摘要 [`AUTH.md`](./AUTH.md) [`RBAC.md`](./RBAC.md) [`SITE.md`](./SITE.md) [`MEDIA.md`](./MEDIA.md) [`CONTENT.md`](./CONTENT.md) [`RESERVATION.md`](./RESERVATION.md) [`PERSISTENCE.md`](./PERSISTENCE.md)。

完成度不要读 `总览/当前功能完成度总览.md` 或 `总览/后端开发路线图与阶段状态.md`。

## 规格

| 问题 | 文件 |
|------|------|
| Maven 依赖 | [`总览/模块职责与依赖边界.md`](./总览/模块职责与依赖边界.md) |
| 模块长文 | [`设计文档/基础架构/架构设计.md`](./设计文档/基础架构/架构设计.md) |
| 新 Java 文件放哪 | [`设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md`](./设计文档/基础架构/单模块文件结构与AI开发约束规则V1.md) |
| 包名 | [`设计文档/基础架构/后端包结构与命名规范V1.md`](./设计文档/基础架构/后端包结构与命名规范V1.md) |
| 表字典 | [`数据字典/`](./数据字典/) |
| 持久层 | [`设计文档/数据与持久层/`](./设计文档/数据与持久层/) |
| RBAC 规则 / 菜单 | [`设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md`](./设计文档/权限与后台管理/阶段5A-Admin-RBAC权限体系详细设计.md)、[`阶段5E-菜单权限与导航说明.md`](./设计文档/权限与后台管理/阶段5E-菜单权限与导航说明.md) |
| AI | [`设计文档/AI/Agent服务技术选型与架构设计V1.md`](./设计文档/AI/Agent服务技术选型与架构设计V1.md) |
| 基础设施环境 | [`设计文档/基础架构/WSL与Docker环境配置说明.md`](./设计文档/基础架构/WSL与Docker环境配置说明.md) |

`设计文档/认证与安全/阶段4*.md` 与 `权限与后台管理/阶段5B`–`5D` 是阶段落地记录。`规则/1.文件名.md` 只指向单模块文件结构，不另写规则。

## 跳转（没有第二份正文）

| 文件 | 去哪里 |
|------|--------|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 架构设计 + 模块依赖 |
| [`设计文档/基础架构/整体架构说明.md`](./设计文档/基础架构/整体架构说明.md) | 全栈连线与本地启动 |
| [`总览/后端整体架构总览.md`](./总览/后端整体架构总览.md) | 同上 |
| [`设计文档/后续规划/README.md`](./设计文档/后续规划/README.md) | ROADMAP |
