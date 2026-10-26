# STATUS_ENUMS

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
source_of_truth: Flyway + *Constants.java + apps/admin-web/src/types/*
```

## Reservation（已下架）

2026-10-27 起预约功能下架（Flyway V30）：`NEW` | `CONFIRMED` | `DONE` | `CANCELLED` 仅存于历史数据，代码与 API 已移除。

## Appearance config v1 keys

`primaryColor` | `fontStyle` | `buttonStyle` | `heroLayout`

**禁止**：`heroStyle`、`logoPlaceholder` 作为当前契约字段。

## Site

状态：`ACTIVE` | `DISABLED`（`AdminSiteConstants`）。库没有 CHECK。

用途标签（2026-10-26 起取代旧行业闭集）：自由标签，校验为 `SITE_TAG_PATTERN`（1–32 字符、字母/数字开头）；预设 `PERSONAL` | `PORTFOLIO` | `BLOG` | `KNOWLEDGE` | `TOOL` | `OTHER`，亦可自订。旧行业值仍可作自由标签存续（仅历史数据）；行业模板目录已于 2026-10-27 下架（V30 软删种子模板与模板权限）。

## Template（已下架）

模板模块已于 2026-10-27 下架：种子模板软删、`/templates` API 与后台页面移除，`site.template_id` 仅历史数据。建站直接配置 appearance，不再经模板。

## Content

类型：`hero` | `service` | `case` | `faq` | `news` | `about` | `cta`。

状态：`DRAFT` | `PUBLISHED` | `HIDDEN`。没有 `ARCHIVED`。

## Media

`UPLOADING` | `READY` | `FAILED`。

## Admin user status

`ACTIVE` | `DISABLED`（`AdminUserConstants`）。
