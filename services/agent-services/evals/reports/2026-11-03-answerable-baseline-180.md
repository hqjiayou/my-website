# #180 基线报告：可答题覆盖补测（UoW-1）＋集合级用例入集（UoW-2）

- 日期：2026-11-03
- 基线代码：origin/dev `71a1668`（现行 harness，未改动）
- 分支：`feat/180-eval-baseline`
- 关联：Issue #180；评估报告 `agent-capability-evaluation-2026-11-03.md` 第六节

## 结论先行

| 层 | 状态 | 数字 |
| --- | --- | --- |
| 真模型答案层（run_answer_eval 全链） | **BLOCKED，未跑**（见下） | 无数字，不编造 |
| 确定性检索层（原 12 题，pristine 语料） | 已测 | 可答题 retrieval 命中 **8/8**；不可答题零命中 **4/4** |
| 确定性检索层（新增集合级家族后，全 18 题） | 已测 | 可答题 retrieval 命中 **13/13**；不可答题零命中 **5/5** |

## UoW-1：真模型层为何 BLOCKED

在沙箱内按既有设施尝试跑真模型 eval：

```text
$ python -m agent_services.evals.run_answer_eval
BLOCKED: answer quality eval did not run. AI model is not configured (missing AI_MODEL). Refusing to score with a fake model.
exit code: 2
```

原因：沙箱进程环境没有 `AI_MODEL` 与任何 provider 密钥（各 worktree 只有 `.env.example`）。
连接器侧的 Gemini 密钥经只读探针确认存活（`gemini_probe.py models` 返回 HTTP 200、50 个模型，
含 `models/gemini-3-flash-preview`），但该密钥由连接保管、仅供连接调用，
按连接文档与凭据纪律不能写入独立进程的环境或文件，因此 runner 在本沙箱内无法取得模型。
这与 runner 的设计一致：无模型时诚实 BLOCKED，不用假模型冒充跑分。

**待在配有密钥的环境（kou 本机，与免费层额度同一桶）执行的命令**（在 `services/agent-services` 目录）：

```bash
AI_MODEL=google:gemini-3-flash-preview GEMINI_API_KEY=<本机密钥> \
  uv run python -m agent_services.evals.run_answer_eval --out evals/reports
```

预计调用量：全集 18 题（可答 13＋不可答 5），每题至少 2 次模型请求（工具调用轮＋作答轮），
集合级题可能多次检索，预计约 **40–60 次请求**；额度不足时先只跑原 12 题建立答案层基线，
集合级 6 题次日补跑，并在本单回填数字。

## UoW-1 附带：确定性检索层基线（原 12 题）

方法：`build_eval_index`（DeterministicEmbedder＋内存 store，rerank 关）种入 pristine
`corpus.json`，对每题执行一次 `retrieve_chunks`（top_k=4），核对期望来源是否全命中
（不可答题核对零命中）。这量的是「检索能否把该用的文档送到模型面前」，
**不等于**答案层通过率（引用正确性、关键词覆盖、错误拒答须真模型跑分才有）。

| id | category | 期望来源 | 实际命中 | 结果 |
| --- | --- | --- | --- | --- |
| zh-proper-hours | hours | cafe-hours | cafe-hours | hit |
| zh-menu-name | menu | cafe-menu | cafe-menu | hit |
| ja-access | access | cafe-access | cafe-access | hit |
| model-xj900 | model | xj900-specs | xj900-specs, xj900-warranty | hit |
| multi-spec-warranty | multi | xj900-specs, xj900-warranty | xj900-specs, xj900-warranty | hit |
| reservation-hold | reservation | cafe-reservation | cafe-reservation | hit |
| warranty-water | warranty | xj900-warranty | xj900-warranty | hit |
| weekend-open | hours | cafe-hours | cafe-hours | hit |
| unans-wifi | unanswerable | — | （无） | clean |
| unans-orbit | unanswerable | — | （无） | clean |
| unans-snow | unanswerable | — | （无） | clean |
| unans-ferry | unanswerable | — | （无） | clean |

可答题覆盖（检索层）：**8/8 = 100%**。这说明原 12 题的检索供给侧无缺口；
答案层是否同样全过，仍欠真模型一跑（2026-11-01 A/B 只测不可答侧的欠账，
本单已把跑法与题量备齐，数字待有密钥环境回填）。

## UoW-2：集合级问题用例家族（新增 6 题）

语料新增 7 份 eval 专用文档（`corpus.json` 中带 `eval_only: true` 与标题标注）：
工资单 4 份（`payslip-2026-01..04`，2026 年 1–4 月）与发票 3 张（`invoice-2026-01..03`）。
工资单数据刻意设计口径区分度：实发最低为 2 月（159,366 日元，与真实案例同值），
税前支给合计最低为 3 月（195,000 日元），混用口径即答错；四个月实发合计 659,293 日元
为派生数值，不出现在任何单份文档中。

| id | 问题 | 期望行为要点 | 期望答案 |
| --- | --- | --- | --- |
| coll-payslip-lowest-net | 现有工资单里哪一个月的工资最低（原题） | 枚举 4 份→逐份抽实发→比较→注明覆盖与口径；不以反问收尾 | 2026年2月，实发 159,366 |
| coll-payslip-highest-net | 现有工资单里哪一个月的工资最高 | 同上 | 2026年4月，实发 175,400 |
| coll-payslip-total-net | 现有工资单的实发工资合计是多少 | 枚举 4 份→逐份抽实发→求和；只汇总部分月份即错 | 合计 659,293 |
| coll-payslip-lowest-gross | 按税前支给合计算，哪一个月的工资最低 | 逐份抽税前支给合计（非实发）→比较 | 2026年3月，税前 195,000 |
| coll-invoice-highest | 现有发票里金额最高的是哪一张 | 换文档类：枚举 3 张发票→逐张抽金额→比较 | 2026年3月，15,200 |
| coll-rent-unanswerable | 现有租赁合同里哪一份的月租金最高 | 文档类不存在：明确声明没有这类文档，不编造、不拿其他文档类冒充 | 拒答（unanswerable） |

标注方式（向后兼容的最小扩展）：`golden_qa.jsonl` 新增可选字段
`expected_behavior`（期望行为）与 `expected_answer`（期望答案，承载派生数值）；
runner 的 `GoldenCase`/`load_cases` 解析此二字段（缺省空串、出现时须非空），
`validate_golden` 强制 `collection` 类用例必须写 `expected_behavior`，
报告行在用例带标注时输出 `expectedBehavior`/`expectedAnswer`。评分规则未改，
旧 12 题的加载与报告形状不变。

检索层核对（新全集 18 题，同上方法）：可答题 **13/13** 命中
（工资单四题单次检索 top-4 恰好覆盖全部 4 份工资单；发票题覆盖全部 3 张），
不可答题 **5/5** 零命中（含 coll-rent-unanswerable）。
注意：eval 检索是确定性嵌入＋内存 store；生产是真嵌入，且真模型 eval 量的是
agent 多次检索的并集，结果可能不同——集合级题在真模型下的完成率正是后续
skills 单（#182）要对照的基线，预期在枚举工具（#181）落地前多数不达标。

## 验证

- `pytest tests/test_answer_eval.py -q`：17 passed（含新增 3 个测试：
  集合级家族锚点、可选字段解析、collection 缺 behavior 被拒；
  黄金集一致性测试计数更新为 corpus 13 / cases 18 / unanswerable 5 / collection 5，
  合计题关键词改由 expected_answer 核对）。
- `pytest -q`（全量）：410 passed，1 failed——
  `test_retrieval.py::test_embedding_client_closes_owned_client`（httpx InvalidURL ':1]'），
  在 pristine dev `71a1668` 上同命令复现，与本单改动无关，属沙箱环境既有失败。
- `ruff check src tests`：All checks passed。
