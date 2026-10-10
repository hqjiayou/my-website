---
name: review
description: 审查一次改动：派 integration-reviewer 按四层出结论；用户选中的 BLOCKER / HIGH 交给 /fix 自动修完再审。Use when the user types /review or asks to review current uncommitted changes, a branch, or a set of files before merge.
disable-model-invocation: true
---

# /review 变更审查

输入：默认是当前未提交改动；也可以是分支（对比基线）或一组路径。审查本身只读，出完报告就停。

## 步骤

1. 定改动集：
   - 未提交：`git status --short` 与 `git diff`（含已暂存 `git diff --cached`）
   - 分支：`git diff <基线>...HEAD`
   - 只取本次任务相关的路径；与本次无关的历史改动列出来请用户确认是否纳入，不默认一起审。
2. 派 `integration-reviewer`，把改动集路径、任务目标、相关契约文档一起交给它。按改动范围选层：单文件 / 逻辑 / 功能 / 模块。
3. 原样转述它的结论：问题按严重级别排列，每条带层级、路径、为什么、最小修改建议，以及是否建议合并。没有实质问题就明确写通过。
4. 有 BLOCKER / HIGH 时，用 AskQuestion（多选）问用户要修哪几条。不问 MEDIUM / LOW 以外的扩展改进。

## 用户选了要修的条目

建 `.cursor/runs/fix-review-<slug>.md`（`mode: fix`），按 `.cursor/skills/fix/SKILL.md` 自动跑完：每条选中的结论一个验收项，加上「相关测试全过」与「`integration-reviewer` 再审无 BLOCKER / HIGH」。

用户一条都不选，就停在报告。

## 不做

- 审查阶段不改代码，不 git commit
- 不把本次 diff 以外的历史债务列成必修
- 按域找缺陷用 `/hunt`，看功能齐不齐用 `/audit`
