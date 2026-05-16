# website-services 企业级后端基础架构查漏补缺审查

审查时间：2026-05-16

## 1. 审查结论摘要

当前 `services/website-services` 的基础架构骨架整体符合既定方向：Maven 多模块关系完整，`gateway/admin/site-app` 三个可启动应用边界清晰，`site` 已作为聚合模块管理前台子模块，`auth` 未承载具体用户业务，`infra` 集中接入 PostgreSQL、Redis、MinIO，且本地配置通过环境变量占位。

本次审查未发现需要立即调整的 POM 注册、端口规划、网关路由或模块依赖硬错误。已自动补齐无需额外决策的问题：在 [`docs/website-services/WSL与Docker环境配置说明.md`](../../docs/website-services/WSL与Docker环境配置说明.md) §3.0 增补与本仓库 Compose 对齐的 `.env` 范本说明，更新 `services/website-services/README.md` 中的环境变量说明，并同步修正仓库根 `README.md` 中旧的 `site-service` 叙述。

当前仍有少量需要人工确认的方向性问题，主要集中在后续基础框架 V1 是否引入 ORM/Mapper、数据库迁移、OpenAPI、认证策略、测试基线与模块内部分层规范。这些问题不应在本次审查中擅自落地。

## 2. 已完成项

| 状态 | 检查项 | 结论 | 证据 |
| --- | --- | --- | --- |
| ✅ 已符合 | 根 Maven 工程 | `website-services` 为 `packaging=pom`，注册 `gateway/common/infra/agent/auth/config/admin/site`。 | `pom.xml` |
| ✅ 已符合 | Gateway 模块独立性 | `gateway` 独立 jar，只依赖 Spring Cloud Gateway WebFlux、Actuator、Test，不依赖 `infra` 或业务模块。 | `gateway/pom.xml` |
| ✅ 已符合 | Gateway 端口 | 端口配置为 `8080`。 | `gateway/src/main/resources/application.yml` |
| ✅ 已符合 | Gateway 路由 | `/api/admin/**` 转发到 `http://localhost:8081`，`/api/site/**` 转发到 `http://localhost:8082`。 | `gateway/src/main/resources/application.yml` |
| ✅ 已符合 | Gateway 基础能力 | 已保留 CORS、request-id、基础请求日志；未承载业务控制器。 | `gateway/src/main/resources/application.yml`、`RequestIdGatewayFilter` |
| ✅ 已符合 | Admin 服务 | `admin` 是独立 Spring Boot 应用，端口为 `8081`。 | `admin/pom.xml`、`admin/src/main/resources/application.yml` |
| ✅ 已符合 | Site App 服务 | `site/site-app` 是唯一前台启动应用，端口为 `8082`。 | `site/site-app/pom.xml`、`site/site-app/src/main/resources/application.yml` |
| ✅ 已符合 | Site 聚合模块 | `site` 为 `packaging=pom`，注册 `site-app/site-core/site-car/site-cake`。 | `site/pom.xml` |
| ✅ 已符合 | Site 子模块关系 | `site-app` 依赖 `site-core/site-car/site-cake`，`site-car/site-cake` 仅依赖 `site-core`。 | `site/site-app/pom.xml`、`site/site-car/pom.xml`、`site/site-cake/pom.xml` |
| ✅ 已符合 | Auth 边界 | `auth` 只包含认证上下文、当前用户抽象、TokenService 接口，没有具体用户表、角色表、登录注册实现。 | `auth/src/main/java` |
| ✅ 已符合 | Admin/Site 用户边界预留 | `admin` 有 `identity` marker，`site-core` 有 `user` marker，没有提前建表或实现业务实体。 | `admin/identity`、`site-core/user` |
| ✅ 已符合 | Infra 定位 | `infra` 集中引入 JDBC、PostgreSQL、Redis、MinIO，并提供 MinIO Client 配置。 | `infra/pom.xml`、`infra/src/main/java` |
| ✅ 已符合 | 本地配置入口 | `admin` 与 `site-app` 均有 `application-local.yml`，默认 profile 为 `local`。 | `admin/src/main/resources`、`site/site-app/src/main/resources` |
| ✅ 已符合 | 本地基础设施端口 | PostgreSQL、Redis、MinIO / AIStor 的默认端口与目标一致。 | `application-local.yml`、`README.md` |
| ✅ 已符合 | README 关键说明 | `website-services/README.md` 已说明模块职责、端口、本地依赖、启动顺序和当前骨架边界。 | `README.md` |
| ✅ 已符合 | 根 `.gitignore` | 已覆盖 Node、Maven target、`.env`、IDE、日志、本地基础设施数据目录；未误忽略 lock 文件或配置模板。 | 仓库根 `.gitignore` |

## 3. 部分完成项

| 状态 | 检查项 | 当前情况 | 建议 |
| --- | --- | --- | --- |
| 🟡 部分符合 | 本地 `.env` 说明或可复制的示例 | README 与 WSL 文档已说明变量名；可复制范本已写在 **`docs/website-services/WSL与Docker环境配置说明.md` §3.0**（开发人员本地创建 `.env`，勿入库）。仓库内不提供 `.env.example`，避免与本机 `.env` 双轨维护。 | 若团队需要统一 Compose 与工作区密钥管理，可考虑后续引入 **direnv** 或在 IDE Run Configuration 中固化同一套变量。 |
| 🟡 部分符合 | 文档一致性 | `website-services/README.md` 与 `docs/整体架构说明.md` 已贴近当前设计。仓库根 `README.md` 审查前仍有旧 `site-service` 描述，本次已更新。`docs/admin-web/架构设计.md` 曾有少量旧称呼。 | 后续在 admin-web API 联调前，统一更新 admin-web 专项文档中的服务命名。 |
| 🟡 部分符合 | 测试基线 | POM 已引入 `spring-boot-starter-test`，但当前未发现 `src/test` 测试用例。 | 是否建立基础 smoke test 属于下一步工程规范选择，本次不擅自新增。 |
| 🟡 部分符合 | 基础异常体系 | `common` 已有 `ApiResponse`、`ErrorCode`、`BusinessException`，`admin/site-app` 有全局异常处理。 | 错误码分层、国际化、trace-id 回传格式需要后续确认。 |

## 4. 明确缺失项

| 状态 | 缺失项 | 影响 | 是否本次修复 |
| --- | --- | --- | --- |
| ❌ 缺失 | 数据库迁移基线 | 当前没有 Flyway/Liquibase 或 SQL 版本管理。进入真实表结构阶段前需要决策。 | 否，需要人工确认是否引入 Flyway。 |
| ❌ 缺失 | ORM / Mapper 选型 | 当前只有 JDBC/DataSource 基础能力，没有 JPA/MyBatis/jOOQ 等持久层策略。 | 否，需要人工确认。 |
| ❌ 缺失 | OpenAPI / Swagger | 当前没有 API 文档自动生成能力。 | 否，需要人工确认。 |
| ❌ 缺失 | 认证策略落地 | 当前只有 auth 抽象，没有 JWT/Session/Cookie/API Key 最终策略。 | 否，需要人工确认。 |
| ❌ 缺失 | 统一测试策略 | 当前没有 controller smoke test、模块依赖边界测试或 build profile 约束。 | 否，需要人工确认测试基线范围。 |

## 5. 潜在设计偏差

| 状态 | 位置 | 观察 | 建议 |
| --- | --- | --- | --- |
| ⚠️ 需要确认 | `agent/pom.xml` | `agent` 当前依赖 `infra`，README 中也记录了该依赖。若 `agent` 未来只是外部 AI 服务客户端，可能不需要直接访问基础设施；若它要缓存、读配置或落审计，则依赖 `infra` 可以接受。 | 保持现状，不擅自调整；进入 Agent V1 前确认职责边界。 |
| ⚠️ 需要确认 | `admin`、`site-app` 启动类 | 两者使用 `scanBasePackages = "com.mywebsite"`。当前 classpath 边界能防止扫描到未依赖模块，但随着模块增加，可能需要更精确的扫描策略。 | V1 可以接受；后续若出现 bean 泄漏，再收紧扫描范围。 |
| ⚠️ 需要确认 | `StorageProperties` | 同时存在 `secure` 与 `useSsl` 两个布尔字段，目前本地配置使用 `secure`，MinIO Client 主要依赖 endpoint。 | 暂不影响运行；后续对象存储封装时统一字段命名。 |
| ⚠️ 需要确认 | `docs/admin-web/架构设计.md` | 仍保留 `site-service`、`agent-service` 等旧称呼。 | 不影响 `website-services` 编译与运行；建议后续统一文档术语。 |

## 6. 当前依赖关系判断

| 模块 | 当前依赖方向 | 判断 |
| --- | --- | --- |
| `gateway` | Spring Cloud Gateway、Actuator | ✅ 合理。没有依赖数据库、Redis、MinIO 或业务模块。 |
| `common` | 无内部依赖 | ✅ 合理。可作为底层通用模型。 |
| `infra` | `common`、JDBC、PostgreSQL、Redis、MinIO | ✅ 合理。集中承载基础设施接入。 |
| `auth` | `common` | ✅ 合理。只做认证抽象。 |
| `agent` | `common`、`infra`、Spring Boot Starter | ⚠️ 可接受但需确认未来职责，避免变成业务编排层或隐藏基础设施访问入口。 |
| `config` | `common`、`infra` | ✅ 合理。网站配置未来需要持久化时可承接。 |
| `admin` | `common`、`infra`、`agent`、`auth`、`config` | ✅ 合理。后台服务作为业务入口可组合基础能力。 |
| `site-core` | `common`、`auth`、`config` | ✅ 合理。承载前台通用能力。 |
| `site-car` / `site-cake` | `site-core` | ✅ 合理。当前只保留行业骨架。 |
| `site-app` | `common`、`infra`、`agent`、`auth`、`config`、`site-*` | ✅ 合理。作为前台启动模块统一装配。 |

## 7. 已自动补齐的问题

| 状态 | 问题 | 修复方式 |
| --- | --- | --- |
| ✅ 已修复 | `services/website-services` 缺少可读入的本地环境变量约定。 | 在 WSL 文档 §3.0 提供与 `docker-compose.yml`、应用一致的 `.env` 范本（本机自用文件 `.gitignore`，不提交示例文件）。 |
| ✅ 已修复 | `website-services/README.md` 对环境变量出处说明不足。 | 在本地运行依赖章节改为指向 **`services/website-services/` 目录下的 `.env`** 与 `docs/website-services/WSL与Docker环境配置说明.md` §3.0。 |
| ✅ 已修复 | 仓库根 `README.md` 仍描述旧的 `site-service`，与当前 `website-services` 架构不一致。 | 更新根 README 为当前 `gateway/admin/site-app` 后端结构。 |

## 8. 需要人工确认后再改的问题

| 状态 | 问题 | 需要确认的内容 |
| --- | --- | --- |
| ⚠️ 需要确认 | ORM / Mapper | 选择 JPA、MyBatis、jOOQ、JdbcClient，或先保持纯 JDBC。 |
| ⚠️ 需要确认 | 数据库迁移 | 是否引入 Flyway；迁移脚本放置在哪些模块；多环境如何管理。 |
| ⚠️ 需要确认 | 认证策略 | JWT、Session、Cookie、前后端分离认证方式、刷新机制和多租户站点隔离策略。 |
| ⚠️ 需要确认 | OpenAPI / Swagger | 是否引入接口文档生成；是否只在 local/dev 环境开放。 |
| ⚠️ 需要确认 | 测试基线 | 是否要求每个启动模块保留 `contextLoads`、controller smoke test、依赖边界测试。 |
| ✅ 已收敛 | 模块内包结构 | **`website-services` 已统一为 Spring Boot 常规包**（`controller` / `mapper` / `po` / `config` 等），**不再**使用 `interfaces` / `application` / `domain` / `infrastructure` 作为模块内一级分层包；见 `docs/website-services/架构设计.md` §8。 |
| ⚠️ 需要确认 | Agent 模块职责 | Java `agent` 是纯客户端，还是允许缓存、审计、知识库转调等基础设施访问。 |

## 9. V1 准入判断

`services/website-services` 已达到“可以进入企业级基础框架 V1 建设”的状态。

建议下一步只进入基础框架 V1，不进入业务开发。V1 优先处理需要架构决策的基础能力，例如持久层选型、迁移脚本策略、认证方案、API 文档、测试基线、错误码规范和模块分层规范。继续保持当前禁止事项：不建业务表、不实现登录注册、不写 car/cake 具体业务、不把 gateway 做成业务编排层。
