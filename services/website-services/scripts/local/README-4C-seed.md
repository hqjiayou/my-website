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

## 与 local-stack、`website-services/.env` 对齐

- **PostgreSQL / Redis** 由既有 **local-stack** 提供（**`localhost:5432` / `6379`**）；仓库 **不再**附带第二套 Compose。
- **`services/website-services/.env`**（请先 **`cp .env.example .env`**）仅供 **Java 进程**读取 JDBC / Redis 等；库名、用户名、口令须与你实际连接的 **`local-postgres`** 实例一致，避免 seed 写入 A 库而应用连 B 库。
- **`application-local.yml`** **不在 YAML 内为 JDBC 等敏感项提供默认值**；用户口令应与 **`.env` 中 `DB_*`**（且与 **`POSTGRES_*`**）一致。
- 若使用自建 Postgres，请改用对应 JDBC / psql 连接串，并在 **`.env`** 中维护 `DB_USERNAME`、`DB_PASSWORD`、`DB_URL`。
