# 阶段 4A · Admin 认证体系决策确认单

本文档从 [阶段4A-Admin认证体系详细设计](./阶段4A-Admin认证体系详细设计.md) 提炼 **可供最终拍板的决策项**，用于进入 **4B 实现前定稿**。  
（本文不写代码，仅作决策对齐。）

---

## 1. 当前推荐总方案摘要

**推荐路线：** Admin API 采用 **Spring Security** 作为正式安全框架：**口令**使用 **`PasswordEncoder`（BCrypt / DelegatingPasswordEncoder）**；业务请求采用 **短命 Access JWT** 做 **无状态认证**（Bearer）；**Refresh Token** 与 Access **分离**，Refresh 采用 **不透明随机串**，状态落在 **Redis**（绑定 subject / jti，支持轮换与吊销）；**不**以服务端 HTTP Session 为主模型。**完整 RBAC 授权（路由级权限、菜单）后置到 4B 之后或与 4B 后半并行**，4B 聚焦认证闭环（登录 / 刷新 / 登出）与最小主体注入。

---

## 2. 关键设计决策表

| 决策项 | 推荐方案 | 备选方案 | 推荐理由 | 是否仍需确认 |
| --- | --- | --- | --- | --- |
| 1. 密码加密 | **`BCryptPasswordEncoder`**（或通过 **`DelegatingPasswordEncoder`** 预留 `{bcrypt}` 前缀以利轮换） | 自建哈希、明文或可逆加密 | Spring Security 官方路径；摘要仅存 DB，`matches` 校验 | 否 |
| 2. 登录账号标识（V1） | **`username`** 登录（与现有 **`admin_user.username`** 及设计文档登录流程一致） | V1 同时支持 email / phone 登录 | 表结构与 4B 工作量可控；扩展标识需迁移与唯一约束策略 | **是**（若产品坚持首期支持邮箱/手机号登录） |
| 3. Access Token 类型 | **JWT**（`aud=ADMIN`，`sub=admin_user.id`） | 不透明 Session Id（偏离当前 JWT 骨架） | 与 **`auth`** 模块 **`TokenAudience` / JwtProperties`** 一致 | 否 |
| 4. Access Token 生命周期 | **15～30 分钟**（默认可取 **30 分钟**） | &lt;15 分钟（交互差）或 &gt;1 小时（泄露窗口大） | 设计文档建议区间；可按环境覆盖 | **是**（选定具体分钟数与环境差异） |
| 5. Refresh Token 类型 | **不透明随机串**（UUID / CSPRNG Base64URL） | **JWT 形态的 Refresh** | 吊销与 TTL 由 Redis 掌控简单；JWT Refresh 黑名单成本高 | 否（除非团队强制 JWT-only） |
| 6. Refresh Token 生命周期 | **7～30 天**（默认可取 **14 天**） | 永久 Refresh | 平衡用户体验与被盗窗口；具体天数属产品策略 | **是**（选定天数） |
| 7. Refresh 是否存 Redis | **是**（主存储：`admin:auth:refresh:{jti}`） | 仅存 DB | 高性能校验、TTL 原生、轮换原子性好 | 否 |
| 8. Refresh Rotate | **启用轮换**（每次刷新作废旧 refresh，下发新 refresh + 新 access） | 固定 Refresh 长期使用 | 降低 refresh 泄露后的长期风险；与设计文档一致 | **是**（是否启用「重用旧 refresh → 吊销全家桶」风控） |
| 9. 登出失效 | **删除 Redis 中该会话 refresh**；可选 **`admin:auth:logout:access:{jti}`** 黑名单至 Access `exp` | 仅客户端删 token | 服务端可控；与设计文档 §8.3 一致 | **是**（4B 是否实现 Access 黑名单，默认可不做） |
| 10. Access 黑名单 | **默认不做**（靠短命 JWT）；敏感场景可选 **`logout:access:{jti}`** | 每次校验查 Redis | 避免每笔请求依赖 Redis；黑名单仅补偿「登出后 remainder TTL」 | **是**（4B 是否纳入 MVP） |
| 11. 多端登录 | **允许多端并行**（每端一对 refresh jti）；可选「上限 N」或「新登录踢旧设备」 | **强制单端**（全局唯 refresh） | 后台常见多浏览器/工作台；单端需额外产品与排队逻辑 | **是** |
| 12. Redis Key 结构 | 前缀 **`admin:auth:`**；**`refresh:{jti}`**、可选 **`logout:access:{jti}`**、可选 **`ratelimit:login:{ip}`** | 无前缀扁平 key | 隔离环境与其他域；与设计文档 §7 一致 | 否 |
| 13. JWT Claims（Access） | **`sub`、`aud`、`iat`、`exp`**；可选 **`jti`**；业务侧 **`username`**；**`roles` 精简枚举**（若入 JWT） | 大体量自定义 claims | 保持令牌小；时钟与受众标准 | **是**（是否强制含 `jti`） |
| 14. role / permission 是否入 Access JWT | **4B 推荐：`roles` 少量入 JWT；`permissions` 不入**（偏大则服务端加载） | 全量权限入 JWT | 4B 以认证为主；权限体量增长后不宜塞进 JWT | **是**（首批 Admin 接口是否仅需角色就够了） |
| 15. Spring Security 实现路径 | **`spring-boot-starter-oauth2-resource-server`** + JWT（**`JwtDecoder`**） | **自定义 `OncePerRequestFilter` + JwtDecoder** | 官方对齐 JWT 校验；自定义留给错误体/上下文绑定补强 | **是**（若 401 体必须与全局 `ApiResponse` 完全一致，评估过滤器扩展点） |
| 16. Gateway 与 admin 认证边界 | **模式 A**：Gateway **不校验 JWT**，仅路由；**admin** 完整校验（密钥不外泄） | **模式 B**：Gateway **`JwtDecoder`（公钥）** 预校验 | 起步简单、密钥面小；B 适合高流量前置拒绝 | **是**（4B 是否坚持模式 A） |
| 17. **`auth` 模块（4B）职责** | **`JwtProperties`、`TokenAudience`、Claims 映射、`AuthenticatedUser`、`AuthContext`、可选 `JwtEncoder`/解码适配 Bean**；**不**放 `AdminUserEntity` | 把登录全塞进 `admin` 且 `auth` 空心化 | 保持 JWT 语义复用 Site 潜力；实体留在 `admin` | 否 |
| 18. **`admin` 模块（4B）职责** | **Spring Security 配置、登录/刷新/登出端点、`PasswordEncoder`、`UserDetails`/Principal 适配、Refresh 读写 Redis、调用 `admin_user` Mapper** | 把 Redis 细节放进 `auth` | 会话/存储是应用边界；`auth` 保持库级无业务 | 否 |

---

## 3. 必须由用户拍板的开放问题

以下问题在详细设计中**仍有两可或强产品属性**，建议在 4B 开工前书面定案。

### 问题 1：Access / Refresh 的下发载体（Body vs Cookie）

- **方案 A：** Access、Refresh 均 JSON Body 返回（前端自行存储策略）。
- **方案 B：** Access 仍 Body 或 **`Authorization: Bearer`**；**Refresh 仅 HttpOnly + Secure Cookie**（防 XSS 带票）。
- **当前文档更推荐：** **方案 B**（至少 Refresh 走 HttpOnly Cookie）。
- **为什么需要现在拍板：** 影响 **Admin Web** 调用方式、**CORS**、`Set-Cookie` 域与 **Gateway** 转发头行为。
- **若不拍板会影响 4B 哪部分代码：** **`admin` 登录/刷新接口的响应契约**、Spring Security **CORS/Cookie** 配置、可能与 **Gateway** 的 `SameSite`/代理头约定。

### 问题 2：JWT 对称（HS256）与非对称（RS256）及密钥轮转

- **方案 A：** **HS256**（单密钥配置简单，适合单机与小团队）。
- **方案 B：** **RS256**（私钥签发、公钥校验，利于多实例与将来 **Gateway 模式 B**）。
- **当前文档更推荐：** **4B 先 HS256**，密钥环境变量注入；路线图预留 **RS256**。
- **为什么需要现在拍板：** 决定 **`JwtEncoder`/`JwtDecoder` Bean** 形态与密钥管理方式。
- **若不拍板会影响 4B 哪部分代码：** **`auth` 与 `admin` 安全 Bean 装配**、配置属性命名、后续网关验签扩展成本。

### 问题 3：权限是否写入 Access JWT（roles / permissions）

- **方案 A：** JWT **仅 `sub` + `username` + 最小 `roles`**；细粒度 **`permissions`** 每次请求 DB 或缓存加载。
- **方案 B：** **roles + permissions** 全量嵌入 JWT。
- **当前文档更推荐：** **方案 A**。
- **为什么需要现在拍板：** JWT 体积、`exp` 更新频率与 **权限变更生效延迟**。
- **若不拍板会影响 4B 哪部分代码：** **签发 Claims 构建器**、是否引入 **权限缓存层**；若选 B，可能倒逼 **短时 Access + 频繁刷新**。

### 问题 4：多端 Refresh 策略（并行上限 / 踢下线）

- **方案 A：** **多端并行**，每 refresh 一条 Redis；不设上限（V1）。
- **方案 B：** **单端**：新登录吊销用户维度全部 refresh。
- **方案 C：** **并行但上限 N**，FIFO 或 LRU 淘汰最旧会话。
- **当前文档更推荐：** **方案 A（V1）**，后续再上 **方案 C**。
- **为什么需要现在拍板：** Redis **key 维度与写入逻辑**、登出时删除一条还是全家桶。
- **若不拍板会影响 4B 哪部分代码：** **登录与刷新时 Redis 写入策略**、可选「吊销全部 refresh」接口语义。

### 问题 5：4B 是否在网关校验 JWT（模式 A vs B）

- **方案 A：** **Gateway 不校验**，仅 **admin** 校验（文档默认）。
- **方案 B：** **Gateway** 增加 **`JwtDecoder`（公钥）** 预校验。
- **当前文档更推荐：** **方案 A（4B）**。
- **为什么需要现在拍板：** 是否引入 **网关依赖密钥分发**、错误响应是否在边缘统一。
- **若不拍板会影响 4B 哪部分代码：** **gateway 模块依赖与安全配置**（若选 B）；否则 **gateway 零改动**。

### 问题 6：登录标识是否仅限 username（V1）

- **方案 A：** **仅 `username`** 登录。
- **方案 B：** V1 即支持 **email / phone**（需唯一约束与查询路径）。
- **当前文档更推荐：** **方案 A**。
- **为什么需要现在拍板：** **Mapper 查询路径**、错误消息是否泄露标识类型。
- **若不拍板会影响 4B 哪部分代码：** **登录 Service 查询逻辑**；若选 B，可能牵涉 **Flyway 与数据字典**（超出纯认证时可切开）。

---

## 4. 建议最终拍板方案（闭环）

以下为基于现有详细设计的 **一体化推荐**（用于评审默认选项）。

| 维度 | 建议 |
| --- | --- |
| **登录方式** | **`username + password`** → **`PasswordEncoder.matches`** → 查 **`admin_user`**（过滤逻辑删除与非 ACTIVE）。 |
| **双令牌模型** | **Access JWT**（Bearer，15～30 min，建议 **30 min**）；**Refresh 不透明串**（建议 **14 d TTL**），**仅存 Redis**。 |
| **Redis 保存内容** | **`admin:auth:refresh:{jti}`**（绑定 `sub`、签发时间、轮换链可选）；可选 **`logout:access:{jti}`**（若 MVP 要做登出后立即作废 Access）；可选 **`ratelimit:login:{ip}`**（可延后）。 |
| **登出** | **删除对应 refresh**；若产品要求「登出后 access 立刻作废」，对该 access **`jti`** 写入短期黑名单（TTL = access 剩余寿命）。 |
| **多端策略** | **默认多端并行**（每终端独立 refresh）；**轮换**每次刷新换新 refresh；是否「reuse 旧 refresh → 吊销全部」作为可选风控开关后续开启。 |
| **Spring Security** | **`PasswordEncoder` + OAuth2 Resource Server JWT（`JwtDecoder`）**；Principal/`Authentication` 映射 **`AuthenticatedUser`** + **`AuthContext`**。 |
| **权限是否入 Token** | **仅少量 `roles` claims**；**`permissions` 不放 JWT**，后续 RBAC 由服务端加载或缓存。 |
| **Gateway / admin** | **4B 采用模式 A**：Gateway **不做 JWT 校验**；**admin** 独占密钥与校验逻辑。 |

---

## 5. 阶段 4B 实施范围建议（确认方案后）

以下按模块归类 **建议开发内容**（不写具体接口签名与代码）。

### 5.1 `auth` 模块

- 扩展/固化 **`JwtProperties`**（issuer、access TTL、算法与密钥引用）。
- Access JWT **签发与解码 Bean**（与所选 HS256/RS256 一致）。
- **`TokenClaims` → `AuthenticatedUser`** 映射工具；与 **`AuthContext`** 绑定约定。
- **不**引入 `AdminUserEntity`；**不**直连 Redis。

### 5.2 `admin` 模块

- **Spring Security 配置**：登录/刷新/登出路径放行规则；其余 **`/api/admin/**`** JWT 认证。
- **`PasswordEncoder` Bean**；登录流 **`admin_user` 查询 + matches`**。
- **Refresh**：生成不透明串、Redis 读写、**轮换**、登出删除。
- **Controller（认证三类端点）**、 thin **Service**、DTO（与确认后的 Body/Cookie 契约一致）。
- **`JwtAuthenticationToken` → AuthContext** 填充（或与 Resource Server 默认流程对齐）。

### 5.3 `infra` / Redis 支撑

- **RedisTemplate/StringRedisTemplate**（若已有则复用）与 **`admin:auth:`** key 序列化约定。
- **TTL** 与 key 命名与运维对齐（可与 **`spring.cache.redis`** 区分）。

### 5.4 Swagger / 文档

- **springdoc**：登录/刷新/登出分组与安全 scheme（Bearer）；说明 Cookie 场景下的 **`authorize`** 局限（若 Refresh 走 Cookie）。
- **数据字典 / 数据库模型**：若新增 **`last_login_at`** 等字段，走 **Flyway + 字典同步**（可作为 4B 子任务或 4B+）。

### 5.5 测试

- **单元测试**：`PasswordEncoder`、`Jwt` 签发/校验边界（过期、错误 aud）。
- **集成测试**：**MockMvc / `@SpringBootTest`** 覆盖登录成功、错误口令、刷新轮换、登出后刷新失效（**不接真实 Redis 可用 Testcontainers Redis 或嵌入式替身——具体选型属实现细节，可由 4B 技术任务书规定**）。
- **持久层**：若有字段迁移，跑 **`my_website_dev`** 或现有回归脚本策略。

---

## 6. 暂缓到后续阶段的内容

以下内容 **不建议纳入 4B MVP**，除非决策单另行勾选：

- **完整 RBAC**：路由级 **`@PreAuthorize` 全覆盖**、权限缓存失效策略。
- **菜单权限 / 前端按钮级权限**。
- **登录失败锁定 / 递增延迟 / CAPTCHA**。
- **多因素认证（MFA）**。
- **Site 前台认证**（`TokenAudience.SITE` 独立里程碑）。
- **全链路操作审计系统**（谁在何时改了什么——独立审计域）。
- **OAuth2 第三方登录 / 扫码登录**。
- **Gateway 模式 B（JWT 边缘校验）**（可作为 4C/网关硬化项）。
- **密码找回 / 邮箱验证**。

---

## 相关文档

- [阶段4A-Admin认证体系详细设计](./阶段4A-Admin认证体系详细设计.md)
