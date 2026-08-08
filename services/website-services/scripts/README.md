# `scripts/` · 本地 PostgreSQL 辅助脚本

用于 **`my_website_dev` 不存在**（Flyway / JDBC 报错 `database "my_website_dev" does not exist`）时的补救。

本仓库 **不再**附带 PostgreSQL / Redis 的 Compose；请确保你已启动既有 **local-stack**（典型容器名 **`local-postgres`**），且 **`5432`** 映射到宿主机。

---

## 方式 A：Docker 中的 Postgres（local-stack / `local-postgres`）

### A1 保留其它数据的情况下单独建库（推荐）

容器名以 **`docker ps`** 为准（常见为 **`local-postgres`**）。将 **`mywebsite`** 替换为你 **`website-services/.env`** 中与 Postgres 一致的登录用户（范本 `.env.example` 默认为 **`mywebsite`**）：

```bash
docker exec -i local-postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_dev OWNER mywebsite ENCODING 'UTF8';"
```

等价地，也可用宿主机 **`psql`** 直连 **`localhost:5432`**：

```bash
psql "postgresql://mywebsite:<口令>@localhost:5432/postgres" -v ON_ERROR_STOP=1 -c "CREATE DATABASE my_website_dev OWNER mywebsite ENCODING 'UTF8';"
```

若提示「already exists」则说明库已有，无需再执行。

### A2 重置 Postgres 数据目录（会清空该实例上的库）

仅在可接受清空本地开发数据时使用：按 **local-stack** 自带的 Compose / volume 管理方式执行（本仓库不提供第二套 Postgres Compose）。

---

## 方式 B：本机安装的 PostgreSQL（非 Docker）

使用具备 **`CREATEDB`** 权限的角色连接维护库 **`postgres`**，执行仓库内 SQL：

```powershell
# Windows PowerShell（示例：管理员账号 postgres）
$env:PGPASSWORD = '<管理员密码>'
psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_dev.sql
```

执行前若尚无脚本中的 Owner 角色，可先按需 **`CREATE ROLE`**（口令须与本机 **`website-services/.env`** 一致）。

---

# 持久层验证库：`my_website_3c_verify`

用于 **[持久层真实 PostgreSQL 验证说明](../../../docs/website-services/数据文档/持久层真实PostgreSQL验证说明.md)**（Flyway V1 + Mapper 回归测试），与日常开发库 **`my_website_dev`** 分离。

在 **`local-postgres`**（或任一可达 **`localhost:5432`** 的实例）上建库示例：

```bash
docker exec -i local-postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_3c_verify OWNER mywebsite ENCODING 'UTF8';"
```

用户 **`OWNER`** 须与 **`website-services/.env`** 中 JDBC 登录用户一致。

本机 PostgreSQL：可用仓库 SQL 文件（见下表「持久层验证」一行）。

---

# RBAC 种子验证库：`my_website_rbac_seed_verify`

用于 **[阶段5C.1 RBAC 种子真实 PostgreSQL 迁移验证](../../../docs/website-services/数据文档/阶段5C.1-RBAC种子真实PostgreSQL迁移验证.md)**（Flyway **V1 + V2** 与种子数据），与开发库、持久层验证库分离。

```bash
docker exec -i local-postgres psql -U hqjiayou -d postgres -v ON_ERROR_STOP=1 \
  -c "CREATE DATABASE my_website_rbac_seed_verify OWNER hqjiayou ENCODING 'UTF8';"
```

回归测试：`mvn clean test -Ppersistence-verify-pg -pl admin -am "-Dtest=AdminRbacSeedVerifyPgTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

---

## SQL 文件

| 文件 | 用途 |
|------|------|
| [`create-my_website_dev.sql`](./create-my_website_dev.sql) | `CREATE DATABASE my_website_dev`（脚本内 Owner 默认为 **`hqjiayou`**；若你的 `.env` 使用 **`mywebsite`** 等账号，请编辑 SQL 或改用上文交互式 `CREATE DATABASE`） |
| [`create-my_website_persistence_verify.sql`](./create-my_website_persistence_verify.sql) | `CREATE DATABASE my_website_3c_verify`（持久层回归专用临时库），Owner 默认同上 |
| [`create-my_website_rbac_seed_verify.sql`](./create-my_website_rbac_seed_verify.sql) | `CREATE DATABASE my_website_rbac_seed_verify`（5C.1 RBAC 种子 Flyway 验证专用） |

---

## 本地 super_admin 账号（hqjiayou）

在 **`my_website_dev`** 已建库且应用已跑过 Flyway（含 V2 RBAC 种子）后，可插入本地开发管理员：

| 文件 | 用途 |
|------|------|
| [`local/seed-admin-hqjiayou-super.sql`](./local/seed-admin-hqjiayou-super.sql) | SQL：用户 `hqjiayou` / 密码 `123456`，绑定 `super_admin` |
| [`local/run-seed-admin-hqjiayou-super.ps1`](./local/run-seed-admin-hqjiayou-super.ps1) | Windows：默认 `docker exec` → `local-postgres` |
| [`local/run-seed-admin-hqjiayou-super.sh`](./local/run-seed-admin-hqjiayou-super.sh) | Linux/macOS：同上 |

```powershell
# 在 services/website-services 目录
pwsh ./scripts/local/run-seed-admin-hqjiayou-super.ps1

# 本机 psql（不用 Docker）
pwsh ./scripts/local/run-seed-admin-hqjiayou-super.ps1 -UseHostPsql
```

连接参数从同级 **`.env`** 读取 `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`（缺省为 `my_website_dev` / `hqjiayou` / `hqjiayou`）。

---

## 本地 Site 前台账号（demo）

在 **`my_website_dev`** 已建库且 Flyway V1 已创建 `site` / `site_user` 后，可插入本地前台站点与用户：

| 文件 | 用途 |
|------|------|
| [`local/seed-site-demo.sql`](./local/seed-site-demo.sql) | SQL：站点 `demo` + 用户 `site_demo` / 密码 `123456` |
| [`local/run-seed-site-demo.ps1`](./local/run-seed-site-demo.ps1) | Windows：默认 `docker exec` → `local-postgres` |
| [`local/run-seed-site-demo.sh`](./local/run-seed-site-demo.sh) | Linux/macOS：同上 |

```powershell
pwsh ./scripts/local/run-seed-site-demo.ps1
```
