# PERSISTENCE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- Flyway 脚本只在 `services/website-services/infra/src/main/resources/db/migration`，当前 **V1–V21**。禁止改已经执行过的脚本，新变更追加版本。
- 日常由 admin 执行（`FLYWAY_ENABLED` 默认 true）。auth 关闭。site-app 默认 `SITE_APP_FLYWAY_ENABLED=false`。
- 验证：在 `services/website-services` 下 `mvn -B -ntp -Ppersistence-verify-pg test`（Testcontainers PostgreSQL）。
- 现行表对照：[`设计文档/数据与持久层/数据库模型设计V1.md`](./设计文档/数据与持久层/数据库模型设计V1.md) 文首「现行模型」。字典未覆盖的表以 Flyway 为准。
- Java 映射规范：[`Java持久层设计V1.md`](./设计文档/数据与持久层/Java持久层设计V1.md)。该文第 2 节「只有首批 8 表」是阶段 3A 范围；其后 Entity 已覆盖模板、外观、媒体、内容、预约和 AI。
- `auth` 为了不依赖 admin jar，自持 `AdminUserEntity`、`SiteUserEntity`、`SiteEntity` 只读映射。这和「实体不要放进 common」不冲突。
