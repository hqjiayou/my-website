# my-website

[繁體中文](README.md) · [日本語](README.ja.md)

一套中央控制的網站集合。一個後台總控（`apps/admin-web`），一個 Java 服務（`services/website-services`），一個 Python AI 執行服務（`services/agent-services`），一個站點用戶端（`apps/agent-portal`）。面向訪客的公開站前台（原 `apps/site-web`）已於 2026-10-28 移除。

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
│   ├── admin-web         # 管理後台
│   └── agent-portal      # 站點用戶端
├── docs                  # 三格：現在 / 規格 / 歷史，見 docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**現在是什麼**看 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文件先看 [`docs/README.md`](docs/README.md)，不要從舊總覽翻起。未完成工作在 GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) 只留 Issue 號）。表級字典在 [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 子專案職責

### apps/agent-portal（站點用戶端）

站點用戶端（登入 / AI 對話）。公開訪客前台 `apps/site-web/` 已於 2026-10-28 移除，不要再找它。

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

請求都走同一條 gateway。登入在 auth：`/api/auth/admin/**`、`/api/auth/agent/**`。`/me` 不在 auth，而在對應業務服務。

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
- MinIO：`local-dev-minio-1` → API `http://localhost:9000`（容器名以當前 local-stack（compose 專案 `local-dev`）為準，細節見 LOCAL_DEVELOPMENT §1）

應用在 **`services/website-services/.env`**（範本 `.env.example`）中設定 JDBC / Redis / MinIO 等；與 **local-stack** 目錄下的 `.env` **分開維護**，也與 **`services/agent-services/.env`** 分開。

更多說明見 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)（原 WSL/Docker 專項文件已於 2026-10-29 移除，見 git 歷史）。
