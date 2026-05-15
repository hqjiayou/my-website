# 数据字典（Data Dictionary）

本目录收录 **已与 Flyway migration 对齐** 的表级数据定义，作为 `docs/数据库模型设计V1.md` 的补充：设计文档侧重规范与蓝图，**数据字典侧重已落地字段、约束与索引的可查手册**。

## 文档索引

| 文档 | 覆盖表 |
| --- | --- |
| [平台与站点数据字典](./平台与站点数据字典.md) | `site` |
| [Admin 认证授权数据字典](./Admin认证授权数据字典.md) | `admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission` |
| [Site 用户数据字典](./Site用户数据字典.md) | `site_user`、`site_user_profile` |

## 对应 Migration

首批表结构来源（唯一 DDL 入口）：

- `admin/src/main/resources/db/migration/V1__init_platform_core_tables.sql`

Flyway 执行策略见 `README.md`（工程根）与 `docs/数据库模型设计V1.md` §8。

## 维护约定（强制）

凡发生以下任一情况，**必须同步更新**本目录中与该表对应的文档条目，并在《数据库模型设计V1》中维护与蓝图一致的高层说明（若涉及规范变更）：

- 新增表
- 修改表结构（增删改列、改类型、改默认值）
- 新增或调整索引、唯一约束（含 partial unique）
- 新增或调整外键、关联关系说明
- 新增 Flyway migration 版本

**流程建议**：先写/审 migration SQL → 再改数据字典 → 必要时更新设计文档 § 与 ER 描述，避免文档与数据库长期漂移。
