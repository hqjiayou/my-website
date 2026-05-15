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

当前仓库暂未提交统一的 `docker-compose.yml`。如果本机已通过 Docker Desktop、WSL Docker 或手动容器方式启动上述服务，只要端口和账号与应用配置一致即可。

## 3. 后端本地配置

`services/website-services` 中 `admin` 与 `site/site-app` 使用 `application-local.yml` 读取本地环境变量。

### 3.1 数据库

默认配置：

```text
DB_URL=jdbc:postgresql://localhost:5432/my_website_dev
DB_USERNAME=mywebsite
DB_PASSWORD=CHANGE_ME
```

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
REDIS_PASSWORD=CHANGE_ME
```

要求：

- Redis 容器需要暴露 `6379` 到宿主机。
- 如果本地 Redis 没有配置密码，需要同步调整应用环境变量或本地配置。

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

- PostgreSQL 未启动或数据库名不存在。
- Redis 未启动或密码不一致。
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 未注入到当前终端。
- `REDIS_PASSWORD` 与本地 Redis 配置不一致。

处理方式：

- 先确认 Docker 依赖可用。
- 再确认启动应用的终端中已经设置正确环境变量。
- 最后查看应用日志中的具体连接错误。

### 6.3 Gateway 返回 502 或连接失败

`gateway` 只负责转发，不承载业务逻辑。出现 502 时先确认目标服务是否启动：

- `admin` 是否运行在 `8081`。
- `site/site-app` 是否运行在 `8082`。
- 请求路径是否以 `/api/admin/` 或 `/api/site/` 开头。

### 6.4 MinIO / AIStor 文件接口失败

排查顺序：

- 确认 `MINIO_ENDPOINT` 指向 `http://localhost:9000`。
- 确认 Access Key 与 Secret Key 正确。
- 确认 `MINIO_BUCKET` 对应的桶已经存在。
- 确认 Console 可以通过 `http://localhost:9001` 打开。

## 7. 环境变量管理建议

- 本地真实密码只保存在本机环境变量、IDE Run Configuration 或未提交的本地脚本中。
- 仓库内只保留示例值、占位值和默认端口。
- 如果后续补充 `docker-compose.yml`，建议同时提供 `.env.example`，但不要提交 `.env`。
- 修改本地基础设施账号、密码或端口后，需要同步检查 `application-local.yml` 中对应的变量名和默认值。

## 8. 相关文档

- 总体架构：`doc/整体架构说明.md`
- 后端架构：`doc/website-services/架构设计.md`
- 后端工程说明：`services/website-services/README.md`
