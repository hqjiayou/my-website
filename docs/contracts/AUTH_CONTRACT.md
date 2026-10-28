# AUTH_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-10
```

## 入口

经 **gateway :8080**：

| Audience | 路径前缀 | Cookie path（refresh） |
|----------|----------|------------------------|
| ADMIN | `/api/auth/admin/**` | `/api/auth/admin` |
| SITE | `/api/auth/agent/**` | `/api/auth/agent`（以实现为准） |

直连 auth:8083 仅限运维/烟测；产品流量走 gateway。

## JWT

- 算法：**RS256**；Auth 持有私钥；业务服务 **JWKS** 验签  
- Admin / Site **audience / subject_type 隔离**；跨 audience → 401  
- Access：Bearer；Refresh：HttpOnly Cookie + 旋转/重放吊销  

## Admin 关键 endpoints

- `POST /api/auth/admin/login`  
- `POST /api/auth/admin/refresh`  
- `POST /api/auth/admin/logout`  
- `POST /api/auth/admin/register`（受 open-registration 门控；默认关闭）  

## Site 关键 endpoints（BACKEND_COMPLETE）

- `POST /api/auth/agent/login`  
- `POST /api/auth/agent/refresh`  
- `POST /api/auth/agent/logout`  
- `GET /api/agent/auth/me`（`SiteAuthMeController`，在 agent-app）

**站点用户端是 `apps/agent-portal`**，这些接口（更名后为 `/api/auth/agent/**`）已由它对接；`apps/site-web` 已于 2026-10-28 移除。

## 限流与 Client IP

`ClientIpUtils` 取 `X-Forwarded-For` **最后**一段（假定 gateway 追加直连 IP）。  
若 gateway 未启用可信代理 / XFF 追加，则 IP 限流可被伪造绕过 → **HIGH**（见 ROADMAP / Issue）。

## `/api/admin/auth/me`

Admin Resource Server：返回用户、roles、permissions、menus（只读菜单树）。
