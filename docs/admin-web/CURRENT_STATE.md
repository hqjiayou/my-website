# admin-web CURRENT_STATE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-11-09
source_of_truth: apps/admin-web/src
```

## 总览

管理后台 React 应用。主纵向域走 **REAL API**。侧栏父级叫 agent；智能体、模型 API、AI 用量等同组。`/agent` 为智能体列表（列显示管理、状态/类型服务端筛选、行级 `···` 操作；无全局 `q/sort` 时不开放本地伪装搜索）。Agent 工作区取消常驻二级侧栏，改为横向 Tabs：`概览 / 配置 / 调试 / 运行记录 / 设置`；配置内子导航为 `模型链 / Prompt / 知识空间 / Tools`（薄容器复用既有业务面板）。Settings 聚合 Basic＋Access（平台默认只读）并以高级入口保留 Theme／Contents／Files。旧路径 `prompt/knowledge/preview/conversations/runtime/basic/access` 绝对重定向到 V2 等价页；`theme/contents/files` 仍直达。对话预览左栏顺序为本轮执行 → MCP（状态只读，选择在运行状态／Tools 页）→ Skills → Todo；成功轮次落库为 `ADMIN_PREVIEW`。概览左侧上方是当前模型、Prompt 和知识库，下面是 Harness，右侧是对话列表。

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
| AI 工作台与全局 Prompt / 知识库 / 会话 | FORCE REAL | 工作台概览、对话预览、Prompt、知识库（上传成功后索引后台进行，列表先见 PENDING）、对话、运行状态走 `aiApi`。对话预览按 CLI / API key 选择接入，请求带上浏览器 IANA 时区和用户授予的定位坐标，并可在该模式的生效链里指定下一轮入口；不指定则用该模式的默认入口，只调用这一条；不勾选 skill 则本轮不注入。「正在使用 X / 无可用入口」文案、默认模式初始化与 DONE 回传模型的比对基准，均来自 `GET .../ai/preview/model` 回填（`mode` 缺省拿服务端默认判定，切换 CLI/KEY 带 `mode` 重查），与后端 `previewStream` 同一判定路径；本页不再本地拉全平台入口列表算这条规则。对话预览页呈现为左右分栏（左：模式/入口、Skills、本轮执行轨迹、Todo 控制面板，窄宽度时收为会话上方展开区；右：会话流＋贴底输入框），助手回复的引用以正文 `[n]` 角标＋可展开来源卡呈现（引用数据来自 DONE 事件的 citations，Issue #178）。概览读运行状态里的 Harness（平台文件、技能、运行上限、MCP）、Prompt、知识库和会话消息上的用量；会话列表按条展示模型、条数、最近一轮上下文和累计输入输出 token。新的一轮会把请求的模型 spec 记在助手消息上，更早的消息没有这条记录时列表用当前模型名。`pages/ai/settingsPage` 是模型入口，同页有运行配置卡和 MCP 服务器卡；工作台运行状态页可改本智能体的 MCP 选择（继承／关闭／子集），深检按该选择过滤。AI 子页页头不再展示共享备注文案。 |
| 占位页 | 无后端 | 域名页已从工作台拿掉，`/domain` 回到基础信息。`ParamsConfigPage`、`AnalyticsPage`、`SystemSettingsPage`、`DeveloperPage` 仍是 `EmptyModulePage` |
| 运营全局 Contents/Files | REAL | `api/operation/*` 客户端跨站聚合，无单独跨站后端 |

## Mock

- `src/mocks/**` 与 `src/api/ai/aiApi.ts`、`src/config/apiMode.ts` 已删除。
- `VITE_USE_MOCK` 无消费者。

## 开发规范

命名、路由、样式、列表基建：[`开发规范.md`](./开发规范.md)（SPEC，CURRENT）。

## 已知缺口（Issue 指针）

见 [`../overview/ROADMAP.md`](../overview/ROADMAP.md)：Browser E2E [#2](https://github.com/hqjiayou/my-website/issues/2)、AI LIVE [#5](https://github.com/hqjiayou/my-website/issues/5)。
