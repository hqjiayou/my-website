# Agent Console V2 实施文档

> 项目：[`hqjiayou/my-website`](https://github.com/hqjiayou/my-website)  
> 目标分支：`dev`（实际开发请从最新 `dev` 创建功能分支）  
> 前端项目：`apps/admin-web`（React 19 + TypeScript + Vite + Tailwind 4）  
> 目标原型：`agent_admin_redesign_v2.html`（本次对话提供的 V2 单文件 HTML）  
> 版本：V1.0 / 2026-11-09  
> 状态：**可作为开发任务书；未实际修改仓库代码**。

## 0. 阅读方式与交付原则

本文是一份**落到现有代码结构上的实施规格**，不是重新写一个独立后台的方案。

- 以 **V2 HTML 的页面结构和视觉交互**为产品验收目标。
- 以仓库 `dev` 分支现有 **TypeScript 类型、真实接口、权限体系、设计规范**为实现基础。
- **不引入 HTML 里的模拟数据和字符串拼接渲染**；全部转成 React 组件、hooks、路由和真实 API 状态。
- 不改动登录、用户/角色、运营等非 Agent 业务；全局壳仅做 Agent 菜单的局部适配。**V2 原型只有 Agent 导航，并不意味着要删除整个后台现存的其他菜单。**
- 一个 PR 只处理一个独立工作单元，做到可测试、可回滚。
- 不允许“按钮点击后只弹成功 Toast、实际上没有后端操作”的假实现；后端不支持的功能应显示禁用/解释，或进入明确标注的后端任务。

### 0.1 范围

**本次实现：**

1. Agent 列表与新建/操作交互。
2. Agent 详情：`概览 / 配置 / 调试 / 运行记录 / 设置`。
3. Agent 资源导航：`模型 API / Prompt 模板 / Skills / MCP 服务 / 知识空间`。
4. 模型 API 分离 `CLI / OAuth` 和 `API Key` 两个视图；精确展示模型 ID/版本、连接和状态。
5. 通用数据表格、搜索筛选、选择/批量操作、排序/列管理、详情浮层。
6. 通用资源选择抽屉，Agent 对平台资源的关联。
7. 兼容旧路由、旧数据、RBAC 和现有设计 token。

**明确不做：**

- 不重做非 Agent 业务页面；不搬运整套 HTML CSS 覆盖现有设计系统。
- 不从前端创造“真实模型版本”“配额/用量”“API 连通性”的值。
- 不把 CLI 派生模型当作人工可编辑的普通 API Key 接入。
- 不把当前属于 Agent 的操作错误地变成平台全局默认模型的修改。
- 不在没有服务端支持时伪造全量跨页搜索、精确运行日志或批量写入事务。

## 1. 必须先读的仓库文件

开发者/Cursor 开始前先对比当前分支，避免文档与代码漂移。

| 类别 | 真实路径 | 用途 |
|---|---|---|
| 设计规范 | `docs/admin-web/DESIGN_SYSTEM.md` | 侧栏 256/64、64px 顶栏、4px 间距、主题语义 token、密度与控件规范 |
| 设计约束 | `.cursor/rules/admin-web-design.mdc` | 修改前自检规则 |
| 路由常量 | `apps/admin-web/src/router/config/routePaths.ts` | 新旧路由与跳转 |
| 路由装配 | `apps/admin-web/src/router/routes/adminRoutes.tsx` | 新页面装配、旧路径 redirect |
| 菜单标注 | `apps/admin-web/src/router/navigation/menu.ts` | 顶栏标题与静态标签；**不代表真实侧栏唯一数据源** |
| Agent 列表 | `apps/admin-web/src/pages/agents/agentsPage.tsx` | 列表当前实现 |
| Agent 工作区 | `apps/admin-web/src/pages/agents/agentWorkspaceLayout.tsx` | 当前常驻二级侧栏，需重点重构 |
| 预览/Prompt/KB/对话 | `apps/admin-web/src/pages/agents/agentPages.tsx` | 旧路由页面容器 |
| 模型链 | `apps/admin-web/src/pages/agents/agentAiChainPresetSection.tsx` | 已有按 CLI/KEY 模式保存 Agent 独立链 |
| Prompt | `apps/admin-web/src/pages/agents/agentAiPromptPanel.tsx` | Agent 自有 Prompt 与默认 Skills |
| MCP/运行配置 | `apps/admin-web/src/pages/agents/agentAiRuntimePanel.tsx` | 现有运行状态与 MCP 绑定逻辑 |
| 聊天预览 | `apps/admin-web/src/pages/agents/agentAiPreviewChat.tsx`、`agentAiPreviewAdapter.ts` | 调试聊天与执行信息 |
| 模型资源 | `apps/admin-web/src/pages/ai/modelEntriesPage.tsx`、`modelEntriesCard.tsx` | 已存在 CLI/KEY 切换、CLI 派生数据与连接操作 |
| 模型链模板 | `apps/admin-web/src/pages/ai/chainPresetsCard.tsx` | 当前模型页下方的模板库 |
| Agent API | `apps/admin-web/src/api/apps/agentApi.ts` | Agent 列表/详情/状态 |
| AI API | `apps/admin-web/src/api/apps/aiApi.ts` | 模型、Prompt、链、Skills、MCP、KB、Preview |
| 后端接口类型 | `apps/admin-web/src/types/ai.ts`、`src/types/site.ts` | 数据契约，以它们为准 |
| 复用控件 | `apps/admin-web/src/components/admin/{dataTable,searchToolbar,filterTabs,statusTag,controls,formDialog}.tsx` | 优先扩展，不平行复制组件 |
| 权限 | `apps/admin-web/src/types/rbac.ts`、`src/router/permission` | 按现有权限控制读取/写入 |

> 注意：真实侧边栏菜单由登录后的权限菜单数据驱动，`router/navigation/menu.ts` 主要承担静态展示/标题解析。需要同时审查服务端菜单种子数据（Flyway 等）与权限映射；**只改路由常量不会自动改变侧栏**。

## 2. 产品信息架构（IA）

### 2.1 Agent 导航组

```text
Agent Console / Agent 管理
├── 智能体
│   └── 智能体列表                    /agent
│       └── Agent 详情               /agent/:agentId/agent
│           ├── 概览                 /agent/:agentId/agent
│           ├── 配置                 /agent/:agentId/agent/config/*
│           │   ├── 模型与回退链       /agent/:agentId/agent/config/models
│           │   ├── Prompt            /agent/:agentId/agent/config/prompt
│           │   ├── 知识空间           /agent/:agentId/agent/config/knowledge
│           │   └── Skills / MCP      /agent/:agentId/agent/config/tools
│           ├── 调试                 /agent/:agentId/agent/debug
│           ├── 运行记录             /agent/:agentId/agent/runs
│           └── 设置                 /agent/:agentId/agent/settings
├── Agent 资源管理
│   ├── 模型 API                     /ai/model-entries
│   │   ├── CLI / OAuth             ?mode=CLI
│   │   └── API Key                 ?mode=KEY
│   ├── Prompt 模板                  /ai/prompt-templates
│   ├── Skills                       /ai/skills
│   ├── MCP 服务                     /ai/mcp-servers
│   └── 知识空间                     /ai/knowledge-bases
└── Agent 运行
    └── 执行日志                    /ai/conversations （初期复用/增加视图）
```

**路由说明：**

- 上面的 `config/*`、`debug`、`runs`、`settings` 是**建议新增的目标路由**，不是现有接口已存在的证明。
- 既有 `ROUTES.apps.agentPrompt/agentKnowledge/agentPreview/agentConversations/agentRuntime/agentBasic/agentAccess` 等路径保留为重定向入口；迁移期间可临时由旧组件处理，禁止直接 404。
- `?mode=CLI|KEY` 要能分享、刷新、浏览器前后退还原；不存在或非法 mode 时回到默认 CLI。
- 菜单名称与页面标题放入 i18n（现有项目为 `react-i18next`），不把原型中的中文硬编码散落到组件里。
- 运行记录与执行日志分清作用域：前者是**当前 Agent**；后者是**平台跨 Agent**。不能把“对话列表”直接冒充含完整 trace 的执行日志。

### 2.2 Agent 页面框架

```text
┌ 全局顶栏：面包屑 / 搜索入口 / 主题 / 用户 ──────────────────────────────────┐
│  Agent Console  >  智能体  >  通用智能助手                                 │
├ 左侧全局侧栏 ┬─────────────────────────────────────────────────────────┤
│ Agent 列表   │ ←  [Agent 图标] 通用智能助手 · 状态       [调试] [···]   │
│ 模型 API     │ 概览 | 配置 | 调试 | 运行记录 | 设置                       │
│ Prompt 模板  ├─────────────────────────────────────────────────────────┤
│ Skills       │ 页面内容：                                                │
│ MCP 服务     │   概览：状态 / 能力摘要 / 近期活动                         │
│ 知识空间     │   配置：紧凑二级导航 + 配置主工作区                         │
│ 执行日志     │   调试：聊天主区 + 可收起 Inspector                        │
│              │   运行记录：DataTable / 记录详情                           │
└──────────────┴─────────────────────────────────────────────────────────┘
```

- 不保留**永久常驻**的“全局侧栏 + Agent 侧栏 + 调试检查侧栏”三栏叠加。
- Agent 一级导航改为页头下横向 tabs；**仅配置**页面保留紧凑二级导航（约 184–208px，可根据现有 token 调整）。
- 调试 Inspector 只在调试页出现，可折叠；1280px 以下默认收起。
- 操作与内容保持对齐：页级唯一主操作靠右，低频操作放 `···` 菜单，不在页头摆三到五个并列大按钮。

### 2.3 响应式

| 断点/容器 | 行为 |
|---|---|
| ≥1440px | 全局导航常驻、数据区充分展示、调试 Inspector 可默认展开 |
| 1024–1439px | 全局导航常驻或折叠；配置子导航保留；调试 Inspector 默认折叠 |
| 768–1023px | 全局导航抽屉化；配置二级导航改 tabs / 下拉；表格允许容器横滚 |
| <768px | 页头按钮合并，工具栏换行，表格隐藏低优先列；模型版本与状态不可隐藏；弹窗/抽屉自适应 |

遵守仓库设计规范：侧栏展开 256px、收起 64px、顶栏高 64px、内容 max-width 按页面类型、语义色与 4px 间距阶梯。

## 3. 页面功能与交互规格

### 3.1 智能体列表 `/agent`

**信息层级：**

1. 页头：`智能体` + 一句话说明 + 唯一主按钮 `新建智能体`。
2. 轻量统计（可选，仅真实服务端能提供时展示）：总数 / 运行中 / 停用 / 最近更新。
3. 表格上方工具栏：搜索名称/Code、状态、类型、列设置、刷新；选中行时显示批量操作条。
4. 表格；分页；空结果/无数据/加载失败/无权限状态。

**推荐列：**

| 列 | 默认 | 来源与交互 |
|---|---|---|
| 勾选框 | 显示 | 行选择与批量操作；操作不允许时不勾选 |
| Agent 名称 / Code | 显示，主列 | 点击进入详情；名称较醒目，Code 用等宽小字 |
| 类型 kind / 用途标签 | 显示 | `kind` 与自由文本 `agentType` 分开；编辑移动到行菜单/详情 |
| 状态 | 显示 | 用现有 `ACTIVE / DISABLED` 等真实值映射，不杜撰 `paused` 状态 |
| 模型链 | 显示 | `KEY n / CLI n`，可展开看首选入口；无数据时 `—` |
| Prompt / Skills | 显示 | 显示模板名称或本地 Prompt、技能数量 |
| 知识空间 | 可隐藏 | 关联数量，不在单元格内放过多写操作 |
| 最后更新 | 显示 | 本地时区统一格式；允许排序仅在数据源支持时启用 |
| 行操作 `···` | 显示 | 查看、调试、修改类型、启用/停用、复制（有真实接口后才启用）、删除 |

**交互规定：**

- 点击整行（非复选框、按钮区域）进入详情；链接支持在新标签打开；菜单按钮 `stopPropagation`。
- `新建智能体` 使用现有 `AgentCreateDialog` 或现有创建页，不重复引入两套表单。
- 状态和类型写操作继续使用现有 `version` 乐观锁；遇到版本冲突时提示并刷新当前行。
- 选中项跨页时需要统一规则。**建议初期仅当前页选择**；改变筛选/分页时清空选择并有可见说明。
- 批量停用、删除：目前没有证实存在后端批量端点。**MVP 禁用或不显示**；若要实现，先补服务端端点、RBAC、部分成功/失败结果与审计，再开放按钮。不得把逐条并发请求假装成原子批量事务。
- 搜索：`agentApi.listSites()` 当前可见查询主要是 `pageNum/pageSize/status/agentType`；**全量名称/Code 服务端搜索、全局排序不是既有能力**。MVP 可先保留当前已有筛选；需要 GitHub 风格完整查询时按第 7 节新增 API 参数。

### 3.2 Agent 概览

- 标题、状态、Code、类型、最近更新。
- 能力摘要：当前选中的 CLI/KEY 模式、当前生效模型入口（需真实解析）、Prompt 来源、Skills 数、MCP 有效数、知识空间数。
- `配置` 与 `运行调试` 两个明确入口；其他操作放菜单。
- 检查/异常区：未配置模型链、绑定已停用模型、MCP 被平台禁用等，以事实显示 Warning。
- **禁用纯示例的调用成功率、Token 成本、请求次数图表**；无真实接口时用“暂无可用统计”空状态。

### 3.3 Agent 配置

配置子导航：`模型链 / Prompt / 知识空间 / Skills 与 MCP`。右侧内容坚持 **一屏只处理一个主题**。

**模型链：**

- CLI 与 KEY 用分段控件切换；每个模式**保留独立顺序列表**，切换不抹掉另一模式配置。
- 数据真相源：`AiAgentChain.keyEntryIds` / `cliEntryIds`，`updateAgentChain(agentId, mode, entryIds)`。
- 入口卡片显示：优先级、连接名称、模型可读名称、**原始 modelSpec/请求模型 ID**、连接模式、状态、主用/回退角色；上移、下移、移除。
- 添加时打开 `ResourcePickerDrawer`，候选项严格过滤同模式、当前未加入、真实可用/启用的条目。
- 禁用/不可达/已删除的历史链成员：仍需显示异常占位，用户可移除、替换；不能在界面中静默过滤导致链顺序被误解。
- `保存模型链` 仅提交当前 Agent 的当前模式；成功后更新 Query Cache；切换 Agent 前有未保存变更确认。
- 失败回退策略文案必须匹配真实服务端逻辑；没有自动回退时不可只凭 UI 文案承诺。
- 模型链模板仍支持“载入模板（覆盖本模式成员）/另存为模板”；载入前二次确认。
- **不得在这里调用 `updateModelEntry(... defaultEntry:true)` 设置平台默认模型**。

**Prompt：**

- System Prompt 编辑区（使用现有 `agentAiPromptPanel.tsx` 数据逻辑）。
- Temperature 输入、字段级校验（当前实现规定范围 0–2）。
- 模板选择和套用、当前 Agent 本地 Prompt 副本；明确“修改模板不会同步改已套用 Agent”。
- 默认 Skills 在工具配置处统一管理，Prompt 页面只展示摘要/快捷跳转，避免同一集合出现两套编辑入口（如技术上暂需共用状态，应共用 hook）。
- 未保存提示、重置与显式保存。

**知识空间：**

- “已关联”与“可关联”两个区域；展示索引状态、文档数（仅真实值）。
- 关联/解绑使用现有 `linkKnowledgeBase` / `unlinkKnowledgeBase`；引导用户理解关联并非复制知识库。
- 文档上传、重建索引、重嵌入能力复用现有页面，避免在 V2 中丢功能；若有高级操作，可放二级页/抽屉。

**Skills / MCP：**

- 两组独立卡片/页签；Skills 默认选择落到 Agent Prompt 配置中的 `defaultSkillIds`，不新增一套无依据的 Agent-Skills 后端表。
- Skills 来源为 `getSkillCatalog`、`listPreviewSkills`；目录 ID、版本、说明、允许运行工具可查；不允许随手输入不存在的技能 ID 保存。
- MCP 使用真实三态 `INHERIT / OFF / CUSTOM`（中文：继承平台 / 关闭 / 自定义）。
- 自定义模式的候选集合来自平台 MCP 注册表，已禁用项不得新选；历史失效项需标出“已选但无效”并可移除。
- 清晰区分 **已选择、平台已启用、最终有效** 三层状态，不能把 `CUSTOM` 里存的服务器名等同于实际运行可用。
- 保存后应反馈 Agent 生效配置；如还需后端推送，展示真实 `APPLIED/BLOCKED/FAILED` 状态。

### 3.4 调试页面

结构：左侧**主聊天区**（会话/消息/输入），右侧**可收起检查器 Inspector**（模型路由、Skills、MCP、执行信息）。

- 复用现有 `agentAiPreviewChat.tsx` 和预览适配器，不从零改造流式协议。
- 输入框、消息列表、清空/新会话、停止/重试等动作必须是真实、可测的；不支持时不可显示假按钮。
- 模型切换：显式显示 `CLI / KEY` 与选中具体入口；临时选择仅影响调试会话，不暗改 Agent 保存的默认配置。
- 检查器显示**本轮**真正提交的模型入口/模式、工具调用、技能选择、MCP 状态及错误；没有返回的数据要标“未提供”，不要填演示值。
- 发送前检查 Agent 是否存在当前模式有效模型链；不可用时提供跳转配置的操作。
- `agentAiPreviewAdapter.ts` 中的 `navigator.geolocation`：必须调整为显式开启/用户授权或缓存，**普通聊天不应每次等待定位权限/超时才开始流式响应**。
- Inspector 的关闭状态保留在页面交互状态内；屏幕较窄时默认关闭，不压缩聊天到过小宽度。

### 3.5 运行记录 / Agent 执行日志

- 运行记录面向当前 Agent，可按时间、状态、会话 ID、执行来源筛选；点击进入详情查看真实对话和可获得的运行信息。
- 现有 `listConversations` 与 `listConversationMessages` 能支撑会话/消息层面的第一阶段。
- **runId、耗时、Token、费用、trace、tool call 明细**只有服务端有数据时才作为字段加入；如无 API，开一个后端独立任务设计游标分页与详情端点。
- 跨 Agent 的“执行日志”首期可以是现有平台会话页的列表视图，但应清楚命名“对话记录”；等执行事件数据就绪后再升格为“执行日志”。

### 3.6 Agent 设置

- 基本信息（名称、Code、类型等）与状态修改复用 `agentApi`；危险操作单独区域、二次确认、RBAC。
- 旧 `agentAccessPage.tsx` 中混入的**平台模型默认项修改**不得作为 Agent 设置继续暴露；平台入口创建和密钥管理移到 `模型 API`。
- `theme/domain/contents/files` 等已有 Agent 功能**不能因新版五个 tabs 就直接删除**；先梳理它们是否属于站点外观、发布或内容功能，再决定放入“设置 → 高级”或保留兼容入口。此项必须由产品/开发在迁移 PR 里给出对照表。

## 4. 模型 API：CLI 与 API Key 双视图（重点）

### 4.1 概念与数据所有权

```text
平台模型 API 资源
├── CLI / OAuth
│   ├── 代理服务及健康状态 (getProxyServices)
│   ├── 账号登录/认证 (startProxyLogin / proxyLoginStatus)
│   ├── 代理公布的账号-模型清单 (getProxyServicesUsage)
│   └── CLI 派生模型入口 (listModelEntries + syncCliModelEntries)
└── API Key
    ├── 接入配置名称、供应商、地址
    ├── modelSpec / 精确模型 ID
    ├── API Key 是否配置及脱敏提示
    └── 启停状态及可选检测结果

Agent 自己拥有：
  KEY entryIds[] + CLI entryIds[]（两套有序链）
  Agent 只引用平台资源 ID，不复制密钥、覆盖平台默认模型。
```

现有 `AiModelEntry` 已含 `mode / modelSpec / alias / derived / derivedSource / apiKeyConfigured / apiKeyHint / baseUrl / enabled / defaultEntry / version`。这些是**实际契约字段**。V2 原型中的 `provider、account、quota、latency、lastCheck、modelVersion` 并不全部存在于该 DTO：需通过真实代理/配置数据关联获得；无法可靠解析时显示 `— / 未检测`，不能擅自把它们塞进无后端支持的 DTO。

### 4.2 CLI 视图

表格默认列：

| 字段 | 取值原则 |
|---|---|
| 连接/账号 | `ProxyServicesAccount.name` 或模型入口实际关联；不能仅从模型字符串猜账号 |
| 平台/供应商 | 优先代理账号 `provider`，必要时按明确规则呈现“推断值” |
| 认证状态 | 来自 OAuth 登录/代理状态，非模型条目 enabled 的同义词 |
| 具体模型 | 解析 `modelSpec` 的模型 ID，主行展示可读名称、下方等宽展示完整 ID |
| 配额/健康 | 仅显示代理实际返回的 `usedPercent/modelUsedPercent`、disabled/unavailable/reachable 等信息 |
| 更新时间 | 模型入口 `updatedAt`（如果有） |
| 行操作 | 查看详情；派生行只读；重新同步需后台端点与确认流程 |

**CLI 建立连接**应走已有 OAuth/代理登录流程，不应让用户把“API Key 密钥字符串”填进 CLI 认证表单。当前代码里 `syncCliModelEntries()` 会物化代理已公布模型：**`derived === true` 的模型入口只读，不允许表格 inline 编辑或删除**；历史人工 CLI 行依现有后端兼容行为处理。`CLI Proxy Client Key` 是网关客户端凭证，不等同于上游 OAuth 账号凭证，必须单独展示、单独权限。

### 4.3 API Key 视图

表格默认列：连接名称、供应商、精确模型名称/ID、接口地址、密钥状态（配置/未配置 + hint）、启停、更新时间、`···`。

**新增/编辑表单：**

- 连接名称：`displayName`。
- 精确模型：`modelSpec`，例如 `anthropic:<真实模型 ID>`、`openai:<真实模型 ID>`；不以“Opus 5.5”这样的营销名称代替模型请求 ID。
- 供应商：可由 `modelSpec` 协议前缀解析；若需独立 vendor 字段，必须先定后端契约。
- 接口地址：`baseUrl`，校验协议及允许的 endpoint 规则；不能在 UI 中依赖不可信 URL 作任意回调。
- API Key：创建可输入，编辑为空表示保留既有密钥；回显只显示 `apiKeyConfigured/apiKeyHint`；绝不回传完整密钥。
- Alias：`alias`，服务器按既有规则校验。
- Enabled：`enabled`；`defaultEntry` 是**平台级**默认标记，普通 Agent 编辑不触碰。
- 编辑需提交 `version` 乐观锁，冲突刷新且不覆盖别人修改。

### 4.4 精确型号与版本展示

界面须分别显示：

1. **显示名**：便于阅读，如 `Claude Opus（示例）`。
2. **完整请求模型 ID**：后端使用的确定字符串，如 `modelSpec` 里 `:` 后的部分。
3. **接入来源**：CLI 账号/代理，或 API Key 连接。
4. **可用状态**：资源启用、代理/认证可用、检测通过分别标记。

> `Opus 5.5 / Opus 5.1` 是原型中的示例名称，不代表已确认该供应商有对应正式产品或该账户当前可用。正式实现必须以 **供应商/代理实时目录、管理员保存的精确 `modelSpec`**为准。未知版本不推测、不伪装为精确型号。

### 4.5 视图切换与互斥

- CLI 和 KEY 用 URL 查询参数 `mode` 持久化，切换时各自保留独立查询和分页状态（也可在换模式时重置并说明）。
- 两个视图筛选的数据集合严格按 `entry.mode` 分类；`derived` 决定可编辑性，而不是仅靠 mode。
- 新增按钮跟随当前 mode：CLI → `登录/同步 CLI 账号`，KEY → `新增 API Key 入口`。
- “检测连接”只有 API/后端真正支持时才允许使用；不能以列表存在或 enabled=true 认定检测通过。
- `ChainPresetsCard` 已是平台模型页下方已有功能。V2 不要无意移除：可折叠成 `对话链模板` 区或单独子标签；优先避免在页顶把复杂模板和连接表格混为一个操作平面。

## 5. 通用数据表格规范（GitHub 风格的交互密度）

目标是参考成熟后台表格的**查询、排序、选择、状态及空结果处理**，不是照搬 GitHub 样式。

### 5.1 统一组件接口

基于现有 `components/admin/dataTable.tsx` 增强，建议拆为：

```text
components/admin/table/
├── AdminDataTable.tsx         # 表体/固定表头/状态/分页
├── DataTableToolbar.tsx       # 搜索、筛选、刷新
├── DataTableColumnMenu.tsx    # 显示/隐藏列
├── DataTableBulkActions.tsx   # 选中反馈/合法批量操作
├── DataTableRowActions.tsx    # 行末 ··· 菜单
├── DataTableEmptyState.tsx    # 首次空/筛选无结果/无权限/失败
├── useTableQueryState.ts      # URL 查询、排序、分页与选择
└── types.ts
```

**优先扩展现有 `DataTable`，保持旧 API 兼容**。如决定引入 `@tanstack/react-table`，先核对当前依赖（当前 `admin-web/package.json` 不含它），独立 PR 添加并说明使用原因；不要同时引入第二套完整 UI 框架。

### 5.2 状态契约

```ts
// 建议前端视图状态，不等于当前后端已支持所有字段
export interface AdminTableQuery {
  q?: string;
  status?: string;
  provider?: string;
  sort?: string;
  order?: 'asc' | 'desc';
  page: number;       // 1-based
  pageSize: number;
}
```

- URL 示例：`/agent?q=research&status=ACTIVE&sort=updatedAt&order=desc&page=2&pageSize=20`。
- 搜索输入防抖 250–400ms，`Enter` 立即提交；清空恢复第一页；筛选改变时 `page=1`。
- 后端分页列表只能在**同一查询条件的服务端全集**上排序和统计；不允许拿当前页 20 条数据本地排序然后显示“全局排序”。
- 如果后端暂不支持 `q/sort`：仅在完整、已加载的数据集上提供标注清楚的“当前结果内筛选/排序”，或先禁用相关控件直到接口完成。
- `aria-sort`、键盘导航、输入焦点、菜单外点击关闭必须正常；加载中不丢失搜索文本。
- 列显示偏好可先存 localStorage（不含敏感值）；必需列如名称/模型 ID、状态、操作不得全部隐藏。
- 时间显示统一项目现有国际化策略；后台数据传 UTC，前端格式化，不用硬编码“刚刚”。
- 表格行选择与翻页/刷新兼容；权限不足或派生行选择/操作必须受限。

### 5.3 空状态

| 状态 | 应显示 |
|---|---|
| 首次无记录 | 说明 + 允许时提供“创建”操作 |
| 查询无结果 | `无匹配记录` + 清空筛选 |
| API 加载中 | Skeleton，不闪烁原始零值 |
| API 失败 | 错误描述 + 重试按钮；不得吞异常后显示空列表 |
| 无权限 | 明确“无查看权限”；与零记录区分 |
| 资源已失效 | 保留记录、明显告警、指向修复入口 |

## 6. ResourcePickerDrawer：统一资源关联

平台资源维护与 Agent 绑定必须分开：

```text
平台页（创建、编辑、禁用资源）
       │
       └── Agent 配置 → [添加模型/知识/技能/MCP]
                           └── ResourcePickerDrawer
                                 搜索、状态、版本、来源
                                 已关联标识 / 不可用原因
                                 多选 → 确认 → Agent 作用域保存
```

建议 props：

```ts
interface ResourcePickerDrawerProps<T> {
  open: boolean;
  title: string;
  items: T[];
  selectedIds: string[];
  disabledReason?: (item: T) => string | null;
  getId: (item: T) => string;
  onConfirm: (ids: string[]) => Promise<void> | void;
  onClose: () => void;
}
```

- 仅允许选择**当前 Agent 可绑定**、未关联、启用且符合模式的资源；失效资源可见但不可添加。
- 模型展示 `displayName / modelSpec / mode / enabled / derived`；Skills 展示 `id / version / description`；MCP 展示 `serverName / transport / enabled`。
- 资源数量多时搜索先从服务端查询；目录规模小可全量加载后本地过滤，但需据实说明。
- 抽屉确认时按照资源类型调用**各自既有 API**。不应设计一个虚构的通用 `/bind-resource` 接口，也不应把所有资源保存为同一数据表。
- 保存中禁用重复提交；失败保持抽屉打开并保留选择；成功刷新相关 Agent 绑定详情。

## 7. 前后端接口对照与真实缺口

### 7.1 可直接复用的既有接口

| UI 功能 | 已有前端调用 | 结论 |
|---|---|---|
| Agent 列表/详情/CRUD | `agentApi.listSites/getSite/createSite/updateSite/deleteSite` | 复用 |
| Agent 状态/类型 | `updateAgentStatus`、`updateAgentKind`（带 version） | 复用 |
| 模型入口 | `aiApi.listModelEntries/createModelEntry/updateModelEntry/deleteModelEntry` | 复用 |
| CLI 代理、OAuth、发布模型 | `getProxyServices`、`getProxyServicesUsage`、`startProxyLogin`、`proxyLoginStatus` | 复用 |
| CLI 派生同步 | `syncCliModelEntries` | 复用，注意幂等/只读 |
| Agent 两模式模型链 | `getAgentChain`、`updateAgentChain` | 复用 |
| 链模板 | `listChainPresets`、`loadAgentChainTemplate`、`saveAgentChainAsTemplate` | 复用 |
| Prompt 与模板 | `getAgentPromptConfig`、`updateAgentPromptConfig`、`listPromptTemplates` 等 | 复用 |
| Skills 目录 | `getSkillCatalog`、`listPreviewSkills` | 复用只读目录与 Agent 默认技能配置 |
| MCP 注册及 Agent 三态选择 | `getMcpConfig`、`getMcpSelection`、`updateMcpSelection` | 复用 |
| KB 空间关联 | `listAllKnowledgeBases`、`linkKnowledgeBase`、`unlinkKnowledgeBase` | 复用 |
| Preview & Runtime | 现有 Agent Preview/Runtime 调用 | 复用流式协议与运行状态 |
| 会话及消息记录 | `listConversations`、`listConversationMessages` | 复用；不等于完整执行 trace |

### 7.2 必须单独立项或核实的能力

| 缺口 | 实施建议 | 优先级 |
|---|---|---|
| Agent 列表全局搜索、服务端排序 | 扩展 `GET /admin/agents` 的 `q/sort/order`，白名单字段、稳定排序与索引 | P1（完整表格所需） |
| 批量状态/删除 | 新增受 RBAC 保护的批量端点，返回每条操作结果，提供审计 | P2，可先不做 |
| 连接探针与真实延迟 | 设计诊断接口（时间戳、状态、错误代码、latency），避免浏览器直接探测上游密钥 | P2 |
| 模型 ID 与账号的稳定关联 | 核查 Proxy Usage 与派生条目的关联键，必要时补 DTO 字段；不靠字符串猜测 | P1 |
| Agent 运行事件/trace/成本 | 明确定义 run、message、tool call、usage 的服务端视图及分页 | P2 |
| Agent 复制 | 无确认接口前不展示为可用操作；若补需复制范围规则 | P2 |
| 平台默认模型原子切换 | 将非原子双请求行为移出 Agent 页，后端如需切换提供单次事务端点 | P0（避免错误修改） |
| 密钥校验/脱敏 | 核实写入 API 校验、密文持久化、响应脱敏、日志清洗 | P0 |

**后端变更约束：**

- 先写 DTO/API 契约与迁移说明，再改表与 Controller/Service；优先复用已有 Flyway 管理。
- 分页接口采用明确排序白名单和最大 pageSize，避免动态 SQL 注入。
- 探针调用后台并设超时/并发限流/权限；不允许前端暴露或日志打印上游 API Key。
- 如果当前后端不支持某字段，就保持未提供状态；不能用随机数字或硬编码模拟真实结果。

## 8. 推荐 React 代码组织

```text
apps/admin-web/src/
├── pages/
│   ├── agents/
│   │   ├── agentsPage.tsx                     # 升级为 V2 列表
│   │   ├── agentWorkspaceLayout.tsx           # 横向 tabs + Agent 页头
│   │   └── console/                           # 新增：仅放 Agent 工作区的组合 UI
│   │       ├── AgentOverview.tsx
│   │       ├── AgentConfigLayout.tsx
│   │       ├── AgentModelChainSection.tsx
│   │       ├── AgentPromptSection.tsx
│   │       ├── AgentKnowledgeSection.tsx
│   │       ├── AgentToolsSection.tsx
│   │       ├── AgentDebugWorkspace.tsx
│   │       ├── AgentRunHistory.tsx
│   │       └── AgentSettings.tsx
│   └── ai/
│       ├── modelEntriesPage.tsx                # 模型页外壳 + mode URL 参数
│       ├── modelEntriesCard.tsx                # 逐步拆迁，非一次性重写
│       └── model-connections/
│           ├── ModelModeTabs.tsx
│           ├── CliConnectionsTable.tsx
│           ├── ApiKeyConnectionsTable.tsx
│           ├── CliConnectionDetail.tsx
│           ├── ApiKeyConnectionDialog.tsx
│           ├── ModelEntryDetail.tsx
│           └── modelEntryViewModel.ts
├── components/admin/
│   ├── table/                                 # 第 5 节通用表格
│   ├── ResourcePickerDrawer.tsx
│   └── ...原有组件
├── hooks/agent/
│   ├── useAgentModelChain.ts
│   ├── useAgentResources.ts
│   └── useAgentDirtyGuard.ts
└── router/
    ├── config/routePaths.ts
    └── routes/adminRoutes.tsx
```

这只是**推荐目录与新组件命名**，不强制与现有实际文件完全一致。迁移原则：

1. **复用逻辑，替换视图。** 已有 mutations、query keys、权限、i18n 与 API 适配器，不要复制到两个组件里。
2. 可先做薄容器，把旧 `AgentAiChainPresetSection` / Prompt / Runtime / Knowledge 面板嵌入新配置页，再逐步拆逻辑。
3. 重用 `AdminPageHeader` / `controls.ts` / `StatusTag` / `FormDialog`，避免多套按钮尺寸。
4. 表单用现有 `react-hook-form` + `zod`，不在 JSX 中手工拼表单校验。
5. 数据交互使用 `@tanstack/react-query`，查询键需包含 Agent ID、模式、筛选、分页；切换 Agent 时不得出现旧 Agent 详情串页。
6. UI 状态（tab/抽屉/筛选/调试 Inspector）与服务端数据状态分离；不要把 API 结果复制到全局 Zustand 后又与 React Query 双向同步。
7. 每个影响真实配置的表单都实现 dirty 检测、取消/重置、写入中保护、失败保持草稿。

## 9. 权限、安全和兼容要求

### 9.1 RBAC

- 使用已有 `PermissionGate` / `useHasPermission` 与 `PERMISSION_CODES`，不可用“隐藏按钮”代替服务端鉴权。
- Agent 列表读、模型资源读、创建/编辑/删除、Prompt/KB/MCP/Skills 各自按已有权限分开控制。
- 资源抽屉可查看但不可关联时，应解释缺少权限或资源不可用原因。
- CLI OAuth 登录、API Key 更新、Proxy Client Key 管理均为敏感权限操作。

### 9.2 密钥与隐私

- API Key 只写入，不以明文回显；不存浏览器 localStorage/sessionStorage、不做 URL 参数。
- CLI 代理的账号邮件、客户端 Key 与上游 OAuth token 遵循最小展示原则；展示时脱敏。
- 调试输出、日志与 Toast 需要脱敏，不能把 headers/env/credential 原文打印。
- 用户手动开启定位方可取地理位置；任何定位失败不阻止普通文本调试。

### 9.3 历史路由与数据

| 旧路径/入口 | 新归属建议 |
|---|---|
| `/agent/:id/agent/prompt` | `/agent/:id/agent/config/prompt` |
| `/agent/:id/agent/knowledge` | `/agent/:id/agent/config/knowledge` |
| `/agent/:id/agent/preview` | `/agent/:id/agent/debug` |
| `/agent/:id/agent/conversations` | `/agent/:id/agent/runs`（功能若不完全等价，先保留原入口） |
| `/agent/:id/agent/runtime` | `/agent/:id/agent/config/tools` 或运行状态详情（必须保留未迁移能力） |
| `/agent/:id/basic` | `/agent/:id/agent/settings` |
| `/agent/:id/access` | **先审计其全局写入副作用，再拆分**；Agent 绑定归配置，平台模型入口归模型 API |
| `/ai/model-entries` | URL 保持不变，增加 `mode` 分段 |

对外分享的旧链接应可访问；如旧页面包含新版未覆盖的关键功能，先兼容展示，**禁止为了“路由整洁”直接丢功能**。

## 10. 分阶段 PR / 执行任务

采用小步部署，不一次性替换所有 Agent 页面。

### PR-0：基线、回归与风险隔离（P0）

**任务：**

- [ ] 从最新 `dev` 新建分支，记录当前路由与菜单权限映射。
- [ ] 运行并记录 `admin-web` 的 build / lint / 已有 Playwright 冒烟；标记当前已有失败，避免把遗留失败算成新改动。
- [ ] 盘点旧 Agent 工作区的所有能力、页面及权限，形成一张迁移对照表。
- [ ] 审查 `AgentAccessPage` 是否修改平台默认模型；在 Agent 页面移除/阻断该误导性的写操作。
- [ ] 审查密钥回显/日志与 CLI 派生只读规则。
- [ ] 确认新 UI 不影响现有知识库关联、代理账号 OAuth 登录、流式预览。

**验收：**旧功能可用；跨 Agent 变更无误触平台默认；安全边界明确。

### PR-1：设计底座与表格组件（P1）

- [ ] 复用 design tokens，抽取必要的 `DataTableToolbar/RowActions/ColumnMenu/BulkActions`。
- [ ] 统一弹窗/抽屉/状态标签/空态和 loading。
- [ ] 支持客户端可验证的排序/筛选（前提是全量数据）；服务器分页表格只对接真实参数。
- [ ] 新增表格交互单测/组件测试和键盘可访问性检查。

**验收：**列表与模型 API 使用同一套表格交互；无布局跳动；暗色模式/密度兼容。

### PR-2：智能体列表与 Agent 工作区骨架（P1）

- [ ] 列表改为 V2 表格；名称进入详情，动作折叠行菜单。
- [ ] Agent 页头与横向 `概览/配置/调试/运行记录/设置` tabs。
- [ ] 配置子导航单独出现；调试 Inspector 暂可沿用旧组件。
- [ ] 路由兼容与 i18n；修改 Agent 后刷新对应 Query。
- [ ] 窄屏、折叠导航验证。

**验收：**新旧深链接有效；Agent 切换不串数据；已有权限保持。

### PR-3：模型 API 双模式（P1，核心）

- [ ] `CLI` 和 `KEY` 用 URL 参数保持独立页面状态。
- [ ] 将 `modelEntriesCard.tsx` 拆为两套模式内容、共用表格/详情层。
- [ ] CLI：代理账号状态、OAuth 操作、模型列表、派生只读、同步报告。
- [ ] KEY：新增编辑、密钥脱敏、模型 ID、endpoint、enabled、version 冲突。
- [ ] 明确展示 `modelSpec` 的实际模型 ID，未知不猜测。
- [ ] 复核 Proxy Client Key 与上游密钥概念不混淆。

**验收：**两种资源互不混入；模型版本可追踪；CLI 派生不能误编辑；KEY 不泄密。

### PR-4：Agent 配置（P1，核心）

- [ ] 两模式模型链编辑器、状态告警、保存、模板导入/导出。
- [ ] `ResourcePickerDrawer` 用于模型、知识、Skills、MCP 的各自绑定流程。
- [ ] Prompt 模板套用和 Agent 自有副本、Temperature 校验。
- [ ] MCP 三态与有效集合展示；Skills ID 严格校验。
- [ ] 未保存保护、写失败回滚/保留草稿与缓存刷新。

**验收：**只更改目标 Agent 配置；刷新后服务端状态一致；无伪成功。

### PR-5：调试与记录（P1/P2）

- [ ] 聊天区 + 折叠 Inspector，调试入口显示具体模型。
- [ ] 移除/优化预览前的隐式定位等待。
- [ ] 当前 Agent 的会话/消息记录以表格展示。
- [ ] 缺少真实 run trace 时明确降级；后端扩展独立实施。

**验收：**流式试聊不回归；模型链与调试选择一致；错误反馈和可追踪 ID 正确。

### PR-6：完整回归、可用性与收尾（P1）

- [ ] 统一页面宽度、按钮层级、间距、图标和状态色。
- [ ] 新增端到端用例和安全回归；合并冗余组件。
- [ ] 移除已完成迁移的旧路由页面逻辑（但保留必要 redirect）。
- [ ] 更新 `docs/admin-web/CURRENT_STATE.md`、相关路由/操作文档。
- [ ] 将未来后端缺口登记 Issue，而不是在前端造假完成。

**验收：**符合第 11 节的逐项 DoD。

> 允许 PR-3 与 PR-2 视人员安排部分并行，但**表格底座/API 契约先稳定**；PR-4 不应在模型入口模式与 ID 契约不稳定时先完成。

## 11. 测试与验收（Definition of Done）

### 11.1 关键 E2E 场景

| 编号 | 操作 | 必须断言 |
|---|---|---|
| E01 | Agent 列表搜索、筛选、分页 | 参数/结果一致，刷新或返回保留约定状态 |
| E02 | 从列表进入 Agent A → 切换 Agent B | 标题/模型链/Prompt/Skills 全部属于 B，不闪现 A 的数据 |
| E03 | 新建 Agent | 正确请求、权限、错误提示、完成后导航 |
| E04 | Agent 状态与类型修改 | 正确提交 `version`；409/冲突时刷新而非覆盖 |
| E05 | 模型 API CLI ↔ KEY 切换 | URL 恢复正确，各模式资源不串页 |
| E06 | CLI OAuth/同步、打开派生模型 | 显示真实状态；派生行无修改/删除入口 |
| E07 | API Key 创建/编辑 | 密钥不回显；空白编辑不清空现有密钥；字段级校验 |
| E08 | 模型链 KEY 添加/排序/保存 | 刷新后顺序不变；CLI 链未修改 |
| E09 | 切换到 CLI 链并保存 | KEY 链保留，绝不更新平台 `defaultEntry` |
| E10 | 添加停用的 Skills/MCP/模型 | 不允许新增，给出原因；历史失效项可见可处理 |
| E11 | Prompt 套用模板并本地编辑 | 本地保存成功；修改模板不会自动改 Agent 本地副本 |
| E12 | KB 关联/解绑 | 只改变 Agent/空间关系，不删除原知识库 |
| E13 | 调试发送与检查器 | 流式可用、错误可见、Inspector 反映真实执行信息 |
| E14 | 未保存修改后切换 tab/Agent | 防丢失确认；取消后草稿仍在 |
| E15 | 无权限账户进入 | 写按钮不可操作，服务端拒绝非授权请求 |
| E16 | 390/768/1024/1440px | 无内容遮挡、关键操作可用、弹窗无溢出 |
| E17 | 暗色/密度切换 | 对比度/状态可读，表格、抽屉无视觉断层 |
| E18 | 旧 `/agent/:id/agent/preview` 等链接 | redirect 或能力等价，历史收藏不失效 |

### 11.2 手动 QA 注意

- 逐个验证行内 `···`、弹窗外点击、ESC、键盘 Tab、焦点回退、屏幕阅读器名称。
- 空数据、禁用数据、部分 API 失败、网络超时、代理下线、无 Client Key、失效知识空间都需测试。
- 大于 100 条列表、长 Agent 名称/长模型 ID、中文与英文、两种主题、浏览器缩放 125–150%。
- 新建/编辑/删除前后的查询缓存失效正确，不能靠整页 reload 掩盖状态问题。
- 模型列表更新与 Agent 链缓存保持一致；已失效成员显示风险，不静默忽略。

### 11.3 命令与合并门禁

在 `apps/admin-web` 目录：

```bash
npm run lint
npm run build
npm run test:e2e
```

> 按仓库实际锁文件与包管理器执行安装/工作区命令；以上仅列项目 `package.json` 已声明的 scripts。**不能把“命令已列出”当成“已经通过 CI”。**

PR 合并至少满足：

- [ ] 每条使用中的按钮都有对应合法操作，不存在假成功。
- [ ] 无新泄密风险、权限绕过或全局配置副作用。
- [ ] 旧 Agent 能力未无故消失；旧 URL 可迁移。
- [ ] ESLint、TypeScript build、相关单测/E2E 通过；遗留失败写明。
- [ ] UI 和 V2 原型交互/布局对照通过；符合设计系统，不新造一套 token。
- [ ] 新增/调整的真实接口有前后端契约与错误处理。
- [ ] 文档、i18n 和必要的权限菜单种子数据同步更新。

## 12. 实施时最容易踩的坑

1. **把 HTML 直接翻成 JSX。** 原型拥有模拟 `resources`、`agents` 及大量 DOM 行为；真实代码要接 React Query/Router/RBAC，不能复制 fake action。
2. **把 CLI 登录与 KEY 注册当作同一种表单。** CLI 是代理账号/OAuth/派生资源；KEY 是密钥型模型入口；`derived` 只读。
3. **只有模型显示名，没有精确模型 ID。** 实际调用必须保存准确 `modelSpec`；营销版本名只是附属展示。
4. **将 Agent 级配置变为平台级设置。** 平台 `defaultEntry` 不等于 Agent 首选模型，禁止混淆。
5. **只把当前页排序当作全局排序。** 查询、分页、排序需要同源契约。
6. **把 MCP 已选择当成有效。** `INHERIT/OFF/CUSTOM` 与平台可用集要一起解析。
7. **误删旧能力。** 现有 `/agent/:id/basic/access/theme/contents/files` 的功能要有迁移归宿。
8. **菜单只改前端。** 真实菜单可受后端授权数据/种子控制，必须一起审查。
9. **调试时隐式定位阻塞。** 普通预览不能每次依赖浏览器地理定位。
10. **只验证视觉。** 模型链写入、版本冲突、错误状态、权限、长文本与移动端同样属于验收。

## 13. 给 Cursor / Codex 的执行指令（可直接复制）

```text
你正在修改 hqjiayou/my-website 仓库的 dev 衍生分支。

首先阅读：
1. AGENT_CONSOLE_V2_IMPLEMENTATION.md
2. docs/admin-web/DESIGN_SYSTEM.md
3. .cursor/rules/admin-web-design.mdc
4. apps/admin-web/src/router/config/routePaths.ts
5. apps/admin-web/src/router/routes/adminRoutes.tsx
6. apps/admin-web/src/api/apps/agentApi.ts
7. apps/admin-web/src/api/apps/aiApi.ts
8. apps/admin-web/src/types/ai.ts
9. 对应 PR 的目标页面/组件与相关 E2E。

目标 UI 参考：agent_admin_redesign_v2.html。
但 HTML 是交互原型，有模拟数据，禁止直接复制其状态管理或虚构接口。

只执行实施文档中指定的一个 PR 阶段；先输出：
A. 当前代码与文档的差异
B. 将修改的文件及原因
C. 现有 API 是否足够；缺口列表
D. 风险、回滚点与测试方案

然后实际编码。要求：
- 复用现有 React Query、Router、RBAC、i18n、主题变量和公共组件。
- Agent 模型链与平台默认模型严格隔离。
- CLI/KEY 双模式清晰且显示完整模型 ID；CLI 派生项只读。
- 不新增不真实的模型/延迟/配额数据，不制造假成功按钮。
- 不影响非 Agent 页面，保留现有旧路由兼容。
- 完成后运行 lint、build 与相关 E2E；报告真实结果及未完成项。

禁止一次性大规模重写业务；按 PR 切分，并在每个阶段末给出可验证的改动摘要。
```

---

## 14. 最终交付清单

- [ ] V2 页面与交互落在原 `apps/admin-web`，而非另起一个假后台。
- [ ] Agent 列表、详情五个 tabs、配置四分区实现。
- [ ] CLI/KEY 模型双视图、精确模型 ID、密钥/登录安全及模型链生效。
- [ ] 通用 DataTable/资源抽屉可复用，不出现按钮样式碎片化。
- [ ] 调试和执行记录使用真实数据，有明确的功能降级边界。
- [ ] 真实菜单和权限正确；新旧路径迁移可控。
- [ ] 测试/截图/回归、开发文档、接口契约、后端缺口 Issue 均交付。

**优先顺序总结：**先安全边界与表格底座 → 再 Agent 页面骨架 → CLI/KEY 模型管理 → Agent 配置 → 调试/记录 → 回归收尾。**不要以重构 UI 为由重写已有后端。**
