> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/workflows/instructions/templates/FULL_STACK_IMPLEMENTATION_TEMPLATE.md  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
# my-website 全功能实施总指令（Master Implementation Runbook）

> **用途**：在 Cursor 的高能力 Agent 模式中执行一次完整的全功能实施任务。
>
> **主基线**：`docs/audit/FULL_FUNCTION_IMPLEMENTATION_AUDIT.md`
>
> **目标**：基于当前真实代码、既有 Mock/UI 意图、现有数据库基础与已确认的架构边界，按“后端先行、按业务域纵向闭环”的方式，把当前项目从 Auth/RBAC 核心已完成、其他业务域大量 Mock/Placeholder 的状态，推进到真实 API、真实持久化、真实前端接入、测试与 Review 闭环。
>
> **执行方式**：这是一个“单次总任务”，但必须内部按 Phase 严格分阶段执行。不要把所有模块同时乱改。每个 Phase 都要先调查、列 Plan、实施、验证、Review，再进入下一 Phase。
>
> **禁止把 Mock 页面、toast 占位、Local State 成功提示当作完成。**

---

# 0. 开始前必须读取的基线

执行前完整读取并确认：

```text
AGENTS.md

.cursor/agents/**
.cursor/rules/**

docs/audit/FULL_FUNCTION_IMPLEMENTATION_AUDIT.md
docs/cursor/CURSOR_AGENT_RULES_ARCHITECTURE_INVESTIGATION.md
```

并读取与当前阶段直接相关的真实代码。

如果 Audit / Docs 与当前代码冲突：

> **当前真实代码优先。**

但必须在最终报告记录差异。

不要机械按照 Audit 中的建议修改代码；Audit 是基线与 Finding 来源，不是无条件实施清单。

---

# 1. 总体执行原则

本次开发全过程必须遵守以下原则。

## 1.1 Evidence First

修改任何功能前：

1. 读取当前真实实现；
2. 确认现有调用链；
3. 确认数据来源；
4. 确认真实 API / Mock / Placeholder 状态；
5. 确认数据库结构；
6. 确认权限模型；
7. 确认已有测试；
8. 再决定修改。

不得根据：

- 文件名
- 旧文档
- Mock Shape
- 历史对话
- “一般项目都这么做”

直接设计真实业务模型。

---

## 1.2 Backend First，但按 Domain 纵向闭环

正确执行方式：

```text
Domain Requirement / Current Intent
        ↓
Contract / Data Model
        ↓
Database
        ↓
Backend
        ↓
Backend Tests
        ↓
Frontend API
        ↓
Frontend Real Integration
        ↓
Frontend States / Permission / Error
        ↓
Remove / Retire Domain Mock
        ↓
End-to-End Verification
```

禁止：

```text
先一次性写完所有 Backend
然后再统一接所有 Frontend
```

也禁止：

```text
先把所有 Mock 改成 axios
让不存在的 Backend 以后再补
```

每完成一个业务域，就完成该域前后端闭环。

---

## 1.3 修改边界

每次修改必须明确：

- 当前 Domain
- Allowed Files
- Forbidden Files
- Dependencies
- DB Changes
- API Changes
- Frontend Changes
- Tests

只修改完成当前 Domain 所必需的文件。

发现其他问题：

记录到 `OUT OF SCOPE / LATER PHASE`。

不要顺手修。

---

## 1.4 模块与文件职责

新增功能必须与模块、目录、文件职责匹配。

后端：

```text
Controller → HTTP boundary
Service → business logic
ServiceImpl → implementation
Mapper → persistence
Entity → persistence model
DTO → API contract
infra → infrastructure
common → stable cross-module contract only
gateway → routing / gateway cross-cutting only
```

前端：

```text
pages → screen / page orchestration
components → reusable UI
api → HTTP / API boundary
router / guards → navigation / route authorization
stores → real client/global state
TanStack Query → server state
mocks → prototype/test-like data source only
```

不要把业务逻辑塞入：

- utils
- common
- gateway
- pure UI component
- mock type

---

## 1.5 不过度拆分

禁止为了“架构漂亮”制造：

```text
FooHelper
FooSupport
FooManager
FooProcessor
FooFacade
FooAdapter
```

如果它们只是：

- 转发
- 包一层调用
- 两三行无独立职责逻辑

则不要创建。

职责清楚时实现尽量集中。

反过来，如果一个文件确实承担两个稳定、独立职责，可以合理拆分。

判断依据：

> **职责，不是行数。**

---

## 1.6 注释要求

必须写注释的情况：

- 非显然权限边界
- 安全策略
- 兼容逻辑
- 特殊数据约束
- 重要并发 / 幂等逻辑
- 容易被未来开发者误改的设计决策

禁止：

- 逐行解释代码
- “这里调用 service”
- “判断是否为空”
- 与代码完全重复的废话注释

---

# 2. Agent 使用规则

必须充分使用当前项目已有 Subagents，但不要为了流程而机械调用全部 Agent。

当前角色：

```text
codebase-explorer
architecture-planner
admin-backend
admin-frontend
integration-reviewer
```

## codebase-explorer

负责：

> 找事实。

适用于：

- 每个新 Domain 开始前
- 现有实现不清楚
- Mock / Real 状态不清楚
- API / DB / Permission 链路需要定位

默认只读。

---

## architecture-planner

负责：

> 定最小合理方案。

每个主要 Domain 开始前必须使用。

必须输出：

- Current State
- Goal
- Scope
- Contract
- Data Model
- API
- Permission
- Files
- Tests
- Risks
- Things Not To Change

规划后不需要等待用户确认，除非出现真正无法从现有代码 / UI / Mock / Audit 推导的业务决策。

---

## admin-backend

负责：

> Backend 实现。

默认只修改与当前任务直接相关的：

```text
services/website-services/**
```

必要时处理：

- Flyway
- Tests
- OpenAPI
- auth/admin/site
- infra

不得越界修改 React。

---

## admin-frontend

负责：

> `apps/admin-web` 真实功能实现与 API 对接。

负责：

- 页面
- API client
- Query
- Form
- Loading / Empty / Error
- Permission
- i18n
- Mock retirement

默认不得修改 Java / DB。

---

## integration-reviewer

负责：

> 每个 Phase 完成后的独立只读审查。

必须检查：

- Requirement Match
- Modification Boundary
- Module Responsibility
- Contract
- Security
- Permission
- DB
- Mock Residue
- Tests
- Over-Splitting
- Under-Separation
- Regression Risk

Reviewer 默认只读。

发现真实问题后：

1. 输出 Finding；
2. 主 Agent 根据 Finding 修正；
3. 再复审；
4. 通过后才进入下一 Phase。

---

# 3. 单次任务的执行生命周期

整个任务必须遵循：

```text
Ground Truth
    ↓
Master Plan
    ↓
Phase Plan
    ↓
Implementation
    ↓
Verification
    ↓
Integration Review
    ↓
Fix Review Findings
    ↓
Re-Verification
    ↓
Next Phase
```

禁止：

```text
边看边改
想到哪改到哪
最后统一跑一下 build 就宣布完成
```

---

# 4. 开始实施前：先建立 Master Plan

首先根据当前代码和 `FULL_FUNCTION_IMPLEMENTATION_AUDIT.md`，输出本次实际 Master Plan。

必须列出：

| Phase | Domain | Current State | Target State | Backend | Frontend | DB | Risk |
|---|---|---|---|---|---|---|---|

确认当前功能数量和状态有没有发生变化。

如果 Audit 中某项已经被后续 WIP 实现：

更新 Plan，不重复实现。

Master Plan 完成后继续执行，无需暂停等待用户。

只有以下情况才能暂停：

- 产品语义无法合理推导；
- 两种设计会导致明显不同业务行为；
- 涉及不可逆数据迁移且缺乏证据；
- 需要外部密钥 / 第三方账号 / 外部服务而当前环境没有；
- 存在安全高风险但无法确定正确策略。

其他普通工程决策由 Planner 选择最小、可逆、符合当前架构的方案。

---

# 5. Feature Completion Definition

从本轮开始，功能只能按下面标准标记。

## COMPLETE

适用维度全部满足：

```text
Backend Logic             ✅
Database / Persistence    ✅
API                       ✅
Authorization             ✅
Validation                ✅
Tests                     ✅
Frontend                  ✅
Real API Connected        ✅
Loading / Empty / Error   ✅
Persistence Verified      ✅
```

N/A 项可以不计。

---

## BACKEND_COMPLETE

Backend + DB + API + Permission + Tests 完成，但 Frontend 尚未实现。

---

## PROTOTYPE

只有：

```text
Frontend + Mock
```

---

## PLACEHOLDER

只有：

```text
UI Shell
toast.info
hardcoded
EmptyModulePage
fake KPI
```

---

## BLOCKED

确实缺少业务定义、外部服务或不可推导信息。

不得把 BLOCKED 伪装成 COMPLETE。

---

# 6. Phase 1 — Production Security Gate

目标：

解决当前 Audit 已确认的真实认证攻击面。

## 6.1 Auth Rate Limiting

先调查当前：

- gateway
- auth
- Redis
- login
- register
- refresh
- request-id
- client IP handling

设计最小安全策略。

要求：

### Login

至少考虑：

- IP
- username
- IP + username
- time window
- failure counter
- cooldown / progressive delay
- successful login reset

不要简单采用：

```text
失败 N 次 → 长时间永久锁号
```

避免攻击者利用账号锁定进行 DoS。

### Register

对开放注册路径增加合理限制。

### Refresh

根据真实风险采用与 login 不同的限流策略，不要机械复用同一阈值。

### Redis

优先复用现有 Redis 能力。

不要为了限流引入新的大型基础设施。

---

## 6.2 Open Registration Guard

当前默认：

```text
open-registration = false
```

保持。

生产环境如果显式开启 open registration：

必须有非常明确的高风险日志。

如果当前配置体系适合，评估 production fail-fast。

不要影响 bootstrap 首管理员合法流程。

---

## 6.3 Username Enumeration

评估并实现低成本 dummy password hash / equivalent mitigation。

不得改变统一错误消息行为。

---

## 6.4 Security Tests

至少覆盖：

- 正常登录
- 失败计数
- 超阈值
- cooldown
- 成功后恢复
- 不同 IP
- 不同 username
- register 限制
- refresh 行为
- Redis unavailable 的安全策略（按当前系统设计）

完成：

Backend tests → Review → 修复 → PASS。

---

# 7. Phase 2 — Site Core Domain

这是第一个主要业务纵向闭环。

## 7.1 Contract First

不能直接复制 Mock Shape。

重新调查：

- `site` Flyway table
- Site Entity
- Mapper
- PG tests
- Frontend site mock
- Site List
- Site Create Wizard
- Workspace
- current API client

确定真实 Site Contract。

至少分析：

- id
- site code / slug
- name
- type
- status
- description
- template relation
- primary color
- domain
- createdAt
- updatedAt
- version

不要强行把尚未属于 Site Core 的字段塞进 `site`。

例如：

- appearance
- content
- domain relation

如果属于独立 Domain，应保持边界。

---

## 7.2 Site Backend

实现真实：

- list
- detail
- create
- update
- delete / soft delete
- status change（若当前产品语义支持）
- pagination
- search / filter（只做当前 UI 真需要的）
- optimistic locking
- validation
- permission
- error contract

使用现有 site table / Entity / Mapper。

不要创建额外平行 persistence model。

---

## 7.3 Site Permissions

核对当前种子：

```text
admin:site:*
```

只使用真实存在 / 本次真实实现的权限。

如果 seed 有预留权限：

不要为了匹配 seed 强行创建无需求 endpoint。

---

## 7.4 Site Tests

至少：

- create
- duplicate
- list
- detail
- update
- optimistic conflict
- delete
- status
- authorization
- validation
- not found
- PG persistence

---

## 7.5 Site Frontend Real API

Backend 完成后立即接入：

- Site List
- Site Create
- Site Detail / Workspace
- Site Basic

要求：

- Real API
- Query Keys
- invalidate
- loading
- empty
- error
- permission
- form validation
- duplicate submit protection
- persisted reload verification

---

## 7.6 Site Mock Retirement

只退休已经真实化的 Site capability。

例如：

```text
Site List   → REAL
Site Create → REAL
Site Detail → REAL
Site Update → REAL
```

但：

```text
Appearance
Content
Reservation
```

如果仍未实现，可以继续 Mock。

不要因为 Site Core 完成就删除整个 Workspace 所有 Mock。

---

# 8. Phase 3 — Admin Management Closure

## 8.1 Admin User CRUD

基于现有：

```text
admin_user
admin:user:*
```

重新确认真实需求后实现：

- list
- detail
- create
- update
- enable / disable
- delete（若业务允许）
- password reset / credential action（仅在有明确当前需求时）

安全要求：

- 不允许危险 mass assignment
- 保护 super_admin
- 防止自我危险操作
- 状态 / role 修改边界清楚

不要与：

```text
bootstrap register
```

混为同一能力。

---

## 8.2 User ↔ Role Frontend

后端已经存在真实：

```text
GET/PUT /api/admin/users/{id}/roles
```

补齐真实 UI。

必须：

- 使用真实 API
- 权限控制
- loading/error
- self-protection feedback
- invalidation

---

## 8.3 Menu Write

只有当前真实产品语义明确需要后台动态维护菜单时实现。

如果现阶段菜单仍以 Flyway/Seed 管理更合理：

标记：

```text
DEFERRED_BY_DESIGN
```

不得为了 Audit 里存在预留权限就强行实现。

如果实施：

必须沿用现有：

```text
admin_permission(resource_type=MENU)
```

禁止创建独立 `admin_menu` 平行模型。

---

# 9. Phase 4 — Template

先 Contract Review。

确定：

- template 是什么
- 是否全局
- 是否 site-type 相关
- 是否可编辑
- 是否仅系统预置
- version / status
- preview / metadata

不要直接把 Mock 全字段落 DB。

实现顺序：

```text
DB / migration
→ Entity/Mapper
→ Backend API
→ Permission
→ Tests
→ Frontend Real API
→ Retire Template Mock
```

---

# 10. Phase 5 — Appearance / Theme

这是 Site Creation Workflow 的重要组成。

先明确：

```text
Appearance
Theme
Layout
Section
Primary Color
Button Style
Font Style
Hero Style
Logo
```

哪些是：

- Site Core
- Appearance Domain
- Template default
- User override

避免字段重复存储。

---

## 10.1 Persistence Design

优先选择当前规模下最简单、清楚、可演进的模型。

不要为了“灵活”直接创建过度复杂 JSON DSL 或十几张配置表。

如果使用 JSONB：

必须说明：

- 为什么适合
- schema validation 如何保证
- versioning
- backward compatibility

---

## 10.2 Complete Site Creation Workflow

最终确保：

```text
Create Site
→ Choose Template
→ Configure Appearance
→ Submit
→ Backend Persist
→ Reload
→ Exact State Restored
```

Audit 发现的：

```text
buttonStyle
fontStyle
heroStyle
logoPlaceholder
```

等字段不得继续“输入后丢失”。

要么真实保存；
要么从 UI 删除；
要么明确不属于当前版本。

禁止假闭环。

---

# 11. Phase 6 — File / Media

先 Contract First。

确定：

- object storage
- metadata DB
- owner / site relation
- file size
- MIME
- extension
- path / key
- upload
- download / preview
- delete
- orphan cleanup
- permission

当前已有 MinIO 配置，优先复用。

安全：

- MIME 验证
- extension 不可信
- object key 生成
- path traversal
- size limit
- unauthorized access
- dangerous file type policy

不要只实现“把文件传上去”。

---

# 12. Phase 7 — Content / CMS

Content 与 File 关联后再实施。

先明确当前 UI 真正需要的 Content Model。

避免一步设计成万能 CMS。

至少分析：

- title
- type
- status
- body
- media
- site
- ordering
- publish
- draft
- timestamps
- version

根据真实 UI / Mock /产品意图设计最小模型。

实现：

```text
Backend
DB
Permission
Tests
Frontend
Real API
Mock retirement
```

---

# 13. Phase 8 — Reservation

重新确认：

- reservation 属哪个 site type
- data model
- status lifecycle
- contact / privacy
- conflict / duplicate
- time zone
- cancel
- admin operations

不得仅复制 Mock。

如当前产品语义不足：

允许实现 Audit / UI 已能确认的最小 CRUD / list capability，
其余标记 BLOCKED / FUTURE。

---

# 14. Phase 9 — Site Frontend Authentication

当前：

```text
POST /api/auth/site/login → 501
GET /api/site/auth/me → placeholder
```

必须完整设计成一条真实链路：

```text
Site Login
→ SITE Audience Token
→ Resource Server Verification
→ Site User Context
→ Authorization
→ /site/auth/me
```

必须与 ADMIN auth 分离。

不得：

- 复用 ADMIN token 冒充 SITE token
- 混用 admin_role 与 site user roles
- 为了省代码破坏 audience boundary

实现后：

- Backend tests
- JWT tests
- Frontend site-web integration（如果当前 site-web 已具备真实登录需求）

---

# 15. Phase 10 — AI

当前 Java agent 是 stub，TS agent-services 是 mock。

必须先做职责决策：

```text
Java agent module
vs
TS agent-services
```

只能有清晰边界。

不要保留两个“看起来都负责 AI”的真实生产路径。

先根据项目当前技术方向选择：

- Java 作为 client / abstraction，TS 作为真实 AI service；
- 或 Java service 直接承担；
- 或其他有证据的最小方案。

必须明确：

- auth
- request contract
- timeout
- retry
- rate limit
- trace/request id
- prompt/config ownership
- secret
- error model

如缺少真实 AI Provider / API Key：

可以完成真实服务架构与受控 provider abstraction，
但外部调用标记 `EXTERNAL_DEPENDENCY_BLOCKED`。

禁止伪造“真实 AI 成功”。

---

# 16. Phase 11 — Analytics / Team / System / Developer

这些是低优先业务。

逐项重新判断需求：

## Analytics

Audit 已发现 `analyticsApi` 死链。

如果没有真实需求：

```text
REMOVE / DEFER
```

不要为了“功能数量”重建。

如果当前产品明确需要：

先基于真实数据源设计统计指标。

禁止 fake KPI。

---

## Team

确认是否：

- admin user collaboration
- site member
- separate team model

不要重复 RBAC。

---

## System / Params

只有确实存在平台配置需求才实现。

优先：

- typed configuration
- explicit ownership
- validation

不要造通用万能 key-value 配置中心，除非真实需求支持。

---

## Developer

确认当前页面真实目的。

hardcoded/demo 能力不得标记 COMPLETE。

---

# 17. Phase 12 — Frontend Functional Closure

等主要真实 API 形成后统一收口。

## 17.1 Loading / Empty / Error

所有真实列表页必须完整。

不要：

```ts
data ?? []
```

然后错误时显示“0 条数据”。

---

## 17.2 Query Key

统一 Query Key 策略。

避免：

- 同一资源多个无意义 key
- 不必要重复请求
- invalidate 范围过大

不要为了统一创建复杂 QueryKey Factory，如果当前规模不需要。

---

## 17.3 Route Permission

真实业务 endpoint 落地后：

对受保护页面检查：

```text
Frontend Route Permission
↔ Backend ACTION Permission
```

前端属于纵深防御 / UX。

Backend 始终是权威。

不要把 Menu Visibility 当 Authorization。

---

## 17.4 Navigation Metadata

调查：

- backend menu
- static navigation
- title
- breadcrumb
- page metadata

消除真正重复表达相同业务信息的双维护。

但不要把所有 Router metadata 都强行服务器化。

---

## 17.5 i18n

新增真实功能用户可见文案必须进入当前 i18n。

backend code / enum 不直接裸显示。

---

# 18. Phase 13 — Modernization

Modernization 永远最后做。

只有存在实际收益才实施。

## 18.1 Route Lazy Loading

先看最终 build output / chunk。

如果 bundle/route 数量达到实际收益：

使用 React 当前稳定推荐方式完成 route-level code splitting。

必须：

- build pass
- loading fallback
- error behavior
- 不破坏 route permission

如果收益不明显：

KEEP CURRENT。

---

## 18.2 Argon2id

BCrypt 当前可接受。

除非已有明确安全/合规收益与 migration strategy：

不要仅为了“2026 更现代”替换。

---

## 18.3 Metrics / Tracing

基于实际运维需求评估。

已有 requestId / logging 基础。

不要在没有 observability backend 时过度建设。

---

# 19. Database Migration Rules

所有新 DB 能力：

- 使用新 Flyway migration
- 不修改已执行 V1–V4
- migration 单向
- nullable / default 谨慎
- FK
- unique
- index
- optimistic lock
- soft delete

必须基于真实访问模式。

禁止：

- 为每个字段都建 index
- 大量 speculative index
- production migration 加开发管理员密码
- 删除历史 migration 重新合并

---

# 20. API Design Rules

新 API 必须遵守现有真实 contract 风格。

优先：

```text
HttpMessageBody<T>
```

不要创建第三套响应 Envelope。

检查：

- method
- path
- DTO
- validation
- status
- pagination
- error
- permission

Mock shape 不是 API authority。

---

# 21. Mock Retirement Rules

每个 Domain 完成 Real API 后：

重新搜索：

```text
mocks/services
mocks/types
mocks/db
USE_MOCK
```

只删除已经无消费者的 Domain Mock。

如果 Domain 部分能力仍需要 Mock：

可以暂时保留。

正式 contract 必须独立于 mock。

目标依赖方向：

```text
Formal Contract
      ↑      ↑
 Real API   Mock
```

禁止：

```text
Production API
      ↓
 Mock Type
```

---

# 22. Security Review Rules

每个真实新 Domain 都必须检查：

- authentication
- authorization
- object ownership
- IDOR
- mass assignment
- injection
- validation
- data exposure
- file/path risk
- sensitive logging
- brute force / abuse
- concurrency
- replay / duplicate request（如适用）

必须区分：

- REAL VULNERABILITY
- DEFENSE_IN_DEPTH
- UX
- OPTIONAL HARDENING

不要夸大安全 Finding。

---

# 23. 每个 Phase 的 Review Gate

每个 Phase 完成后都必须：

## 23.1 Self Verification

运行受影响范围：

- backend tests
- frontend lint
- frontend build
- import checks
- PG contract tests（涉及 DB）
- targeted tests

---

## 23.2 integration-reviewer

执行严格只读 Review。

输出：

- BLOCKER
- HIGH
- MEDIUM
- LOW
- MODERNIZATION

如果存在：

```text
BLOCKER / HIGH
```

必须修复后重新 Review。

MEDIUM：

根据是否影响当前 Feature Completion 决定 FIX NOW / NEXT PHASE。

---

## 23.3 Phase Completion

只有满足：

```text
Requirement Match
Contract Match
Tests PASS
No BLOCKER
No HIGH
No fake persistence
No unresolved permission gap
```

才允许进入下一 Phase。

---

# 24. 最终全仓 Review

所有 Phase 结束后，不要直接宣布完成。

先重新执行一次：

`Full Repository Functional Review`

以最初：

```text
docs/audit/FULL_FUNCTION_IMPLEMENTATION_AUDIT.md
```

作为 Baseline。

重新统计：

- Backend Functions
- Frontend Functions
- COMPLETE
- BACKEND_COMPLETE
- PROTOTYPE
- PLACEHOLDER
- BLOCKED
- E2E

逐项对照最初 35 项功能口径。

如果功能划分发生合理变化：

说明原因，不要为了让数字好看硬凑 35。

---

# 25. 最终代码质量 Review

必须重新检查：

## Architecture

- module boundary
- dependency direction
- common / infra pollution
- gateway business leakage

## File Classification

- misplaced code
- mock leakage
- business logic in utils

## Code Quality

- dead code
- duplicate code
- over-splitting
- under-separation
- conservative code
- unnecessary abstraction

## Frontend

- server state
- Query
- Zustand
- Form
- Router
- Permission
- Error State
- i18n
- Mock residue

## Backend

- Controller / Service / Mapper
- Transaction
- Validation
- Permission
- Error
- Logging
- DB
- Performance

## Security

重新审查所有真实攻击面。

---

# 26. 最终 Verification

至少：

## Frontend

```text
pnpm check-imports
pnpm check-import-casing
pnpm lint
pnpm build
```

按真实 package scripts 调整，不得虚构命令。

## Backend Fast

```text
mvn -B -ntp test
```

## PostgreSQL Contract

执行当前项目真实 profile：

```text
mvn -B -ntp -Ppersistence-verify-pg test
```

或按模块实际需要执行。

必须真的启动 / 连接 PostgreSQL。

不得因为 Docker 不可用而把 SKIPPED 写成 PASS。

## Git

检查：

```text
git status
git diff
```

确认没有：

- 无关修改
- 意外删除
- 密钥
- 本地环境配置
- build output
- generated junk

---

# 27. 最终报告

完成全部实施后创建：

```text
docs/audit/FULL_FUNCTION_IMPLEMENTATION_EXECUTION_REPORT.md
```

不要修改原始 Baseline：

```text
FULL_FUNCTION_IMPLEMENTATION_AUDIT.md
```

最终报告必须包含：

# 1. Executive Summary

- 实施前
- 实施后
- 完成功能数量
- E2E 数量
- Remaining Blocked

# 2. Master Plan Result

| Phase | Planned | Implemented | Result |

# 3. Backend Functional Result

# 4. Frontend Functional Result

# 5. End-to-End Result

# 6. Mock Retirement Result

列出：

| Domain | Before | After | Mock Remaining | Reason |

# 7. Database / Migration Result

# 8. Security Result

# 9. API Contract Result

# 10. Test Result

列出实际命令、测试数量、结果。

# 11. Code Review Findings

按：

- BLOCKER
- HIGH
- MEDIUM
- LOW
- MODERNIZATION

# 12. Review Fixes

说明 Review 发现的问题如何修复。

# 13. Architecture Result

回答：

- 是否仍符合模块边界
- 是否新增不合理抽象
- 是否过度拆分
- 是否存在垃圾代码
- 是否存在 mock leakage

# 14. 2026 Practice Assessment

重新判断：

- Backend
- Frontend
- Security
- DB
- Testing

只评价有证据的项目。

# 15. Things Kept Intentionally

明确哪些原设计保持不动以及原因。

# 16. Remaining Gaps

只能列真实未完成 / blocked 项。

# 17. Final Completion Matrix

| Domain | Feature | Backend | DB | API | Frontend | Security | Tests | E2E | Status |

# 18. Final Recommendation

给出：

```text
READY FOR NEXT PRODUCT PHASE
```

或：

```text
NEEDS REVISION
```

不得在存在 BLOCKER/HIGH 时写 READY。

---

# 28. 最终 Reviewer

最终报告生成后必须再调用：

```text
integration-reviewer
```

做最后一次独立只读 Review。

检查：

- 是否真的实现
- 是否假完成
- 是否 Mock 残留冒充真实
- 是否测试真实运行
- 是否出现架构漂移
- 是否出现安全退化
- 是否过度设计
- 是否遗漏功能
- 报告数字是否虚高

如果 Reviewer 不通过：

修复 → 重测 → 重审。

直到：

```text
FINAL REVIEW: PASS
```

或确实存在：

```text
BLOCKED BY BUSINESS DECISION
BLOCKED BY EXTERNAL DEPENDENCY
```

---

# 29. Git / Commit Policy

本任务：

- 可以查看 Git history/status/diff
- 不得 `git reset --hard`
- 不得 `git clean`
- 不得覆盖用户原有 WIP
- 不得删除不属于本任务的修改
- 不得 commit
- 不得 push

最终由用户决定如何拆分 Commit。

---

# 30. Stop Conditions

正常情况下：

> **不要在每个 Phase 等待用户确认。**

自动推进直到全部计划完成。

只有以下情况暂停：

1. 业务语义存在重大不可推导分歧；
2. 不可逆 DB 决策缺少依据；
3. 需要真实第三方账号 / Key / Secret；
4. 外部服务不可用导致无法真实验证；
5. 发现 CRITICAL 安全问题；
6. 当前 WIP 与目标修改发生无法安全合并的冲突。

暂停时必须输出：

```text
BLOCKED
Reason
Evidence
Decision Needed
Safe Options
Recommended Option
```

不要只说“需要确认”。

---

# 31. 最终目标

本轮不是为了：

- 增加文件数量
- 追最新技术
- 把所有 Mock 机械删除
- 把所有 Placeholder 强行做成业务
- 让代码看起来更“架构化”

本轮目标是：

> **在保持当前健康架构的前提下，把有明确产品意图的功能逐域从 Mock / Placeholder 推进为真实、可持久化、有权限、有测试、有前端闭环的功能，并在全过程保持模块职责、修改边界、不过度拆分、必要注释和独立 Review。**

最终衡量标准不是：

```text
改了多少文件
```

而是：

```text
有多少真实功能形成了：
Backend → DB → API → Frontend → Persistence → Tests → Review
```

完整闭环。
