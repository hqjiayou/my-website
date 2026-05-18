# 持久层真实 PostgreSQL 验证说明

## 前言与历史

本仓库将 **Entity / Mapper / MyBatis-Plus / Flyway** 与真实 **PostgreSQL** 联通做一次固定回归验证：**插入与查询、主键回填、`created_at` / `updated_at` 与触发器、partial unique + 逻辑删除、外键与按 `site_id` 查询、乐观锁**等。集成测试基于 **Spring Boot**，**不使用 Testcontainers**。

**历史说明：**该验证能力最初在「阶段 3C」落地并完成一次性核验；现已改为 **长期基础设施**，命名与文档均以「持久层真实 PostgreSQL 回归验证」为维护口径，而非阶段性临时产物。

---

## 何时应运行本验证

建议在下列时机执行（任选其一即有价值）：

- 变更 **`infra`** 下 Flyway migration、或调整全局 MyBatis-Plus / 数据源配置之后；
- 变更 **`admin`** / **`site-core`** 首批持久层 Entity、Mapper 映射或与审计、乐观锁、逻辑删除相关的约定之后；
- 发布或合并前做本地最后一公里确认（需本机 Postgres 与验证库就绪）。

日常 **`mvn test`** **不会**连接真实 PostgreSQL（Surefire 默认排除 JUnit Tag **`verifyPg`**），避免 CI 或未就绪环境失败。

---

## 验证环境与临时库

### 数据库名（保持不变）

默认 JDBC URL 指向专用临时库：**`my_website_3c_verify`**（与历史脚本一致；仅「脚本文件名」与「Maven Profile」长期化，**未改库名**，以免打乱既有本地环境）。

### 创建验证库

1. PostgreSQL 可达（默认 `localhost:5432`）。
2. 使用具备 **`CREATEDB`** 的角色连接维护库 **`postgres`**，创建 **`my_website_3c_verify`**；Owner 建议与 JDBC 登录用户一致（与 **`website-services/.env`** 中 **`POSTGRES_USER`** / **`VERIFY_PG_USER`** 对齐；**`.env.example`** 范本默认为 **`mywebsite`**）。

**Docker（local-stack / `local-postgres`）示例**：

```bash
# 用户名与 OWNER 请与 website-services/.env 中 Postgres 登录用户一致（范本默认为 mywebsite）
docker exec -i local-postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_3c_verify OWNER mywebsite ENCODING 'UTF8';"
```

若容器名不同，请以 **`docker ps`** 为准；也可改用宿主机 **`psql`** 直连 **`localhost:5432`** 执行等价 **`CREATE DATABASE`**。

**SQL 文件（与本仓库脚本命名一致）：**

- [`services/website-services/scripts/create-my_website_persistence_verify.sql`](../../../services/website-services/scripts/create-my_website_persistence_verify.sql)

详细副本命令见 [`services/website-services/scripts/README.md`](../../../services/website-services/scripts/README.md) 中「持久层验证库」小节。

---

## Flyway 如何执行

迁移脚本 **唯一权威路径**：**`infra/src/main/resources/db/migration/`**，运行时 classpath 为 **`classpath:db/migration`**。

Spring Profile **`verify-pg`**（见 **`admin`** / **`site-app`** 的 `src/test/resources/application-verify-pg.yml`）下 **`spring.flyway.enabled=true`**。首次连接 **`my_website_3c_verify`** 时会执行迁移；后续在同一库上校验时 Flyway 应报告 schema 已是最新。

**勿**在 **`admin`、`site-app` 等业务模块**再复制同名 **`V1__...`**，否则易产生 classpath 上重复版本（见下文排查）。

---

## Maven Profile 与命令

根聚合 **`services/website-services/pom.xml`**：

- **默认**：Surefire **`excludedGroups=verifyPg`**；
- Profile **`persistence-verify-pg`**：Surefire **`groups=verifyPg`**，仅运行带 **`@Tag("verifyPg")`** 的测试。

**推荐命令（含清理，避免 `target/` 残留资源导致 classpath 漂移）：**

```bash
cd services/website-services
mvn clean test -Ppersistence-verify-pg -pl admin,site/site-app -am
```

### 为何建议使用 `mvn clean`

增量编译时，历史 **`target/classes`** 可能仍带有已从源码删除的资源（例如早期误放在 **`admin`** 下的 **`db/migration/V1__...`**）。Flyway 会扫描 classpath 上 **全部** migration，若同一 **`V1`** 出现两份即报错。**`mvn clean`** 清空各模块 **`target/`**，与当前仓库源码一致后再跑测试。

### 为何 `-am` 不可省略

`-pl admin,site/site-app` 只选中这两个模块时，若不 **`-am`（also-make）**，Reactor **不会**自动先构建 **`infra`、`common`** 等上游模块，易导致 **编译失败**（缺少依赖）或 classpath 不完整。**务必保留 `-am`**。

---

## 回归测试类（Surefire）

使用 **`Test`** 类名后缀（**`*IT`** 默认划归 **Failsafe**，与本验证使用的 Surefire + Tag 策略不一致）。

| 模块 | 类 |
|------|-----|
| admin | `com.mywebsite.admin.AdminUserPersistenceVerifyPgTest` |
| site-app | `com.mywebsite.site.persistence.SitePersistenceVerifyPgTest` |

二者均标注 **`@Tag("verifyPg")`**，测试逻辑聚焦持久层基础行为，**不**替代业务用例测试。

---

## 环境变量（可选）

**`application-verify-pg.yml`** 解析顺序：**`VERIFY_PG_*` → 环境变量 `POSTGRES_USER` / `POSTGRES_PASSWORD` → 字面默认值 `hqjiayou`**（历史默认；本地请以 **`website-services/.env`** 与 **`POSTGRES_*`** 为准）。

| 变量 | 含义 | 未设置时的回落 |
|------|------|----------------|
| `VERIFY_PG_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/my_website_3c_verify` |
| `VERIFY_PG_USER` | 用户名 | `${POSTGRES_USER}`，再默认 `hqjiayou` |
| `VERIFY_PG_PASSWORD` | 密码 | `${POSTGRES_PASSWORD}`，再默认 `hqjiayou` |

在 IDE 或未加载 `.env` 的终端中运行 Maven 时，请保证 **`VERIFY_PG_*`** 或导出的 **`POSTGRES_*`** 与本机 Postgres **实际口令一致**。

---

## 故障排查

### `database "my_website_3c_verify" does not exist`

按上文创建库，或执行 **`create-my_website_persistence_verify.sql`** / README 中等价 **`CREATE DATABASE`**。

### Connection refused

启动 PostgreSQL（例如启动 **local-stack** / **`local-postgres`**），确认 **`localhost:5432`** 可达。

### `password authentication failed`（SQLState **28P01**）

用户或口令与实例不符：对齐 **`VERIFY_PG_*`** / **`POSTGRES_*`** 与 **`website-services/.env`**（以及实际 **local-postgres** 账号）。

### `Found more than one migration with version 1`

**典型根因：** classpath 上存在 **重复的 `V1` migration**，例如：

- **正确**：**`infra`** 打包路径下的 **`classpath:db/migration/V1__...`**；
- **干扰**：某模块 **`target/classes/db/migration/`** 中的 **历史残留**（源码已删但 **`target/`** 未清理）。

**处理：**

1. 执行 **`mvn clean`**；
2. 再跑 **`mvn clean test -Ppersistence-verify-pg -pl admin,site/site-app -am`**；
3. 确认 **仅 `infra`** 持有 migration 源文件，**勿**在各应用模块复制同名版本脚本。

**说明：**Flyway 脚本以当前仓库 **`infra/.../db/migration`** 为规范路径；**`target/`** 残留会导致 classpath「漂移」。**临时验证库**可安全 **`DROP DATABASE`** 后重建；**正式开发库 `my_website_dev` 不要随意 drop**。

### Flyway / 验证库迁移状态错乱

仅限 **验证库** 时，可删除后重建 **`my_website_3c_verify`** 再执行本验证。

---

## 验证项清单（回归范围）

下列能力由 **`AdminUserPersistenceVerifyPgTest`** / **`SitePersistenceVerifyPgTest`** 覆盖（具体以测试代码为准）：

| 验证项 | 说明 |
|--------|------|
| `admin_user` 基础 CRUD / 主键回填 | MP `insert` 后主键与时间戳字段 |
| `created_at` / `updated_at` | 插入后即存在；更新触发器推进 `updated_at` |
| `admin_user.username` partial unique | 物理重复插入失败；逻辑删除后可同名再插入 |
| `admin_user` 乐观锁 | `version` 递增；陈旧版本 `updateById` 影响行数为 0 |
| `site_user` 外键 | 非法 `site_id` 插入失败 |
| 按 `site_id` 查询 `site_user` | `lambdaQuery` 与数据一致 |
| `site` 乐观锁 | 与 admin 侧同类断言 |

---

## 相关文档

- [`services/website-services/README.md`](../../../services/website-services/README.md) · 工程说明与文档索引（含本验证入口）
- [`services/website-services/scripts/README.md`](../../../services/website-services/scripts/README.md) · 验证库创建脚本说明
