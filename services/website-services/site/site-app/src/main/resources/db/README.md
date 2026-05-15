# site-app 数据库脚本说明

平台级 Flyway migration **不在本模块维护**。

- 首批及后续结构变更脚本统一放在：`admin/src/main/resources/db/migration`
- `site-app` 默认**不启用** Flyway（`SITE_APP_FLYWAY_ENABLED`，默认 `false`），避免与 `admin` 共用同一数据库时重复执行 migration、产生 `flyway_schema_history` 冲突。

本地或联调时：先启动 **`admin`** 一次以完成迁移，再启动 **`site-app`**。
