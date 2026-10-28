# SECURITY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-28
```

## 已建立

- RS256 + JWKS；Admin/Site audience 隔离  
- Refresh 旋转与重放吊销  
- RBAC Method Security  
- Auth Redis 滑窗限流（login 含 username 维；register/refresh 偏 IP 维）  
- Media MIME / 归属  

## Client IP / X-Forwarded-For

**机制**：gateway `XForwardedHeadersFilter` 在对端可信时把「与 gateway 直连的对端 IP」追加到 XFF 末尾（`x-forwarded.for-append: true`）；auth 的 `ClientIpUtils` 取 XFF **末段**作限流键。客户端能控制的只有 XFF 前缀条目，控制不了末段。

**集成断言（2026-10-28，Issue #1 已落地）**：gateway 模块 `GatewayXForwardedTrustBoundaryTest` 用上下文实际装配的 filter 证明：伪造 `X-Forwarded-For: 1.1.1.1`（含多段伪造）经 gateway 后末段是真实对端 IP、不是 `1.1.1.1`；与 auth 侧限流 MVC 测试（取末段）合为 Gateway→Auth 证据链。

**配置分工**：

- 本机/dev：`application.yml` 默认 `trusted-proxies: ".*"`（gateway 即最外层，直连对端就是客户端本人），本地全栈照常。
- 生产：启用 **prod profile**（`application-prod.yml`），`trusted-proxies` 只能由环境变量 `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES` 提供，值是前置 LB 对端 IP 的 Java 正则（如 LB 在 10.0.5.0/24 用 `^10\.0\.5\.\d+$`；SCG 以正则匹配，不是 CIDR 语法）。未设置时启动 fail-fast，不静默退回 `.*`。收窄后还有额外收益：不匹配该正则的既有 XFF 条目会被 filter 整段剥除。

**暴露面（与部署拓扑一致，强制）**：只有 gateway 对外；auth（:8083）、admin、agent-app 只在内网。prod 还必须在网络层保证 gateway **只能被前置 LB 到达**——对端不被信任时 filter 不追加可信条目、原样放行，收窄 trusted-proxies 不是单独成立的控制。

**残留（已文档化，接受）**：本机 smoke 直连 auth（绕过 gateway）时 XFF 末段仍可由客户端控制——这是本地调试面，不属生产拓扑；生产 auth 不对外暴露，故不构成生产风险。

状态：**RESOLVED**（集成断言 + prod 收窄落地，2026-10-28）。

## 其他

- Open registration：默认关；误开 warn-only（可加强为 prod fail-fast）  
- Menu 写权限种子无 API：DEFERRED，非假闭环  
