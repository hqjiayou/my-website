# Skill 编写约定

平台 skill 目录：每个 skill 一个子目录，加载器读取 `{id}/SKILL.md`
（见 `agent_services.agent.skills`）。本约定吸收 anthropics
[skill-creator](https://github.com/anthropics/skills/tree/main/skills/skill-creator)
的写作与评测方法，**不**移植其脚本、`eval-viewer` 或其他可执行资源。

## 1. Frontmatter

| 字段 | 要求 |
|------|------|
| `name` | 必填；须与目录名一致；`^[a-z0-9]+(-[a-z0-9]+)*$`，≤64 |
| `description` | 必填；≤1024。须含 **Use** 与 **Do not use** 双向边界，并点名改走哪个 sibling skill |
| `version` | 可选；本站历史字段，非开放标准必填 |
| `license` | 外部移植时必填（如 `Apache-2.0`） |
| `compatibility` / `metadata` / `allowed-tools` | 可选；`allowed-tools` 只进 catalog，**不做工具门控** |

触发信息写在 `description`，不要只写在正文里。偏「宁可多触发」的描述要有明确的 Do not use，避免误抢 sibling。

## 2. 正文结构

建议顺序：

1. **何时用**（可与 description 呼应，正文可略写）
2. **流程**（有序步骤；多步任务先建 todo）
3. **约束**（引用、诚实缺口、预算意识）
4. **示例或反例**（可选）

正文里用反引号引用的工具名必须是本站真实工具（如
`search_site_knowledge`、`list_knowledge_documents`、`query_site_table`、
`add_todo`）。不要写本站没有的宿主能力。

## 3. references 形态

```
skill-id/
├── SKILL.md          # 必填；现行加载器只读此文件 → 正文必须自足可用
├── references/       # 可选；细则、模板、长附录（B 层落地前不会注入）
└── LICENSE / NOTICE  # 外部移植时保留许可证与出处
```

- **正文自足**：必守流程与约束全在 `SKILL.md`。缺了细则就等于没有。
- **细则下沉**：扩展模板、长示例放 `references/`，为渐进披露预留；B 层未落地前不要依赖模型去「读」这些文件。
- 不放 scripts/、assets 可执行物到本目录作为产品路径（见 §4 / C 层禁令）。

## 4. 外部 skill 引入流程

1. **逐字安全审查**：提示注入、与本站无关的外部指令、原宿主工具假设。
2. **剥脚本引用**：去掉 CLI、代码片段执行假设、文件写入/artifacts 通道等本站没有的能力。
3. **对齐工具面改写**：步骤改写成知识检索 / 业务 / 表格 / todo / MCP / 委派等现行工具；场景落在本站产品面。
4. **许可证核查**：
   - **Apache-2.0**：可移植；保留 LICENSE/NOTICE 与出处声明；注明改写。
   - **Proprietary**（如 anthropics 的 docx / pdf / pptx / xlsx）：**禁止**搬运正文与脚本；只能借鉴思路后自研。
5. **上架**：落目录 → 补 eval → 确定性测试通过 → 真模型读数按 #187 行程（不在本约定强制当场跑）。

## 5. 上架门槛

- 每个新 skill 必须在 `evals/skill_qa.jsonl` 配题：至少覆盖
  `positive` / `negative` / `quality`（建议每 skill 3–5 题）。
- 确定性校验：`tests/test_skill_eval_set.py`（catalog 发现、description
  双向边界、resolve、正文工具名、题集结构）。
- 真模型触发率 / 完成率：归 [#187](https://github.com/hqjiayou/my-website/issues/187)
  UoW-3；沙箱无密钥时诚实 BLOCKED，不伪造读数。

## 6. 明确不做（产品路径）

- **C 层脚本执行**（进程内沙箱或任意「跑 skill 自带脚本」）：不做。
  真有文档生成 / 代码执行需求时，走 MCP server 路线，另行立项。
- 把开发侧 Cursor 资产（前端设计、webapp-testing 等）原样挂进站点 agent 货架。
