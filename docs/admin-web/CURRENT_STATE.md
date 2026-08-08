# admin-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
source_of_truth: apps/admin-web/src
```

## 总览

管理后台 React 应用。主纵向域（Site / User / Template / Appearance / Media / Content / Reservation / RBAC）走 **REAL API**；AI 与 runtime-status 仍可 mock；部分导航页为占位。

## 域 × 数据源

| 域 | 数据源 | 说明 |
|----|--------|------|
| Auth | REAL | `authApi` |
| RBAC / Users / Roles / Menus读 | REAL | `rbacApi` |
| Site Core | FORCE REAL | `siteApi` |
| Site runtime-status | MOCK if USE_MOCK；else null | 禁止假 KPI |
| Template | FORCE REAL | |
| Appearance | FORCE REAL | `heroLayout` |
| Media | FORCE REAL | |
| Content | FORCE REAL | |
| Reservation | FORCE REAL | status `DONE` |
| AI | ACTIVE MOCK | `aiApi` → `aiMock`；EXTERNAL_BLOCKED |
| Analytics / System / Dev / Domain 等 | PLACEHOLDER | Empty / 未接入页 |
| 运营全局 Contents/Files/Reservations | CONNECTED=false 占位 | 站点工作区内为 REAL |

## Mock 保留

- `mocks/services/aiMock.ts`（及 ai db/types）— **STILL_REQUIRED**  
- `mocks/services/siteMock.ts`（**仅** `getSiteRuntimeStatus`）— **STILL_REQUIRED**  

## Mock / legacy（工作区事实）

- Orphan mocks / `operation/userApi`：**工作区已删除且无 consumer**（交付动作：与 FORCE REAL / rename WIP 同批提交；**不**建长期 Issue）。
- 保留：`aiMock`、`siteMock`（runtime only）。

## 开发规范

命名、路由、样式、列表基建：[`开发规范.md`](./开发规范.md)（SPEC，CURRENT）。

## 已知缺口（Issue 指针）

见 [`../overview/ROADMAP.md`](../overview/ROADMAP.md)：Browser E2E [#2](https://github.com/hqjiayou/my-website/issues/2)、AI LIVE [#5](https://github.com/hqjiayou/my-website/issues/5)。
