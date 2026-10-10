# STATUS_ENUMS

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
source_of_truth: Flyway + *Constants.java + apps/admin-web/src/types/*
```

## Reservation

`NEW` | `CONFIRMED` | `DONE` | `CANCELLED`

迁移：`NEW→CONFIRMED|CANCELLED`；`CONFIRMED→DONE|CANCELLED`；终态不可再迁。

**禁止**：`COMPLETED`（历史文档/旧 mock 用语，已失效）。

## Appearance config v1 keys

`primaryColor` | `fontStyle` | `buttonStyle` | `heroLayout`

**禁止**：`heroStyle`、`logoPlaceholder` 作为当前契约字段。

## Site

状态：`ACTIVE` | `DISABLED`（`AdminSiteConstants`）。库没有 CHECK。

类型：`CAR_REPAIR` | `USED_CAR` | `CAKE` | `SALON` | `CLINIC` | `GYOSEISHOSHI`。不要用 V1 注释里的 `CAR`。

## Template

`ENABLED` | `DRAFT` | `DISABLED`（`AdminTemplateConstants`）。

## Content

类型：`hero` | `service` | `case` | `faq` | `news` | `about` | `cta`。

状态：`DRAFT` | `PUBLISHED` | `HIDDEN`。没有 `ARCHIVED`。

## Media

`UPLOADING` | `READY` | `FAILED`。

## Admin user status

`ACTIVE` | `DISABLED`（`AdminUserConstants`）。
