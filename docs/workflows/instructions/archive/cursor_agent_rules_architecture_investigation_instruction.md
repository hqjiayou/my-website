> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/overview/CURRENT_STATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# Cursor Agent / Rules 架构调查指令

> 目的：对当前整个 Monorepo 项目进行一次完整、基于真实代码的 Cursor Agent / Rules 架构调查。
>
> 本阶段只做调查、分析和报告，不执行 Agent / Rule 重构。

---

# 0. 最终产物

完成调查后，将完整调查结果写入：

`docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md`

如果 `docs/cursor/` 不存在，可以仅为本报告创建该目录。

**本阶段唯一允许创建或修改的文件就是上面的调查报告。**

除此之外：

- 禁止修改任何业务代码
- 禁止修改任何配置代码
- 禁止创建、修改、删除 `.cursor/agents/**`
- 禁止创建、修改、删除 `.cursor/rules/**`
- 禁止修改 `AGENTS.md`
- 禁止修改 README
- 禁止修改现有 docs
- 禁止安装依赖
- 禁止升级依赖
- 禁止重构
- 禁止格式化整个项目
- 禁止执行会改变 Git 工作区的命令

完成报告后立即停止，不执行 Phase 2。

---

# 1. 调查目标

你现在负责对当前整个 Monorepo 项目进行一次完整的 Cursor Agent / Rules 架构调查。

目标是理解当前项目的：

- 真实项目架构
- Monorepo 结构
- 技术栈
- 应用职责
- 服务职责
- 公共模块职责
- 模块依赖方向
- 前后端边界
- API Contract
- Database 边界
- Security 边界
- Common Module 边界
- 真实代码 Pattern
- 当前 `.cursor/agents`
- 当前 `.cursor/rules`
- Agent / Rule 重复、冲突和缺口
- 后续合理的 Cursor Agent / Rules 架构

**不要根据所谓最佳实践重新设计当前项目。**

首先描述：

`CURRENT ARCHITECTURE`

如果发现可以改善的内容，单独放入：

`IMPROVEMENT SUGGESTIONS`

禁止把建议混入项目事实。

---

# 2. Repository Baseline

开始调查前，先记录当前仓库基线。

读取并记录：

- Repository root
- Repository name
- Current Git branch
- HEAD commit hash
- Working tree status
- Modified files
- Untracked files
- Monorepo workspace definition
- Frontend package manager
- Maven parent / reactor modules

只读取。

禁止执行：

- `git checkout`
- `git reset`
- `git clean`
- `git stash`
- `git commit`
- `git merge`
- `git rebase`
- 自动修复
- 自动格式化整个项目

如果工作区存在未提交修改，标记：

`WORK IN PROGRESS`

不要自动把这些未提交代码认定为稳定的项目 Pattern。

---

# 3. Scan Scope

扫描项目根目录以及所有主要子目录。

重点识别但不限于：

- `apps`
- `services`
- `packages`
- `libs`
- `docs`
- `scripts`
- `config`
- `tests`
- `deploy`
- `docker`
- CI/CD
- `.cursor`

如果项目实际目录名称不同，以真实项目为准。

不要根据目录名称猜测职责。

必须读取真实配置和真实代码进行确认。

---

# 4. Scan Exclusions

默认排除：

- `node_modules/**`
- `dist/**`
- `build/**`
- `target/**`
- `coverage/**`
- `.git/**`
- cache
- tmp
- logs
- compiled output
- generated output
- binary files
- third-party vendored dependencies

如果某个生成文件对判断架构确实有必要，可以读取，但必须说明原因。

不要通过生成文件反推源码结构，除非源码不存在。

---

# 5. Source Of Truth

判断项目事实时，使用以下证据优先级：

1. 当前实际业务代码
2. 实际运行配置
3. Database migration
4. Tests
5. CI / scripts
6. Architecture / technical documentation
7. README
8. Comments

如果多个来源发生冲突，不要擅自选择其中一个。

必须标记：

`DOCUMENTATION DRIFT`

并分别说明：

- Current Code
- Documentation
- Difference
- Severity

---

# 6. Evidence Standard

所有重要结论必须提供 Evidence。

Evidence 至少包含：

- File Path
- Symbol / Configuration
- Why this proves the conclusion

条件允许时补充：

- Line / Range
- Related files

每个重要结论增加 Confidence：

- `HIGH`
- `MEDIUM`
- `LOW`

如果无法确认：

`UNKNOWN`

禁止根据以下内容直接判定项目行为：

- 文件名
- 目录名
- 常见项目结构
- 通用最佳实践
- README 单一描述
- 历史印象

---

# 7. Secret Handling

允许读取配置结构，但禁止在报告中输出任何敏感值，包括：

- passwords
- access tokens
- refresh tokens
- API keys
- secrets
- private keys
- cookies
- complete database credentials
- complete connection strings

可以写：

`DATABASE_PASSWORD is configured by environment variable.`

不能输出实际秘密值。

---

# 8. Project Map

输出完整 Project Map。

必须说明：

1. 每个应用的职责
2. 每个服务的职责
3. 公共模块
4. 模块之间的依赖关系
5. 哪些目录属于 frontend
6. 哪些目录属于 backend
7. 哪些属于基础设施
8. 哪些属于数据库相关
9. 哪些属于测试
10. 哪些属于文档和规范
11. 哪些属于 Cursor 配置

需要提供实际目录树摘要。

不要输出无意义的所有文件列表。

重点展示架构层级。

---

# 9. Real Configuration Investigation

读取真实配置，而不是根据目录名称猜测。

包括但不限于：

## Frontend / Monorepo

- `package.json`
- workspace 配置
- lock file
- `tsconfig*`
- `vite.config*`
- webpack config
- eslint
- prettier
- test config
- environment config

## Backend

- `pom.xml`
- parent pom
- Maven modules
- `application.yml`
- `application.yaml`
- `application.properties`
- profile config
- logging config
- security config
- test config

## Infrastructure

- Dockerfile
- docker-compose
- deployment config
- CI config
- scripts
- environment templates

如果配置不存在，标记：

`NOT PRESENT`

如果无法确认，标记：

`UNKNOWN`

---

# 10. Technology Stack

分别整理每个应用 / 服务使用的技术。

## Frontend Investigation

调查：

- Framework
- Language
- Build Tool
- Router
- UI Library
- State Management
- HTTP / API Client
- Form
- Validation
- CSS / Style
- Testing
- Lint
- Formatting
- i18n
- Permission mechanism
- Theme mechanism

## Backend Investigation

调查：

- Java Version
- Spring Boot
- Maven
- Database
- ORM / Mapper
- Redis
- Security
- Authentication
- Authorization
- Validation
- Exception Handling
- Logging
- Testing
- Migration
- OpenAPI / Swagger

对于无法确认的内容必须写：

`UNKNOWN`

禁止猜测。

---

# 11. Sampling Strategy

不要只看 README。

必须从真实业务代码中抽样调查。

不要求机械读取每一个文件，但每种重要 Pattern 要检查多个实际文件。

优先包含：

- 一个当前活跃功能
- 一个较早功能
- 一个涉及前后端交互的功能

如果不同模块 Pattern 不一致，应分别记录。

禁止看到一个文件就宣布：

`PROJECT-WIDE PATTERN`

只有出现足够重复证据时才能归类为：

`EXISTING PATTERN`

---

# 12. Frontend Code Pattern Investigation

前端至少抽查：

- Page
- Component
- Layout
- Router
- API
- Types
- Hooks
- State
- Form
- Table
- Modal / Dialog
- Permission
- Error Handling
- Styles
- i18n
- Theme
- Tests

分析：

- 文件命名
- 目录命名
- Component Pattern
- Props Pattern
- TypeScript Pattern
- API Pattern
- Router Pattern
- 状态管理 Pattern
- Permission Pattern
- Error Pattern
- Form Pattern
- Table Pattern
- Layout Pattern
- Theme Pattern
- i18n Pattern

特别检查：

- 是否有页面直接调用 axios / fetch
- 是否存在重复 API 封装
- 是否存在 hardcoded backend enum
- 是否直接展示后端字段值
- 是否存在重复状态逻辑
- 是否存在过度组件拆分
- 是否存在一个文件承担过多无关职责

---

# 13. Backend Code Pattern Investigation

后端至少抽查：

- Controller
- Service
- ServiceImpl
- Mapper
- Entity
- DTO
- Request
- Response
- Security
- Auth
- Config
- Exception
- Database
- Redis
- Migration
- Tests

分析：

- package 结构
- 命名方式
- 分层方式
- 调用方向
- DTO 使用方式
- Entity 使用边界
- Service 职责
- Mapper 职责
- Controller 职责
- 权限实现
- Error Handling
- Validation
- Transaction 使用方式
- Migration Pattern
- Test Pattern

特别检查：

- Controller 是否包含业务逻辑
- Service 是否承担过多职责
- Mapper 是否被滥用
- Entity 是否直接暴露给 API
- DTO 是否重复
- common 是否被滥用
- auth 是否承载 admin 业务
- admin 是否复制认证逻辑
- gateway 是否出现业务逻辑

---

# 14. Current Architecture Boundaries

识别：

- App 边界
- Service 边界
- Module 边界
- Frontend / Backend 边界
- API Contract
- Database 边界
- Security 边界
- Common Module 边界

重点分析：

- 哪些模块允许依赖哪些模块
- 当前实际依赖方向
- 哪些依赖方向是项目明确禁止的
- 哪些禁止方向只是建议而不是现行规则

不要重新设计架构。

这里只描述：

`CURRENT ARCHITECTURE`

如果发现问题，单独放：

`IMPROVEMENT SUGGESTIONS`

---

# 15. Historical Areas Requiring Re-Verification

以下内容曾是项目的重要开发方向。

不要假定已经完成。

必须根据当前代码重新确认。

---

## 15.1 admin-web

重新调查：

- Admin Layout
- Header
- Sidebar
- MainContainer
- Router
- Menu source
- Permission source
- RBAC UI
- API service layer
- mock data
- hardcoded data
- `Promise.resolve` fake API
- user page
- role page
- permission page
- menu page
- loading handling
- empty handling
- error handling
- 401 handling
- 403 handling
- i18n
- theme
- light / dark / system
- font size
- page density
- primary color
- local UI preferences
- backend user preference persistence
- localStorage fallback

每一项标记：

- `IMPLEMENTED`
- `PARTIAL`
- `MOCK`
- `HARDCODED`
- `NOT IMPLEMENTED`
- `UNKNOWN`

必须提供 Evidence。

---

## 15.2 website-services

重点重新确认：

- admin
- auth
- gateway
- common

调查真实职责和真实依赖方向。

特别确认：

- gateway 是否实际作为统一入口
- auth 当前实际职责
- admin 当前实际职责
- common 当前实际包含什么
- 是否存在跨模块职责泄漏
- admin 是否直接实现认证逻辑
- auth 是否承载 admin 业务
- common 是否包含只被一个模块使用的业务能力

这些都是调查项。

不要根据历史设计直接认定结果。

---

# 16. RBAC Investigation

重新调查：

- `admin_user`
- `admin_role`
- `admin_permission`
- menu
- user-role relation
- role-permission relation
- menu-permission relation
- `/me`
- menu filtering
- permission filtering
- frontend permission guard
- backend authorization

输出实际调用链。

明确区分：

## Authentication

和：

## Authorization

不要混为一谈。

如果前后端授权逻辑不一致，标记：

`AUTHORIZATION DRIFT`

---

# 17. Mock To Real API Investigation

建立完整 Mock Inventory。

对于每个发现的 mock / hardcoded data，记录：

- Module
- File
- Consumer
- Purpose
- Real API Exists?
- Backend Exists?
- Database Exists?
- Still Used?
- Removal Candidate?

分类：

- `ACTIVE MOCK`
- `LEGACY MOCK`
- `TEST FIXTURE`
- `DEMO DATA`
- `DEV SEED`
- `REAL DATA`

不要把：

- test fixture
- dev seed
- migration seed

误判成业务 mock。

---

# 18. Database / Flyway Investigation

调查：

- PostgreSQL
- Flyway
- current migrations
- schema history related config
- development seed
- test seed
- H2
- PostgreSQL local development
- Entity
- Mapper
- migration consistency

特别确认：

- 是否存在 admin 用户开发初始化
- development seed 如何启用
- production migration 是否包含测试数据
- H2 test SQL 是否包含 `DELETE`
- H2 test SQL 是否包含 `TRUNCATE`
- test data 是否可能影响 PostgreSQL
- Flyway 是否存在清理业务表逻辑
- Docker / volume 如何持久化 PostgreSQL
- local PostgreSQL 数据目录如何配置
- Entity / Mapper / migration 是否一致
- 数据字典是否同步

不要沿用过去结论。

重新通过当前代码验证。

---

# 19. API Contract Investigation

抽查主要前后端接口。

确认链路：

`Frontend TypeScript Type`
↓
`HTTP API`
↓
`Request / Response DTO`
↓
`Service`
↓
`Entity / Mapper`
↓
`Database`

检查：

- field naming
- nullability
- enum
- pagination
- error response
- success response
- HTTP status
- message
- validation
- permission

每个抽查接口识别为：

- `CONTRACT MATCH`
- `CONTRACT DRIFT`
- `PARTIAL`
- `UNKNOWN`

如果有 drift，必须说明 drift 在哪一层。

---

# 20. File Responsibility Investigation

特别检查功能内容和文件职责是否匹配。

识别：

- 一个文件承担过多无关职责
- 一个职责被拆成过多小文件
- Helper / Support / Manager / Processor / Adapter 等模糊职责
- 只有几行转发逻辑的小文件
- 相同领域功能被无意义拆散
- 跨模块代码放错位置

但不要自动建议拆分。

同时检查：

- `OVER-SPLITTING`
- `UNDER-SEPARATION`

项目原则：

**职责清楚优先。**

在职责清楚的前提下：

**实现应尽量集中。**

不要把：

“更多文件”

视为：

“更好的架构”。

---

# 21. Comment Pattern Investigation

调查当前代码注释方式。

区分：

## USEFUL COMMENT

例如：

- 非显然业务规则
- 安全边界
- compatibility
- migration reason
- architectural decision
- tricky side effect

和：

## NOISE COMMENT

例如：

- 重复代码本身含义
- 每行代码翻译成人话
- 无实际信息的模板注释

后续 Recommended Rules 应要求：

“必要位置写清楚注释。”

但禁止建议：

“所有方法 / 字段机械增加注释”。

---

# 22. Existing Cursor Configuration

完整读取：

- `.cursor/agents/**`
- `.cursor/rules/**`

同时读取：

- `AGENTS.md`
- 根目录关键 README
- 主要模块 README
- 与架构和开发规范有关的 docs

对于 `docs/**`：

先完整枚举目录和文档标题。

优先读取：

- architecture
- development conventions
- frontend conventions
- backend conventions
- API conventions
- database
- data dictionary
- TODO
- roadmap
- current design documents

其余文档根据调查主题按需读取。

不要无脑将整个 docs 内容塞入上下文。

---

# 23. Existing Agent Analysis

对于每个现有 Agent，整理：

- name
- description
- scope
- responsibility
- trigger
- allowed actions
- forbidden actions
- required investigation
- workflow
- verification
- output format

检查：

- 重复
- 冲突
- 职责重叠
- scope 过宽
- scope 过窄
- 定义不明确
- 与真实项目不一致
- 与 Rule 内容大量重复
- 是否已过时

---

# 24. Existing Rule Analysis

对于每个 Rule，整理：

- file name
- description
- globs
- alwaysApply
- 内容摘要
- 实际作用范围

检查：

- Rule 重复
- Rule 冲突
- Rule 过宽
- Rule 过窄
- glob 错误
- alwaysApply 过宽
- 技术栈假设错误
- 模块范围错误
- 已过时规则
- 与 Agent 内容大量重复
- 与真实代码不一致

---

# 25. Fact Classification

所有调查结果必须分类为：

## A. PROJECT FACT

项目真实存在的架构和行为。

## B. HARD RULE

项目已有且明确要求遵守的规范。

## C. EXISTING PATTERN

目前代码中大量采用，但未明确规定为硬规则的模式。

## D. IMPROVEMENT SUGGESTION

可以改善，但当前项目并没有要求。

禁止把：

`D. IMPROVEMENT SUGGESTION`

自动转成：

`HARD RULE`

---

# 26. Agent Design Principles

根据真实项目决定需要多少 Agent。

不要预设数量。

重点评估是否需要：

- codebase-explorer
- architecture-planner
- frontend agent
- backend agent
- integration-reviewer
- test-reviewer
- security-reviewer
- database-reviewer
- debug agent

如果项目有多个真正独立的 frontend / backend：

判断是否值得进一步拆分。

但必须有真实边界证据。

---

# 27. Agent Split Criteria

只有满足明确职责边界时才能推荐独立 Agent。

一个独立 Agent 至少必须拥有明确的：

- Scope
- Trigger
- Responsibility

并且与其他 Agent 不存在大面积职责重叠。

如果两个候选 Agent：

- 读取相同代码
- 执行相同任务
- Trigger 高度相同
- 只是技术名称不同

优先合并。

特别评估：

- test-reviewer
- security-reviewer
- database-reviewer
- debug-agent

是否真的值得成为长期独立 Agent。

如果这些只是：

- integration-reviewer
- frontend agent
- backend agent

的少量子职责，则不要单独创建。

Agent 数量服从项目实际需要，而不是角色列表。

---

# 28. Recommended Agent Definition

每个推荐 Agent 输出：

- Name
- Purpose
- Scope
- Trigger
- Responsibilities
- Allowed Actions
- Forbidden Actions
- Required Investigation
- Implementation Workflow
- Verification
- Output Format
- Dependencies / Cooperation
- Evidence
- Recommendation

其中 Recommendation 必须是：

- `KEEP`
- `MODIFY`
- `MERGE`
- `CREATE`
- `REMOVE`

本阶段只提出建议，不实际执行。

---

# 29. Rules Design Principles

根据真实项目设计 Rules。

不要为了数量拆文件。

按真正的责任边界拆。

可以考虑但不要预设必须存在：

- project
- architecture
- frontend
- components
- router
- styles
- api
- backend
- packages
- database
- security
- testing

必须根据真实代码判断。

---

# 30. Rule Split Criteria

Rule 只有存在清晰适用范围时才拆分。

优先：

- 一个最小 project-wide Rule
- 少量明确 module-specific Rule

不要创建大量只有几十行且高度重复的 Rule。

Rule 拆分依据应主要来自：

- 不同技术栈
- 不同目录边界
- 不同模块职责
- 不同适用 glob

而不是：

“一个主题一个文件”。

例如：

如果 Component / Router / Style 实际都属于同一个 admin-web Pattern，并且约束高度共享，应评估是否合并成 frontend Rule。

如果确有明显不同约束，再拆分。

---

# 31. Always Apply Review

特别审查：

`alwaysApply: true`

只允许真正项目全局都成立的规则使用。

例如可能适合：

- 修改边界
- 禁止无关重构
- 模块职责
- 不过度拆分
- 必要注释
- 不猜测现有架构

技术栈专属 Rule 通常不应该 alwaysApply。

React Rule 不应影响 Java。

Java Rule 不应影响 React。

Database Rule 不应影响纯前端修改。

---

# 32. Agent vs Rule Principle

必须遵守：

## Agent

回答：

“谁负责做什么。”

## Rule

回答：

“做事情时必须遵守什么。”

不得：

- 把大量代码规范复制进 Agent
- 把完整工作流程全部塞进 Rule
- Agent 和 Rule 重复维护同一大段内容

必须检查：

- Agent vs Agent
- Rule vs Rule
- Agent vs Rule
- Rule vs 真实代码
- Rule glob 范围
- 模块职责冲突
- 技术栈假设错误
- 已过时规则
- 重复内容

---

# 33. Recommended Rule Definition

每个推荐 Rule 输出：

- File Name
- Purpose
- description
- globs
- alwaysApply
- Rule Content Summary
- Evidence
- Recommendation

Recommendation 必须是：

- `KEEP`
- `MODIFY`
- `MERGE`
- `CREATE`
- `REMOVE`

其中 Evidence 必须指出：

该规则来自哪些真实文件、配置或大量存在的代码 Pattern。

---

# 34. Recommendation Evidence Threshold

推荐创建、修改、删除或合并任何 Agent / Rule 时，必须使用：

## Recommendation

`KEEP / MODIFY / MERGE / CREATE / REMOVE`

## Reason

为什么需要。

## Evidence

真实代码或配置依据。

## Benefit

带来的实际收益。

## Risk

修改可能带来的风险。

如果只是理论上看起来更漂亮：

不要推荐。

---

# 35. Severity

发现问题时按照以下严重程度：

## Critical

会导致明显安全问题、错误架构行为、严重规则冲突，或 Agent 可能错误修改关键模块。

## High

会显著影响后续开发正确性、模块边界或 Cursor 行为。

## Medium

存在维护性、重复、边界不清或 Pattern 漂移问题。

## Low

非阻塞优化。

禁止为了让报告看起来更丰富而制造问题。

---

# 36. Final Validation

最终推荐的 Cursor Architecture 必须再次检查：

- 是否覆盖主要开发任务
- 是否存在 Agent 职责重复
- 是否存在 Rule 内容重复
- 是否存在 Agent / Rule 重复
- globs 是否真实匹配项目目录
- alwaysApply 是否过宽
- 技术栈是否来自真实代码
- 模块边界是否符合当前项目
- 是否存在过度拆分
- 是否遗漏重要模块
- 是否错误包含废弃模块
- 是否把 Improvement Suggestion 错写成 Hard Rule
- 是否存在为了“最佳实践”而违背当前项目 Pattern 的建议

最后给出：

## COMPLEXITY ASSESSMENT

仅可选择：

- `TOO SIMPLE`
- `BALANCED`
- `TOO COMPLEX`

并说明原因。

目标不是构建最复杂的 Cursor 配置。

目标是：

**以最少但足够清晰的 Agent / Rule，准确约束当前项目。**

---

# 37. Final Report Format

最终将报告完整写入：

`docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md`

报告必须使用以下结构。

---

# 1. Executive Summary

简要说明：

- 当前项目架构概况
- 当前 Cursor 配置概况
- 最大问题
- 推荐方向
- Complexity Assessment

---

# 2. Repository Baseline

包括：

- Root
- Branch
- HEAD
- Working Tree
- Workspace
- Package Manager
- Maven Modules

---

# 3. Project Map

展示项目主要目录及职责。

---

# 4. Technology Stack

按应用 / 服务分别列出。

---

# 5. Application / Service Responsibilities

逐个应用 / 服务说明职责和 Evidence。

---

# 6. Current Architecture

只写当前真实架构。

不要混入建议。

---

# 7. Dependency Direction

说明：

- 当前依赖方向
- 允许方向
- 已明确禁止方向
- 当前违规方向

---

# 8. Existing Code Patterns

分类：

## Frontend Patterns

## Backend Patterns

## Database Patterns

## Testing Patterns

## Documentation Patterns

---

# 9. Historical Feature Re-Verification

至少包含：

## admin-web

## website-services

## RBAC

## UI Preferences

## Mock To Real API

## Database / Flyway

逐项标记状态。

---

# 10. Mock Inventory

使用表格：

| Module | File | Type | Consumer | Real API | Backend | Database | Still Used | Recommendation |

---

# 11. API Contract Review

使用表格：

| API | Frontend | Backend | DB | Status | Drift |

---

# 12. Database / Flyway Review

说明：

- migration
- dev seed
- test seed
- H2
- PostgreSQL
- admin user initialization
- persistence
- destructive SQL
- consistency

---

# 13. Existing Cursor Agents

使用表格：

| Agent | Scope | Trigger | Responsibility | Problem | Recommendation |

---

# 14. Existing Cursor Rules

使用表格：

| Rule | Glob | Always Apply | Purpose | Problem | Recommendation |

---

# 15. Fact Classification

分为：

## A. PROJECT FACT

## B. HARD RULE

## C. EXISTING PATTERN

## D. IMPROVEMENT SUGGESTION

---

# 16. Problems Found

按：

## Critical

## High

## Medium

## Low

分类。

---

# 17. Recommended Agent Architecture

使用表格：

| Agent | Scope | Trigger | Responsibility | Recommendation | Evidence |

然后逐个给出完整 Agent 定义建议。

---

# 18. Recommended Rules Architecture

使用表格：

| Rule | Glob | Always Apply | Purpose | Recommendation | Evidence |

然后逐个给出完整 Rule 定义建议。

---

# 19. Agent / Rule Responsibility Matrix

清楚说明：

什么由 Agent 管。

什么由 Rule 管。

避免重复。

---

# 20. Recommended Files To Keep

说明原因。

---

# 21. Recommended Files To Modify

说明：

- 文件
- 修改方向
- 原因
- Evidence

本阶段不实际修改。

---

# 22. Recommended Files To Create

说明：

- 文件
- Purpose
- Scope
- Reason
- Evidence

本阶段不实际创建。

---

# 23. Recommended Files To Remove / Merge

说明：

- 文件
- Remove / Merge
- 原因
- 风险

这里只提出建议。

禁止实际删除。

---

# 24. Final Recommended `.cursor` Structure

给出完整推荐目录树。

例如：

```text
.cursor/
├── agents/
│   └── ...
└── rules/
    └── ...
```

必须基于调查结果生成。

不要套模板。

---

# 25. Documentation Drift

使用表格：

| Document | Current Code | Difference | Severity |

---

# 26. Evidence Index

使用表格：

| Finding | Evidence | Confidence |

重要结论必须能追溯到这里。

---

# 27. Investigation Uncertainties

使用表格：

| Unknown Item | Why Unknown | What Would Confirm It |

无法确认就写 UNKNOWN。

禁止猜测。

---

# 28. Complexity Assessment

只能是：

- TOO SIMPLE
- BALANCED
- TOO COMPLEX

说明原因。

---

# 29. Final Recommendation

最后总结：

1. 当前 Cursor Agent / Rules 架构是否适合项目
2. 哪些保留
3. 哪些修改
4. 哪些合并
5. 哪些新增
6. 哪些移除
7. 下一阶段推荐执行顺序

但不要执行。

---

# 38. Stop Condition

完成：

`docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md`

之后立即停止。

不要：

- 修改 `.cursor/agents`
- 修改 `.cursor/rules`
- 创建新的 Agent
- 创建新的 Rule
- 删除旧 Agent
- 删除旧 Rule
- 修改业务代码
- 修复发现的问题
- 自动重构
- 安装依赖
- Commit
- Push

本阶段仅调查和报告。

下一阶段由用户确认调查报告后再决定是否执行 Cursor Agent / Rules 重构。
