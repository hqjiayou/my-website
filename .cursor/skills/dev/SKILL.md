---
name: dev
description: 全自动开发一个需求：查事实、写验收项、用户确认后按模块分派实现、逐项验证，最后审本次 diff，验收项全过就停。Use when the user types /dev or hands over a feature to be implemented end to end.
disable-model-invocation: true
---

# /dev 全自动开发

输入：一句需求，可带模块。停止条件：验收项全部通过，且 `integration-reviewer` 对本次 diff 没有 BLOCKER / HIGH。

运行状态格式、闸门、续跑、不能跳过见 `.cursor/README.md`。

## 开始（一次）

1. 读调用链和相邻代码；范围大时先派 `codebase-explorer`。
2. 按 `architecture-planner` 的输出格式写方案，并读它的验收项写法。验收项每条标执行者：`admin-backend` / `admin-frontend` / `agent-services` / 主会话。跨模块按 Flyway → Java → `agent` 契约 DTO → Python → admin-web 排序；同一层里先做最可能被证伪的那一项。五类高风险项写明主张，以及哪条测试或哪条请求会证伪。
3. 建 `.cursor/runs/dev-<slug>.md`，`status: confirm`，写入验收项和 `round: 0 / <验收项数 × 3，最多 30>`。
4. 用 AskQuestion 请用户确认验收项。**确认前不改代码。** 用户改了就更新验收项再问。
5. 确认后改成 `status: running`，进入第一轮。

## 每一轮

1. 先读运行状态，只做第一个没勾的验收项。
2. 交给该项的执行者：派对应 subagent，或主会话按该 agent 文件的 Scope / Forbidden / Verification 做。改动不出方案的允许范围。
3. 会改变行为的验收项，红和绿留在这一轮：先写测试并跑到失败，失败摘要写入运行状态，再改实现，再贴通过输出，然后才勾。文档、文案、无行为的样式和配置不套这条。勾选前对上 `.cursor/README.md` 的「不能跳过」。验证没过不勾，也不开始下一项。代码没变，不把同一条命令再跑一遍。
4. 验证：

   | 模块 | 命令 |
   |------|------|
   | Java | `mvn -pl <模块> -am test "-Dtest=<类>" "-Dsurefire.failIfNoSpecifiedTests=false"`（在 `services/website-services`） |
   | Python | `uv run python -m pytest -q`（在 `services/agent-services`；无 uv 时用 Docker `ghcr.io/astral-sh/uv:python3.13-bookworm-slim`） |
   | admin-web | `pnpm lint` 与 `pnpm build`；界面行为变了，在浏览器走通这次改到的主路径，以及空态或错误态里被改到的那一个。截图不算通过 |

5. 通过才勾上。行为变了，同步任务范围内的 CURRENT_STATE / 契约 / 环境变量。
6. 更新 `round` 和「每轮记录」，结束本轮回复。stop hook 会接下一轮。

## 收尾

验收项全勾后派 `integration-reviewer`。交给它的只有本次 diff、验收项、相关契约路径。它若标出要翻转的条件，主会话只改那一处、跑一次覆盖测试、再还原，然后把结果并进结论。BLOCKER / HIGH 追加为新验收项继续；没有就 `status: done`，按下面交代。

审查结论是**唯一**允许追加验收项的来源。顺带看到的缺陷、未做功能写进「顺带发现」，不在本次做。

## 卡住

边界不清、要改跨模块契约、要新增迁移、同一失败两次没解决：`status: blocked`，写进「需要用户决定」，用 AskQuestion 问。其余不相关的验收项可以先做。

## 交代

- 每轮：本项验收句、改了哪些文件、验证结果、还剩几项
- 结束：全部验收项与验证、审查结论、顺带发现、未提交改动清单（不 git commit）
