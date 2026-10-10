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
| [`docs.mdc`](rules/docs.mdc) | `docs/**` | CURRENT_STATE / 契约同步 |

## Agents（`.cursor/agents/`）

| Agent | 何时用 |
|-------|--------|
| [`codebase-explorer`](agents/codebase-explorer.md) | 只读盘点：实现 / mock / 调用链 |
| [`architecture-planner`](agents/architecture-planner.md) | 范围不清、要最小方案与验收项 |
| [`admin-backend`](agents/admin-backend.md) | Java admin、Flyway、RBAC、Admin AI API |
| [`admin-frontend`](agents/admin-frontend.md) | admin-web UI、mock→real、i18n |
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

Agents 只写「谁做、能改哪里、怎么验证」；循环和停法只写在对应 skill；Rules 不复制二者。

## Hooks（`.cursor/hooks.json`）

| 脚本 | 事件 | 作用 |
|------|------|------|
| [`guard.js`](hooks/guard.js) | `beforeShellExecution` | 拒绝 git push / rebase / merge、reset --hard、checkout -- 等丢弃改动的命令；git commit 与递归删除要确认 |
| [`guard.js`](hooks/guard.js) | `preToolUse` | 拒绝写本地 `.env`、会话记录、`secrets/`、已有的 Flyway 迁移，拒绝删除测试；改 hooks 本身要确认 |
| [`continue.js`](hooks/continue.js) | `stop` | 本会话有 `status: running` 的运行状态时，自动发起下一轮 |

- 脚本异常按拒绝处理（`failClosed`），续跑最多 30 次（`loop_limit`）。
- 「要确认」在自动运行模式下可能直接放行；必须挡住的规则写成拒绝。
- 拒绝只覆盖 Agent 的文件工具和 shell 命令文本，shell 里间接改文件不在拦截范围。

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

## 顺带发现

## 每轮记录
- r3: 改了 HttpAgentClient.java；测试 1 passed；继续
```

- 续跑只认 `status: running`。stop hook 第一次接手时写入 `conversation:`，之后只续跑本会话的文件；新建后 30 分钟内没被认领的不再接手。
- 一轮结束时运行状态没有更新，视为没有进展，不再续跑。`round` 到上限也停。
- 想手动停：把 `status` 改成 `done` 或 `blocked`，或在对话里说停；Agent 收到「停」先把 `status` 改成 `blocked` 再回复。

## AI 域快速指针

- **业务真相**：Java admin + site-app + PostgreSQL  
- **执行层**：`services/agent-services`（internal only）  
- **契约**：`services/website-services/agent` 的 `HttpAgentClient`  
- **前端**：`aiApi` FORCE REAL；见 [`docs/rules/MOCK_POLICY.md`](../docs/rules/MOCK_POLICY.md)

## 修改本目录时

- Rule 单文件宜 **简洁可执行**（约 50 行内）；一文件一关切。
- Agent 描述（frontmatter `description`）决定 Cursor 何时自动选用；写清 **Scope / Forbidden / Trigger**。
- 与 `AGENTS.md`、ADR、CURRENT_STATE 冲突时，以代码与 ADR 为准，并更新漂移文档。
