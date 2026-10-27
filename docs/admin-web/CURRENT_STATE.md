# admin-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
source_of_truth: apps/admin-web/src
```

## 总览

管理后台 React 应用。主纵向域、运营聚合与站点 AI 管理走 **REAL API**。站点工作台以 Agent 为中心组织（左栏分组：Agent／素材／站点设置，落地页为 Agent 总览）；发布检查由现有 REAL API 推导。部分导航页为占位。

## 域 × 数据源

| 域 | 数据源 | 说明 |
|----|--------|------|
| Auth | REAL | `authApi` |
| RBAC / Users / Roles / Menus读 | REAL | `rbacApi` |
| Site Core | FORCE REAL | `siteApi` |
| Site runtime-status | 由 REAL API 推导 | 非独立端点；访问量 / 当日 AI 会话为 `null`；AI 配置看 prompt profile 是否已落库 |
| Appearance | FORCE REAL | `heroLayout` |
| Media | FORCE REAL | |
| Content | FORCE REAL | |
| AI 站点工作台与全局 Prompt / 知识库 / 会话 | FORCE REAL | 站点工作台 `agent/*` 一级页（总览/试聊/Prompt/知识库/对话/运行状态，面板组件沿用原 AI 页）与 `pages/ai/promptsPage` 等走 `aiApi`。`pages/ai/settingsPage` 只解释环境变量，不调 API |
| 占位页 | 无后端 | `siteDomainPage`（DNS/CDN 文案）；`ParamsConfigPage`、`AnalyticsPage`、`SystemSettingsPage`、`DeveloperPage` 使用 `EmptyModulePage` |
| 运营全局 Contents/Files | REAL | `api/operation/*` 客户端跨站聚合，无单独跨站后端 |

## Mock

- `src/mocks/**` 与 `src/api/ai/aiApi.ts`、`src/config/apiMode.ts` 已删除。
- `VITE_USE_MOCK` 无消费者。

## 开发规范

命名、路由、样式、列表基建：[`开发规范.md`](./开发规范.md)（SPEC，CURRENT）。

## 已知缺口（Issue 指针）

见 [`../overview/ROADMAP.md`](../overview/ROADMAP.md)：Browser E2E [#2](https://github.com/hqjiayou/my-website/issues/2)、AI LIVE [#5](https://github.com/hqjiayou/my-website/issues/5)。
