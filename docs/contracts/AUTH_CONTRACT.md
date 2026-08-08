# AUTH_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-08-09
```

## 入口

经 **gateway :8080**：

| Audience | 路径前缀 | Cookie path（refresh） |
|----------|----------|------------------------|
| ADMIN | `/api/auth/admin/**` | `/api/auth/admin` |
| SITE | `/api/auth/site/**` | `/api/auth/site`（以实现为准） |

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

- `POST /api/auth/site/login`  
- `POST /api/auth/site/refresh`  
- `POST /api/auth/site/logout`  
- `GET` site me（以实现 Controller 为准）  

**site-web 前端尚未对接** → 不得描述为完整产品功能。

## 限流与 Client IP

`ClientIpUtils` 取 `X-Forwarded-For` **最后**一段（假定 gateway 追加直连 IP）。  
若 gateway 未启用可信代理 / XFF 追加，则 IP 限流可被伪造绕过 → **HIGH**（见 ROADMAP / Issue）。

## `/api/admin/auth/me`

Admin Resource Server：返回用户、roles、permissions、menus（只读菜单树）。
