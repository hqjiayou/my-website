# SELF_HOSTING

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-10-24
```

单人自用：常开主机 + Docker Compose + Tailscale。不买域名，不开路由器端口，不开 Tailscale Funnel。手机走 tailnet 里的 HTTPS 打开 Agent Portal。

这份说明对齐 `docker-compose.yml`。和方案文件有出入的地方写在最后一节。仓库里的 Java / Python / 前端代码和它们的默认配置没有为这份部署改过。

本文件编写时的环境没有 Docker，没有执行过 `docker compose up`。第一次在目标主机上的启动，就是这套文件的运行验收。

## 1. 主机

- Linux 上安装 Docker Engine 与 Compose v2 插件（`docker compose version` 能打出版本）。
- 安装 Tailscale 并登录。手机之后要登录同一个账号。
- 内存：四个 Java 进程各 `-Xmx384m`，再加上 PostgreSQL、Redis、Python、Caddy。方案里 1.8–2.2GB 是按三个 JVM 估的。四个 heap 再算上每个 JVM 的堆外内存，4GB 主机会偏紧，更稳的是 6GB 及以上。
- 磁盘预留 10GB 以上。主机关闭睡眠，并让 Docker 开机自启：`sudo systemctl enable --now docker`。
- 容器的 `restart: unless-stopped` 会在 Docker 起来之后把堆叠拉回来。

## 2. 取出代码并填写 `.env`

```bash
git clone <仓库地址>
cd my-website
git checkout dev
cp .env.example .env
```

编辑仓库根的 `.env`。真实值只放这个文件，不要写回 `.env.example`，也不要提交 `.env`（根目录 `.gitignore` 已经忽略 `.env`）。

至少换掉这些占位：

| 变量 | 怎么生成 |
|------|----------|
| `POSTGRES_PASSWORD`、`REDIS_PASSWORD`、`AGENT_RETRIEVAL_PASSWORD` | `openssl rand -hex 24` |
| `AGENT_INTERNAL_SERVICE_TOKEN` | `openssl rand -hex 32` |
| `AI_PROVIDER_API_KEY` | Google AI Studio 的 Gemini key |
| `AI_MODEL` | 保持 `google:gemini-3-flash-preview`，除非明确改 provider |
| `VITE_PORTAL_SITE_ID` | 先留空。管理后台建好站点后填数字 ID，然后重建 `web` |

口令用十六进制。`$`、`@`、`:` 和空格会截断 Compose 替换或检索库 URL。

`AI_MODEL` 留空，或 key 仍是 `CHANGE_ME` 时，agent 进程可以起来，公开的 `/health` 仍是 200。对话接口返回 503，`providerStatus=BLOCKED`。这不是故障转移，是没有可用模型。

## 3. JWT 密钥

Auth 只从 PEM 文件读密钥（`BEGIN PRIVATE KEY` / `BEGIN PUBLIC KEY`），不读内联的密钥正文。Compose 把 `services/website-services/secrets/local` 只读挂到 auth 容器的 `/secrets`。这个目录已经在 `.gitignore` 里。

容器里的 auth 用户不是 root。密钥模式如果是 `600`，进程会报 `RSA key file not found` 或读失败。生成后要 `chmod a+r`。

OpenSSL（与加载器匹配的做法）：

```bash
mkdir -p services/website-services/secrets/local
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out services/website-services/secrets/local/auth-private.pem
openssl pkey -in services/website-services/secrets/local/auth-private.pem -pubout \
  -out services/website-services/secrets/local/auth-public.pem
chmod a+r services/website-services/secrets/local/auth-private.pem \
  services/website-services/secrets/local/auth-public.pem
```

`.env` 里的路径保持容器内路径，不要改成 `CHANGE_ME`：

```text
AUTH_JWT_PRIVATE_KEY_PATH=/secrets/auth-private.pem
AUTH_JWT_PUBLIC_KEY_PATH=/secrets/auth-public.pem
```

keytool 再导出成同样的 PEM（加载器不读 JKS / PKCS12）：

```bash
mkdir -p services/website-services/secrets/local
keytool -genkeypair -alias auth -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore /tmp/auth.p12 -storepass CHANGE_ME -dname "CN=my-website-auth"
openssl pkcs12 -in /tmp/auth.p12 -passin pass:CHANGE_ME -nocerts -nodes \
  -out services/website-services/secrets/local/auth-private.pem
openssl pkcs12 -in /tmp/auth.p12 -passin pass:CHANGE_ME -nokeys -out /tmp/auth-cert.pem
openssl x509 -in /tmp/auth-cert.pem -pubkey -noout \
  -out services/website-services/secrets/local/auth-public.pem
rm -f /tmp/auth.p12 /tmp/auth-cert.pem
chmod a+r services/website-services/secrets/local/auth-private.pem \
  services/website-services/secrets/local/auth-public.pem
```

`/tmp` 里的口令 `CHANGE_ME` 只保护临时 PKCS12，用完就删。运行中的 auth 读的是 PEM 文件。

## 4. 启动

在仓库根：

```bash
docker compose up -d --build
docker compose ps
```

第一次会拉镜像并做 Maven / pnpm 构建，时间很长。等 `docker compose ps` 里 postgres、redis、admin、auth、site、gateway、agent、web 都是 healthy（web 没有健康检查，Up 即可）。

平台表结构只由 **admin** 在启动时跑 Flyway。auth 的本地配置把 Flyway 关掉了，site-app 默认也不跑。所以 auth 和 site 会等 admin 健康之后才启动。不要为了“每个 Java 进程都迁移”去改配置。

看某个服务：

```bash
docker compose logs -f admin
docker compose exec gateway curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
```

Java 的 `/actuator/health` 在各自容器里，不映射到宿主。宿主 80 是 Agent Portal，8081 只在本机，是 admin-web。

## 5. 管理员种子

等 admin 健康后再执行。这是仓库里的本地管理员种子，用户名 `hqjiayou`，口令明文是 `123456`。不要执行 `seed-site-demo.sql`（示范站 `demo` / `123456`，不能进这台主机）。

```bash
docker compose exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' \
  < services/website-services/scripts/local/seed-admin-hqjiayou-super.sql
```

然后用浏览器打开管理后台（见下一节的第二个地址），用 `hqjiayou` / `123456` 登录，**第一件事就是在使用者管理里把這個預設密碼改掉**（在改密碼之前不要執行後面的 `tailscale serve` 發佈步驟），再建自己的站点。记下站点的数字 ID，写入 `.env` 的 `VITE_PORTAL_SITE_ID`，再重建 web：

```bash
docker compose up -d --build web
```

Agent Portal 登录框要的是站点的 `siteCode`。`VITE_PORTAL_SITE_ID` 只给 `/api/site/{siteId}/ai/**` 用。改这个值必须重建镜像，重启容器不会改已经打进静态文件的值。

## 6. 检索库（问答不依赖这一步）

不建检索库、或 `AI_EMBEDDING_MODEL` 留空时，检索是 BLOCKED，纯问答仍然可以走。要开检索时，在仓库根执行一次（用的是 postgres 容器里的变量，脚本外不要写口令）：

```bash
docker compose exec -T postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
      -v pass="$AGENT_RETRIEVAL_PASSWORD" \
      -v dbname="$AGENT_RETRIEVAL_DB" \
      -v role="$AGENT_RETRIEVAL_USER" \
      -v appdb="$POSTGRES_DB"' <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pass')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'dbname', :'role')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'dbname')
\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'appdb') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM %I', :'appdb', :'role') \gexec
SQL

docker compose exec -T postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$AGENT_RETRIEVAL_DB" -c "CREATE EXTENSION IF NOT EXISTS vector"'
```

Java 用的 `POSTGRES_USER` 是镜像创建的超级用户，撤销 `PUBLIC` 的 CONNECT 不会把它锁在业务库外面。检索账号不应再连业务库。

嵌入模型例：`AI_EMBEDDING_MODEL=google:gemini-embedding-001` 时把 `AI_EMBEDDING_DIMENSIONS=768`。改完后 `docker compose up -d agent`。

## 7. Tailscale

不要执行 `tailscale funnel`。Funnel 会把服务暴露到公网。

Caddy 监听容器内 8080 和 8081，没有证书。宿主把 80 转到容器 8080，把 127.0.0.1:8081 转到容器 8081。

```bash
sudo tailscale serve --bg --https=443 http://127.0.0.1:80
sudo tailscale serve --bg --https=8443 http://127.0.0.1:8081
sudo tailscale serve status
```

不同 Tailscale 版本的子命令不完全一样。目标是：443 转到本机 80（Agent Portal），8443 转到本机 127.0.0.1:8081（admin-web）。`tailscale serve --help` 如果没有 `--https=8443`，用该版本里“额外 HTTPS 端口”的等价写法。不要改成 Funnel。

手机安装 Tailscale，登录同一账号：

- Agent Portal：`https://<主机名>.<tailnet>.ts.net/`
- 管理后台：`https://<主机名>.<tailnet>.ts.net:8443/`

Agent Portal 可以加到主屏幕。管理后台走第二个端口，因为 admin-web 的 Vite `base` 不能靠环境变量改成 `/admin/`，静态资源按站点根路径引用。

同一局域网里可以用 `http://<主机局域网 IP>/` 作为 Agent Portal 备援。不要在路由器上转发 80 或 8081。`SECURITY_REFRESH_COOKIE_SECURE` 保持 `false`，这样这条 HTTP 备援还能保存登录 Cookie；Tailscale 的 HTTPS 页面也会带上它。

对象存储（MinIO / AIStor）不在这套 compose 里。需要上传文件的功能在这台主机上不可用。Redis 没有卷，重建 redis 容器会丢掉会话，用户要重新登录。

## 8. 备份

每天把 `pg_dump` 放到仓库下的 `backups/`，只保留最近 14 份。脚本不包含口令。

```bash
chmod +x deploy/backup.sh
./deploy/backup.sh
```

cron 例（路径换成主机上的克隆目录）：

```cron
15 3 * * * cd /path/to/my-website && ./deploy/backup.sh >> /var/log/my-website-backup.log 2>&1
```

`.env` 和 `services/website-services/secrets/local/` 另外离线留一份。丢掉它们就要重做全部口令和 JWT。`backups/` 里是数据库全文，不要提交进 git。

## 9. 更新

主机只拉已经合入 `dev` 的提交。

```bash
git pull
docker compose up -d --build
```

改了 `VITE_PORTAL_SITE_ID` 时，这一步会重建 web 镜像。看日志：`docker compose logs -f gateway`。

## 10. 故障对照

| 现象 | 含义 |
|------|------|
| chat 503，`providerStatus=BLOCKED` | 没配 `AI_MODEL`，或 `AI_PROVIDER_API_KEY` 不是可用的 Gemini key。agent 的 `/health` 仍可能是 200 |
| chat 503，`MCP_UNAVAILABLE` | 配了 `MCP_SERVERS_JSON` 但连不上。这套 `.env.example` 默认不配 MCP |
| 检索不可用，问答仍可以 | `AI_EMBEDDING_MODEL` 为空，或第 6 节的库还没建 |
| auth 日志 `RSA key file not found` | PEM 不在挂载目录、路径被写成 `CHANGE_ME`，或文件不是容器用户可读 |
| Flyway / `password authentication failed` | `.env` 的 `POSTGRES_*` 和已经初始化的卷不一致。卷一旦用旧口令初始化，只改 `.env` 不会改库内口令 |
| gateway 日志仍在连 `localhost:8081` 等 | 环境变量没有盖掉 `application.yml` 里的上游。这是未在编写环境跑过的风险点。看 gateway 启动日志里的 route URI 是不是 `http://auth:8083`、`http://admin:8081`、`http://site:8082` |
| 宿主 80 绑定失败 | 已经有程序占用 80。先停掉那个程序再 `docker compose up -d web` |
| 管理后台打开后接口 404，页面在 `/admin/` 下 | 不要把 admin-web 挂到子路径。用第 7 节的 8443 端口 |
| 上传失败 | 这套堆叠没有 MinIO |

## 11. 和方案文件的差异

方案文件 `my-website-selfhost-tailscale-plan.md` 写的是官方 Postgres 镜像、三个 JVM、先不部署 admin-web。本仓库的 compose 按任务 brief 覆盖了这三点：

- Postgres 使用 `pgvector/pgvector:pg17`。
- 服务包含 admin，以及 Caddy 上的 admin-web（容器 8081，只绑宿主回环）。
- Java heap 仍是每个 `-Xmx384m`，因此比方案里“三个 384MB”更占内存。

另外，代码和 brief 背景句也不完全一样，部署没有改代码：

- Flyway 不是每个 Java 进程都跑。只有 admin 默认开启。auth 在 `application-local.yml` 里写死关闭。
- gateway 的上游在 `application.yml` 里是 `localhost`。compose 用 `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_*` 覆盖。
- agent 沿用 `services/agent-services/Dockerfile`，没有改该文件。镜像里的 uvicorn 端口是 3001，`/health` 只表示进程活着。
