# Brief 模板（编排入口定式）

外部编排者（如 kevin）派 headless `cursor-agent` 前，按本模板逐栏写 brief；缺栏不开工。执行者在独立 worktree 内只写代码、不启动程序；测试、探针与 diff 核验由编排者执行（见 README「与实际编排的关系」）。

```markdown
# Brief: <一句话任务名>

## 来源
- Issue：#<号>（本 brief 对应其中 UoW-<n>；验收标准以 Issue 为准，不另起一套）
- 基线分支：dev @ <sha 或「开工时 origin/dev 顶端」>
- 工作分支：<feat|fix|docs|chore|test|refactor>/<slug>（独立 worktree：~/workspace/my-website-<slug>）

## 目标与验收项
- [ ] <逐条抄 Issue 的 UoW 项，每条可独立验证>
- [ ] diff 只落允许范围；行为变了同步 CURRENT_STATE / 契约 / 环境变量

## 允许改动范围
- <路径或模块清单，逐个写明>

## 禁止事项
- 不动允许范围外的文件；不顺手重构、不顺手修范围外问题
- 不启动程序（含测试与 dev server）；不 git commit / push（除非本栏另行写明范围）
- 不新增 Flyway 迁移、不改跨模块契约（这两项需要时先停下回报）

## 执行者须知
- 先读 AGENTS.md 与命中的 .cursor/rules/，再读 Issue 全文
- 会改变行为的项：先写测试跑到失败、留输出，再改实现（红绿同轮，见 README「不能跳过」）

## 汇报与问题处置
- 收尾交代：改了哪些文件、每条验收项状态、未做项与原因
- 发现范围外问题：不开工修，按 README「发现问题怎么办」开 Issue 并在交代里回链号码
- BLOCKER 级问题（数据／安全／契约破坏）或需要用户决定的事项：立即停下回报，不等收尾
- 同一失败两次没解决：停下回报，写明失败路径与已试方法
```
