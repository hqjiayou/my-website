# admin-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
source_of_truth: apps/admin-web/src
```

## 总览

管理后台 React 应用。主纵向域、运营聚合与站点 AI 管理走 **REAL API**。站点概览指标由现有 REAL API 推导（访问量 / 当日 AI 会话为 `null`）。部分导航页为占位。

## 域 × 数据源

| 域 | 数据源 | 说明 |
|----|--------|------|
| Auth | REAL | `authApi` |
| RBAC / Users / Roles / Menus读 | REAL | `rbacApi` |
| Site Core | FORCE REAL | `siteApi` |
| Site runtime-status | 由 REAL API 推导 | 非独立端点；访问量 / 当日 AI 会话为 `null`；AI 配置看 prompt profile 是否已落库 |
| Template | FORCE REAL | |
| Appearance | FORCE REAL | `heroLayout` |
| Media | FORCE REAL | |
| Content | FORCE REAL | |
| Reservation | FORCE REAL | status `DONE` |
| AI | FORCE REAL | `aiApi` → `/admin/sites/{id}/ai/**`；无 `AI_MODEL` 时预览诚实失败 |
| Analytics / System / Dev / Domain 等 | PLACEHOLDER | Empty / 未接入页 |
| 运营全局 Contents/Files/Reservations | REAL | `api/operation/*` 跨站聚合；进页分别要 content/reservation/media list |

## Mock

- `src/mocks/**` 与 `src/api/ai/aiApi.ts`、`src/config/apiMode.ts` 已删除。
- `VITE_USE_MOCK` 无消费者。

## 开发规范

命名、路由、样式、列表基建：[`开发规范.md`](./开发规范.md)（SPEC，CURRENT）。

## 已知缺口（Issue 指针）

见 [`../overview/ROADMAP.md`](../overview/ROADMAP.md)：Browser E2E [#2](https://github.com/hqjiayou/my-website/issues/2)、AI LIVE [#5](https://github.com/hqjiayou/my-website/issues/5)。
