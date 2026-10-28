# PERSISTENCE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- Flyway 脚本只在 `services/website-services/infra/src/main/resources/db/migration`，当前 **V1–V21**。禁止改已经执行过的脚本，新变更追加版本。
- 日常由 admin 执行（`FLYWAY_ENABLED` 默认 true，当前迁移至 V35）。auth 关闭。agent-app 默认不执行（代码里变量名仍是历史名 `AGENT_APP_FLYWAY_ENABLED`，默认 `false`）。
- 验证：在 `services/website-services` 下 `mvn -B -ntp -Ppersistence-verify-pg test`（Testcontainers PostgreSQL）。
- 现行表对照：[`数据字典/`](./数据字典/) 与 Flyway migration（原《数据库模型设计 V1》已于 2026-10-29 移除，见 git 历史）。字典未覆盖的表以 Flyway 为准。
- Java 映射规范：现行代码与 [`.cursor/rules/backend-structure.mdc`](../../.cursor/rules/backend-structure.mdc)（原《Java 持久层设计 V1》已于 2026-10-29 移除，见 git 历史）。
- `auth` 为了不依赖 admin jar，自持 `AdminUserEntity`、`SiteUserEntity`、`SiteEntity` 只读映射。这和「实体不要放进 common」不冲突。
