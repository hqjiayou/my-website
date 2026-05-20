# 阶段 4C：Admin 认证真实联调与安全边界验证

> **文档状态**：历史验证有效 + **入口已多次演进**（§0：**现行路径以 README 为准**）  
> **对应阶段**：4C  
> **当前适用性**：**Cookie / Redis / Rotation / Replay / i18n / `invalid_token`** 等结论在**语义上仍成立**；**URL / Cookie 名 / 响应信封**请以 **`README`** §认证 HTTP 契约为准；§6 起表格为 **4C 原始归档**。  
> **最近同步**：2026-05-17（对齐 **`/api/auth/*`** 文档口径）

本文记录在本机 **真实 PostgreSQL + 真实 Redis + 真实 HTTP（curl）** 下对 Admin 认证主链路与安全边界的核验结论；不包含新业务扩展（RBAC、Site 登录、Gateway JWT 校验等均不在本轮范围）。

---

## 0. 与阶段 4F / 现行契约的关系（必读）

- **4C 成文时**：联调脚本的 **$AdminBase**（默认 **8081**）上**同时**承担 **认证与业务**（历史上曾为 **`/api/admin/auth/*`** 与 `/api/admin/ping` 等同进程）。  
- **4F 后**：**认证编排**迁至 **`auth`（默认 8083）**；**现行**编排路径为 **`/api/auth/admin/login`**、**`/api/auth/admin/refresh`**、**`/api/auth/admin/logout`**（经 Gateway：**`/api/auth/**`** → Auth）。**业务 API**（如 **`GET /api/admin/ping`**、**`GET /api/admin/auth/me`**）仍在 **admin**。  
- **权威清单**：**[`services/website-services/README.md`](../../../services/website-services/README.md)** §认证 HTTP 契约；脚本 **`scripts/local/run-4c-http-verify.ps1`** 已与 **`/api/auth/*`** 对齐。  
- **4C 中的验证结论**（Rotation、重放、Redis Key、i18n、`WWW-Authenticate` 等）仍可作为安全语义参考；**§6 起正文为 4C 时点原始抄录**（旧 Cookie 名、`ApiResponse.code`、HTTP 200+业务码等），**仅供对照历史**。  
- **端到端网关**：见 **[阶段4H](./阶段4H-Gateway唯一入口全链路真实联调.md)**（含 **2026-05** 契约修订说明）。

---

## 1. 阶段目标（本轮验收项）

1. Admin 登录真实可用  
2. Access JWT 可访问受保护接口（`/api/admin/ping`）  
3. Refresh Cookie 写入并由浏览器类客户端自动携带（curl `-c`/`-b` 模拟）  
4. Refresh Token Rotation 生效  
5. Refresh Replay 被拒绝且会话清理符合实现  
6. Logout 后会话与 Cookie 失效（幂等策略一致）  
7. Redis Key 命名与 TTL 符合设计预期  
8. `Accept-Language` 多语言在登录失败 / 校验失败 / 未带 Bearer 场景真实生效  
9. `invalid_token`（篡改 JWT）边界：`WWW-Authenticate` 含 `invalid_token`  
10. Cookie `HttpOnly` / `Path` / `SameSite` / `Secure`（profile）符合配置  
11. **CSRF 风险边界**：Refresh/Logout 依赖 Cookie 的跨站调用评估（结论见 §13）

---

## 2. 本地环境前提

- Java 21，Maven 构建 `services/website-services`。  
- Docker：`local-postgres`（5432）、`local-redis`（6379；容器环境示例中带 `REDIS_PASSWORD`，本地请以实际为准）。  
- 数据库：`jdbc:postgresql://localhost:5432/my_website_dev`，Flyway schema **已为 version 1**（存在 `admin_user` 等表）。  
- Admin 验证账号：已通过 `scripts/local/seed-admin-local-verify.sql` 插入（见 §5）。

---

## 3. 服务端口与访问入口（以代码为准）

| 组件 | 端口 | 说明 |
|------|------|------|
| **Admin API** | **8081** | `admin/src/main/resources/application.yml` → `server.port` |
| **Gateway** | **8080** | **推荐的外部统一入口**（架构意图）；`gateway` → `/api/admin/**` → `http://localhost:8081` |
| **Site API** | **8082** | 本轮未测 |

**Swagger / OpenAPI（Admin）**

- OpenAPI JSON：`GET http://127.0.0.1:8081/v3/api-docs`  
- Swagger UI：`http://127.0.0.1:8081/swagger-ui/index.html`（SpringDoc 默认）

**本轮 HTTP 实测**：优先直连 Admin（`8081`），与脚本 `scripts/local/run-4c-http-verify.ps1` 默认一致；Gateway 仅在脚本末尾可选探测（本轮未单独拉起 Gateway 进程）。

---

## 4. PostgreSQL / Redis 配置要点（local）

`application-local.yml` 中 **`DB_*` / `REDIS_*` / `SECURITY_*`** 均为占位引用，**不设 YAML 默认值**；请在 **`services/website-services/`** 执行 `cp .env.example .env` 并按本机填写。

| 配置项 | 变量（值见 `.env` / `.env.example`） |
|--------|-------------------------------------|
| JDBC URL | `DB_URL` |
| DB 用户 / 密码 | `DB_USERNAME` / `DB_PASSWORD` |
| Redis 主机 / 端口 / 密码 | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` |
| Flyway | **由 Admin 应用执行**，`classpath:db/migration`（脚本维护于 `infra` 模块） |

**示例片段（完整键见 `.env.example`）：**

```text
REDIS_PASSWORD=
SECURITY_JWT_SECRET=dev-dev-dev-signing-secret-at-least-32-bytes-x
SECURITY_REFRESH_COOKIE_SECURE=false
SPRING_PROFILES_ACTIVE=local
```

若本机数据库账号与范本不同，只需改 **`website-services/.env`** 中的 `DB_USERNAME`、`DB_PASSWORD`、`DB_URL`（并与 **`POSTGRES_*`**、**local-postgres** 实际账号一致）。

---

## 5. Admin 本地验证账号准备

参见仓库：

- `scripts/local/README-4C-seed.md`  
- `scripts/local/seed-admin-local-verify.sql`  

插入账号：`username=admin_local_verify`，明文口令 **`Local4c_Verify`**（仅本地文档与脚本注释）；口令哈希在 SQL 中以 BCrypt 存储。

---

## 6. Login 实测摘要（4C 归档：`POST /api/admin/auth/login`，非现行契约）

> **现行复测**：**`POST http://127.0.0.1:8083/api/auth/admin/login`**（或经 Gateway **`POST http://127.0.0.1:8080/api/auth/admin/login`**）；响应 **`{ message, data }`**，HTTP **401/400** 表示失败。下文表格为 **4C 同进程 Admin** 时期的 **`ApiResponse.code`** 口径。

**直连**：`http://127.0.0.1:8081/api/admin/auth/login`

| 场景 | Accept-Language | HTTP | code | 备注 |
|------|-----------------|------|------|------|
| 口令错误 | ja-JP / zh-CN / en-US | 200 | **1002** | `message` 随语言变化；用户名不存在与口令错误 **口径一致**（均 1002） |
| 不存在用户 | ja-JP / zh-CN / en-US | 200 | **1002** | 同上 |
| username 为空 | ja-JP | 200 | **1001** | 校验国际化 |
| password 为空 | zh-CN | 200 | **1001** | 校验国际化 |
| password 过长（129） | en-US | 200 | **1001** | 校验国际化 |
| 正确登录 | ja-JP | 200 | **0** | Body 含 `accessToken`；**响应 Body 不出现 refreshToken**；`Set-Cookie` 写入 `admin_refresh_token` |

**Set-Cookie（登录成功样本）**

```http
Set-Cookie: admin_refresh_token=<opaque>; Max-Age=604799; Expires=...; Path=/api/admin/auth; HttpOnly; SameSite=Strict
```

本轮 `SECURITY_REFRESH_COOKIE_SECURE=false`，响应头中 **未出现 `Secure`**（符合本地 HTTP profile）。

完整控制台输出已保存：`services/website-services/target/phase4c-http-output.txt`（JWT 仅在报告中保留前缀）。

---

## 7. Bearer 访问受保护接口（`GET /api/admin/ping`）

| 场景 | HTTP | 说明 |
|------|------|------|
| 合法 Bearer | 200 | `code=0`，业务 data 返回 |
| 不带 Bearer | 401 | **OAuth2 Resource Server**：JSON `code=1002`（EntryPoint 国际化）；参见 §11 |
| 篡改 JWT（签名段替换） | 401 | `WWW-Authenticate: Bearer error="invalid_token", ...`（实测存在 RFC6750 片段） |

---

## 8. Refresh / Rotation（4C 归档：`POST /api/admin/auth/refresh`；**现行：`POST /api/auth/admin/refresh`**）

- 请求 **无需 Body**，使用 `-b` 携带 Cookie 文件即可。  
- **轮换成功**：HTTP 200，`code=0`，Body 返回新 `accessToken`；响应再次 `Set-Cookie` 写入新的 `admin_refresh_token`（Max-Age 仍约 7 天）。

---

## 9. Refresh Replay（旧 Cookie 再用）

步骤：`Cookie A` 登录 → `refresh` 得到 `Cookie B` → 再用 **`Cookie A`** 调用 `refresh`。

**实测**

- HTTP **200**，业务 **`code=1002`**（统一未授权信封）。  
- **随后使用 `Cookie B` 再次 refresh**：HTTP **200**，**`code=1002`** → **会话已被 replay 防御整体吊销**（与 `RedisAdminRefreshSessionServiceImpl` 中 replay → `purgeSession` 语义一致）。

---

## 10. Logout（4C 归档：`POST /api/admin/auth/logout`；**现行：`POST /api/auth/admin/logout`**）

| 步骤 | HTTP | code | 备注 |
|------|------|------|------|
| 携带当前 Refresh Cookie logout | 200 | 0 | 响应 `Set-Cookie` 清除令牌（见 §12） |
| logout 后再 refresh | 200 | 1002 | Refresh 失效 |
| **无 Cookie** 再次 logout | 200 | 0 | **幂等**：仍为成功信封 |

**Logout 清除 Cookie（实测摘录）**

```http
Set-Cookie: admin_refresh_token=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/api/admin/auth; HttpOnly; SameSite=Strict
```

---

## 11. 国际化（HTTP）

**登录失败 / 校验**：见 §6，`1002` / `1001` 与文案符合设计预期。

**未带 Bearer 访问 `/api/admin/ping`**

- `Accept-Language: zh-CN` → `message` 中文  
- `Accept-Language: en-US` → `message` 英文  
- `Accept-Language: ko-KR` → **回落日文**（与 `app.i18n.supported-locales` 一致）

说明：在 Windows PowerShell 控制台直接打印 UTF-8 JSON 时，日文偶发显示为乱码；以 **`curl`/文件保存** 或 UTF-8 终端观测为准。并行观测脚本输出的日文 **`認証されていないかトークンが無効です`** 显示正常。

---

## 12. Cookie 安全属性核验（4C 归档：`admin_refresh_token`；**现行：`MYWEBSITE_REFRESH_TOKEN`，`Path=/api/auth/admin`，`SameSite=Lax`**）

配置来源：`admin/src/main/resources/application.yml` → `security.auth.*`，local 覆盖见 `application-local.yml`。

| 属性 | 本轮实测（local + `SECURITY_REFRESH_COOKIE_SECURE=false`） | 生产设计 |
|------|---------------------------------------------------------------|----------|
| HttpOnly | **是** | **必须 true** |
| Path | **`/api/admin/auth`** | **限定 Refresh 链路路径** |
| SameSite | **Strict** | **Strict（文档默认值）** |
| Secure | **未发送**（本地 HTTP 可接受） | **HTTPS 下设 `SECURITY_REFRESH_COOKIE_SECURE=true`** |
| Max-Age | **604799（≈7 天）** | 与 `security.auth.refresh-token-ttl: 7d` 一致 |

---

## 13. CSRF 安全边界评估（本轮结论）

### 13.1 当前状态（实现）

| 能力 | Cookie | Bearer |
|------|--------|--------|
| Login | 服务端下发 Refresh Cookie | Access JWT **仅在 Body**，走 Bearer |
| Refresh | **依赖 HttpOnly Cookie** | **不需要** Access Body |
| Logout | **依赖 HttpOnly Cookie** | **不需要** Access Body |

SameSite：**Strict**（见 §12）。

### 13.2 风险评估

- **Strict** 阻止浏览器在 **跨站** 导航/表单 POST 中携带 Cookie：对经典 CSRF（恶意站点诱导已登录管理员浏览器发起请求）有 **强抑制**。  
- Refresh / Logout **均为 state-changing POST** 且依赖 Cookie；在 **Strict** 未放宽、Admin API **不与不可信前端随意交叉挂载** 的前提下，**浏览器 CSRF 风险可控**。  
- **非浏览器客户端**（curl、服务端 Job）不受影响（无同源策略）；这不属于 CSRF，而属于 **令牌保管责任**。  

### 13.3 后续工单建议（无需本轮改造）

若未来：**前后端跨站部署**、需要 **`SameSite=None`**、或浏览器侧存在「可信站内嵌第三方发起 POST」等场景，应单独立项评估 **CSRF Token**、**双重 Cookie** 或 **OAuth 风格 Authorization Code + PKCE** 等机制。**本轮不修改安全模型。**

---

## 14. Swagger 与真实行为一致性

- `/v3/api-docs` 中 **`/api/admin/auth/login|refresh|logout`** 路径存在，`Admin Auth` 分组描述与「Refresh 仅 Cookie」「轮换」「logout 幂等」语义一致。  
- OpenAPI **不要求**重复展开每个 `Accept-Language` 字段（由全局 i18n 策略覆盖）。

---

## 15. Redis 观测（真实 `redis-cli`，无明文 Refresh）

命名常量：`AdminAuthRedisKeyConstants`

- `auth:admin:session:{sessionId}` → **hash**（仅存 refresh **SHA-256**，无明文 Refresh）  
- `auth:admin:refresh:{sha256(refresh)}` → **string** → sessionId  
- `auth:admin:refresh-replay:{sha256(oldRefresh)}` → **replay 指针**  
- `auth:admin:user:sessions:{adminUserId}` → **set**

### 15.1 登录后（样本）

- `auth:admin:session:*` **TTL ≈ 604800s**  
- `auth:admin:refresh:*` **TTL ≈ 604799s**  
- `auth:admin:user:sessions:1` **TTL = -1**（集合键当前实现不设 TTL）

### 15.2 Refresh 轮换后

- 旧 refresh 索引删除，新增 `auth:admin:refresh-replay:{旧哈希}`，并写入 **新** `auth:admin:refresh:{新哈希}`。

### 15.3 Replay 后

- **所有 `auth:admin:*` 键清空**（会话 purge）。

### 15.4 Logout 后

- **无 `auth:admin:*` 键**（与 purge 一致）。

---

## 16. 本轮修复的确定性问题（代码 / 脚本）

1. **`java -jar admin` 缺少 PostgreSQL 驱动**：`admin/pom.xml` 将 `postgresql` 依赖从 **`test`** 调整为 **`runtime`**。  
2. **Redis Refresh 会话 Bean 注册顺序错误**：`@Component` + `@ConditionalOnBean(RedisConnectionFactory)` 会在 Redis 自动配置完成前判定失败，导致 **运行时缺少 `AdminRefreshSessionService`**。引入 `RedisAdminRefreshSessionConfig`，并以 **`@ConditionalOnBean(StringRedisTemplate)` + `@ConditionalOnMissingBean(AdminRefreshSessionService)`** 注册 Redis 实现；测试 profile 继续走 `AdminRefreshSessionTestBeans` 内存实现。  
3. **联调脚本**：新增 `scripts/local/run-4c-http-verify.ps1`（覆盖登录失败多语言、未知用户、校验、Bearer、篡改 JWT、轮换/replay、logout、OpenAPI 摘要），以及种子 SQL / README。

---

## 17. 文档变更清单

| 文件 | 变更 |
|------|------|
| `docs/website-services/联调说明/阶段4C-Admin认证真实联调与安全边界验证.md` | **新增（本文）** |
| `services/website-services/README.md` | 增补阶段 4C 文档入口、`swagger-ui/index.html`、环境变量覆盖提示 |
| `scripts/local/*` | 新增联调脚本与种子说明 |

---

## 18. 构建结果

在 `services/website-services` 目录执行：

```bash
mvn -q -DskipTests validate
mvn -q test
```

**结果**：均 **SUCCESS**（本轮完成后实测）。

---

## 19. 是否可关闭阶段 4C

**可以关闭**，前提 acknowledge：

- **403**：当前后台路由尚未划分需 **`authenticated` 且显式 `denyAll` 的场景**，本轮未做独立 403 接口实测（RBAC 不在范围）。  
- **Gateway**：架构上推荐 **`8080` 入口**，本轮联调数据主要来自 **直连 `8081`**；若要以 Gateway 为验收主体，请在手册或 CI 中追加「Gateway 进程」步骤。
