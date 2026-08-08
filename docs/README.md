# docs — 文档地图（Source of Truth Map）

```yaml
artifact_type: REFERENCE
status: CURRENT
owner: docs-governance
last_verified: 2026-08-09
```

本目录是仓库的**人类可读知识库**。代码与自动化测试优先于任何文档。未完成工程工作以 **GitHub Issues** 为准，不以本文档内嵌 TODO 为准。

---

## 我想知道…

| 问题 | 去哪里 |
|------|--------|
| **系统现在是什么** | [`overview/CURRENT_STATE.md`](./overview/CURRENT_STATE.md) |
| 前端（admin）现在是什么 | [`admin-web/CURRENT_STATE.md`](./admin-web/CURRENT_STATE.md) |
| 前端（site）现在是什么 | [`site-web/CURRENT_STATE.md`](./site-web/CURRENT_STATE.md) |
| 后端现在是什么 | [`website-services/CURRENT_STATE.md`](./website-services/CURRENT_STATE.md) |
| 接口 / 枚举 / 错误契约 | [`contracts/`](./contracts/) |
| 为什么这样设计 | [`decisions/`](./decisions/) |
| 怎么执行标准任务 | [`workflows/`](./workflows/) |
| 某次历史开发发生过什么 | [`implementation-history/`](./implementation-history/) |
| 过去分析 | [`analysis/archive/`](./analysis/archive/) |
| 验收结果 | [`audit/`](./audit/)（`current/` = 进行中；`archive/` = 历史） |
| **还没解决什么** | **GitHub Issues**（权威 Tracker；[`overview/ROADMAP.md`](./overview/ROADMAP.md) 只留 Issue 号） |
| 本地怎么跑 | [`operations/LOCAL_DEVELOPMENT.md`](./operations/LOCAL_DEVELOPMENT.md) |
| 文档生命周期规则 | [`rules/DOCUMENT_LIFECYCLE.md`](./rules/DOCUMENT_LIFECYCLE.md) |

---

## Agent 默认阅读顺序

1. 仓库根 [`AGENTS.md`](../AGENTS.md) 与 `.cursor/rules/`
2. 本文
3. [`overview/CURRENT_STATE.md`](./overview/CURRENT_STATE.md)
4. 相关域 `CURRENT_STATE` / `contracts` / `decisions`
5. **当前** Task Brief / Instruction（若有）

以下目录**默认不得**作为当前实现依据：

- `implementation-history/`
- `audit/archive/`
- `analysis/archive/`
- `archive/`

---

## 目录一览

```text
docs/
├── overview/           # 全栈 Current SoT
├── admin-web/          # 管理后台
├── site-web/           # 前台站点
├── website-services/   # Java 后端（含中文 SPEC 正文）
├── contracts/          # 前后端契约
├── testing/            # 测试策略
├── operations/         # 运行与环境
├── rules/              # 文档与变更治理
├── decisions/          # ADR
├── analysis/           # 分析（current / archive）
├── workflows/          # 指令模板 / runbook
├── implementation-history/  # 一次大实施的闭环历史
├── audit/              # 验收（current / archive）
└── archive/            # 其他历史快照
```

---

## 事实优先级

见 [`rules/SOURCE_OF_TRUTH.md`](./rules/SOURCE_OF_TRUTH.md)。

简记：`source code` > Flyway/schema > tests > runtime config > frontend API consumers > **CURRENT docs** > historical reports。
