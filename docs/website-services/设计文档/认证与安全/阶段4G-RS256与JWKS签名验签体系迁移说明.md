# 阶段 4G：RS256 与 JWKS 签名验签体系迁移

> **文档状态**：当前有效  
> **对应阶段**：4G（已完成）  
> **当前适用性**：描述 **JWT 签发/验签** 在仓库中的事实配置；与 **[阶段4F](./阶段4F-Auth单服务收敛与认证主链路上提落地说明.md)** 衔接。  
> **最近同步**：2026-05-17  

---

## 1. 目标达成

- **Auth**：持有 **RSA 私钥**，使用 **RS256** 签发 Access JWT；Header 含 **`kid`**（与 JWKS 中 JWK 的 `kid` 一致）。暴露 **`GET /.well-known/jwks.json`**（仅公钥，无需认证）。
- **Admin**：**不持有** `SECURITY_JWT_SECRET` 或任何 JWT **签名**密钥；通过 **`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`**（环境变量 **`AUTH_JWKS_URI`**）拉取 JWKS 并验签；保留 **`issuer` / `audience`** 等 Claims 校验与 **`AuthContext`** 链路。
- **Site**：本轮未实现前台登录；后续若启用 Resource Server，复用同一 **`AUTH_JWKS_URI`** 指向 Auth 即可（见 **§5**）。
- **Gateway**：**不**在 4G 做 JWT 验签；与 4F 一致，全路径真实联调在 **4H**。

---

## 2. 配置键（与 `.env.example` 一致）

| 用途 | 环境变量 / 说明 |
|------|-----------------|
| Auth 私钥 PEM 路径 | `AUTH_JWT_PRIVATE_KEY_PATH`（或 `mywebsite.auth.jwt.private-key-path` 等绑定） |
| Auth 公钥 PEM 路径 | `AUTH_JWT_PUBLIC_KEY_PATH` |
| JWT Header `kid` | `AUTH_JWT_KEY_ID` |
| Admin（及未来 Site）JWKS 地址 | `AUTH_JWKS_URI`，默认 `http://localhost:8083/.well-known/jwks.json` |

**勿将真实私钥提交仓库**；本地仅通过路径引用 `secrets/` 下 PEM（见 **`services/website-services/scripts/local/README-4G-jwks.md`**）。

---

## 3. 实现位置（代码锚点）

- **Auth**
  - RSA 密钥加载、**`JwtEncoder` / 验证用 `JwtDecoder`**、**`TokenService`**：`com.mywebsite.auth.config.AuthJwtConfiguration`、`com.mywebsite.auth.properties.AuthJwtKeyProperties`、`com.mywebsite.auth.crypto.RsaPemLoader`
  - Access JWT 签发与 **`kid`**：`com.mywebsite.auth.token.JwtTokenService`
  - JWKS：`com.mywebsite.auth.controller.WellKnownJwksController`，文档载体 `com.mywebsite.auth.jwks.AuthJwkSetDocument`（JSON 由 **`JWKSet#toString()`** 序列化，标准 JSON）
- **Admin**
  - Resource Server：`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
  - 解码与校验器：`com.mywebsite.admin.security.AdminJwtDecoderConfiguration`（若类名以仓库为准）

---

## 4. 启动顺序与 JWKS 拉取行为

- **`jwk-set-uri`** 场景下，Spring Security **OAuth2 Resource Server** 一般在 **首次需要校验 JWT** 时向 Auth 请求 JWKS（并缓存）；**不要求** Admin 启动瞬间 Auth 已就绪。若 Admin 进程在 Auth 不可用时收到带 Bearer 的请求，验签可能失败（**401**），属预期。
- **测试**通过 **本机 HttpServer 动态 JWKS** 注入 `jwk-set-uri`，**不依赖**先启动真实 Auth（见 `admin` 模块测试基类）。

---

## 5. Site 后续接入（预留）

- **`site-app`** 尚未配置 OAuth2 Resource Server 时，无需为 4G 强塞安全链；上线 Site 业务 API 需 JWT 时，与 Admin 相同：仅增加 **`jwk-set-uri`** 指向 **`AUTH_JWKS_URI`**，Claims / Audience 与产品约定对齐即可。

---

## 6. 关联文档

- [后端开发路线图与阶段状态](../../总览/后端开发路线图与阶段状态.md)  
- [阶段4D-Auth独立认证服务架构回归设计与迁移方案.md](./阶段4D-Auth独立认证服务架构回归设计与迁移方案.md)  
- [阶段4F-Auth单服务收敛与认证主链路上提落地说明.md](./阶段4F-Auth单服务收敛与认证主链路上提落地说明.md)  
- [`services/website-services/README.md`](../../../../services/website-services/README.md)  
