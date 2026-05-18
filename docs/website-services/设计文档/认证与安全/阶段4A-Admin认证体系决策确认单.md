# 阶段 4A · Admin 认证体系决策确认单

> **文档状态**：历史决策记录（**认证 HTTP 进程归属以 4F 为准**）  
> **对应阶段**：4A / 4B  
> **当前适用性**：表中 **口令/JWT/Refresh/Redis** 等决策仍有效；**「Admin API 进程」承载登录** 的表述指 **4B 历史落地**，**4F 后登录入口在 `auth`**。  
> **最近同步**：2026-05-17  

本文档记录 **阶段 4A Admin 认证体系** 的决策对齐结论。**阶段 4A 已正式关闭**；**阶段 4B（Admin 认证基础链路）已在仓库落地**。字段级契约以 **`admin` 模块 Swagger / OpenAPI** 为准（本文不重复维护一份并行 Markdown 字段表）。

---

## 1. 总方案摘要

Admin API 采用 **Spring Security**：口令 **BCrypt**；请求侧 **短命 Access JWT（Bearer）**；**Refresh 为不透明随机串 + Redis**；Refresh **HttpOnly Cookie** 下发（**不回 JSON**）；会话模型 **STATELESS**；**Gateway（4B）不校验 JWT**。完整 RBAC、菜单权限等不在 4B 范围。

---

## 2. 关键设计决策表（已定案）

| # | 决策项 | 定案 | 是否仍需确认 |
| --- | --- | --- | --- |
| 1 | 密码加密 | **`BCryptPasswordEncoder`**；DB 仅存哈希；**口令最大长度**由 **`security.auth.admin-login-max-password-length`** 统一约束 | 否 |
| 2 | 登录标识（V1） | **仅 `username + password`**（不支持 email / phone 作为主标识） | 否 |
| 3 | Access Token 形态 | **JWT**；受众 **`ADMIN`**（配置 **`security.jwt.audience`**）；**HS256** | 否 |
| 4 | Access TTL | **15 分钟**（`security.jwt.access-token-ttl`） | 否 |
| 5 | Refresh 形态 | **不透明随机串**（非 JWT Refresh） | 否 |
| 6 | Refresh TTL | **7 天绝对会话锚点**；轮换 **不延长**（`security.auth.refresh-token-ttl`） | 否 |
| 7 | Refresh 存储 | **Redis**（生产）；测试排除 Redis 时 **内存替身**（语义对齐） | 否 |
| 8 | Rotation | **启用**；旧 Refresh **立即作废**；**旧 Refresh 重放 → 吊销服务端会话（基础版）** | 否 |
| 9 | 登出 | **删除 Redis 会话状态 + 清除 Refresh Cookie**；统一成功语义（Cookie 缺失亦幂等） | 否 |
| 10 | Access 黑名单 | **4B 不做**（靠 Access 短命自然过期） | 否 |
| 11 | 多端并发 | **允许**并行会话（独立 **`sessionId`**）；不设设备上限（4B） | 否 |
| 12 | Redis Key | **`auth:admin:session:{sessionId}`**、**`auth:admin:user:sessions:{adminUserId}`**、**`auth:admin:refresh:{sha256(refresh)}`**、**`auth:admin:refresh-replay:{sha256(oldRefresh)}`** | 否 |
| 13 | JWT Claims | **`sub`、`jti`、`aud`、`iss`、`iat`、`exp`、`token_type=ADMIN`、`admin_user_id`、`username`、`session_id`** | 否 |
| 14 | roles / permissions | **`permissions` 禁止入 JWT**；**`roles` 不作为 4B 认证链路依赖** | 否 |
| 15 | Spring Security | **OAuth2 Resource Server JWT + `JwtDecoder`**。**现行**：**admin** 除 actuator/swagger 外默认 Bearer；**无** **`/api/admin/auth/login|refresh|logout`**。**4B 归档**：曾放行上述三路径 | 否 |
| 16 | Gateway | **模式 A**：Gateway **不校验 JWT**（4B） | 否 |
| 17 | `auth` 模块边界 | **`JwtProperties`**（对称 JWT）；**`AuthConfig`**（含 **`JwtBeansEnabledCondition`** 保护的内嵌 JWT Bean 装配）；**`JwtTokenService`/`TokenClaims`/上下文映射**；**不**承载 **`security.auth.*` 绑定类**（该类在 **`admin`**：**`AdminAuthProperties`**）；**不**承载登录业务；**不**直连 Redis | 否 |
| 18 | `admin` 模块边界 | **`AdminSecurityConfig`**（SecurityFilterChain）、**`AdminAuthProperties`**（`security.auth.*`）、登录三接口、**Refresh Cookie**、**`AdminRefreshSessionService`（Redis 实现）**、**`AdminAuthService`** | 否 |

---

## 3. 开放问题收口说明

历史上「Body vs Cookie」「HS256 vs RS256」「roles 是否入 JWT」「多端上限」「网关是否验签」「登录标识形态」等问题 **已全部定案**，正文口径以《阶段4A-Admin认证体系详细设计》**§13～§18** 为准。

---

## 4. 已定案方案快照（对照实现）

| 维度 | 定案 |
| --- | --- |
| **Cookie（Refresh）** | **`HttpOnly=true`**；生产 **`Secure=true`、`SameSite=Strict`**；本地 HTTP 允许 **`Secure=false`**（`SECURITY_REFRESH_COOKIE_SECURE`）；**Path**：**现行** **`/api/auth/token/refresh`**（与 **`auth`** `refresh-cookie-path` 一致）；**4B 归档**：`/api/admin/auth` |
| **配置前缀** | **`security.jwt.*`**（issuer/audience/secret/access TTL）；**`security.auth.*`**（refresh TTL + cookie 策略 + 口令长度上限） |
| **JWT Bean 装配** | **`security.jwt.secret` 非空** 才启用对称 JWT Bean（避免 **`site-app`** 未配置密钥时测试上下文失败）；**Admin 运行时必须配置密钥** |

---

## 5. 阶段 4B 落地核对清单（已实现）

### 5.1 `auth`

- **`JwtProperties`** 绑定；**`AuthConfig`** 在内嵌配置类上配合 **`JwtBeansEnabledCondition`** 装配 **`JwtEncoder`/`JwtDecoder`/`TokenService`**（实现 **`JwtTokenService`**）。
- **`JwtTokenService`/`JwtTokenServiceImpl`**：**现行** Access JWT **RS256**（见 **阶段 4G**）；签发 / 校验 **`aud`、`subject_type`**（与 **`SubjectType`** 枚举比对）等核心语义。
- **编排 HTTP**：**`AuthAdminLoginController`** 等 — **`POST /api/auth/admin/login`**、**`POST /api/auth/token/refresh`**、**`POST /api/auth/logout`**。
- **`AdminAuthService`** / **`AdminAuthServiceImpl`**：**username** 登录、`ACTIVE` 校验、模糊失败语义。
- **`RedisAdminRefreshSessionServiceImpl`**：Refresh 轮换 + replay 吊销；测试可用 **`InMemoryAdminRefreshSessionServiceImpl`**。
- **`IssuedAccessToken`、`RefreshTokenGenerator`、`JwtClaimConstants`、`AuthenticatedUserUtils`** 等与实现对齐。

### 5.2 `admin`

- **`AdminSecurityConfig`**：**`STATELESS`**、OAuth2 JWT Resource Server；匿名放行 Swagger/Actuator；**其余默认 Bearer**（**现行无** **`/api/admin/auth/login|refresh|logout`**）。
- **`GET /api/admin/auth/me`**（**`AdminAuthMeController`**）：业务上下文接口。
- **4B 归档**：**`admin`** 曾内含 **`AdminAuthController`**、登录 **`AdminAuthService`**、**Redis Refresh** 编排等；已迁至 **`auth`**。

### 5.3 Swagger

- **`adminBearer`** scheme；**`Admin Ping`** 标注 **`@SecurityRequirement`** 作为 Bearer 示例。

### 5.4 测试

- **`JwtTokenServiceImplTest`**、**`InMemoryAdminRefreshSessionServiceImplTest`**、**`AdminAuthMvcTest`** 等（**`auth`**）；默认 **`mvn test`** **不强依赖本机 Redis**。

---

## 6. 暂缓到后续阶段的内容

以下内容 **不在 4B**，除非另行立项：

- **完整 RBAC**、菜单权限、按钮级权限。
- **登录失败锁定 / CAPTCHA / MFA**。
- **Site 前台认证**（`TokenAudience.SITE`）。
- **Gateway 模式 B（边缘 JWT 校验）**。
- **OAuth2 第三方登录 / 扫码登录 / 密码找回**。
- **Access Token 服务端黑名单（强制即时作废）**（除非产品有强诉求）。

---

## 7. 阶段状态声明

- **阶段 4A**：**已收口并关闭**。
- **阶段 4B**：**Admin 认证基础链路已完成**（代码 + 默认构建测试 + Swagger）。
- **建议下一阶段**：**阶段 4C — Admin 认证真实联调与安全边界验证**（HTTPS、反向代理、`Set-Cookie` 域名路径、网关转发、`SameSite` 真实浏览器行为、Redis 运维观测等）。

---

## 相关文档

- [阶段4A-Admin认证体系详细设计](./阶段4A-Admin认证体系详细设计.md)
