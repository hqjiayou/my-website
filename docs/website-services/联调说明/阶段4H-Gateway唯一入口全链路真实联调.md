# 阶段 4H：Gateway 唯一入口全链路真实联调



> **文档状态**：联调结论（运行态验收）**+ 契约修订说明（2026-05）**  

> **前置**：阶段 4G（RS256 + JWKS）、**4G.1**（Auth ↔ Admin 直连烟测）  

> **最近同步**：2026-05-17  



---



## 0. 契约修订（2026-05 · 必读）



认证编排路径已由旧前缀 **`/api/admin/auth/*`** 收敛为统一 **`/api/auth/*`**（见 **[`services/website-services/README.md`](../../../services/website-services/README.md)** §认证 HTTP 契约）。



| 语义 | 现行路径 |

|------|-----------|

| 管理端登录 | **`POST /api/auth/admin/login`** |

| 刷新 Access | **`POST /api/auth/token/refresh`** |

| 登出 | **`POST /api/auth/logout`** |

| 管理端上下文 | **`GET /api/admin/auth/me`**（Admin Resource Server） |

| 前台上下文（占位） | **`GET /api/site/auth/me`**（Site-app Resource Server） |



**Refresh Cookie**：默认 **`MYWEBSITE_REFRESH_TOKEN`**，**`Path=/api/auth/token/refresh`**（配置前缀 **`mywebsite.security.token`**）。



**响应**：Auth 编排接口以 **HTTP 状态码**为主，Body **`{ message, data }`**（**无**业务数值 **`code`**）；§4 下文描述以现行代码为准。



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

| 1 | `auth-service-api` | **`Path=/api/auth/**`** | `http://localhost:8083`（Auth） |

| 2 | `admin-service` | `Path=/api/admin/**` | `http://localhost:8081`（Admin，含 **`GET /api/admin/auth/me`**） |

| 3 | `site-service` | `Path=/api/site/**` | `http://localhost:8082`（Site-app，含 **`GET /api/site/auth/me`**） |



**要点**：**`/api/auth/**` 必须列在 `/api/admin/**`、`/api/site/**` 之前**，否则 **`POST /api/auth/admin/login`** 等会被 **`/api/admin/**`** 误匹配。



Spring Cloud Gateway **默认将客户端请求头（含 `Authorization`）与 Cookie 转发到下游**；本仓库 **未** 使用 `StripPrefix`，路径保持与直连一致。



---



## 4. 真实烟测记录（现行契约）



以下均在 **`http://localhost:8080`**（Gateway）上执行，终端 **PowerShell**；测试账号 **`admin_local_verify` / `Local4c_Verify`**（见 `scripts/local/seed-admin-local-verify.sql`）。也可使用 **`scripts/local/run-4c-http-verify.ps1`**（已对齐 **`/api/auth/*`**）。



### 4.1 经 Gateway 登录



- **请求**：**`POST /api/auth/admin/login`**，`Content-Type: application/json`  

- **Body**：`{"username":"admin_local_verify","password":"Local4c_Verify"}`  

- **结果**：HTTP **200**；JSON **`message`** + **`data.accessToken`** / **`data.accessTokenExpiresIn`**（**无**业务 **`code`** 字段）。  

- **Set-Cookie（Refresh）**：默认名称 **`MYWEBSITE_REFRESH_TOKEN`**，**`Path=/api/auth/token/refresh`**，**HttpOnly**，**SameSite=Lax**（见 **`mywebsite.security.token`**）；本地 **`SECURITY_REFRESH_COOKIE_SECURE=false`** 时 **无 `Secure` 属性**（便于 HTTP 联调）。



### 4.2 经 Gateway 带 Bearer 访问 Admin



- **请求**：`GET /api/admin/ping`，`Authorization: Bearer <accessToken>`  

- **结果**：HTTP **200**；业务 **`ApiResponse`**（含 **`code`** 字段）成功载荷。  



说明：**Gateway 透传 `Authorization`**，**Admin** 仍仅通过 **`AUTH_JWKS_URI` → Auth JWKS** 验签，与 4G 一致。



### 4.3 非法 Token（负向）



- **请求**：`GET /api/admin/ping`，`Authorization: Bearer <篡改的 token>`  

- **结果**：HTTP **401**。



### 4.4 经 Gateway Refresh



- **前提**：使用同一 **Web 会话**携带登录下发的 **Refresh Cookie**（或用浏览器 / `curl -b -c` 等价行为）。  

- **请求**：**`POST /api/auth/token/refresh`**（无 Body，依赖 Cookie）。  

- **结果**：HTTP **200**，**`message`** + **`data.accessToken`**；若启用轮换，响应可带 **`Set-Cookie`** 更新 Refresh。  

- **复验**：`GET /api/admin/ping` + **`Authorization: Bearer <newAccessToken>`** → **200**。



### 4.5 经 Gateway Logout



- **请求**：**`POST /api/auth/logout`**，携带 **Refresh Cookie**。  

- **结果**：HTTP **200**，**`message`**；响应 **`Set-Cookie`** 清除 Refresh（`Max-Age=0` 等）。  

- **再调用** **`POST /api/auth/token/refresh`**（会话已无有效 Refresh）：  

  - **现行**：HTTP **401**，Body **`{ message, data: null }`**（与 Auth **`GlobalExceptionHandler`** 一致）。  



### 4.6 Access JWT 语义（与文档对齐）



- **`aud`**：Admin Access 面向配置的 **`security.jwt.audience`**（默认 **`ADMIN`**）。  

- **`subject_type`**：**`SubjectType`**（**ADMIN** / **SITE**），与 **`aud`**「面向谁」语义分离；**`auth`** 内解析时令牌声明还原为 **`SubjectType`** 后与期望 **枚举比对**（见 **`JwtTokenServiceImpl`**）。



---



## 5. 排查备忘（联调失败时）



| 现象 | 优先检查 |

|------|----------|

| Login 404 或打到 Admin | Gateway **路由顺序**；**`/api/auth/**`** 是否在 **`/api/admin/**`** **之前**。 |

| Refresh 不带 Cookie | Cookie **`Path`** 是否为 **`/api/auth/token/refresh`**；请求 URL 是否与之同前缀（经网关：`http://localhost:8080/api/auth/token/refresh`）。 |

| 直连 Admin 200，经 Gateway 401 | **`Authorization`** 是否被客户端发到 Gateway；网关下游是否丢失头（本仓库默认不丢）。 |

| Admin 验签失败 | **Admin** 能否访问 **`AUTH_JWKS_URI`**；与 **4G.1** 相同，不经过 Gateway 时先验证 Auth JWKS。 |



---



## 6. 代码 / 配置变更（归档）



| 项 | 说明 |

|----|------|

| `gateway/.../application.yml` | **`Path=/api/auth/**`** → Auth；注释强调须在 **`/api/admin/**`、`/api/site/**`** 之前。 |

| Auth / Admin / Site-app | 编排 **`/api/auth/*`**；业务上下文 **`/api/*/auth/me`**；JWT **`subject_type`** ↔ **`SubjectType`**。 |



---



## 7. 构建与测试



```bash

cd services/website-services

mvn -q -DskipTests validate

mvn -q test

```



---



## 8. 结论：阶段 4H



**阶段目标已达成并已收口。** 已在 **真实进程**下验证 **Gateway → Auth 编排 → Bearer 访问 Admin → Refresh → Logout → 非法 Refresh** 等链路。



**对外对接**：请以 **`README`** §认证 HTTP 契约为唯一清单；脚本 **`run-4c-http-verify.ps1`** 与之对齐。



---



## 9. 参考



- [阶段4G.1-RS256与JWKS真实运行烟测](./阶段4G.1-RS256与JWKS真实运行烟测.md)  

- [后端开发路线图与阶段状态](../总览/后端开发路线图与阶段状态.md)  

- [`services/website-services/gateway/src/main/resources/application.yml`](../../../services/website-services/gateway/src/main/resources/application.yml)  

- [`services/website-services/README.md`](../../../services/website-services/README.md)（§认证 HTTP 契约）

