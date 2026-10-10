---
name: audit
description: 盘点一个模块或功能链的完善程度：按能力标成已接上、占位、未做、既定不做，只出清单不改代码；用户点名的条目交给 /dev。Use when the user types /audit or asks whether an area's features are complete, wired up, or still placeholders.
disable-model-invocation: true
---

# /audit 功能完善盘点

输入：一段范围（模块或功能链）。没给范围只问这一句。一次做完本段，不续跑，不改代码。

## 步骤

1. 按 `.cursor/agents/area-reviewer.md` 的「功能完善审查」执行：先写本段范围，再按能力逐条标记，每条写能力、标记、入口路径、依据（测试或调用处）。
2. 范围大时先派 `codebase-explorer` 收集调用链与 mock / real 现状，分类结论由本流程给出。
3. 输出：本段范围与看过的目录 → 四类清单 → 漂移的文档 → UNKNOWN。
4. 「未做」「占位」有条目时，用 AskQuestion（多选）问用户要做哪几条。

## 用户点名了条目

把选中条目作为需求，按 `.cursor/skills/dev/SKILL.md` 开一次 `/dev`。验收项仍要用户确认后才实现。

用户一条都不选，就停在清单。

## 不做

- 不改代码，不写实施步骤，不把规划写成已完成
- 不顺手修发现的缺陷；记进清单末尾，建议用 `/fix` 或 `/hunt`
