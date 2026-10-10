# SECURITY

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

## 已建立

- RS256 + JWKS；Admin/Site audience 隔离  
- Refresh 旋转与重放吊销  
- RBAC Method Security  
- Auth Redis 滑窗限流（login 含 username 维；register/refresh 偏 IP 维）  
- Media MIME / 归属  

## Client IP / X-Forwarded-For

**代码侧（2026-10-10 仍如此）**：gateway `application.yml` 的 `trusted-proxies` 默认 `.*`，`x-forwarded.for-append: true`。auth 取 XFF 末段。生产必须收成前置代理网段。经 gateway 的集成断言仍然没有（Issue #1）。

**仍开放**：经 gateway 的自动化集成断言（TEST_GAP）。生产应将 `trusted-proxies` 收紧为前置 LB CIDR/正则，勿长期使用 `.*`。

状态：**PARTIALLY_RESOLVED**（配置已加；缺 gateway↔auth 集成测试 → 跟踪 Issue）。

## 其他

- Open registration：默认关；误开 warn-only（可加强为 prod fail-fast）  
- Menu 写权限种子无 API：DEFERRED，非假闭环  
