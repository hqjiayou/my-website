# 数据字典（Data Dictionary）

**仓库权威路径（相对仓库根目录）**：`docs/数据字典/`。

本目录收录 **已与 Flyway migration 对齐** 的表级数据定义，作为 [`../设计文档/数据与持久层/数据库模型设计V1.md`](../设计文档/数据与持久层/数据库模型设计V1.md) 的补充：设计文档侧重规范与蓝图，**数据字典侧重已落地字段、约束与索引的可查手册**。

## 归档约定（长期）

| 类别 | 权威路径 |
| --- | --- |
| website-services **设计正文**（架构、数据库模型、持久层 Java 约定、环境与流程等） | **`docs/website-services/设计文档/`**（含 **`基础架构/`**、**`数据与持久层/`**、**`认证与安全/`**） |
| website-services **根目录同名 `.md`** | **仅跳转入口**，勿与 `设计文档/` **并行维护两份正文** |
| 数据库数据字典（按业务域拆分） | **`docs/数据字典/`**（本目录） |

**禁止**在 `services/website-services/docs/` 维护权威正文（该路径仅保留跳转说明）。凡新增表、变更字段、索引、唯一约束、外键或新增 Flyway migration，**必须同步更新**本目录对应条目及 **`docs/website-services/设计文档/数据与持久层/数据库模型设计V1.md`** 中与蓝图相关的章节。

## 文档索引

| 文档 | 覆盖表 |
| --- | --- |
| [平台与站点数据字典](./平台与站点数据字典.md) | `site` |
| [Admin 认证授权数据字典](./Admin认证授权数据字典.md) | `admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission` |
| [Site 用户数据字典](./Site用户数据字典.md) | `site_user`、`site_user_profile` |

后续新增业务域（内容、媒体、行业表等）请在本目录新增 Markdown，并在本 README 登记索引。

## 对应 Migration（代码路径）

DDL 仍位于 Maven 模块内（非文档路径）：

- `services/website-services/infra/src/main/resources/db/migration/V1__init_platform_core_tables.sql`

表、函数 `fn_touch_updated_at` 及关键列均已附加 `COMMENT ON`（具体见脚本末尾补充段）。

Flyway 执行策略见 [`services/website-services/README.md`](../../services/website-services/README.md) 与《数据库模型设计 V1》§8。

## 维护流程（强制）

凡发生以下任一情况，须在同一变更交付内更新字典与设计文档：

- 新增表、删除表或更名表
- 新增 / 修改 / 删除字段（类型、非空、默认值）
- 新增或调整索引、唯一约束（含 partial unique）
- 新增或调整外键或关联语义
- 新增 Flyway migration 版本

推荐顺序：**编写或审查 migration SQL → 更新本目录（数据字典）→ 按需修订 `docs/website-services/设计文档/数据与持久层/数据库模型设计V1.md`**。
