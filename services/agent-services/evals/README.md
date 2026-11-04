# 离线 Eval（答案质量 / skill 触发）

离线评估的题集、语料与 runner 说明。runner 是离线工具，不进 CI 跑真模型
（额度与密钥纪律），真模型读数固定在本机执行；CI 只守确定性层
（评分器单测、题集结构与管线 smoke，见 `tests/test_answer_eval.py`、
`tests/test_skill_eval_set.py`、`tests/test_eval_runner_wiring.py`、
`tests/test_eval_baseline.py`）。

## 资产

| 文件 | 内容 |
| --- | --- |
| `golden_qa.jsonl` | 黄金集 18 题：`id/question/expected_sources/expected_keywords/category`，集合级题另带 `expected_behavior/expected_answer` |
| `skill_qa.jsonl` | skill 题集 17 题：`id/skill/kind/question/expected_behavior/expected_answer`，部分带 `expected_keywords` 与 `golden_ref`（与黄金集的同锚引用） |
| `corpus.json` | 评估语料（进程内索引，非业务真相源） |
| `distractors.json` | 对抗性近失文档层，仅 `--distractors` 时并入索引 |
| `reports/` | 跑分报告归档（UoW-2 起含基线约定） |

## Runner

```bash
cd services/agent-services
uv run python -m agent_services.evals.run_answer_eval [--out <dir|file>]
```

未配置模型（无 `AI_MODEL`/凭证）时打印 `BLOCKED` 并以退出码 2 退出，
不用假模型冒充跑分；注入类文件（`--cases`/`--lessons-file`/`--harness-dir`/
`--baseline`）坏或缺失时先于跑分打印 `ERROR` 并以退出码 2 退出，不静默降级。

退出码（#187 UoW-2 起）：`0` 正常（含无基线、或有基线但无 pass→fail
翻转）；`2` 文件错误/BLOCKED（含整轮全 unscored，见下）；`3` 给了
`--baseline` 且出现 pass→fail 翻转（stderr 以 `REGRESSION:` 明示翻转题）。

### 参数与缺省口径（#187 UoW-1 接通）

| 参数 | 语义 | 缺省 |
| --- | --- | --- |
| `--cases <path>` | 任意题集 `.jsonl`：任一行带 `skill` 字段按 skill 题集解析，否则按黄金集解析 | `golden_qa.jsonl` |
| `--skills <id,id,...>` | 本轮基础 skill 集，经生产同一函数 `agent.skills.resolve_skills` 解析后注入每轮 `run_chat`（未知 id 按生产缺省 ignore 口径记 missing，不中断）。带 `skill` 字段的题在此基础上再追加该题自己的 skill（与 #182「每题显式带该题 skill」的人工口径一致） | 无 skill |
| `--harness-dir <dir>` | 平台文件目录（`background.md`/`guardrails.md`/`tool_guidance.md`），语义等价生产 `AGENT_HARNESS_DIR`，供平台文件变体对照 | 源码树 `harness/` |
| `--lessons-file <path>` | 经验条目 JSON 数组，形状与对话请求字段 `experienceLessons` 一致：`[{"lessonNo": 6, "lesson": "…"}]`；经组装段注入每轮。空数组是合法的「关」臂；文件缺失/JSON 坏/条目形状不对一律报错退出 | 无条目 |
| `--distractors` | 并入 `distractors.json` 给检索与引用精度施压 | 不并入 |
| `--baseline <report.json>` | 与既有报告逐题对照：本轮报告附 `baselineDiff`（指标 delta、pass 翻转、单边题），有 pass→fail 翻转时退出码 3；基线文件坏/缺失先于跑分报错退出 | 不对照 |
| `--out` | 报告输出目录或 `.json`/`.md` 路径 | `evals/reports/` 下时间戳文件 |

四参数可叠加（如 golden＋默认 skills、skill_qa＋对应 skills、golden＋lessons
关/开两轮）。实际启用的注入会如实标注在报告里：JSON 顶层 `skills` /
`harnessDir` / `lessonsCount` 三键与 Markdown 报告头同名行，只在启用时出现；
缺省跑的报告形状与接通前一致。

skills 解析目录与单轮 skill 指令字数上限取进程 Settings
（`AGENT_SKILLS_DIR` / `AGENT_SKILLS_MAX_INSTRUCTION_CHARS`），与生产同源。

## 判分口径

四指标与 pass 规则在 `src/agent_services/evals/scoring.py`：可答题
`retrieval_hit` 且 `keyword_coverage ≥ 0.5`；不可答题 `refusal_correct`。

skill 题集的评分锚点（category / expected_sources / expected_keywords）按
「同锚」派生：题面自带字段以题面为准（显式空即空）→ 否则取 `golden_ref`
指向的黄金用例同名字段 → 再否则来源与关键词皆空、类别记 `skill`。
pass 按可用指标判：缺失（None）的指标不计，两样都缺（纯行为题，如
表格题）时四指标全为 None、`passed` 恒 False——这类题的完成与否只能
人工核对 `expected_behavior`，报告里不要把它们的 `passed=False` 读成
模型答错。

逐题行在题面带 `skill` 字段时另有两列（JSON 与 Markdown 同步）：

- `triggered`：该题 skill 出现在本轮 run 的 `skillsApplied`（即本轮
  `resolve_skills` 的 applied 集，与生产响应 `skillsApplied` 同源）；
- `completed`：`triggered` 且该题四指标 `passed`。

行内同时给出 `skillsApplied` 全集，未知 skill 记 `skillsMissing`。
按 kind 解读：positive/quality 看 triggered 与 completed；negative 题
按口径同样显式带上题面 skill 发送，关键看 `passed`（挂着 skill 仍按
锚点答对/正确拒答即未被误导），`triggered` 对这类题恒真、不作信号。

## 对照与评分完整性（#187 UoW-2）

### 配置指纹

每份报告 JSON 顶层带 `fingerprint`（Markdown 报告头同步摘要），随报告
归档，字段为：`model`、`passRule`、`casesFile`（`path`＋`count`，函数
注入时 path 为 null 表示未标注）、`corpus`（`documents`＋`sha256`——
按 source_id 排序拼接 source_id/title/content 的内容哈希，同一语料
必得同一哈希，`--distractors` 并入与否也会反映在哈希上）、`harnessDir`
（实际生效值：未指定时为组装缺省目录）、`skills`（本轮基础集）、
`lessons`（`file`＋`count`）。分数只与**同指纹**的报告直接可比。

### --baseline 逐题 diff

报告附 `baselineDiff`：每题给出四指标的 baseline/current/delta、
pass 状态与 status（`pass_to_fail` / `fail_to_pass` / `unchanged` /
`added` / `missing` / `unscored` / `baseline_unscored`），另有总体均值
对比与翻转清单。规则：

- 翻转只认两边都有真实 pass 状态的题。本轮 unscored 的题、基线
  unscored 的题单列，**不算翻转**；基线有而本轮缺的题、本轮新增题
  单列说明，不混入翻转判定。
- 指纹关键项（`model` / `casesFile` / `corpus`）不一致，或基线是
  UoW-2 之前的旧报告（无 `fingerprint` 块、无从核对题集与语料）时，
  diff 照常产出，但 `fingerprintWarnings` 非空、CLI 同时向 stderr 打
  `WARNING:`——此时分数**不可直接比**，是否采信由人结合警告判断。
  `harnessDir` / `skills` / `lessons` 是对照实验要变的臂，只记录、
  不警告。
- 给了基线且出现 pass→fail 翻转 → 退出码 3（见上「退出码」）。

### unscored 口径

单题运行抛异常或超过单题墙钟上限（runner 常量
`CASE_TIMEOUT_SECONDS = 300` 秒）时，该题记 **unscored**：报告行带
`unscored: true` 与 `unscoredReason`（异常类型＋信息，或超时说明），
四指标与 `passed` 全为 None；汇总（overall 与 byCategory 的 count 与
均值）只统计已计分题，unscored 另在 `summary.unscoredCount` /
`summary.unscoredCases` 单列清单。注意与「模型如实 BLOCKED」的计分题
区分：`run_chat` 返回 ChatBlocked 是产生了可判结果（照常计分），
unscored 只用于异常/超时这类**没有结果**的题。整轮全部 unscored 时
按 BLOCKED 等价处理：报告照常落盘，CLI 打印 `BLOCKED` 并退出 2；
部分 unscored 时整轮继续，退出码语义不变。

## 报告归档与基线维护

- **产物落点与命名**：runner 产物落 `evals/reports/`，命名
  `answer-eval-<UTC 时间戳>.json` / `.md` 一对（`--out` 指定目录或
  文件名时从其口径）。`evals/reports/*` 默认被 gitignore——归档是
  本机留存；人工总结报告（如 `2026-11-03-*.md`）经人工确认后以
  `git add -f` 入库。
- **基线文件**：正式基线固定为 `evals/reports/baseline.json`（已在
  `.gitignore` 里单独放行、可入库），内容就是一份经确认的完整报告
  JSON（自带指纹与 generatedAt，来源可溯）。runner 只读它
  （`--baseline`），**从不自动写入或覆盖**。
- **何时产生与更新**：第一份正式基线由 UoW-3 本机读数产生——跑出
  golden_qa 全集报告后，人工确认数字无误（题集、指纹、unscored
  清单都看过），把该报告 JSON 复制为 `baseline.json` 入库。之后
  更新基线的时机同理：**只在人工确认新一轮读数应成为新基准时**
  更新（如模型更换、题集扩容后重读基线），由执行人提交变更并在
  提交信息里注明依据的运行时间戳；任何人不得用未确认的跑分覆盖。
- 在基线产生之前，`--baseline` 指向缺失文件会按文件错误退出 2，
  这是预期行为，不是故障。
