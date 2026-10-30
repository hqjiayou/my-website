# SECURITY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-30
```

## 已建立

- RS256 + JWKS；Admin/Site audience 隔离  
- Refresh 旋转与重放吊销  
- RBAC Method Security  
- Auth Redis 滑窗限流（login 含 username 维；register/refresh 偏 IP 维）  
- Media MIME / 归属  

## Client IP / X-Forwarded-For

**机制**：Spring Cloud Gateway 在对端匹配 `trusted-proxies` 时，会用 `X-Forwarded-For` 或 `Forwarded` 的第一段覆盖 TCP 连接地址。第一段可由客户端伪造。因此 dev 与 prod 的 `trusted-proxies` 都固定为 `(?!)`（谁都不匹配），`server.forward-headers-strategy` 为 `none`。`ClientIpGatewayFilter` 把出站 `X-Forwarded-For` 写成与 gateway 直连的 TCP 对端，并去掉客户端带来的 `Forwarded`。值不是 `(?!)` 时该 Filter 拒绝启动。auth 的 `ClientIpUtils` 取 XFF **末段**作限流键；经网关后这一段就是 TCP 对端。

**集成断言（2026-10-30，Issue #136）**：`SpoofedForwardedForGatewayTest` 打到已启动的 gateway，伪造 `X-Forwarded-For` 与 `Forwarded` 后，下游末段是测试客户端的对端地址，不是 `1.1.1.1`。`GatewayXForwardedTrustBoundaryTest` 覆盖过滤器改写；auth 限流 MVC 测试覆盖取末段。

**部署**：不要设置 `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES`。把它改成 `.*` 或前置 LB 正则会重新打开「第一段覆盖连接地址」。gateway 是最外层时，限流键是客户端自己的 TCP 地址；前面有负载均衡时，限流键是负载均衡器的对端地址，不是终端用户地址。

**暴露面**：只有 gateway 对外；auth（:8083）、admin、agent-app 只在内网。

**残留（已文档化，接受）**：本机 smoke 直连 auth（绕过 gateway）时 XFF 末段仍可由客户端控制——这是本地调试面，不属生产拓扑；生产 auth 不对外暴露，故不构成生产风险。

状态：**RESOLVED**（Issue #136：不信任转发头，限流键取 TCP 对端，2026-10-30）。

## 其他

- Open registration：默认关；误开 warn-only（可加强为 prod fail-fast）  
- Menu 写权限种子无 API：DEFERRED，非假闭环  
