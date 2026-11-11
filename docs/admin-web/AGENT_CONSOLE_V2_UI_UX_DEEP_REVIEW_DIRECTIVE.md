# Agent Console V2 — UI/UX 深度精修执行指令

> 交付给 Cursor / 代码 Agent 的实施任务书  
> 审查基线：`hqjiayou/my-website`，`dev`，`6f87ce472f86a5a30dd01f672ee6de9c219216c6`。  
> 范围：`apps/admin-web` 的 Agent Console、模型 API 以及这些页面直接复用的管理 UI 基础组件。  
> 目标：在不改变业务能力、后端协议和现有信息架构的前提下，修复真实交互缺陷，统一布局、密度、状态、浮层与键盘可访问性。**不要另起 V3 原型或引入第二套 UI 库。**

## 0. 执行规则与基线

1. 先读：`docs/admin-web/DESIGN_SYSTEM.md`、`.cursor/rules/admin-web-design.mdc`、`docs/admin-web/AGENT_CONSOLE_V2_IMPLEMENTATION.md`、`docs/admin-web/agent_admin_redesign_v2.html`、`docs/admin-web/CURRENT_STATE.md`、`docs/admin-web/TESTING.md`。文档冲突时：现有业务/API 契约 > DESIGN_SYSTEM 的尺寸与 token > V2 HTML 的布局交互参考；HTML 中的示例数据和状态不得照搬。
2. 检查 `git status`、当前 `HEAD`、`origin/dev` 差异、未跟踪文件。保留用户改动；不得 `reset --hard`、强行覆盖、私自 push/merge。若 HEAD 更新，在最新代码复核下述问题后实施，不重复修复已解决项。
3. 先运行 `pnpm exec tsc --noEmit`、lint、build、`test:table`、`test:boundary`、`test:sse`、`test:e2e:mock`，记录基线失败。已发现 `04-agent-console-v2.mock.spec.ts` 仍指向已删除的行菜单「Manage subscriptions」，应优先核对。
4. 不把 Docker、真实登录失败当作 Mock UI 测试的前置条件：本仓 `playwright.mock.config.ts` + `loginWithMocks()` 已能启动真实 React 页面并替换 API。Mock UI 的浏览器截图、交互测试必须完成；真实 API/登录继续单独标为未验证。
5. 本轮仅 Agent Console 及直接共享组件；共享组件修改必须验证 users/roles/menus 等旧调用。仅修 UI / 可访问性 / 表现层状态；后端搜索、完整 Trace、费用、批量事务、平台默认模型等没有 API 的能力不得伪造。

## 第一阶段：P0 — 先修真实交互回归（独立提交）

### A. 表格行导航恢复原生语义

涉及：`src/components/admin/dataTable.tsx`、`src/pages/agents/agentsPage.tsx`，以及调用方。

- 当前 `<tr role="link" tabIndex={0} onKeyDown={Enter/Space}>` 会改变表格语义，并可能接收行内 `<button>`、`<select>`、菜单项冒泡的按键。**删除在 `<tr>` 上模拟链接的 ARIA/键盘模式**。
- Agent 名称作为真正的 `<Link to={...}>`，提供可聚焦、可复制链接/新标签页打开的导航；行的鼠标点击如需保留，只能在非交互区触发，避免抢占文本选择和控件事件。若保留 `onRowClick`，忽略来自 `button, a, input, select, textarea, [role=button], [contenteditable]` 及用户文本选择的事件。
- 验证 Enter/Space 操作 kind 下拉、订阅按钮、行菜单、列管理不会误跳转；行内 Tab 顺序自然；不把鼠标整行跳转当成唯一入口。
- 修改共享组件时保证其他使用 DataTable 的页面无回归。

### B. 修复过时 Mock E2E

涉及：`e2e/04-agent-console-v2.mock.spec.ts`。

- 订阅入口已经从 `DataTableRowActions` 移除，测试仍在菜单查找它。更新 locator 到订阅列中实际可用的“管理订阅”按钮。
- 增加列表最后一行菜单、行内控件键盘操作及点击不误导航用例；避免为使测试通过而恢复重复入口。
- `test:e2e:mock` 所有用例必须通过；如原测试失效来自界面修改，报告具体原因。

### C. 配置区真正以内容宽度响应

涉及：`src/pages/agents/console/AgentConfigLayout.tsx`、`src/pages/agents/agentAccessPage.tsx`。

- 1024–1279px 开启 256px 全局侧栏后，`lg:` 两栏仍会挤压配置内容。配置子导航从 **`xl`（1280px）** 起才可左右分栏；`<1280px` 改横向滚动的配置 tabs。若侧栏收起导致空间足够，可使用 container query 以**实际容器宽**决定，而非仅 viewport 宽。
- Access 两列卡片不要机械沿用 `lg:grid-cols-2`，在内容宽不足时单列，只有卡片实际可用宽度足够再双列。
- 390/768/1024/1279/1280/1440px 测试：表单字段不会缩到难用、没有整页横向滚动、活动子项清晰可见；横向配置导航在当前项被滚出时能显示或滚动定位当前项。

### D. 公共弹窗/抽屉焦点与语义

涉及：`src/components/admin/formDialog.tsx`、`src/components/admin/ResourcePickerDrawer.tsx`、`src/pages/agents/agentAiDebugInspector.tsx`。

- `FormDialog` 当前写死 `aria-labelledby="form-dialog-title"`，同屏两个弹窗会重复 ID。用 `useId()` 或等效方法生成每实例独立标题 ID。
- 为实际模态弹窗提供：首次打开聚焦标题/首个可操作控件；Tab/Shift+Tab 焦点约束；背景 inert 或等效交互阻断；Esc 规则（危险确认与未保存确认不得绕过）；关闭后焦点还原；滚动锁定及嵌套浮层的计数/恢复，防止子确认关闭时误解锁父抽屉。
- Inspector 窄屏有遮罩时按模态处理，追加 `role="dialog"`、`aria-modal` 和正确标签；桌面 `xl+` 停靠侧栏属于非模态区域，不应禁用聊天操作或锁 body。
- 对窗口尺寸从 1024 改到 1440、以及反向改回的情况，抽屉锁滚动状态应随真实媒体条件更新，不只在打开瞬间计算一次。
- 明确选定窄屏 Inspector 的交互：遮罩模式下聊天区虽可见，但应不可交互；如希望边看 Inspector 边输入，需设计为真正的非模态可缩放布局，而不能只“露出输入框”却被遮罩挡住。

## 第二阶段：P1 — 信息架构细节和布局一致性（独立提交）

### E. 全局 Header / Agent 工作区

涉及：`src/layouts/components/header.tsx`、`sidebar.tsx`、`src/pages/agents/agentWorkspaceLayout.tsx`。

- 顶栏已有无实际动作的搜索 `<input>`：若没有真实全局检索/导航搜索能力，暂时移除或用明确不可用说明；不能保留看似可输入但无反馈的假搜索。未来的 Ctrl/⌘K 可单独提 Issue，不在本轮伪造。
- 按 DESIGN_SYSTEM：全局顶栏负责导航位置/面包屑，Agent 页主体负责 Agent 名、状态、代码、类型与主要操作。清除重复“页面标题 + Agent 标题 + 配置标题”的冗余，但保留必要上下文；不要删除已有可访问操作。
- Agent 顶部保持一条清晰紧凑的信息带：返回列表（Lucide 图标+可读 label）、Agent 名、真实状态、code/type、主要“调试”动作、次级更多菜单。更新日期低优先级，窄屏移到下一行或隐藏次要信息，禁止截断主要名称而保留琐碎元信息。
- 工作区主导航固定为 `概览 / 配置 / 调试 / 运行记录 / 设置`；只能有一条 Agent 主 Tabs。活动态、焦点态、横向溢出与移动端滚动一致；`aria-current="page"`/原生 NavLink 的语义需可用。
- 页面宽度：数据/调试工作区最大宽参考 1460；设置与长表单参考 1200；避免超宽显示器上正文撑满。不能为了限宽挤压 Debug 主要聊天空间。
- 全局侧栏在 `<1024px` 始终以 256px 的抽屉展示，桌面折叠偏好不改变手机抽屉内容；检查触屏关闭、Esc、焦点和遮罩层叠。

### F. Agent 列表与通用 Table

涉及：`agentsPage.tsx`、`dataTable.tsx`、`table/DataTableRowActions.tsx`、`DataTableToolbar.tsx`。

- 列表首屏重点依次：Agent 名/code → 类型/状态 → 模型链 → Prompt/Skills → 知识关联 → 更新时间 → 行操作。字段真实，不引入模拟成功率/延迟/费用。必要时合并低优先级副字段，移动端保持横向表格滚动，而不是压缩到无法阅读。
- 表头/行距/状态点/时间数字全部按 DESIGN_SYSTEM token 与密度变量走，不自行加入新色系；长 model ID/Base URL 应可复制、悬停完整可见。
- `DataTableRowActions` 已用 Portal+fixed 解决主要裁切问题，但需补：菜单内容变化时重新测高（ResizeObserver 或等效）、浏览器底部/右边界和 200% 缩放时可见、滚动容器滚动时跟随、打开时把焦点送到可操作项、Esc 还原到触发按钮、菜单动作只执行一次、危险操作保留明确确认。
- 仅当前页数据的客户端搜索/排序必须显式说明作用范围。Agent 后端没有全局 `q/sort` 时，不上线全局搜索或伪装服务端排序。`serverOnlyHint` 应以简短辅助信息表现，不长期占用多余整行高度。
- 筛选重置页码、列管理保留关键列、当前页选择与分页状态、loading/empty/error/filtered-empty/retry 均验收。不要引入未实现的批量停用/复制。

### G. Agent 概览的数据可信度和可扫描性

涉及：`src/pages/agents/agentOverviewPage.tsx`。

- **实际发现**：知识统计通过 `listKnowledgeDocuments(...pageSize:100)` 获取前 100 条，`indexedCount`、`failedCount` 从这批数据算，却与服务端 `total` 同屏展示，并以 `indexedCount/total` 画比例条。这不是全量统计。若无真正聚合 API：不能标成全量索引进度；可只展示可信的 `total`，把已索引/失败数去掉或标注“当前加载的最多 100 条”，进度条不作全量比例。优先复用后端已有聚合接口，如无则不要改后端。
- 模型链概览中 `map(...).filter(entry != null)` 会静默丢掉失效模型引用；必须保留占位节点 `#ID · 入口不存在/失效`，并提供到模型链配置页的排障入口，不能把缺失误判为正常。
- 异步 Query 分别处理 loading/error/empty/无权限；不要把“接口失败”与“未配置”都归为 `—` / 不可用；无权限不应显示误导性失败。
- 概览目标是一个屏幕可判断 Agent 的模型链、Prompt、知识、Harness/Skills/MCP 是否就绪，卡片只展示可核验信息。链接用明确动词，不叠加无意义小标签；同一指标只能一个真相源。

### H. 配置中心四页

涉及：`console/AgentConfig*`、`agentAiChainPresetSection.tsx`、`agentAiPromptPanel.tsx`、`agentAiRuntimePanel.tsx`、`AgentKnowledgeSubscriptions.tsx`。

- 统一层次：**标题与一句说明 → 必要摘要 → 当前配置 → 修改入口 → 保存/取消/结果**。减少卡片套卡片，不重复标题；`AgentConfigSectionShell` 与内部业务面板各自承担清楚职责。
- 模型链：CLI 和 KEY 两组并列逻辑清楚，节点显示真实 displayName、完整 modelSpec、主用/回退顺序与失效状态；增加、移除、上下移动及保存可快速识别；首项为 Agent 主用，不混平台 defaultEntry。套用链模板可能覆盖当前链，展示影响范围与确认。未保存提示始终可见但不压过业务内容。
- Prompt：系统 Prompt 编辑区最大可读宽，模板拷贝与“引用自动同步”明确区分；温度和默认 Skills 的说明靠近字段；保存失败草稿保留；选择 Skills 可见真实目录名/版本及禁用原因。
- 知识：已关联空间和文档/索引管理分两块，管理关联绝不等于删除空间；空状态告诉下一步动作。模型与知识摘要加载中使用 skeleton/加载文案；错误时提供**可点击重试**，不能仅把 `t('aiWorkspace.retry')` 当成静态摘要文字。
- Tools：INHERIT/OFF/CUSTOM 模式语义可直接读懂；CUSTOM 选择先进入本地草稿、保存后才持久化；Runtime 只读状态应明显区别于可编辑 MCP 选择，不能混作一块表单；不可用/未知/未选择分开呈现。
- 所有子页切换仍受 PR-6 的 `useBlocker` 守卫保护：浏览器前进/后退、顶部 Tabs、配置子导航、Agent 切换、刷新/关闭均需验证；401/鉴权失败的强制退出不应因编辑草稿被永久卡住。

### I. 通用 ResourcePickerDrawer

涉及：`src/components/admin/ResourcePickerDrawer.tsx` 与四个业务适配层。

- 抽屉搜索置顶、可滚动列表、底部确认条在可视区，390px 下可滚动到所有字段与确认按钮；已选择数量和“取消 ≠ 保存”反馈清楚。
- 当前资源复选框 `aria-label={id}` 仅朗读数字 ID，应提供 `getAccessibleName(item)` 或与资源名称单元格关联，让屏幕阅读器朗读资源名称＋不可用原因；ID 是辅助信息，不是唯一可访问名称。
- 已关联、已选、不可用、查询失败、空结果须用不同语义状态；已有失效引用应允许辨识和移除，不悄悄丢弃。
- 资源选择打开时冻结原始已选集，后台刷新不重置用户勾选；保存失败不清草稿，部分成功要展示实际成功与失败项；不增加伪批量后端事务。
- 若在抽屉内再弹出放弃确认，必须保证两个 dialog 的唯一 aria-labelledby、焦点层级、Esc 与背景锁定正确。

### J. 模型 API：CLI / API Key

涉及：`modelEntriesPage.tsx`、`modelEntriesCard.tsx`、`modelEntriesCliPane.tsx`、`modelEntriesKeyPane.tsx`。

- 保留 `/ai/model-entries?mode=CLI|KEY` URL 真相源；切换、刷新、复制链接、后退前进正确。顶部一处 mode tabs + 随模式切换的一个主操作：CLI 登录/同步，KEY 新增入口；不要同时强调两个互斥操作。
- 首屏优先：模式切换 → 当前条目与关键异常 → 搜索/筛选/表格。`Client Keys` 与 `ChainPresetsCard` 属次级管理能力，可折叠/分区导航或明确锚点，减少首屏堆叠；折叠不等于删除功能，不新增路由也可实现。
- CLI：账号与模型从属关系、认证状态、发布状态、未知状态、真实用量清晰；发布状态接口错误时必须显示“未知”，不能写“未发布”。derived 保持只读；没有实际探针就不显示假 latency/quota。
- KEY：脱敏 Key hint、供应商、完整 modelSpec、baseURL、启用、平台默认、更新时间；`defaultEntry` 只由模型 API 设置，Agent 配置不可修改；密钥永不回显原文。
- 对无法靠全局 API 支持的搜索明确标“当前加载的数据”，多语言名称/别名不破坏行布局；表格菜单、列隐藏和空状态共用公共组件。

### K. Debug 与 Runs

涉及：`agentPages.tsx`、`agentAiPreviewChat.tsx`、`agentAiDebugInspector.tsx`、`agentAiConversationsPanel.tsx`。

- Debug 聊天占主要视觉面积：顶栏显示模式/请求模型，聊天记录区域独立滚动，输入区稳定处于可见底部；`h-[calc(100vh-11rem)]` 要在 390px、短屏、浏览器缩放、移动键盘以及顶部信息换行时实测。优先 `100dvh` + flex/min-h-0/局部 overflow，而非堆叠固定魔法高度。
- `xl+` Inspector 在右侧停靠，默认关闭；1024px 与手机走抽屉，避免同时常驻全局侧栏、配置导航和 Inspector。Inspector 顺序：模型、执行/tool、Skills/MCP、Todo、Usage、显式定位。没有服务端 meta 就用“未提供”，不是 0；请求模型和实际执行模型分开。
- Debug 所选 Skills/模型/定位只影响预览本轮，不得更改 Agent 默认设置；定位默认关闭且失败不阻塞发送；Agent 切换状态隔离、SSE 流取消和迟到事件过滤不得被 UI 改动破坏。
- Runs 真实来源是 Conversations，非完整 Trace；宽屏列表/详情并排，但不应仅凭 `md:768` 就在内容很窄时强制 330px 左栏。内容宽不够时切换列表→详情单页视图，并提供返回；会话来源、时间、真实消息/用量存在时展示，loading/error/empty/retry 完整。

### L. Settings / 高级入口

- Basic、Access（平台默认只读）、Theme、Contents、Files 及生命周期入口均可达；不能因为精简 Settings 隐藏原有功能。
- 设置页建议限宽约 1200、分区明确，顶部首先显示当前 Agent 的基础信息，Access 用只读“平台入口参考”表述；旧路径 redirect 和离开守卫继续有效。
- 不堆叠多层相似卡片、同一页面不要重复大标题；危险操作需包含 Agent 名称的二次确认。

## 第三阶段：P2 — 统一视觉与无障碍（独立提交）

1. DESIGN_SYSTEM 唯一视觉权威：侧栏 256/64，Header 64；数据 1460、设置 1200、文档 880；4px 间距，语义 token，1px border，内容卡不加阴影、阴影只给浮层。不能照抄 V2 HTML 的暗色侧栏/假数据。
2. 统一字阶：20 页头、16 分区、14 正文、12 元信息；禁止为了“紧凑”普遍使用 10/11px 说明文字。控制在设计规范可读最小值，特别是日语长标签。
3. 统一主次操作：每个区域一个明确主 CTA；其他放 secondary/ghost 或更多菜单。危险操作用危险色和确认，不用普通主色按钮伪装。
4. StatusTag 区分 ACTIVE/DISABLED、unknown/loading/error；色彩只表达业务状态，不作花色装饰；深色模式同步检查对比度 ≥ WCAG AA（正文 4.5:1、重要图标 3:1）。
5. 按 `default/compact/comfortable` 三档分别实测表格行、按钮、工具栏、卡片和弹窗；切换密度后界面不抖动、不遮挡内容，动效遵循 prefers-reduced-motion。
6. 检查焦点可见性、键盘 Tab/Shift+Tab/Esc/Enter/Space、屏幕阅读器名称、表格列头和错误 `role=alert`。Tooltip 不作为重要信息唯一承载；错误提示不可只依赖颜色。
7. 三语 zh-CN/en-US/ja-JP 下验证按钮和 Tabs 不截断业务关键信息；日期/数字用当前界面 locale，长 modelSpec/Base URL 可复制或可扩展。
8. 组件布局不得产生意外整页横向滚动；表格内容区自身横滚是允许的。检查 200% zoom 与系统字体变大情况下，仍可到达主要操作。

## 第四阶段：浏览器验收矩阵（必做，**不依赖 Docker**）

采用真实 React 应用 + `playwright.mock.config.ts` / `loginWithMocks()`；禁止仅给登录页或 `design-preview.html` 截图来冒充 Agent 页面验收。

**视口**：390×844、768×900、1024×768、1279×800、1280×900、1440×900，条件允许补 1920×1080；浅色/深色各至少一组，default/compact 两种密度，桌面侧栏展开/折叠。

**必拍画面**：
- Agent 列表：普通态、筛选后空态、错误态、最后一行 `···` 展开及向上翻转。
- 工作区：概览、主 Tabs 溢出、配置模型链（CLI/KEY 两链）、Prompt、知识、Tools（MCP 三态）。
- ResourcePicker：搜索结果、已选择/不可选、手机展开、确认及放弃提示。
- 模型 API：CLI、KEY、账号异常/状态未知；长 modelSpec、编辑弹窗。
- Debug：默认关闭 Inspector、展开 Inspector、聊天有内容、流式状态、窄屏输入区。
- Runs：会话列表、消息详情、无运行元数据提示；Settings：Basic/Access/Advanced。
- 公共弹窗：二次确认、Esc、Tab、焦点恢复；深色下保证对比。

**自动断言**：
- 无 JS console error、无非预期 `document.documentElement.scrollWidth > innerWidth`、标题/按钮不被遮挡。
- 菜单底部/右边缘不出视口；滚动后仍锚定正确；同时打开菜单与 Modal 不乱层级。
- 390/768/1024 下主要保存/确认 CTA 可见或可明确滚动达到。
- 多语言切换和大小字体下无文本遮挡、错误 aria-label、重复 ID。
- Tab/Shift+Tab 不逃出模态；Escape/关闭后焦点返回；背景不可点；滚动锁最终还原。
- Enter/Space 在行内 select/按钮上不导致 Agent 详情跳转。
- 401/会话过期时强制退出仍可执行；有草稿时普通路由离开受拦截。

**测试命令**（以仓库实际脚本为准）：
```
cd apps/admin-web
pnpm exec tsc --noEmit
pnpm run lint
pnpm run build
pnpm run test:table
pnpm run test:boundary
pnpm run test:sse
pnpm run test:e2e:mock
```
真实后端 `/api/auth/admin/login` 问题若未修复，`test:e2e` 标记为 BLOCKED，**不能替代为 PASS**。

## 最终交付与提交规范

请分三次独立提交，**不要一口气提交全部**：
- `UI-FIX-1`：P0 表格键盘语义、E2E、配置断点、弹窗/Inspector 可访问性；
- `UI-FIX-2`：P1 各页面信息结构、真实统计口径、响应式与核心操作；
- `UI-FIX-3`：P2 视觉 token、密度、深色、多语言与全矩阵测试。

每阶段提供：**代码位置/问题复现 → 修改方案 → UI 前后差异截图 → 自动测试 → 未验证项 → commit hash**。若某问题难以证实，先用 Mock E2E 复现，不应猜测修复。

截图保存在 `tmp/console-v2-ui-audit/<commit>/<viewport>/<page>-<state>.png` 等临时目录，默认不纳入 git；如需基线图，专门提交并说明用途，避免截图噪声污染业务提交。

最终提供一个表格，逐项列出：问题 ID、级别、实际根因、文件、改法、验收截图、测试覆盖、状态（PASS/FAIL/BLOCKED）、风险。保留 Agent CRUD、CLI/KEY、平台默认边界、Agent 自有链、Prompt 模板复制、Skills 目录、KB link/unlink、MCP 三态、SSE/Run 状态隔离及旧路由功能，任何回归都要停止合并。

**完成上述 UI 验收不代表真实端到端系统已经验收；真实登录失败和业务 API 联调仍为独立门槛。不要自动 push/merge。**
