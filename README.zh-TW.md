# my-website

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

一套中央控制的網站集合。一個後台總控（`apps/admin-web`），一個 Java 服務（`services/website-services`），一個 Python AI 執行服務（`services/agent-services`）。當前工作區沒有面向訪客的站點前台。

## 專案定位

- 一個後台總控管理集合裡的各個網站。
- 當前工作區只有總控後台。訪客前台不在樹裡，站點 API 在 Java `site-app`。
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

當前工作區沒有面向訪客的站點前台（`apps/site-web/` 不在樹裡）。

站點 API 經 `website-services/gateway` 存取。  
**Site Auth：後端已完成（`/api/auth/site/**` 登入 / 重新整理 / 登出，`GET /api/site/auth/me` 在 site-app），前端尚未對接**（見 [`docs/site-web/CURRENT_STATE.md`](docs/site-web/CURRENT_STATE.md)）。

### apps/admin-web

後台管理系統。主縱向域（Site / User / Template / Appearance / Media / Content / Reservation / RBAC）與站點 AI 管理為真實 API。無 `AI_MODEL` 時預覽誠實失敗。Analytics / System 等頁仍為佔位。

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
- `site/site-app`：前台站點 API，連接埠 `8082`

基礎與預留模組：

- `common`：通用回應、錯誤碼、例外、分頁；**`common.security`** JWT/身分輕量契約
- `infra`：PostgreSQL、Redis、MinIO / S3 接入、MyBatis-Plus、Flyway、**系統訊息 i18n** 等
- `agent`：`HttpAgentClient` 與 Java↔Python 契約 DTO，不做業務編排
- `site/site-core`：站點共享實體與 Mapper（使用者、內容、預約、站點 AI）
- `site/site-car`、`site/site-cake`：空模組，只有 `pom.xml`，沒有業務程式

專項文件：

- [`docs/website-services/README.md`](docs/website-services/README.md)（文件落在哪一格）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（跳轉到架構正文）
- [`docs/website-services/设计文档/基础架构/架构设计.md`](docs/website-services/设计文档/基础架构/架构设计.md)
- [`docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md`](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)
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

詳見 [`services/agent-services/README.md`](services/agent-services/README.md) 與設計文件 [`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)。

## 後端呼叫關係

建議請求路徑：

```text
admin-web ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（含 GET /api/admin/auth/me）
                                              └──> /api/site/**   → site-app:8082（含 GET /api/site/auth/me）
```

請求都走同一條 gateway。登入在 auth：`/api/auth/admin/**`、`/api/auth/site/**`。`/me` 不在 auth，而在對應業務服務。

內部 Maven 依賴關係（非 HTTP 拓樸）：

```text
admin / site-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent（按需） -> services/agent-services
```

當前階段不引入註冊中心、服務發現、訊息佇列或複雜閘道鑑權。`gateway` 做統一入口、CORS、request-id 與路由，並打開 `trusted-proxies`（預設 `.*`）與 `X-Forwarded-For` 追加。auth 限流讀這個標頭的最後一段。用戶端 IP 還沒有整合斷言，見 [GitHub Issue #1](https://github.com/hqjiayou/my-website/issues/1)。

## 啟動命令

完整步驟（local-stack 帳號對齊、兩份 `.env`、JWT 路徑、健康檢查）見 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先啟動 local-stack 的 `local-postgres` / `local-redis`。`services/website-services/.env` 的 `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` 必須與容器一致；`.env.example` 的 `mywebsite` / `CHANGE_ME` 不能直接拿來連當前庫。`services/agent-services/.env` 單獨一份，`AGENT_INTERNAL_SERVICE_TOKEN` 與 Java 側相同，缺了行程會拒絕啟動。

啟動 `website-services`（四個終端）：

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl site/site-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

啟動 `admin-web`：

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

工作區沒有面向訪客的站點前台。

啟動 `agent-services`：

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # 設定 AGENT_INTERNAL_SERVICE_TOKEN
uv run agent-services
```

## 本地基礎設施

後端本地依賴統一使用既有 **local-stack**（不在 `website-services` 內再起第二套 Postgres/Redis）：

- PostgreSQL：`local-postgres` → `localhost:5432`
- Redis：`local-redis` → `localhost:6379`
- MinIO / AIStor：`local-aistor` → API `http://localhost:9000`（主控台連接埠以堆疊設定為準）

應用在 **`services/website-services/.env`**（範本 `.env.example`）中設定 JDBC / Redis / MinIO 等；與 **local-stack** 目錄下的 `.env` **分開維護**，也與 **`services/agent-services/.env`** 分開。

更多說明見 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md) 與 [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。
