---
name: fix
description: 全自动修复一个已知问题：先写复现失败的测试，再做最小修复，复现测试转绿且模块测试全过就停。Use when the user types /fix or hands over a known bug, error log, stack trace or GitHub issue to be fixed.
disable-model-invocation: true
---

# /fix 已知问题修复

输入：报错、日志、Issue 或一句缺陷描述。停止条件：复现测试从失败变通过，相关模块测试全过，`integration-reviewer` 对本次 diff 没有 BLOCKER / HIGH。

运行状态格式、闸门、续跑见 `.cursor/README.md`。找未知缺陷用 `/hunt`，不用本流程。

## 开始（一次）

1. 读调用链，写出失败路径：哪个请求、哪段代码、坏结果是什么。写不出就 `status: blocked`，问用户要复现条件，不猜着改。
2. 判定是否**既定策略**（文档或测试锁住的行为，例如对话检索失败后继续回答）。是就停，告诉用户它是既定策略及依据。
3. 建 `.cursor/runs/fix-<slug>.md`，`status: running`，`round: 0 / 10`，验收项固定为：
   - [ ] 复现测试写好并确认失败（贴失败输出）
   - [ ] 最小修复
   - [ ] 复现测试通过，所在模块相关测试全过
   - [ ] 行为变了则同步 CURRENT_STATE / 契约 / 环境变量
   - [ ] `integration-reviewer` 审本次 diff 无 BLOCKER / HIGH

   验收项固定，不需要用户确认。

   来自 `/review` 的 `fix-review-*` 运行：验收项为每条选中的审查结论一项，加「相关测试全过」与「再审无 BLOCKER / HIGH」。能写复现测试的结论照样先写测试。

## 每一轮

1. 先读运行状态，做第一个没勾的项。执行者按改动路径选 `admin-backend` / `admin-frontend` / `agent-services`，或主会话按该 agent 文件做。
2. 修复只改必要范围。不删测试、不放宽断言来让测试通过、不顺手重构。
3. 验证命令同 `/dev`（见 `.cursor/skills/dev/SKILL.md` 的验证表）。
4. 更新 `round` 和「每轮记录」，结束本轮回复。

审查有 BLOCKER / HIGH：每条追加为一个新验收项放在审查项之前，审查项保持不勾；修完这些项再审一次。审查结论是唯一允许追加验收项的来源。

同一失败两次没解决，或修复需要改跨模块契约 / 新迁移：`status: blocked`，用 AskQuestion 问。

## 交代

- 结束：失败路径、复现测试名、修复文件、测试结果、审查结论、未提交改动清单（不 git commit）
