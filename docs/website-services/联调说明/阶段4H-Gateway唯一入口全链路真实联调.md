# 阶段 4H：Gateway 唯一入口全链路真实联调

> **文档状态**：联调结论（运行态验收）  
> **前置**：阶段 4G（RS256 + JWKS）、**4G.1**（Auth ↔ Admin 直连烟测）  
> **最近同步**：2026-05-17  

---

## 1. 阶段目标

在 **单一对外入口 `Gateway :8080`** 上，验证完整 **Admin 认证主链路**（不经浏览器 SPA）：

`Client → Gateway(8080) → Auth(8083) / Admin(8081)`

与 **4G.1** 的差异：4G.1 验证 **Auth → Admin** 直连；本阶段验证 **经 Gateway 的路径、Header（Authorization）、Cookie（Refresh）** 是否端到端正确。

---

## 2. 基础设施与启动方式

沿用 **4G.1**：本机 **PostgreSQL / Redis**（如 Docker 容器 `local-postgres` / `local-redis`），**Auth 8083、Admin 8081、Gateway 8080**。

**PowerShell 污染排查**（若 IDE / 系统曾注入 `DB_USERNAME=mywebsite`、`DB_PASSWORD=CHANGE_ME` 等，会覆盖 `.env`，导致 Auth/Admin **连库失败**）：

```powershell
Remove-Item Env:DB_PASSWORD, Env:DB_USERNAME, Env:DB_URL -ErrorAction SilentlyContinue
```

在 **`services/website-services`** 目录分别启动（示例）：

```powershell
mvn -q -pl auth -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8083 -DAUTH_APP_PORT=8083"
mvn -q -pl admin -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8081"
mvn -q -pl gateway -am spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=8080"
```

若环境存在 **`SERVER_PORT`** 等与默认 **8083** 冲突的变量，需删除或为上述 JVM 参数显式指定端口。**Admin 的 `AUTH_JWKS_URI`** 必须指向 **Auth** 上 JWKS（默认 `http://localhost:8083/.well-known/jwks.json`）。

---

## 3. Gateway 路由（仓库事实）

配置见 **`services/website-services/gateway/src/main/resources/application.yml`**。

| 顺序 | 路由 id | Predicate | 转发目标 |
|------|---------|-----------|----------|
| 1 | `auth-admin-auth` | `Path=/api/admin/auth/**` | `http://localhost:8083`（Auth） |
| 2 | `auth-site-auth` | `Path=/api/site/auth/**` | `http://localhost:8083` |
| 3 | `admin-service` | `Path=/api/admin/**` | `http://localhost:8081`（Admin） |
| 4 | `site-service` | `Path=/api/site/**` | `http://localhost:8082` |

**要点**：**`/api/admin/auth/**` 必须列在 `/api/admin/**` 之前**，否则 Login/Refresh/Logout 会被误匹配到 Admin。

Spring Cloud Gateway **默认将客户端请求头（含 `Authorization`）与 Cookie 转发到下游**；本仓库 **未** 使用 `StripPrefix`，路径保持与直连一致。

---

## 4. 真实烟测记录（本轮）

以下均在 **`http://localhost:8080`**（Gateway）上执行，终端 **PowerShell**；测试账号 **`admin_local_verify` / `Local4c_Verify`**（见 `scripts/local/seed-admin-local-verify.sql`）。

### 4.1 经 Gateway 登录

- **请求**：`POST /api/admin/auth/login`，`Content-Type: application/json`  
- **Body**：`{"username":"admin_local_verify","password":"Local4c_Verify"}`  
- **结果**：HTTP **200**；JSON **`code == 0`**；`data.accessToken` 存在。  
- **Set-Cookie（Refresh）**：名称 **`admin_refresh_token`**，`Path=/api/admin/auth`，**HttpOnly**，**SameSite=Strict**；本地 **`SECURITY_REFRESH_COOKIE_SECURE=false`** 时 **无 `Secure` 属性**（便于 HTTP 联调）。

### 4.2 经 Gateway 带 Bearer 访问 Admin

- **请求**：`GET /api/admin/ping`，`Authorization: Bearer <accessToken>`  
- **结果**：HTTP **200**；业务成功（如 `data.message` 含 admin 就绪信息）。  

说明：**Gateway 透传 `Authorization`**，**Admin** 仍仅通过 **`AUTH_JWKS_URI` → Auth JWKS** 验签，与 4G 一致。

### 4.3 非法 Token（负向）

- **请求**：`GET /api/admin/ping`，`Authorization: Bearer <篡改的 token>`  
- **结果**：HTTP **401**。

### 4.4 经 Gateway Refresh

- **前提**：使用同一 **Web 会话**携带登录下发的 **Refresh Cookie**（或用浏览器 / `curl -b -c` 等价行为）。  
- **请求**：`POST /api/admin/auth/refresh`（无 Body，依赖 Cookie）。  
- **结果**：HTTP **200**，`code == 0`，返回 **新 `accessToken`**；若启用轮换，响应可带 **`Set-Cookie`** 更新 Refresh。  
- **复验**：`GET /api/admin/ping` + **`Authorization: Bearer <newAccessToken>`** → **200**。

### 4.5 经 Gateway Logout

- **请求**：`POST /api/admin/auth/logout`，携带 **Refresh Cookie**。  
- **结果**：HTTP **200**，`code == 0`；响应 **`Set-Cookie`** 清除 Refresh（`Max-Age=0` 等）。  
- **再调用** `POST /api/admin/auth/refresh`（会话已无有效 Refresh）：  
  - **实测**：HTTP **200**，业务体 **`code == 1002`（`UNAUTHORIZED`）**，`data` 为 `null`（认证侧业务语义，**非强制 HTTP 401**）。  
  - **结论**：以当前 **Auth 接口设计**为准，记为 **Logout 后会话不可刷新**；前端应对 **`code != 0`** 处理未认证。

---

## 5. 排查备忘（联调失败时）

| 现象 | 优先检查 |
|------|----------|
| Login 打到 Admin 或 404 | Gateway **路由顺序**；`/api/admin/auth/**` 是否在 `/api/admin/**` **之前**。 |
| 直连 Admin 200，经 Gateway 401 | **`Authorization`** 是否被客户端发到 Gateway；网关下游是否丢失头（本仓库默认不丢）。 |
| 有 Access、Refresh 后 Refresh 失败 | **Cookie** 是否随请求发往 `http://localhost:8080` 且 **Path** 为 `/api/admin/auth`；**SameSite=Strict** 在 localhost 同站下发无问题。 |
| Admin 验签失败 | **Admin** 能否访问 **`AUTH_JWKS_URI`**；与 **4G.1** 相同，不经过 Gateway 时先验证 Auth JWKS。 |

---

## 6. 代码 / 配置变更（本轮）

| 项 | 说明 |
|----|------|
| `gateway/.../application.yml` | 增加**注释**：强调 **`/api/admin/auth/**` 路由顺序**，避免后续误改。 |

无 RS256/JWKS、无 Admin Resource Server 逻辑变更。

---

## 7. 构建与测试

```bash
cd services/website-services
mvn -q -DskipTests validate
mvn -q test
```

**要求**：全部通过（与本轮仅能改 YAML 注释的预期一致）。

---

## 8. 结论：阶段 4H 是否关闭

**可关闭。** 已在 **真实进程** 下跑通：

**Login（Gateway）→ Bearer 调 Admin 受保护接口 → Refresh → 新 Token 可访问 → Logout → Refresh 业务失败（code 1002）→ 非法 Bearer 401**。

认证入口已从 **服务间直连** 收敛为 **Gateway 统一入口**，前端后续只需对接 **`http(s)://<gateway>/api/admin/...`**。

---

## 9. 参考

- [阶段4G.1-RS256与JWKS真实运行烟测](./阶段4G.1-RS256与JWKS真实运行烟测.md)  
- [后端开发路线图与阶段状态](../总览/后端开发路线图与阶段状态.md)  
- [`services/website-services/gateway/src/main/resources/application.yml`](../../../services/website-services/gateway/src/main/resources/application.yml)  
