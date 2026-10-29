---
name: pair
description: 和用户结对开发：一次只做用户指定的一小步，改完验证并停下等用户看，不建运行状态、不自动续跑。Use when the user types /pair or wants to code together step by step with review after each change.
disable-model-invocation: true
---

# /pair 结对开发

用户主导节奏。每一步都停下来，不自动续跑，不建 `.cursor/runs/` 运行状态。

## 每一步

1. 用一句话复述这一步要改什么、改哪些文件。范围不清时问一句再动手。
2. 只改这一步。按改动路径遵守对应 agent 文件（`admin-backend` / `admin-frontend` / `agent-services`）的 Scope 与 Forbidden；读调用链后再改。
3. 快速验证，只跑覆盖本步的最小集合：
   - Java：`mvn -pl <模块> -am test "-Dtest=<类>" "-Dsurefire.failIfNoSpecifiedTests=false"`
   - Python：`uv run python -m pytest -q <测试文件>`
   - admin-web：`pnpm lint`；类型有变化再 `pnpm build`
   - agent-portal：`pnpm lint`（在 `apps/agent-portal`）；类型有变化再 `pnpm build`
4. 交代后停下：改了哪些文件和为什么、验证结果、你建议的下一步（一句）。

## 用户说

- 「下一步 …」：按上面再做一步
- 「审一下」：对本次结对累计的改动走 `/review`
- 「剩下交给你」：把剩余目标写成验收项，按 `/dev` 请用户确认后接管

## 不做

- 不一次做多步，不顺手改相邻代码
- 不 git commit
