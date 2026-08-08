> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/rules/DOCUMENT_LIFECYCLE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# Cursor 项目调查 → Agent / Rules 设计方法论

> 这是一套可复用到任何代码项目的 Cursor Agent / Rules 初始化方法。
>
> 核心原则不是“先设计 Agent”，而是：
>
> **先建立 Project Ground Truth，再根据真实项目设计 Agent / Rules。**

---

# 1. 为什么不能一开始直接设计 Agent

很多人拿到一个项目后会直接创建：

- frontend-agent
- backend-agent
- database-agent
- security-agent
- test-agent
- reviewer-agent

这种做法的问题是：

**Agent 数量和职责是根据想象出来的项目结构设计的，而不是根据真实代码设计的。**

一个项目可能：

- 只有一个前端
- 有多个独立前端
- 后端是单体
- 后端是多服务
- 数据库非常简单
- 数据库迁移非常复杂
- 安全逻辑集中
- 安全逻辑跨多个模块
- 测试体系成熟
- 测试几乎不存在

所以不能看到：

`React + Java`

就直接决定：

“需要 8 个 Agent”。

正确流程应该是：

```text
真实项目
   ↓
Project Ground Truth Investigation
   ↓
项目事实报告
   ↓
Agent / Rules Architecture Design
   ↓
Apply Cursor Configuration
   ↓
Validation
   ↓
正式开发
```

---

# 2. 生成项目调查指令时，主要考虑哪些维度

生成 Project Investigation 指令时，至少从以下 10 个维度考虑。

| 维度 | 需要回答的问题 | 作用 |
|---|---|---|
| 调查边界 | Cursor 本阶段能不能改代码、建文件、装依赖 | 防止调查变成重构 |
| Repository Map | 项目有哪些 app / service / package | 建立真实项目地图 |
| Technology Stack | 实际用了什么技术 | 防止 Rule 技术栈假设错误 |
| Existing Code Pattern | 项目实际上怎么写代码 | 让 Agent 适应项目 |
| Architecture Boundary | 各模块分别负责什么 | 决定 Agent 修改边界 |
| Data / API Chain | Frontend → API → Backend → DB | 判断前后端协作方式 |
| Infrastructure | DB / CI / Docker / Security / Test | 判断是否需要专项能力 |
| Existing Cursor Config | 已有哪些 Agent / Rule | 防止重复建设 |
| Evidence | 所有结论有没有代码证据 | 防止 AI 猜测 |
| Agent / Rule Design | 谁负责什么、什么必须遵守 | 最终形成 Cursor 架构 |

最重要的三个判断维度是：

1. **职责边界**
2. **真实代码 Pattern**
3. **Evidence**

---

# 3. 通用调查和项目专项调查要分开

任何项目的 Investigation Prompt 都建议分成两部分：

```text
Universal Investigation
+
Project-Specific Investigation
```

---

## 3.1 Universal Investigation

任何项目都应该调查：

- Repository structure
- Applications
- Services
- Packages
- Technology Stack
- Architecture
- Dependency Direction
- Frontend Pattern
- Backend Pattern
- API Contract
- Database
- Security
- Testing
- Infrastructure
- CI/CD
- Documentation
- Existing Cursor Agents
- Existing Cursor Rules
- Evidence
- Documentation Drift
- Architecture Drift
- Contract Drift

这部分基本可以跨项目复用。

---

## 3.2 Project-Specific Investigation

根据项目历史增加专项调查。

例如一个后台管理系统可能需要重点调查：

- RBAC
- Admin Web
- Auth
- Gateway
- Common
- Flyway
- Mock → Real API
- UI Preference

电商项目可能重点调查：

- Order State Machine
- Payment
- Webhook
- Inventory
- Redis
- Kafka
- Coupon
- Refund

AI 项目可能重点调查：

- Model Provider
- Prompt
- RAG
- Embedding
- Vector Database
- Agent Runtime
- Evaluation
- Token Cost

关键原则：

**历史信息只能作为“需要重新验证的调查方向”，不能直接作为 PROJECT FACT。**

---

# 4. Project Investigation 必须区分四类信息

调查结果必须明确区分：

## A. PROJECT FACT

当前真实存在的项目架构和行为。

例如：

```text
admin 模块实际依赖 common。
```

---

## B. HARD RULE

项目已经明确要求必须遵守的规则。

例如：

```text
禁止修改已经执行过的 Flyway migration。
```

---

## C. EXISTING PATTERN

当前代码里大量采用，但没有明确写成规则的模式。

例如：

```text
大部分 Controller 使用 XxxResponseDto 返回 API 数据。
```

---

## D. IMPROVEMENT SUGGESTION

可以优化，但当前项目并没有明确要求。

例如：

```text
建议将部分重复的 API enum mapping 统一管理。
```

最重要的限制：

```text
IMPROVEMENT SUGGESTION
不能自动升级成
HARD RULE
```

---

# 5. Evidence 是调查的核心

重要结论必须能回答：

```text
你为什么这么判断？
```

建议 Evidence 至少包含：

- File Path
- Symbol / Configuration
- Why this proves the conclusion
- Confidence

Confidence 建议：

- HIGH
- MEDIUM
- LOW

无法确认：

```text
UNKNOWN
```

不要为了报告完整而猜。

---

# 6. Source Of Truth 优先级

判断项目事实时建议使用以下优先级：

1. 当前实际业务代码
2. 实际运行配置
3. Database Migration
4. Tests
5. CI / Scripts
6. Architecture / Technical Documentation
7. README
8. Comments

如果代码和文档不一致：

标记：

```text
DOCUMENTATION DRIFT
```

不要偷偷选择其中一个。

---

# 7. 为什么要检查 Git Baseline

项目调查开始前，最好先记录：

- Repository root
- Current branch
- HEAD commit
- Working tree status
- Modified files
- Untracked files

原因：

当前工作区可能有：

```text
正在开发但未提交的代码
```

这部分不能自动认定为：

```text
稳定的项目 Pattern
```

建议标记：

```text
WORK IN PROGRESS
```

---

# 8. 为什么要限制扫描范围

完整调查不是把所有文件塞进上下文。

默认应该排除：

- node_modules
- dist
- build
- target
- coverage
- .git
- logs
- cache
- generated output
- binary files
- third-party dependencies

目标是：

**调查源码和真实配置，不研究构建垃圾。**

---

# 9. 从调查报告生成 Agent 的原则

Agent 回答的是：

```text
谁负责做什么？
```

一个 Agent 是否值得独立存在，主要看：

- Scope 是否独立
- Trigger 是否独立
- Responsibility 是否独立
- Context 是否明显不同
- Allowed Actions 是否不同
- Risk 是否不同
- Workflow 是否不同

如果两个 Agent：

- 读相同代码
- 做类似任务
- Trigger 高度一样
- 只是技术名称不同

优先合并。

---

# 10. 不要按文件类型拆 Agent

通常不建议：

```text
component-agent
hook-agent
router-agent
css-agent
dto-agent
mapper-agent
```

更合理的是按长期工作职责：

```text
codebase-explorer
architecture-planner
frontend-developer
backend-developer
integration-reviewer
```

是否增加：

- test-reviewer
- security-reviewer
- database-reviewer
- debug-agent

必须根据项目真实复杂度判断。

---

# 11. 从调查报告生成 Rule 的原则

Rule 回答的是：

```text
做事情时必须遵守什么？
```

Rule 应按：

- 技术栈
- 模块
- 目录
- glob
- 真实职责边界

拆分。

不要为了“主题数量”拆文件。

---

# 12. Project-Wide Rule 应该很少

适合 `alwaysApply: true` 的通常是全项目都成立的原则，例如：

- 注意修改边界
- 禁止无关重构
- 新增功能必须和模块 / 文件职责对应
- 不要过度拆分文件
- 职责清晰前提下实现尽量集中
- 必要位置写有价值的注释
- 不根据文件名猜现有实现
- 修改前先调查当前代码

而下面这些通常不应该全局应用：

- React Component Pattern
- Java Controller Pattern
- Flyway Naming
- Database Rule
- CSS Rule

React Rule 不应该影响 Java。

Java Rule 不应该影响 React。

Database Rule 不应该影响纯前端任务。

---

# 13. Agent 和 Rule 不能大量复制内容

推荐：

## Agent

放：

- Purpose
- Scope
- Trigger
- Responsibility
- Allowed Actions
- Forbidden Actions
- Required Investigation
- Workflow
- Verification
- Output Format

## Rule

放：

- Coding Constraints
- Architecture Constraints
- Naming
- Module Boundary
- File Responsibility
- Applicable Pattern
- Glob

不要把完整 React 规范复制进 frontend-agent。

也不要把 Agent 工作流程全塞进 frontend-rule。

---

# 14. 推荐的四阶段工作流

以后任何新项目，都可以按以下流程执行。

---

## Phase 1 — Project Ground Truth Investigation

回答：

```text
这个项目实际上是什么？
```

只调查。

输出：

```text
docs/cursor/PROJECT_INVESTIGATION.md
```

禁止修改 Cursor 配置。

---

## Phase 2 — Cursor Agent / Rules Architecture Design

回答：

```text
Cursor 应该怎么组织？
```

输入：

- Project Investigation Report
- 当前 `.cursor`
- 必要真实代码复核

输出：

```text
docs/cursor/CURSOR_ARCHITECTURE_PLAN.md
```

仍然不修改 `.cursor`。

---

## Phase 3 — Apply

严格按照 Architecture Plan：

- 创建 Agent
- 修改 Agent
- 合并 Agent
- 创建 Rule
- 修改 Rule
- 合并 Rule
- 删除确认废弃配置

这一阶段不重新发明架构。

---

## Phase 4 — Validation

检查：

- Agent overlap
- Rule overlap
- Agent vs Rule duplication
- glob 是否准确
- alwaysApply 是否过宽
- Module Boundary 是否正确
- 技术栈是否准确
- 是否存在过度拆分
- 是否遗漏重要职责
- 是否残留废弃规则

输出：

```text
PASS
```

或者：

```text
NEEDS REVISION
```

---

# 15. 新项目：生成 Investigation Prompt 的母指令

下面这段可以用于任何新项目。

```text
我要为一个新的代码项目建立 Cursor Agents / Rules。

现在不要设计 Agent，也不要设计 Rule。

第一步请帮我生成一份「Project Ground Truth Investigation」调查指令，让 Cursor 先调查真实项目。

调查指令必须遵循以下原则：

1. 不预设技术栈。
2. 不预设 Monorepo / Single Repo。
3. 不预设 frontend / backend 数量。
4. 不预设 Agent 数量。
5. 不根据目录名称猜职责。
6. 必须读取真实配置和真实业务代码。
7. 必须区分：
   - PROJECT FACT
   - HARD RULE
   - EXISTING PATTERN
   - IMPROVEMENT SUGGESTION
8. 重要结论必须提供：
   - Evidence
   - File Path
   - Confidence
9. 无法确认必须写 UNKNOWN。
10. 必须调查：
    - Repository structure
    - Applications / Services / Packages
    - Technology Stack
    - Architecture
    - Dependency Direction
    - Frontend Patterns
    - Backend Patterns
    - API Contract
    - Database
    - Security
    - Testing
    - Infrastructure
    - CI/CD
    - Documentation
    - Existing .cursor/agents
    - Existing .cursor/rules
11. 必须检查：
    - Documentation Drift
    - Architecture Drift
    - Contract Drift
    - 模块职责错误
    - 过度拆分
    - 职责过度集中
    - 重复实现
12. 不允许为了最佳实践重新设计项目。
13. 当前阶段只调查，不修改业务代码，不修改 Cursor 配置。
14. 调查结果必须输出到一个独立 Markdown 文档。
15. 报告必须足以作为下一阶段设计 Cursor Agents / Rules 的事实依据。

另外，请为调查指令预留一个：

PROJECT-SPECIFIC INVESTIGATION

章节。

如果我提供了项目历史、已知模块、近期开发内容、技术债或我特别关心的领域，请把它们放入这个章节，要求 Cursor 重新验证，而不是直接相信这些历史信息。

调查指令最后必须明确：

完成报告后停止。
不要创建 Agent。
不要创建 Rule。
不要实施任何建议。
```

---

# 16. 如何提供项目专项信息

使用母指令时，再补充：

```text
Project Context:

- 项目用途：
- 已知技术：
- 主要应用：
- 主要服务：
- 最近开发过的功能：
- 当前主要问题：
- 已知历史架构：
- 怀疑存在的技术债：
- 特别需要重新验证的内容：
```

这些内容只作为：

```text
Investigation Target
```

不能直接作为：

```text
PROJECT FACT
```

---

# 17. 第二阶段：Agent / Rules 设计母指令

Project Investigation 完成后使用：

```text
基于已经完成的 Project Ground Truth Investigation Report，为当前项目设计 Cursor Agent / Rules Architecture。

本阶段必须以调查报告和真实代码证据为基础。

不要根据通用最佳实践直接套模板。

目标：

用最少但足够清晰的 Agent / Rules 覆盖当前项目真实开发工作。

设计时首先判断：

1. 项目日常有哪些不同类型的开发任务；
2. 哪些任务具有独立 Scope；
3. 哪些任务具有独立 Trigger；
4. 哪些任务需要不同 Context；
5. 哪些任务允许的修改范围不同；
6. 哪些任务风险明显不同；
7. 哪些任务值得成为长期 Agent；
8. 哪些只是其他 Agent 的子职责。

Agent 拆分原则：

Agent = 谁负责什么工作。

只有 Scope / Trigger / Responsibility 明确独立时才拆 Agent。

避免 Agent 职责大面积重叠。

不要为了技术名称创建 Agent。

例如 test / database / security / debug 是否单独成为 Agent，必须根据真实项目复杂度和工作频率判断。

Rules 设计原则：

Rule = 做事情必须遵守什么。

Rule 按真实作用范围、目录、技术栈和模块边界设计。

不要为了主题数量拆大量 Rule。

优先：

- 最小 project-wide Rule
- 少量 module-specific Rule

严格检查 alwaysApply。

技术栈专属 Rule 不应全局应用。

必须检查：

- Agent vs Agent overlap
- Rule vs Rule overlap
- Agent vs Rule duplication
- glob accuracy
- module boundary
- outdated assumptions
- architecture drift
- over-splitting
- missing responsibility

每个推荐 Agent 必须输出：

- Name
- Purpose
- Scope
- Trigger
- Responsibilities
- Allowed Actions
- Forbidden Actions
- Required Investigation
- Workflow
- Verification
- Output
- Cooperation
- Evidence

每个推荐 Rule 必须输出：

- File Name
- Purpose
- description
- globs
- alwaysApply
- Rule Content
- Evidence

对于现有 Agent / Rule：

必须给出：

KEEP
MODIFY
MERGE
CREATE
REMOVE

并说明：

Reason
Evidence
Benefit
Risk

最后输出推荐 `.cursor` 完整目录结构。

本阶段只输出 Cursor Architecture Plan。

不要实际修改 `.cursor`。

等待用户确认后再进入 Apply 阶段。
```

---

# 18. Apply 阶段建议原则

第三阶段真正修改 Cursor 配置时，应要求：

1. 严格按照确认后的 Architecture Plan。
2. 不重新扩大 Agent 数量。
3. 不增加未经确认的新 Rule。
4. 不修改业务代码。
5. Agent 内容和 Rule 内容保持职责分离。
6. 保留必要注释。
7. 不创建大量职责模糊配置。
8. 修改完成后输出实际文件清单。
9. 明确哪些文件：
   - CREATE
   - MODIFY
   - MERGE
   - REMOVE
10. 不自动 Commit / Push。

---

# 19. Validation 阶段建议检查表

最终检查：

## Agent

- Scope 是否明确
- Trigger 是否明确
- Responsibility 是否唯一
- Allowed / Forbidden 是否清楚
- 是否存在大面积职责重复
- 是否存在 Agent 数量过多

## Rule

- glob 是否准确
- alwaysApply 是否合理
- 是否重复
- 是否冲突
- 是否与真实代码一致
- 是否错误限制其他技术栈

## Agent vs Rule

- 是否复制大量相同内容
- Workflow 是否错误写进 Rule
- Coding Constraint 是否全部塞进 Agent

## Project Alignment

- 是否符合真实模块边界
- 是否符合实际技术栈
- 是否遗漏关键领域
- 是否包含废弃模块
- 是否把 Improvement Suggestion 错误升级成 Hard Rule

最终 Complexity Assessment：

- TOO SIMPLE
- BALANCED
- TOO COMPLEX

目标：

```text
BALANCED
```

---

# 20. 最终方法论

以后处理任何新项目时，记住下面这条主线即可：

```text
NEW PROJECT
     │
     ▼
① Ground Truth Investigation
这个项目实际上是什么？
     │
     ▼
② Cursor Architecture Design
Cursor 应该怎么组织？
     │
     ▼
③ Apply
建立 / 修改 Agents 与 Rules
     │
     ▼
④ Validate
配置是否准确、重复、过度复杂？
     │
     ▼
⑤ Development
正式让 Agent 开始工作
```

最终目标不是：

```text
创建最多的 Agent
创建最多的 Rule
看起来最复杂
```

而是：

> **用最少但足够清晰的 Agent / Rule，准确反映项目真实结构，控制修改边界，并让 Cursor 在长期开发中稳定遵守项目规范。**
