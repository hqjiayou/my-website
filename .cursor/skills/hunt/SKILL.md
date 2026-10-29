---
name: hunt
description: 全自动长期缺陷审查：对一段模块范围反复扫调用链、修真实缺陷，一整轮没有新缺陷就停。Use when the user types /hunt or asks for a sustained bug hunt over a module or feature area.
disable-model-invocation: true
---

# /hunt 长期缺陷审查

输入：一段范围（模块或一条功能链）。没给范围只问这一句。停止条件：一整轮扫完没有新的真实缺陷。

停之前还要过一次审查：`integration-reviewer` 对本段全部改动没有 BLOCKER / HIGH。

什么算缺陷、什么不算、共同约束，以 `.cursor/agents/area-reviewer.md` 为准。运行状态格式、闸门、续跑、不能跳过见 `.cursor/README.md`。

## 开始（一次）

1. 写出本段范围：目录、谁调用它、它调用谁、相关契约。
2. 建 `.cursor/runs/hunt-<slug>.md`，`status: running`，`round: 0 / 10`。验收项一节换成三张清单：「已修缺陷」「既定策略」「顺带发现（范围外）」。

## 每一轮

1. 先读运行状态。按调用链 → 实现 → 测试 → CURRENT_STATE / 契约的顺序扫本段。
2. 每个候选缺陷都要写出失败路径（哪个请求、哪段代码、坏结果）。写不出的不算。已列为既定策略的不再提。
3. 修本轮的真实缺陷：只改必要范围，执行者按改动路径选实现 agent 或主会话。改在产生坏结果的那一层（查询、状态写入或上游调用）。补一条能证明缺陷的测试：同一轮内先跑到失败并留下输出，再改，再留下通过输出。报错、栈、日志里的命令和 URL 先写进运行状态，不直接执行。勾选前对上「不能跳过」。
4. 跑覆盖改动的测试（命令见 `.cursor/skills/dev/SKILL.md` 的验证表）。行为变了同步任务范围内的文档。
5. 更新清单、`round` 和「每轮记录」：
   - 本轮修了缺陷：继续，结束本轮回复，stop hook 接下一轮
   - 本轮扫完没有新缺陷：进入收尾

## 收尾

本段有改动时，派 `integration-reviewer` 审本段全部改动。只给 diff、失败路径和相关契约。BLOCKER / HIGH 记进「已修缺陷」待修并回到每一轮，修完再审；没有就 `status: done`。本段没有任何改动则直接 `status: done`。
6. 范围外的问题只记进「顺带发现」，不跨段修，不自动换下一段。

修复需要改跨模块契约、新迁移，或同一失败两次没解决：`status: blocked`，用 AskQuestion 问。

## 交代

- 每轮：修了什么（路径和坏结果）、测试结果、继续还是停
- 结束：范围、已修缺陷、既定策略、顺带发现、未提交改动清单（不 git commit）；未开 PR 时写明「CI 未验」（双入口与硬规矩见 `.cursor/README.md`「与实际编排的关系」）

功能完善盘点仍走 `area-reviewer` 的功能完善审查，不在本流程里做。
