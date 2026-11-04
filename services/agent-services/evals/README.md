# 离线 Eval（答案质量 / skill 触发）

离线评估的题集、语料与 runner 说明。runner 是离线工具，不进 CI 跑真模型
（额度与密钥纪律），真模型读数固定在本机执行；CI 只守确定性层
（评分器单测、题集结构与管线 smoke，见 `tests/test_answer_eval.py`、
`tests/test_skill_eval_set.py`、`tests/test_eval_runner_wiring.py`）。

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
不用假模型冒充跑分；注入类文件（`--cases`/`--lessons-file`/`--harness-dir`）
坏或缺失时先于跑分打印 `ERROR` 并以退出码 2 退出，不静默降级。

### 参数与缺省口径（#187 UoW-1 接通）

| 参数 | 语义 | 缺省 |
| --- | --- | --- |
| `--cases <path>` | 任意题集 `.jsonl`：任一行带 `skill` 字段按 skill 题集解析，否则按黄金集解析 | `golden_qa.jsonl` |
| `--skills <id,id,...>` | 本轮基础 skill 集，经生产同一函数 `agent.skills.resolve_skills` 解析后注入每轮 `run_chat`（未知 id 按生产缺省 ignore 口径记 missing，不中断）。带 `skill` 字段的题在此基础上再追加该题自己的 skill（与 #182「每题显式带该题 skill」的人工口径一致） | 无 skill |
| `--harness-dir <dir>` | 平台文件目录（`background.md`/`guardrails.md`/`tool_guidance.md`），语义等价生产 `AGENT_HARNESS_DIR`，供平台文件变体对照 | 源码树 `harness/` |
| `--lessons-file <path>` | 经验条目 JSON 数组，形状与对话请求字段 `experienceLessons` 一致：`[{"lessonNo": 6, "lesson": "…"}]`；经组装段注入每轮。空数组是合法的「关」臂；文件缺失/JSON 坏/条目形状不对一律报错退出 | 无条目 |
| `--distractors` | 并入 `distractors.json` 给检索与引用精度施压 | 不并入 |
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
