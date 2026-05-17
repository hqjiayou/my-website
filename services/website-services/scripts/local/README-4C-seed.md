# 阶段 4C：本地 Admin 验证账号（可选）

用途：**仅在本地**为 Admin 登录联调插入一条 `admin_user`，不属于 Flyway，也不会在应用启动时自动创建管理员。

## 前置

- 已对目标库执行 Flyway `V1__init_platform_core_tables`（存在 `admin_user` 表）。
- 明文口令（文档与脚本注释一致）：`Local4c_Verify`（请勿用于任何真实环境）。

## 执行

```bash
psql "postgresql://<DB_USER>:<DB_PASS>@localhost:5432/<DB_NAME>" \
  -v ON_ERROR_STOP=1 \
  -f services/website-services/scripts/local/seed-admin-local-verify.sql
```

插入成功后，`username = admin_local_verify`，状态 `ACTIVE`。若该行已存在（未删除），脚本不会重复插入。

## 自建库与本仓库 docker-compose

- **`docker-compose.yml`** 使用 **`${POSTGRES_*}`** 占位符，实际值来自 **`services/website-services/.env`**（请先 **`cp .env.example .env`**）。范本中带本地示例账号 **`hqjiayou`**，可按需改掉。
- **`application-local.yml`** **不在 YAML 内为 JDBC 等敏感项提供默认值**；用户口令应与 **`.env` 中 `DB_*`**（且与 **`POSTGRES_*`**）一致。
- 若使用自建 Postgres，请改用对应 JDBC / psql 连接串，并在 **`.env`** 中维护 `DB_USERNAME`、`DB_PASSWORD`、`DB_URL`。
