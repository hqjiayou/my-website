# Cursor 指令索引（my-website）

本目录为 Cursor Agent / Rules 的**项目级编排**，与根目录 [`AGENTS.md`](../AGENTS.md) 互补：`AGENTS.md` 是全仓 Source of Truth 与阅读顺序；此处是可复用的**角色分工与硬约束**。

## Rules（`.cursor/rules/`）

| 文件 | 作用域 | 说明 |
|------|--------|------|
| [`project.mdc`](rules/project.mdc) | 全仓 `alwaysApply` | 新建、引用与落点 |
| [`backend-structure.mdc`](rules/backend-structure.mdc) | `website-services` 的 `*.java` 与 Flyway `*.sql` | Maven 模块、包树、Flyway |
| [`agent-services.mdc`](rules/agent-services.mdc) | `services/agent-services/**` | Python AI Execution（ADR-0004/0005） |
| [`ai-domain.mdc`](rules/ai-domain.mdc) | AI Java、`agent` 客户端、`aiApi` / `types/ai`、AI 页 | 跨模块 AI 归属与契约 |
| [`admin-web.mdc`](rules/admin-web.mdc) | `apps/admin-web/src/**` | API/mock、权限、i18n |
| [`admin-web-router.mdc`](rules/admin-web-router.mdc) | `router/**` | 路由表与 permission |
| [`admin-web-styles.mdc`](rules/admin-web-styles.mdc) | `assets/styles/**` | 全局样式三层 |
| [`agent-portal.mdc`](rules/agent-portal.mdc) | `apps/agent-portal/**` | 站点用户端 PWA：API、站点上下文、移动端体验 |
| [`docs.mdc`](rules/docs.mdc) | `docs/**` | CURRENT_STATE / 契约同步 |

## Agents（`.cursor/agents/`）

| Agent | 何时用 |
|-------|--------|
| [`codebase-explorer`](agents/codebase-explorer.md) | 只读盘点：实现 / mock / 调用链 |
| [`architecture-planner`](agents/architecture-planner.md) | 范围不清、要最小方案与验收项 |
| [`admin-backend`](agents/admin-backend.md) | Java admin、Flyway、RBAC、Admin AI API |
| [`admin-frontend`](agents/admin-frontend.md) | admin-web UI、mock→real、i18n |
| [`portal-frontend`](agents/portal-frontend.md) | agent-portal 用户端 UI、AI 对话、模式切换、移动端 |
| [`agent-services`](agents/agent-services.md) | Python runtime、SSE、RAG、MCP/Skills/Todo |
| [`integration-reviewer`](agents/integration-reviewer.md) | 变更审查四层：单文件、逻辑、功能、模块 |
| [`area-reviewer`](agents/area-reviewer.md) | 按域：缺陷判定标准，或功能完善盘点 |

## Skills（`.cursor/skills/`，全自动工作方式）

一种工作方式一个入口，在对话里输入 `/<入口>` 加一句任务。

| 入口 | 用途 | 你参与的点 | 续跑 | 停止条件 |
|------|------|-----------|------|----------|
| [`/dev`](skills/dev/SKILL.md) | 全自动开发一个需求 | 开始时确认验收项 | 是 | 验收项全过，且本次 diff 审查无 BLOCKER / HIGH |
| [`/fix`](skills/fix/SKILL.md) | 修一个已知问题 | 不参与（复现不了才问） | 是 | 复现测试转绿、模块测试全过、审查无 BLOCKER / HIGH |
| [`/hunt`](skills/hunt/SKILL.md) | 一段范围的长期缺陷审查 | 给范围 | 是 | 一整轮没有新的真实缺陷，且本段改动审查无 BLOCKER / HIGH |
| [`/review`](skills/review/SKILL.md) | 审一次改动 | 选要修的结论 | 否 | 出完报告；选中的结论转 `/fix` |
| [`/audit`](skills/audit/SKILL.md) | 盘点一段功能齐不齐 | 选要做的条目 | 否 | 出完清单；选中的条目转 `/dev` |
| [`/pair`](skills/pair/SKILL.md) | 和你结对开发 | 每一步 | 否 | 你说停；说「剩下交给你」转 `/dev` |

`/review` 和 `/audit` 只读；要改动时转成会续跑的 `/fix` 或 `/dev`，那时才建运行状态。

Agents 只写「谁做、能改哪里、怎么验证」；循环和停法只写在对应 skill；Rules 不复制二者。勾选前要哪份证据，见「不能跳过」。

## 与实际编排的关系（双入口，同一套闸门）

本目录有两种入口，执行的是同一套规则（Rules、Agents 角色、「不能跳过」证据表、reviewer 收尾）：

| 入口 | 怎么跑 | 跨会话状态 |
|------|--------|-----------|
| IDE 入口 | 人在 Cursor 里打 `/dev`、`/fix`、`/hunt`，AskQuestion 确认，stop hook 续跑 | `.cursor/runs/` 运行状态 |
| 编排入口 | 外部编排者（如 kevin）开 GitHub Issue 并做 UoW 分解，按 [`brief-template.md`](brief-template.md) 写 brief 派 headless `cursor-agent` 在**独立 worktree** 执行；执行者（Cursor）只写代码、**不启动程序**（含测试与 dev server），测试、探针与 diff 核验由编排者执行 | GitHub Issue ＋ brief ＋ worktree 分支 |

两边共同的硬规矩：

- **新規先开 Issue**：新功能 / 新规划先在 GitHub Issues 开单并做 UoW 步骤分解再实施；来自 Issue 的任务以 Issue 的 UoW 为验收项来源，不另起一套。
- **一任务一 worktree**：编排入口不在主工作区直接改；分支只用 `dev`（最新）与 `main`（发布）为长期分支。
- **不自行开 PR**：改动先留分支 / 本地，等用户拍板；获批后只开 base 为 `dev` 的 PR，非 dev 的中间 PR 不开。IDE 入口不 git commit；编排入口的 commit / push 范围以 brief 写明的为准。
- **本地全绿 ≠ 完成**：本地验收是前置；最终把关是 PR 的 CI（backend PG 契约、browser-e2e、admin-web / agent-portal CI）。未开 PR 时，交代里必须写明「CI 未验」。
- **发现问题当场处置**：见下面「发现问题怎么办」。「顺带发现」不是终点，必须转成 GitHub Issue 才算登记完毕。
- **提交规范**：每笔提交标题用 Conventional Commits（`feat/fix/docs/chore/ci/test/refactor/style(scope): …`），禁止空洞訊息（`no message`、`wip`、`update` 之类，guard 会拦）；单笔提交单一关切，混杂改动拆笔；PR 正文按模板逐栏填。
- **分支卫生**：仓库已开启合并后自动删除 head branch；未合并的长期滞留分支由编排者定期盘点，先备份 head SHA 再清理。

## 发现问题怎么办

执行中发现任何问题，先按这张表处置，再继续手头工作。**向用户汇报**分两档：BLOCKER 级（数据、安全、契约破坏）或会影响用户决策的问题，不等收尾、立即汇报；其余在每轮／结束交代里汇报，并附 Issue 号。

| 情形 | 处置 |
|------|------|
| 本次范围内、阻塞当前验收项 | 按对应 skill 的规则追加验收项（审查结论仍是唯一来源时从其规定） |
| 本次范围内、不阻塞 | 记进运行状态，交代时汇报；用户点头才扩范围 |
| 范围外的新问题（bug／缺陷） | **当场开 GitHub Issue**（bug 模板），运行状态「已转 Issue」记号码；不在本次顺手修 |
| 根因未明 | 开 Issue 用 investigation 模板（`investigation` 标签）；查清后在原单拆出 fix／feature 子单并回链 |
| 暂时无法解决（等外部条件／等用户决定） | 开或更新 Issue，打 `blocked` 或 `parked` 标签，正文写明：卡点、等待对象、回访条件（日期或事件）；不许只留在本地运行状态里 |
| 尚未成案的风险 | 开 Issue 用 risk 模板（`risk` 标签），写触发条件、影响、回访条件；成案后转 bug／feature 并回链原单 |

开单纪律：每个 Issue 至少三类标签各一——类型（`bug`／`enhancement`／`security`／`technical-debt`／`risk`／`investigation` 等）、优先级（`P0`–`P3`）、模块（`backend`／`frontend`，流程类用 `documentation`）。主任务在 Issue 内做 UoW 勾选分解；可独立交付的次要任务另开子 Issue 并在主单回链，不混进同一 UoW 项。

## Hooks（`.cursor/hooks.json`）

| 脚本 | 事件 | 作用 |
|------|------|------|
| [`guard.js`](hooks/guard.js) | `beforeShellExecution` | 拒绝 git push / rebase / merge、reset --hard、checkout -- 等丢弃改动的命令；git commit 与递归删除要确认；空洞提交訊息（`no message`、`wip`、`update` 等）直接拒绝 |
| [`guard.js`](hooks/guard.js) | `preToolUse` | 拒绝写本地 `.env`、会话记录、`secrets/`、已有的 Flyway 迁移，拒绝删除测试；改 hooks 本身要确认 |
| [`continue.js`](hooks/continue.js) | `stop` | 本会话有 `status: running` 的运行状态时，自动发起下一轮 |

- 脚本异常按拒绝处理（`failClosed`），续跑最多 30 次（`loop_limit`）。
- 「要确认」在自动运行模式下可能直接放行；必须挡住的规则写成拒绝。
- 定位是**防误触＋破坏性操作硬拒**，不是安全边界：拒绝只覆盖 Agent 的文件工具和 shell 命令文本；把命令写进脚本文件再执行、shell 里间接改文件不在拦截范围。git 匹配已容忍 `-C` / `-c` / `--git-dir` 等全局参数写法。
- guard 的判定逻辑有 [`guard.test.js`](hooks/guard.test.js) 锁住（`node .cursor/hooks/guard.test.js`，改规则先改测试）。

## 运行状态（`.cursor/runs/`，不进 git）

`/dev`、`/fix`、`/hunt`（含 `/review` 转出的 `fix-review-*`、`/audit` 转出的 `/dev`）各建一份 `<mode>-<slug>.md`，跨轮次和上下文压缩以它为准：

```markdown
# run: fix-site-chat-timeout
mode: fix          # dev / fix / hunt，对应 .cursor/skills/<mode>/
status: running    # confirm / running / blocked / done
round: 3 / 10
stop: 复现测试 HttpAgentClientTest.chatReadTimeoutDoesNotRetry 转绿，agent 模块测试全过

## 验收项
- [x] ...
- [ ] ...

## 需要用户决定

## 已转 Issue（顺带发现）
<!-- 范围外问题一律开 GitHub Issue 后记在这里：- #<号> <一句话>；只写文字不算登记 -->

## 每轮记录
- r3: 改了 HttpAgentClient.java；测试 1 passed；继续
```

- 续跑只认 `status: running`。stop hook 第一次接手时写入 `conversation:`，之后只续跑本会话的文件；新建后 30 分钟内没被认领的不再接手。
- 一轮结束时运行状态没有更新，视为没有进展，不再续跑。`round` 到上限也停。
- 想手动停：把 `status` 改成 `done` 或 `blocked`，或在对话里说停；Agent 收到「停」先把 `status` 改成 `blocked` 再回复。

## 不能跳过

`/dev`、`/fix`、`/hunt` 勾选前对上这张表。会改变行为的项，红和绿留在同一轮：先把失败输出写入运行状态，再改实现，再贴通过输出，然后才勾。验证没过不勾，也不开始下一项。代码没变，不把同一条命令再跑一遍。

| 借口 | 勾选前要有的证据 |
|------|------------------|
| 功能写完再补测试 | 行为项的失败输出已在运行状态里 |
| 测试本来就是绿的 | 改实现前它曾经失败 |
| 测试写错了，放宽就能过 | 先说明测试为何错，并改测试；断言还在 |
| 原因我知道 | 失败路径已写明请求、代码、坏结果 |
| 显示层去掉就好 | 改动在产生该结果的那一层 |
| 报错里有修复命令 | 命令已写进运行状态，没有直接执行 |
| 旁边一起整理了 | 整理项已开 Issue 并记进「已转 Issue」，不在本次 diff |
| 再跑一遍更保险 | 上次运行之后代码有变化 |
| 编译过了 | 贴了本项测试或浏览器路径的结果 |

## AI 域快速指针

- **业务真相**：Java admin + agent-app + PostgreSQL  
- **执行层**：`services/agent-services`（internal only）  
- **契约**：`services/website-services/agent` 的 `HttpAgentClient`  
- **前端**：admin-web `aiApi` FORCE REAL（见 [`docs/rules/MOCK_POLICY.md`](../docs/rules/MOCK_POLICY.md)）；用户端 `apps/agent-portal` 的 `agentAiApi` 经 gateway 调 agent-app（见 [`agent-portal.mdc`](rules/agent-portal.mdc)）

## 修改本目录时

- Rule 单文件宜 **简洁可执行**（约 50 行内）；一文件一关切。
- Agent 描述（frontmatter `description`）决定 Cursor 何时自动选用；写清 **Scope / Forbidden / Trigger**。
- 与 `AGENTS.md`、ADR、CURRENT_STATE 冲突时，以代码与 ADR 为准，并更新漂移文档。
