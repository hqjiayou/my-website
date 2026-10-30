# BACKEND_TEST_MATRIX

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-10
```

| Check | Command |
|-------|---------|
| unit/MVC | `cd services/website-services && mvn -B -ntp test` |
| PG verify | `mvn -B -ntp -Ppersistence-verify-pg test` |

Gateway↔Auth XFF：`SpoofedForwardedForGatewayTest` 经真实 Netty 证明伪造转发头不会成为下游末段（Issue #136）。auth 限流 MVC 测试取 XFF 末段。
