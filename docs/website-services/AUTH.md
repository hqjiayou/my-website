# AUTH

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

契约：[`../contracts/AUTH_CONTRACT.md`](../contracts/AUTH_CONTRACT.md)。完成度：[`CURRENT_STATE.md`](./CURRENT_STATE.md)。

对照 `auth` 的 Controller（2026-10-10）：

| 端 | 路径 | 类 |
|----|------|----|
| Admin | `POST /api/auth/admin/login`、`/refresh`、`/logout`、`/register` | `AuthAdmin*Controller`、`AuthLogoutController` |
| Site | `POST /api/auth/site/login`、`/refresh`、`/logout` | `AuthSiteLoginController`、`AuthSiteRefreshController`、`AuthSiteLogoutController` |
| 公钥 | `GET /.well-known/jwks.json` | `AuthJwksWellKnownController` |
| 我是谁 | `GET /api/admin/auth/me`、`GET /api/agent/auth/me` | 在 admin / agent-app，不在 auth |

auth 不跑 Flyway，不依赖 admin jar。Refresh 在 Redis。JWT 为 RS256。

Gateway `trusted-proxies` 为 `(?!)`（dev 与 prod 相同）。`ClientIpGatewayFilter` 把出站 `X-Forwarded-For` 写成 TCP 对端。`SpoofedForwardedForGatewayTest` 证明伪造转发头不会成为下游末段（Issue #136）。

密码、Cookie、轮换的设计细节仍在阶段 4A；登录进程以 4F 为准。4A 已改为：Site 登录不是占位。
