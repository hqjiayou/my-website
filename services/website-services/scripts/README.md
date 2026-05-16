# `scripts/` · 本地 PostgreSQL 辅助脚本

用于 **`my_website_dev` 不存在**（Flyway / JDBC 报错 `database "my_website_dev" does not exist`）时的补救。

---

## 方式 A：Docker Compose（仓库自带 postgres）

镜像 **`postgres`** 在 **数据目录首次为空** 时会根据环境变量 **`POSTGRES_DB`** 创建库（默认 `my_website_dev`）。若你曾经用过同名 volume、但当时未设置该变量，或手动删过库，可任选其一：

### A1 保留其它数据的情况下单独建库（推荐）

在 **`services/website-services`** 目录：

```bash
docker compose exec postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_dev OWNER mywebsite ENCODING 'UTF8';"
```

若提示「already exists」则说明库已有，无需再执行。

说明：`docker-compose.yml` 中 **`POSTGRES_USER`** 默认为 **`mywebsite`**，该用户在官方镜像中具有超级用户权限，足以 **`CREATE DATABASE`**。

若 `.env` 将 **`POSTGRES_USER`** 改成了其它用户名，请将上面 `-U` 换成同一用户名。

### A2 重置 postgres 容器数据目录（会清空该 volume 内所有库）

仅在可接受清空本地开发数据时使用：

```bash
docker compose down
docker volume ls | findstr website-services
docker volume rm <上一步看到的 postgres 数据卷名>
docker compose up -d postgres
```

Volume 名称以 **`docker volume ls`** 为准（前缀常为工程目录名）。

---

## 方式 B：本机安装的 PostgreSQL（非 Docker）

使用具备 **`CREATEDB`** 权限的角色连接维护库 **`postgres`**，执行仓库内 SQL：

```powershell
# Windows PowerShell（示例：管理员账号 postgres）
$env:PGPASSWORD = '<管理员密码>'
psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_dev.sql
```

执行前若尚无角色 **`mywebsite`**，可先：

```sql
CREATE ROLE mywebsite LOGIN PASSWORD 'devpass';
```

（口令请与本机 `.env` / `application-local.yml` 一致。）

---

# 持久层验证库：`my_website_3c_verify`

用于 **[持久层真实 PostgreSQL 验证说明](../../../docs/website-services/数据文档/持久层真实PostgreSQL验证说明.md)**（Flyway V1 + Mapper 回归测试），与日常开发库 **`my_website_dev`** 分离。

Docker Compose（在 **`services/website-services`** 目录、`postgres` 已启动）：

```bash
docker compose exec postgres psql -U hqjiayou -d postgres -c "CREATE DATABASE my_website_3c_verify OWNER hqjiayou ENCODING 'UTF8';"
```

若 `.env` 修改了 **`POSTGRES_USER`**，请将上面 **`hqjiayou`**（`-U` 与 `OWNER`）换成与你容器内一致的登录用户。

本机 PostgreSQL：可用仓库 SQL 文件（见下表「持久层验证」一行）。

---

## SQL 文件

| 文件 | 用途 |
|------|------|
| [`create-my_website_dev.sql`](./create-my_website_dev.sql) | `CREATE DATABASE my_website_dev`，Owner 默认为 **`mywebsite`** |
| [`create-my_website_persistence_verify.sql`](./create-my_website_persistence_verify.sql) | `CREATE DATABASE my_website_3c_verify`（持久层回归专用临时库），Owner 默认 **`hqjiayou`**（与 compose 默认 `POSTGRES_USER` 一致） |
