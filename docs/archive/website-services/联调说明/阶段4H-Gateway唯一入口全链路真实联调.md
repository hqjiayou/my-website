> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/website-services/CURRENT_STATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# 阶段 4H：Gateway 唯一入口全链路真实联调

> **文档状态**：当前有效（运行态验收 + 契约口径）  
> **前置**：4G（RS256 + JWKS）、4G.1（Auth ↔ Admin 直连烟测）、**4H.0**（RequestId / 访问日志 / Token 无效 JSON）  
> **最近同步**：2026-05-20  

---

## 1. 阶段目标

以 **`Gateway :8080`** 为**唯一对外入口**，在真实进程（PostgreSQL + Redis + Auth + Admin + Gateway）下验证：

```text
Client → Gateway 8080
           ├── /api/auth/**  → Auth 8083（登录 / 刷新 / 登出 / JWKS）
           └── /api/admin/** → Admin 8081（Resource Server + 业务 API）
```

证明：**Authorization、Set-Cookie、Cookie、WWW-Authenticate、X-Request-Id** 经网关**不失真**；Gateway / Auth / Admin 日志可用同一 **requestId** 串联；Redis Refresh 会话与重放防御符合设计。

**不在本轮**：RBAC、Site 登录、Gateway JWT 验签、库表/Flyway 变更。

---

## 2. 认证 HTTP 契约（现行 · 禁止旧前缀）

| 语义 | 路径 | 模块 |
|------|------|------|
| 管理端登录 | **`POST /api/auth/admin/login`** | auth |
| 刷新 Access | **`POST /api/auth/admin/refresh`** | auth |
| 登出 | **`POST /api/auth/admin/logout`** | auth |
| 管理端注册（bootstrap） | **`POST /api/auth/admin/register`** | auth |
| 管理端业务探活 | **`GET /api/admin/ping`** | admin（需 Bearer） |
| 管理端上下文 | **`GET /api/admin/auth/me`** | admin |

**禁止**对外文档/脚本再写 **`/api/admin/auth/login`** 等旧编排前缀。**4I 已移除** ~~`/api/auth/token/refresh`~~、~~`/api/auth/logout`~~ 兼容映射，仅保留 **`/api/auth/admin/*`**。

**Refresh Cookie**：`MYWEBSITE_REFRESH_TOKEN`，**`Path=/api/auth/admin`**，`HttpOnly`，**SameSite=Lax**（配置项 `mywebsite.security.token.refresh-cookie-same-site`）；本地 **`SECURITY_REFRESH_COOKIE_SECURE=false`**。

**成功响应**：Auth 编排接口 Body 为 **`{ "message": "success", "data": ... }`**（**省略** `code` 字段）；Admin 业务 **`ApiResponse`** 成功含 **`code: 0`**。

---

## 3. 环境前提

| 组件 | 要求 |
|------|------|
| **local-postgres** | `localhost:5432`，库 **`my_website_dev`**，已 Flyway |
| **local-redis** | `localhost:6379`，**`REDIS_PASSWORD` 与 `.env` 一致**（占位 `CHANGE_ME` 会导致 NOAUTH） |
| **Auth** | `8083`，`AUTH_JWKS_URI` 可被 Admin 访问 |
| **Admin** | `8081`，`AUTH_JWKS_URI=http://localhost:8083/.well-known/jwks.json` |
| **Gateway** | `8080` |
| **种子账号** | `scripts/local/seed-admin-local-verify.sql` → **`admin_local_verify` / `Local4c_Verify`** |

**PowerShell 污染排查**（避免 IDE 注入错误 `DB_*`）：

```powershell
Remove-Item Env:DB_PASSWORD, Env:DB_USERNAME, Env:DB_URL -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "local"
$env:SECURITY_REFRESH_COOKIE_SECURE = "false"
```

**启动示例**（`services/website-services` 目录，三终端）：

```powershell
mvn -q -pl auth -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8083 -DAUTH_APP_PORT=8083"
mvn -q -pl admin -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8081"
mvn -q -pl gateway -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8080"
```

---

## 4. Gateway 路由（仓库事实）

见 `gateway/src/main/resources/application.yml`：

| 顺序 | route id | Predicate | 下游 |
|------|----------|-----------|------|
| 1 | `auth-service-api` | **`/api/auth/**`** | `http://localhost:8083` |
| 2 | `admin-service` | `/api/admin/**` | `http://localhost:8081` |
| 3 | `site-service` | `/api/site/**` | `http://localhost:8082`（预留） |

**`/api/auth/**` 必须在 `/api/admin/**` 之前**，避免 `POST /api/auth/admin/login` 被 admin 路由吞掉。

Spring Cloud Gateway **默认转发** `Authorization`、`Cookie`、下游 `Set-Cookie` / `WWW-Authenticate`；本仓库 **未** `StripPrefix`。

---

## 5. 自动化联调脚本

**推荐**（仅经 Gateway）：

```powershell
cd services/website-services
.\scripts\local\run-4h-gateway-auth-flow-verify.ps1
.\scripts\local\run-4h-gateway-auth-flow-verify.ps1 -RequestId "4h-demo-001" -ObserveRedis
```

覆盖：Login → Bearer Ping → Refresh → Replay → Logout → 错误登录 / 无 Token / Invalid Token / Refresh 无 Cookie。

**历史脚本**（直连 Auth + 部分 Gateway 头检查）：`scripts/local/run-4c-http-verify.ps1`（已对齐 **`/api/auth/admin/refresh|logout`**）。

---

## 6. 联调检查表与预期

### 6.1 Login（Gateway）

```http
POST http://localhost:8080/api/auth/admin/login
Content-Type: application/json
Accept-Language: ja-JP
X-Request-Id: <optional>

{"username":"admin_local_verify","password":"Local4c_Verify"}
```

| 项 | 预期 |
|----|------|
| HTTP | 200 |
| Body | `message=success`，`data.accessToken` 存在，**无** Refresh 明文 |
| Set-Cookie | `MYWEBSITE_REFRESH_TOKEN`，`Path=/api/auth/admin`，HttpOnly |
| JWT | RS256，`kid` 与 JWKS 一致，`aud`/ `subject_type` 符合 4G |
| X-Request-Id | 响应头存在；Gateway + Auth 日志同一 id |

### 6.2 Bearer Ping（Gateway → Admin）

```http
GET http://localhost:8080/api/admin/ping
Authorization: Bearer <accessToken>
```

| 项 | 预期 |
|----|------|
| HTTP | 200 |
| Body | `code=0`，`message=success` |
| 验签 | Admin 仅 `AUTH_JWKS_URI`，无私钥 |

### 6.3 Refresh（Gateway）

```http
POST http://localhost:8080/api/auth/admin/refresh
Cookie: MYWEBSITE_REFRESH_TOKEN=...
```

| 项 | 预期 |
|----|------|
| HTTP | 200，`message=success`，新 `accessToken` |
| Set-Cookie | 轮换后新 Refresh（若启用轮换） |
| Redis | 旧 refresh index 失效，新 index + session 更新 |

### 6.4 Refresh Replay

1. 登录得 Cookie A  
2. Refresh(A) → Cookie B  
3. 再 POST refresh **仅带 Cookie A**

| 项 | 预期 |
|----|------|
| HTTP | **401** |
| Body | `code=1002`，i18n `admin.auth.refresh.invalid` |
| 日志 | `auth security refresh_replay_detected` |
| Redis | session 吊销；再用 B refresh 亦失败（整会话吊销策略） |

### 6.5 Logout

```http
POST http://localhost:8080/api/auth/admin/logout
```

| 项 | 预期 |
|----|------|
| 有 Cookie | 200，`message=success`；Clear-Cookie；Redis session/index 清理；`logout_success` 日志 |
| 无 Cookie | 仍 200 幂等成功 |

### 6.6 错误链路（经 Gateway）

| 场景 | 预期 |
|------|------|
| 错误密码登录 | 401 + JSON + `login_failed` 日志 |
| 无 Bearer Ping | 401 + `code=1002` + `auth.unauthorized` |
| Invalid Bearer | 401 + `auth.token.invalid` + **WWW-Authenticate** 含 `invalid_token` |
| Refresh 无 Cookie | 401 + JSON，不吞 body |

---

## 7. Header / Cookie 透传表（设计目标）

| 内容 | 经 Gateway 透传 |
|------|-----------------|
| Authorization | 是 |
| Set-Cookie | 是 |
| Cookie | 是 |
| WWW-Authenticate | 是 |
| X-Request-Id | 是（`RequestIdGatewayFilter` + 下游 `RequestLoggingFilter`） |

---

## 8. RequestId 日志串联示例

```text
gateway access requestId=4h-demo-001 method=POST path=/api/auth/admin/login route=auth-service-api status=200 costMs=...
auth-service access requestId=4h-demo-001 method=POST path=/api/auth/admin/login status=200 costMs=...
admin-service access requestId=4h-demo-001 method=GET path=/api/admin/ping status=200 costMs=...
```

联调时在请求中加 **`X-Request-Id: 4h-demo-001`**，在三端日志中检索该字符串。

---

## 9. Redis 观测（禁止记录明文 token）

```bash
docker exec local-redis redis-cli -a '<your-redis-password>' KEYS 'auth:admin:*'
docker exec local-redis redis-cli -a '<password>' TTL 'auth:admin:session:<sessionId>'
```

| 阶段 | 预期结构 |
|------|----------|
| Login 后 | `auth:admin:session:{sid}`、`auth:admin:refresh:{sha256}`、`auth:admin:user:sessions:{userId}` |
| Refresh 后 | 新 refresh index；旧 hash 进入 replay 或删除 |
| Replay 后 | session 相关 key 清理 / replay 标记 |
| Logout 后 | session + refresh index 删除 |

---

## 10. 本轮代码/配置变更（2026-05-20）

| 项 | 说明 |
|----|------|
| **路径统一** | **`/api/auth/admin/refresh`**、**`/api/auth/admin/logout`**；Cookie **`Path=/api/auth/admin`** |
| **4I 收口** | 删除旧兼容路径；RequestId 单头；Redis 脚本带密码 |
| **脚本** | 新增 `run-4h-gateway-auth-flow-verify.ps1`；更新 `run-4c-http-verify.ps1` |
| **文档** | README、本文件、路线图同步 |

---

## 11. 构建与测试

```bash
cd services/website-services
mvn -q -DskipTests validate
mvn -q test
```

**2026-05-20**：`mvn -q test` **通过**。

---

## 12. 结论

| 问题 | 结论 |
|------|------|
| Gateway 是否可作为唯一外部入口？ | **是**（路由、透传、契约与自动化脚本已就绪；**需本机三服务 + Redis 口令对齐后跑通 `run-4h-gateway-auth-flow-verify.ps1` 作最终签字**） |
| 是否进入 4I？ | **可进入**（4H 代码/脚本/文档已收口；4I 做迁移清理与口径统一，不阻塞于单次实机失败项） |

**实机注意**：若 Admin `actuator/health` 为 **503**，优先检查 **`REDIS_PASSWORD`** 是否与 **local-redis** 一致（`NOAUTH` / `WRONGPASS`）。

---

## 13. 参考

- [阶段4H.0-请求链路日志与Token错误响应完善说明](../设计文档/认证与安全/阶段4H.0-请求链路日志与Token错误响应完善说明.md)
- [阶段4G.1-RS256与JWKS真实运行烟测](./阶段4G.1-RS256与JWKS真实运行烟测.md)
- [后端开发路线图与阶段状态](../总览/后端开发路线图与阶段状态.md)
- [`services/website-services/README.md`](../../../services/website-services/README.md)
