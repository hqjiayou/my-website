# DOCUMENT_LIFECYCLE

```yaml
artifact_type: SPEC
status: CURRENT
owner: docs-governance
last_verified: 2026-08-09
```

## 允许的文档状态

| status | 含义 | 可否指导实现 |
|--------|------|--------------|
| **CURRENT** | 当前可作为开发依据 | 是 |
| **DRAFT** | 讨论中 | 否 |
| **DEFERRED** | 设计有效但暂不实施 | 否（可指导「不要做」） |
| **SUPERSEDED** | 曾有效，已被新文档替代 | 否 |
| **ARCHIVED** / **HISTORICAL** | 纯历史记录 | 否 |

## artifact_type

`SPEC` · `REFERENCE` · `CONTRACT` · `RUNBOOK` · `TASK_BRIEF` · `INSTRUCTION` · `ANALYSIS` · `DECISION` · `IMPLEMENTATION_PLAN` · `EXECUTION_REPORT` · `AUDIT` · `POSTMORTEM` · `HISTORICAL`

## CURRENT 建议 metadata

```yaml
artifact_type: SPEC
status: CURRENT
owner: <domain>
last_verified: YYYY-MM-DD
source_of_truth: <path or "code">
```

## 功能变更时的顺序

1. 代码变更  
2. 测试  
3. 更新 `CURRENT_STATE` / contracts  
4. 旧文档 SUPERSEDE / ARCHIVE（**不改写历史结论**）  
5. 未解决问题 → GitHub Issue  

## Analysis 拆分规则

| 分析结论类型 | 归宿 |
|--------------|------|
| 已确认事实 | CURRENT_STATE / SPEC / CONTRACT |
| 长期技术决策 | ADR（`docs/decisions/`） |
| 尚未解决 | GitHub Issue |
| 分析过程本身 | analysis/archive |

Analysis **不得**充当 TODO tracker、当前架构规范或 Issue tracker。

## Instruction 规则

- 一次性 Agent 指令 → `implementation-history/` 或 `workflows/instructions/archive/`
- 可复用方法 → `workflows/instructions/templates/`（去掉过时文件名/完成度/Issue）
- Instruction **不得拥有**产品事实权威（枚举、API、完成度）
