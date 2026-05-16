# WSL 与 Docker 环境配置说明

本文档说明本地开发时 WSL、Docker 与项目运行环境的关系。当前约定是：基础设施服务运行在 WSL + Docker 中，前端与后端应用可以在 Windows 终端、IDE 终端或 WSL 终端中启动。

> 说明：如果沟通中提到的 `MSL` 是指本地 Linux 子系统，本文统一按 `WSL`（Windows Subsystem for Linux）描述。

## 1. 目标与范围

- 说明本地开发环境中 WSL、Docker、数据库、中间件与应用服务的边界。
- 记录后端服务依赖的 PostgreSQL、Redis、MinIO / AIStor 端口与默认环境变量。
- 给出日常启动、验证与常见问题排查步骤。
- 不在本文档中维护生产环境部署、云服务配置或真实密钥。

## 2. 本地环境角色

### 2.1 WSL

WSL 用作本机 Linux 开发环境，适合承载 Docker Engine、容器网络和基础设施依赖。

建议用途：

- 运行 Docker 与基础设施容器。
- 执行 Linux 命令行排查，例如 `docker ps`、`docker logs`、`curl`。
- 作为后端或脚本的可选运行环境。

注意事项：

- Windows 应用访问 WSL 中暴露的容器端口时，默认使用 `localhost`。
- 如果端口无法访问，优先检查 WSL 是否启动、Docker 是否运行、容器端口是否正确映射。

### 2.2 Docker

Docker 用于启动本地开发依赖，不承载当前仓库的应用代码。

当前应用默认依赖：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- MinIO / AIStor API：`http://localhost:9000`
- MinIO / AIStor Console：`http://localhost:9001`

仓库在 `services/website-services/docker-compose.yml` 中提供PostgreSQL（含库名与用户）及无口令 Redis；启动方式：

```bash
cd services/website-services
docker compose up -d postgres redis
```

若本机已通过其他方式手动启动同名库与用户，端口和账号必须与 `application-local.yml` 一致；需要覆盖默认口令或连接串时，在 `services/website-services/` 目录创建本地 **`.env`**（勿提交仓库，见本节「与本仓库 Compose 对齐的 `.env` 范本」）。`docker compose` 会自动读取同目录 `.env`。**`admin` / `site-app`** 在 `main` 中会从 `user.dir` 向其父目录向上查找最近一层 `.env`，将其中键值写入 **系统属性**（不覆盖操作系统环境变量或 JVM 已设置的同名属性），从而让 `DB_*` 等与配置占位符生效；若你希望完全由 IDE/OS 注入变量，仍可不在仓库内放 `.env`。若终端直接 `mvn spring-boot:run`，请保持工作目录处于能拾取到上述 `.env` 的模块上级（例如 `services/website-services`，或 `admin` 子目录——父级亦会扫到）。

## 3. 后端本地配置

`services/website-services` 中 `admin` 与 `site/site-app` 使用 `application-local.yml` 读取本地环境变量。

### 3.0 与本仓库 Compose 对齐的 `.env` 范本（仅本机，勿提交）

在 `services/website-services/` 下新建 `.env`，可与 `docker-compose.yml`、应用数据源共用约定。以下值与上文 Compose 默认值一致；若已修改 Compose 或使用自建库，按需替换。

```text
POSTGRES_DB=my_website_dev
POSTGRES_USER=mywebsite
POSTGRES_PASSWORD=devpass

DB_URL=jdbc:postgresql://localhost:5432/my_website_dev
DB_USERNAME=mywebsite
DB_PASSWORD=devpass

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

说明：`POSTGRES_*` 仅用于 `docker compose` 拉起容器时的初始化；应用进程读取的是 **`DB_*` / `REDIS_*`** 等别名，应与数据库实际账号保持一致。

### 3.1 数据库

默认配置：

```text
DB_URL=jdbc:postgresql://localhost:5432/my_website_dev
DB_USERNAME=mywebsite
DB_PASSWORD=devpass
```

（`devpass` 与上述 `docker-compose.yml` 中 `POSTGRES_PASSWORD` 对齐；自建实例请自拟强口令并用 `DB_PASSWORD` 覆盖，勿提交到仓库。）

要求：

- PostgreSQL 容器需要暴露 `5432` 到宿主机。
- 数据库名建议使用 `my_website_dev`。
- 本地开发账号建议使用 `mywebsite`。
- 不要把真实密码提交到仓库。

### 3.2 Redis

默认配置：

```text
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

要求：

- Redis 容器需要暴露 `6379` 到宿主机。
- Compose 自带的 Redis **无口令**；若改用带口令实例，务必设置 `REDIS_PASSWORD`。

### 3.3 MinIO / AIStor

默认配置：

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=CHANGE_ME
MINIO_SECRET_KEY=CHANGE_ME
MINIO_BUCKET=uploads
MINIO_SECURE=false
```

要求：

- API 端口暴露为 `9000`。
- Console 端口暴露为 `9001`。
- 本地开发桶默认使用 `uploads`。
- 如果桶不存在，需要先在 Console 中创建，或通过脚本初始化。

## 4. 推荐启动顺序

### 4.1 启动基础设施

先进入 WSL，确认 Docker 正常运行：

```bash
docker ps
```

确认 PostgreSQL、Redis、MinIO / AIStor 容器已经启动，并且端口映射到宿主机。

### 4.2 启动后端服务

在 `services/website-services` 目录先执行一次构建：

```bash
mvn clean install -DskipTests
```

然后按顺序启动：

```bash
mvn -pl admin -am spring-boot:run
mvn -pl site/site-app -am spring-boot:run
mvn -pl gateway -am spring-boot:run
```

服务端口：

- Gateway：`8080`
- Admin API：`8081`
- Site API：`8082`

### 4.3 启动前端应用

前端应用可分别在对应目录启动：

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

```bash
cd apps/site-web/car-web
pnpm install
pnpm dev
```

```bash
cd apps/site-web/cake-web
pnpm install
pnpm dev
```

前端联调时优先通过 Gateway 访问后端 API，例如：

- `http://localhost:8080/api/admin/ping`
- `http://localhost:8080/api/site/ping`

## 5. 日常验证命令

### 5.1 验证 Docker 容器

```bash
docker ps
```

重点检查：

- PostgreSQL 是否存在 `5432` 端口映射。
- Redis 是否存在 `6379` 端口映射。
- MinIO / AIStor 是否存在 `9000`、`9001` 端口映射。

### 5.2 验证后端接口

```bash
curl http://localhost:8081/api/admin/ping
curl http://localhost:8082/api/site/ping
curl http://localhost:8080/api/admin/ping
curl http://localhost:8080/api/site/ping
```

### 5.3 验证健康检查

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

如果 `admin` 或 `site-app` 的健康检查显示数据库或 Redis 为 `DOWN`，通常表示基础设施容器未启动、端口未映射、账号密码不一致，或应用未使用正确的本地环境变量。

## 6. 常见问题

### 6.1 Windows 访问不到 Docker 容器端口

排查顺序：

- 确认 WSL 已启动。
- 确认 Docker 正在运行。
- 确认容器仍在运行：`docker ps`。
- 确认容器端口已经映射到宿主机，例如 `0.0.0.0:5432->5432/tcp`。
- 检查端口是否被其他本地进程占用。

### 6.2 后端启动成功但 health 显示 DOWN

常见原因：

- PostgreSQL 未启动或数据库名不存在（详见 **§6.3**）。
- Redis 未启动或密码不一致。
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 未注入到当前终端。
- `REDIS_PASSWORD` 与本地 Redis 配置不一致。

处理方式：

- 先确认 Docker 依赖可用。
- 再确认启动应用的终端中已经设置正确环境变量。
- 最后查看应用日志中的具体连接错误。

### 6.3 报错：`database "my_website_dev" does not exist`

含义：PostgreSQL 服务可达，但 **`my_website_dev`** 尚未创建；Flyway / DataSource 会在连接阶段失败。

**使用仓库 `docker-compose.yml` 时**

- 正常情况下 **`POSTGRES_DB=my_website_dev`** 会在 **数据目录首次初始化** 时自动建库。
- 若 volume 已是旧数据目录（从未包含该库），或曾经手动删库，需要在容器内执行一次建库（不删数据）：

```bash
cd services/website-services
docker compose exec postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_dev OWNER mywebsite ENCODING 'UTF8';"
```

若报错「already exists」，说明库已存在，请核对 **`DB_URL`** 是否指向 **`jdbc:postgresql://…:5432/my_website_dev`**。

更多场景（本机 PostgreSQL、重置 volume）见：`services/website-services/scripts/README.md`。

### 6.4 Gateway 返回 502 或连接失败

`gateway` 只负责转发，不承载业务逻辑。出现 502 时先确认目标服务是否启动：

- `admin` 是否运行在 `8081`。
- `site/site-app` 是否运行在 `8082`。
- 请求路径是否以 `/api/admin/` 或 `/api/site/` 开头。

### 6.5 MinIO / AIStor 文件接口失败

排查顺序：

- 确认 `MINIO_ENDPOINT` 指向 `http://localhost:9000`。
- 确认 Access Key 与 Secret Key 正确。
- 确认 `MINIO_BUCKET` 对应的桶已经存在。
- 确认 Console 可以通过 `http://localhost:9001` 打开。

## 7. 环境变量管理建议

- 本地真实密码只保存在本机环境变量、IDE Run Configuration 或未提交的本地脚本中。
- 仓库内只保留占位值与默认端口；真实口令放在本机 **`.env`**、环境变量或 IDE Run Configuration。
- `services/website-services/` 已有 `docker-compose.yml`：**不要提交 `.env`**，自行按 §3.0 范本创建。
- 修改本地基础设施账号、密码或端口后，需要同步检查 `application-local.yml` 中对应的变量名和默认值。

## 8. 相关文档

- 总体架构：`./整体架构说明.md`
- 后端架构：`./架构设计.md`
- 后端工程说明：`services/website-services/README.md`
- 本地库缺失补救：`services/website-services/scripts/README.md`
