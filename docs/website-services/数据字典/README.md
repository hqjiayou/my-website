# 数据字典（Data Dictionary）

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
```

**仓库权威路径（相对仓库根目录）**：`docs/website-services/数据字典/`。

（历史链接常写 `docs/数据字典/`——该路径现为跳转 stub，正文只在本目录维护。）

本目录收录 **已与 Flyway migration 对齐** 的表级数据定义，是已落地字段、约束与索引的可查手册（原配套《数据库模型设计 V1》已于 2026-10-29 随 `设计文档/` 整体移除，见 git 历史）。与 Flyway 冲突时以 schema 为准。

## 归档约定（长期）

| 类别 | 权威路径 |
| --- | --- |
| website-services **设计正文** | `docs/website-services/` 根层各域摘要（`设计文档/` 已于 2026-10-29 移除，见 git 历史） |
| 数据库数据字典（按业务域拆分） | **`docs/website-services/数据字典/`**（本目录） |
| 全栈当前完成度 | **`docs/overview/CURRENT_STATE.md`** |

**禁止**在 `services/website-services/docs/` 维护权威正文（该路径仅保留跳转说明）。凡新增表、变更字段、索引、唯一约束、外键或新增 Flyway migration，**必须同步更新**本目录对应条目。

## 文档索引

已写入手册的表：

| 文档 | 覆盖表 |
| --- | --- |
| [平台与站点数据字典](./平台与站点数据字典.md) | `site` |
| [Admin 认证授权数据字典](./Admin认证授权数据字典.md) | `admin_user`、`admin_role`、`admin_permission`、`admin_user_role`、`admin_role_permission` |
| [Site 用户数据字典](./Site用户数据字典.md) | `site_user`、`site_user_profile` |

Flyway 已有、本目录还没有手册的表，以 `services/website-services/infra/src/main/resources/db/migration/` 为准，不要把上面三篇当成全库：

| 迁移 | 表 |
| --- | --- |
| V7 | `site_template` |
| V8 | `site_appearance` |
| V9 | `media_file` |
| V11 | `site_content` |
| V14 | `site_reservation` |
| V16 | `site_ai_prompt_profile`、`site_ai_conversation`、`site_ai_message` |
| V18 | `site_ai_knowledge_document` |

V20、V21 改的是已有 AI 表的列，不是新表。当前迁移到 V21。

## 对应 Migration（代码路径）

DDL 在 `services/website-services/infra/src/main/resources/db/migration/`，当前至 V21。

表、函数 `fn_touch_updated_at` 及关键列在 V1 脚本末尾有 `COMMENT ON`。后续表以各自迁移为准。

Flyway 执行策略见 [`services/website-services/README.md`](../../../services/website-services/README.md)。

## 维护流程（强制）

凡发生以下任一情况，须在同一变更交付内更新本字典：

- 新增表、删除表或更名表
- 新增 / 修改 / 删除字段（类型、非空、默认值）
- 新增或调整索引、唯一约束（含 partial unique）
- 新增或调整外键或关联语义
- 新增 Flyway migration 版本

推荐顺序：**编写或审查 migration SQL → 更新本目录（数据字典）**。
