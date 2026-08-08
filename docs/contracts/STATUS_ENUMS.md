# STATUS_ENUMS

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
source_of_truth: Flyway + *Constants.java + apps/admin-web/src/types/*
```

## Reservation

`NEW` | `CONFIRMED` | `DONE` | `CANCELLED`

迁移：`NEW→CONFIRMED|CANCELLED`；`CONFIRMED→DONE|CANCELLED`；终态不可再迁。

**禁止**：`COMPLETED`（历史文档/旧 mock 用语，已失效）。

## Appearance config v1 keys

`primaryColor` | `fontStyle` | `buttonStyle` | `heroLayout`

**禁止**：`heroStyle`、`logoPlaceholder` 作为当前契约字段。

## Site status

以代码 `SITE_STATUS` / DB check 为准（常见 `ACTIVE` / `DISABLED` 等——改动时同步本文件与前端 constants）。

## Template / Content / Media

以对应 `*Constants` 与前端 `*_STATUS_VALUES` 为准；新增枚举必须双边同步。

## Admin user status

`ACTIVE` | `DISABLED`（以 `rbacApi` / Admin User API 为准）。
