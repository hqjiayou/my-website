# my-website

[简体中文](README.md) · [繁體中文](README.md)（本文件下半） · [日本語](README.ja.md)

一套中央控制的网站集合。一个后台总控（`apps/admin-web`），一个 Java 服务（`services/website-services`），一个 Python AI 执行服务（`services/agent-services`），一个站点用户端（`apps/agent-portal`）。面向访客的公开站前台（原 `apps/site-web`）已于 2026-10-28 移除。

## 项目定位

- 一个后台总控管理集合里的各个网站。
- 站点用户端是 `apps/agent-portal`（登录 + AI 对话）；站点 API 在 Java `agent-app`（原 site-app，2026-10-28 改名合并）。
- `apps/admin-web` 是这套系统的后台总控，同时管平台和各个站点。
- `services/website-services` 是这套系统的 Java 服务，包含**统一网关**、**独立认证服务（`auth`）**、后台 API、站点 API 与基础设施接入；**JWT 为 RS256 + JWKS（Auth 签发，业务服务 JWKS 验签）**。
- `services/agent-services` 是 **Python 3.13 + FastAPI + Pydantic AI 2.54** 的 AI Execution Runtime（internal only，不经 gateway 暴露）。Java 持有业务真相，Python 只做 AI 执行（ADR-0005）。没有 `AI_MODEL` 时进程仍启动，调用诚实失败（ADR-0004）。

## 目录结构

```text
my-website/
├── apps
│   ├── admin-web         # 管理后台
│   └── agent-portal      # 站点用户端
├── docs                  # 三格：现在 / 规格 / 历史，见 docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**现在是什么**看 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文档先看 [`docs/README.md`](docs/README.md)，不要从旧总览翻起。未完成工作在 GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 号）。表级字典在 [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 子项目职责

### apps/agent-portal（站点用户端）

站点用户端（登录 / AI 对话）。公开访客前台 `apps/site-web/` 已于 2026-10-28 移除，不要再找它。

站点 API 经 `website-services/gateway` 访问。  
**Agent Auth（原 Site Auth）：后端完成（`/api/auth/agent/**` 登录 / 刷新 / 登出，`GET /api/agent/auth/me` 在 agent-app），前端已由 agent-portal 对接**（现状见 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)）。

### apps/admin-web

后台管理系统。主纵向域（Agent / User / Appearance / Media / Content / RBAC）与站点 AI 管理为真实 API。（Template、Reservation 已于 2026-10-27 删除。）无 `AI_MODEL` 时预览诚实失败。Analytics / System 等页仍为占位。

专项文档：

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 的 Maven 多模块后端工程。

核心运行模块：

- `gateway`：统一 API 网关，端口 `8080`
- `auth`：**统一认证服务**（Admin 登录/刷新/登出、JWT 签发等），端口 `8083`
- `admin`：后台管理 API（OAuth2 Resource Server + 业务），端口 `8081`
- `agent/agent-app`：站点业务 API，端口 `8082`

基础与预留模块：

- `common`：通用响应、错误码、异常、分页；**`common.security`** JWT/身份轻量契约
- `infra`：PostgreSQL、Redis、MinIO / S3 接入、MyBatis-Plus、Flyway、**系统消息 i18n** 等
- `agent/agent-core`：站点共享实体与 Mapper（用户、内容、站点 AI）＋ `HttpAgentClient` 与 Java↔Python 契约 DTO（原 site-core 与 agent 客户端库于 2026-10-28 合并至此）

专项文档：

- [`docs/website-services/README.md`](docs/website-services/README.md)（文档落在哪一格）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（跳转到架构正文）
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/)（表级字典，与 Flyway 对齐）
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 的 AI Execution Runtime，默认端口 `3001`。禁止 LangChain / LiteLLM。

内部接口（需 `X-Internal-Service-Token`，只由 Java `HttpAgentClient` 调用）：

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream`（SSE）

公开探活：`GET /health`（只表示进程存活）。缺 `AGENT_INTERNAL_SERVICE_TOKEN` 时拒绝启动；缺 `AI_MODEL` 时仍启动，chat 返回 503。

详见 [`services/agent-services/README.md`](services/agent-services/README.md)（原 Agent 设计文档已于 2026-10-29 移除，见 git 历史）。

## 后端调用关系

推荐请求路径：

```text
admin-web ／ agent-portal ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（含 GET /api/admin/auth/me）
                                              └──> /api/agent/**  → agent-app:8082（含 GET /api/agent/auth/me）
```

请求都走同一条 gateway。登录在 auth：`/api/auth/admin/**`、`/api/auth/agent/**`。`/me` 不在 auth，而在对应业务服务。

内部 Maven 依赖关系（非 HTTP 拓扑）：

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent-core（HttpAgentClient） -> services/agent-services
```

当前阶段不引入注册中心、服务发现、消息队列或复杂网关鉴权。`gateway` 做统一入口、CORS、request-id 与路由。`trusted-proxies` 为 `(?!)`，出站 `X-Forwarded-For` 写成 TCP 对端。auth 限流读这个头的最后一段。见 [`docs/operations/SECURITY.md`](docs/operations/SECURITY.md)。

## 启动命令

完整步骤（local-stack 账号对齐、两份 `.env`、JWT 路径、健康检查）见 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先启动 local-stack（当前环境容器名为 `local-dev-postgres-1` / `local-dev-redis-1`，见 LOCAL_DEVELOPMENT §1）。`services/website-services/.env` 的 `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 必须与容器一致；`.env.example` 的 `mywebsite` / `CHANGE_ME` 不能直接拿来连当前库。`services/agent-services/.env` 单独一份，`AGENT_INTERNAL_SERVICE_TOKEN` 与 Java 侧相同，缺了进程会拒绝启动。

启动 `website-services`（四个终端）：

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl agent/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

启动前端（两个终端）：

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

启动 `agent-services`：

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 配置 AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

本地基础设施见本文件末尾「本地基础设施」节与 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

---

# my-website

[简体中文](README.md) · [繁體中文](README.md)（本文件下半） · [日本語](README.ja.md)

一套中央控制的網站集合。一個後台總控（`apps/admin-web`），一個 Java 服務（`services/website-services`），一個 Python AI 執行服務（`services/agent-services`），一個站點用戶端（`apps/agent-portal`）。公開訪客前台（原 `apps/site-web`）已於 2026-10-28 移除。

## 專案定位

- 一個後台總控管理集合裡的各個網站。
- 站點用戶端是 `apps/agent-portal`（登入 + AI 對話）；站點 API 在 Java `agent-app`（原 site-app，2026-10-28 改名合併）。
- `apps/admin-web` 是這套系統的後台總控，同時管平台和各個站點。
- `services/website-services` 是這套系統的 Java 服務，包含**統一閘道**、**獨立認證服務（`auth`）**、後台 API、站點 API 與基礎設施接入；**JWT 為 RS256 + JWKS（Auth 簽發，業務服務 JWKS 驗簽）**。
- `services/agent-services` 是 **Python 3.13 + FastAPI + Pydantic AI 2.54** 的 AI Execution Runtime（internal only，不經 gateway 暴露）。Java 持有業務真相，Python 只做 AI 執行（ADR-0005）。沒有 `AI_MODEL` 時行程仍啟動，呼叫誠實失敗（ADR-0004）。

## 目錄結構

```text
my-website/
├── apps
│   └── admin-web         # 當前工作區只有管理後台
├── docs                  # 三格：現在 / 規格 / 歷史，見 docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**現在是什麼**看 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文件先看 [`docs/README.md`](docs/README.md)，不要從舊總覽翻起。未完成工作在 GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 號）。表級字典在 [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 子專案職責

### 訪客前台

站點用戶端是 `apps/agent-portal`（登入 / AI 對話）。公開訪客前台 `apps/site-web/` 已於 2026-10-28 移除。

站點 API 經 `website-services/gateway` 存取。  
**Agent Auth（原 Site Auth）：後端完成（`/api/auth/agent/**` 登入 / 重新整理 / 登出，`GET /api/agent/auth/me` 在 agent-app），前端已由 agent-portal 對接**（現狀見 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)）。

### apps/admin-web

後台管理系統。主縱向域（Agent / User / Appearance / Media / Content / RBAC）與站點 AI 管理為真實 API。（Template、Reservation 已於 2026-10-27 刪除。）無 `AI_MODEL` 時預覽誠實失敗。Analytics / System 等頁仍為佔位。

專項文件：

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 的 Maven 多模組後端工程。

核心執行模組：

- `gateway`：統一 API 閘道，連接埠 `8080`
- `auth`：**統一認證服務**（Admin 登入/重新整理/登出、JWT 簽發等），連接埠 `8083`
- `admin`：後台管理 API（OAuth2 Resource Server + 業務），連接埠 `8081`
- `agent/agent-app`：站點業務 API，連接埠 `8082`

基礎與預留模組：

- `common`：通用回應、錯誤碼、例外、分頁；**`common.security`** JWT/身分輕量契約
- `infra`：PostgreSQL、Redis、MinIO / S3 接入、MyBatis-Plus、Flyway、**系統訊息 i18n** 等
- `agent/agent-core`：站點共享實體與 Mapper（使用者、內容、站點 AI）＋ `HttpAgentClient` 與 Java↔Python 契約 DTO（原 site-core 與 agent 客戶端庫於 2026-10-28 合併至此）

專項文件：

- [`docs/website-services/README.md`](docs/website-services/README.md)（文件落在哪一格）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（跳轉到架構正文）
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/)（表級字典，與 Flyway 對齊）
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 的 AI Execution Runtime，預設連接埠 `3001`。禁止 LangChain / LiteLLM。

內部介面（需 `X-Internal-Service-Token`，只由 Java `HttpAgentClient` 呼叫）：

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream`（SSE）

公開探活：`GET /health`（只表示行程存活）。缺 `AGENT_INTERNAL_SERVICE_TOKEN` 時拒絕啟動；缺 `AI_MODEL` 時仍啟動，chat 回傳 503。

詳見 [`services/agent-services/README.md`](services/agent-services/README.md)（原 Agent 設計文件已於 2026-10-29 移除，見 git 歷史）。

## 後端呼叫關係

建議請求路徑：

```text
admin-web ／ agent-portal ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（含 GET /api/admin/auth/me）
                                              └──> /api/agent/**  → agent-app:8082（含 GET /api/agent/auth/me）
```

請求都走同一條 gateway。登入在 auth：`/api/auth/admin/**`、`/api/auth/site/**`。`/me` 不在 auth，而在對應業務服務。

內部 Maven 依賴關係（非 HTTP 拓樸）：

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent-core（HttpAgentClient） -> services/agent-services
```

當前階段不引入註冊中心、服務發現、訊息佇列或複雜閘道鑑權。`gateway` 做統一入口、CORS、request-id 與路由。`trusted-proxies` 為 `(?!)`，出站 `X-Forwarded-For` 寫成 TCP 對端。auth 限流讀這個標頭的最後一段。見 [`docs/operations/SECURITY.md`](docs/operations/SECURITY.md)。

## 啟動命令

完整步驟（local-stack 帳號對齊、兩份 `.env`、JWT 路徑、健康檢查）見 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先啟動 local-stack（當前環境容器名為 `local-dev-postgres-1` / `local-dev-redis-1`，見 LOCAL_DEVELOPMENT §1）。`services/website-services/.env` 的 `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 必須與容器一致；`.env.example` 的 `mywebsite` / `CHANGE_ME` 不能直接拿來連當前庫。`services/agent-services/.env` 單獨一份，`AGENT_INTERNAL_SERVICE_TOKEN` 與 Java 側相同，缺了行程會拒絕啟動。

啟動 `website-services`（四個終端）：

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl agent/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

啟動前端（兩個終端）：

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

啟動 `agent-services`：

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 設定 AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## 本地基礎設施

後端本地依賴統一使用既有 **local-stack**（不在 `website-services` 內再起第二套 Postgres/Redis）：

- PostgreSQL：`local-dev-postgres-1` → `localhost:5432`
- Redis：`local-dev-redis-1` → `localhost:6379`
- MinIO：`local-dev-minio-1` → API `http://localhost:9000`（容器名以當前 local-stack（compose 項目 `local-dev`）為準，細節見 LOCAL_DEVELOPMENT §1）

應用在 **`services/website-services/.env`**（範本 `.env.example`）中設定 JDBC / Redis / MinIO 等；與 **local-stack** 目錄下的 `.env` **分開維護**，也與 **`services/agent-services/.env`** 分開。

更多說明見 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)（原 WSL/Docker 专项文档已于 2026-10-29 移除，见 git 历史）。


```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 配置 AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## 本地基础设施

后端本地依赖统一使用既有 **local-stack**（不在 `website-services` 内再起第二套 Postgres/Redis）：

- PostgreSQL：`local-dev-postgres-1` → `localhost:5432`
- Redis：`local-dev-redis-1` → `localhost:6379`
- MinIO：`local-dev-minio-1` → API `http://localhost:9000`（容器名以当前 local-stack（compose 项目 `local-dev`）为准，细节见 LOCAL_DEVELOPMENT §1）

应用在 **`services/website-services/.env`**（范本 `.env.example`）中配置 JDBC / Redis / MinIO 等；与 **local-stack** 目录下的 `.env` **分开维护**，也与 **`services/agent-services/.env`** 分开。

更多说明见 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)（原 WSL/Docker 专项文档已于 2026-10-29 移除，见 git 历史）。
