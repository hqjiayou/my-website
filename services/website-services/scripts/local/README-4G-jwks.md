# 阶段 4G：本地 RSA 密钥与 JWKS 相关配置

Auth 使用 **RS256**；私钥仅存在于 **Auth** 进程可读路径；Admin **只配置 JWKS URI**，不读取私钥。

## 1. 生成 RSA 密钥对（示例）

在项目外或 `services/website-services/secrets/local/`（该目录应被 `.gitignore`，**勿提交私钥**）生成 PEM：

**OpenSSL（Git Bash / WSL / macOS / Linux）**

```bash
mkdir -p secrets/local
openssl genrsa -out secrets/local/auth-private.pem 2048
openssl rsa -in secrets/local/auth-private.pem -pubout -out secrets/local/auth-public.pem
```

PowerShell 若无 `openssl`，可用 WSL 执行同上命令，或安装 [Win32 OpenSSL](https://slproweb.com/products/Win32OpenSSL.html) 后执行。

## 2. `.env` 示例键（与仓库 `.env.example` 对齐）

```env
AUTH_JWT_PRIVATE_KEY_PATH=./secrets/local/auth-private.pem
AUTH_JWT_PUBLIC_KEY_PATH=./secrets/local/auth-public.pem
AUTH_JWT_KEY_ID=auth-local-rs256-key-1

# Admin（Resource Server）拉取 Auth 公钥
AUTH_JWKS_URI=http://localhost:8083/.well-known/jwks.json
```

路径可为相对 **`services/website-services`** 工作目录的相对路径；也可用绝对路径。**若从 `auth` 子目录启动 Spring Boot**（`user.dir` 为 `…/website-services/auth`），相对路径会优先在子模块下解析；`RsaPemUtils` 会在找不到文件时**再尝试父目录（`website-services`）**，与 PEM 放在 `services/website-services/secrets/local` 的推荐布局一致。

### 数据库环境变量与 `.env` 的优先级

`LocalDotenvSupport` **不会覆盖** 系统中已存在的 OS 环境变量。若机器上已设置 **`DB_USERNAME` / `DB_PASSWORD` / `DB_URL`**（例如 IDE 注入的 `CHANGE_ME`），将优先生效，可能导致与 **local-stack Postgres** 账号不一致、**Hikari 连库失败**。本地联调前可临时清除：`Remove-Item Env:DB_PASSWORD,Env:DB_USERNAME,Env:DB_URL`（PowerShell），或仅依赖 **`POSTGRES_*`** 与 **`website-services/.env`** 对齐。

### Redis 无密码（local-stack 常见）

本地 **Redis**（如 **`local-redis`**）未设 `requirepass` 时，`application-local.yml` 中 Redis 口令**默认空**；勿让占位串作为口令连接，否则 **`auth`** 会认证失败。

## 3. 运行注意

- 更换密钥或 `AUTH_JWT_KEY_ID` 后需**重启 Auth**；已签发的旧 Token 将失效（`kid` 与 JWKS 不一致时验签失败）。
- **Admin** 仅依赖 **`AUTH_JWKS_URI`**；勿在 Admin 配置对称 `SECURITY_JWT_SECRET`（4G 主链路已移除）。
- 验证 JWKS：`curl -s http://localhost:8083/.well-known/jwks.json`
