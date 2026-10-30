# admin-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-30
source_of_truth: apps/admin-web/src
```

## 总览

管理后台 React 应用。主纵向域走 **REAL API**。侧栏父级叫 agent。打开 `/agent` 进入最近更新的智能体工作区（没有智能体时只留下新建），API 管理在同一菜单下的 `/agent/api`，页签切换两种：CLI 代理（登录 GPT CLI、Claude、Gemini 或 Muse，查看该账号用量和模型，客户端 key 在这一页；Cursor 不能登录）和供应商 API key（模型 spec 与 key，不经过登录账号）。智能体用哪一条在接入里选，这里不改智能体。工作台导航只留对话、接入和基础信息。接入页的 CLI 仍保存代理地址、客户端 key 和已登录账号放出的模型；客户端 key 这一行是进代理的门禁，已保存时默认保留、展开后才从代理上的客户端 key 里更换。API key 那一行是直连默认入口，显示名称、spec 和末四位，只从 API 管理里已有的条目里选一条绑到当前智能体。概览左侧上方是当前模型、Prompt 和知识库，下面是 Harness（三个平台文件是否装上、技能、运行上限、MCP），右侧是对话列表（模型、条数、上下文、输入和输出 token）。

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
| AI 工作台与全局 Prompt / 知识库 / 会话 | FORCE REAL | 工作台概览、试聊、Prompt、知识库、对话、运行状态走 `aiApi`。试聊按 CLI / API key 选择接入里该模式的默认模型，只调用这一条；不勾选 skill 则本轮不注入。「正在使用 X / 无可用入口」文案、默认模式初始化与 DONE 回传模型的比对基准，均来自 `GET .../ai/preview/model` 回填（`mode` 缺省拿服务端默认判定，切换 CLI/KEY 带 `mode` 重查），与后端 `previewStream` 同一判定路径；本页不再本地拉全平台入口列表算这条规则。概览读运行状态里的 Harness（平台文件、技能、运行上限、MCP）、Prompt、知识库和会话消息上的用量；会话列表按条展示模型、条数、最近一轮上下文和累计输入输出 token。新的一轮会把请求的模型 spec 记在助手消息上，更早的消息没有这条记录时列表用当前模型名。`pages/ai/settingsPage` 是模型入口。 |
| 占位页 | 无后端 | 域名页已从工作台拿掉，`/domain` 回到基础信息。`ParamsConfigPage`、`AnalyticsPage`、`SystemSettingsPage`、`DeveloperPage` 仍是 `EmptyModulePage` |
| 运营全局 Contents/Files | REAL | `api/operation/*` 客户端跨站聚合，无单独跨站后端 |

## Mock

- `src/mocks/**` 与 `src/api/ai/aiApi.ts`、`src/config/apiMode.ts` 已删除。
- `VITE_USE_MOCK` 无消费者。

## 开发规范

命名、路由、样式、列表基建：[`开发规范.md`](./开发规范.md)（SPEC，CURRENT）。

## 已知缺口（Issue 指针）

见 [`../overview/ROADMAP.md`](../overview/ROADMAP.md)：Browser E2E [#2](https://github.com/hqjiayou/my-website/issues/2)、AI LIVE [#5](https://github.com/hqjiayou/my-website/issues/5)。
