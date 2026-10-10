# SITE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

对照 `site-app` 的 Controller（2026-10-10）：

| 能力 | 状态 | 代码 |
|------|------|------|
| Site Auth `me` | 后端已有 | `SiteAuthMeController` `GET /api/site/auth/me` |
| 登录 / 刷新 / 登出 | 在 auth，不在 site-app | 见 [`AUTH.md`](./AUTH.md) |
| 站点 AI | 已有 chat、stream、会话读 | `SiteAiController` |
| 给 Python 的内部工具 | 已有读站点、读内容、建预约 | `SiteInternalController` `/internal/site/v1/...`，不经对公产品页 |
| 访客内容 / 预约公开 API | 没有 | 内容与预约的管理接口在 admin |
| 行业模块 | 无业务 | `site-car`、`site-cake` 无 Controller |
| 前台应用 | 工作区没有 | `apps/site-web` 无行业站点 |
| runtime-status 独立端点 | 没有 | 管理端用现有 API 推导 |
| `deleted_at` 与 `deleted` | Admin 删站点 / 外观 / 用户已对齐写 `deleted_at`；无 DB CHECK | [#4](https://github.com/hqjiayou/my-website/issues/4) |

Admin 侧站点 CRUD、模板目录、外观、媒体、内容、预约是 admin 模块，不在 site-app。
