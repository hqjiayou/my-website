# my-website

[繁體中文](README.md) · [日本語](README.ja.md)

ウェブサイトを一つの管理画面で中央制御する集合です。総控は `apps/admin-web`、Java サービスは `services/website-services`、AI 実行は Python の `services/agent-services`、サイト利用者向けクライアントは `apps/agent-portal` です。来訪者向け公開フロント（旧 `apps/site-web`）は 2026-10-28 に削除されました。

## 位置づけ

- 一つの総控が、集合内の各サイトを管理する。
- サイト利用者向けクライアントは `apps/agent-portal`（ログイン + AI チャット）。サイト API は Java の `agent-app`（旧 site-app、2026-10-28 改名統合）。
- `apps/admin-web` がその総控（プラットフォームと各サイト）。
- `services/website-services` がこのシステムの Java サービス。**共通ゲートウェイ**、**独立した認証サービス（`auth`）**、管理 API、サイト API、インフラ接続を含む。**JWT は RS256 + JWKS**（Auth が署名し、業務サービスは JWKS で検証する）。
- `services/agent-services` は **Python 3.13 + FastAPI + Pydantic AI 2.54** の AI Execution Runtime（internal only。gateway には出さない）。業務の正は Java、AI 実行だけが Python（ADR-0005）。`AI_MODEL` がなくてもプロセスは起動し、呼び出しは正直に失敗する（ADR-0004）。

## ディレクトリ

```text
my-website/
├── apps
│   ├── admin-web         # 管理画面
│   └── agent-portal      # サイト利用者向けクライアント
├── docs                  # 三つの棚：いま / 仕様 / 履歴。docs/README.md を見る
└── services
    ├── agent-services
    └── website-services
```

**いま何があるか**は [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文書は [`docs/README.md`](docs/README.md) から入る。未完了は GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) は Issue 番号だけ）。テーブル辞書は [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 各プロジェクト

### apps/agent-portal（サイト利用者向け）

サイト利用者向けクライアント（ログイン / AI チャット）。来訪者向け公開フロント `apps/site-web/` は 2026-10-28 に削除された。探さないこと。

サイト API は `website-services/gateway` 経由で呼ぶ。  
**Agent Auth（旧 Site Auth）のバックエンドは完了**（`/api/auth/agent/**` がログイン / リフレッシュ / ログアウト、`GET /api/agent/auth/me` は agent-app）。**フロントは agent-portal が接続済み**（現状は [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)）。

### apps/admin-web

管理コンソール。縦割り本流（Agent / User / Appearance / Media / Content / RBAC）とサイト AI 管理は実 API。（Template / Reservation は 2026-10-27 に削除。）`AI_MODEL` がないとき、プレビューは正直に失敗する。Analytics / System などのページはまだプレースホルダ。

文書:

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 の Maven マルチモジュール。

起動するモジュール:

- `gateway`: 共通 API ゲートウェイ。ポート `8080`
- `auth`: **共通認証**（Admin のログイン / リフレッシュ / ログアウト、JWT 発行）。ポート `8083`
- `admin`: 管理 API（OAuth2 Resource Server と業務）。ポート `8081`
- `agent/agent-app`: サイト API。ポート `8082`

共通モジュールと予約モジュール:

- `common`: 共通レスポンス、エラーコード、例外、ページング。**`common.security`** は JWT / 身分の薄い契約
- `infra`: PostgreSQL、Redis、MinIO / S3、MyBatis-Plus、Flyway、**システムメッセージの i18n**
- `agent/agent-core`: サイト共通のエンティティと Mapper（ユーザー、コンテンツ、サイト AI）＋ `HttpAgentClient` と Java↔Python 契約 DTO（旧 site-core と agent クライアントを 2026-10-28 に統合）

文書:

- [`docs/website-services/README.md`](docs/website-services/README.md)（文書がどの棚に属するか）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（アーキテクチャ本文へのジャンプ）
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/)（テーブル辞書。Flyway と揃える）
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 の AI Execution Runtime。既定ポート `3001`。LangChain と LiteLLM は使わない。

内部 API（`X-Internal-Service-Token` が必要。呼ぶのは Java `HttpAgentClient` だけ）:

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream`（SSE）

公開の生存確認: `GET /health`（プロセスが生きていることだけ）。`AGENT_INTERNAL_SERVICE_TOKEN` が無いと起動を拒否する。`AI_MODEL` が無くても起動し、chat は 503 を返す。

詳細は [`services/agent-services/README.md`](services/agent-services/README.md) を参照（元の Agent 設計ドキュメントは 2026-10-29 に削除、git 履歴参照）。

## 呼び出し関係

推奨するリクエスト経路:

```text
admin-web ／ agent-portal ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（GET /api/admin/auth/me を含む）
                                              └──> /api/agent/**  → agent-app:8082（GET /api/agent/auth/me を含む）
```

リクエストは同じ gateway を通る。ログインは auth の `/api/auth/admin/**` と `/api/auth/agent/**`。`/me` は auth にはなく、対応する業務サービスにある。

Maven の内部依存（HTTP トポロジではない）:

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent-core（HttpAgentClient） -> services/agent-services
```

この段階ではレジストリ、サービスディスカバリ、メッセージキュー、ゲートウェイ側の認可は入れない。`gateway` は単一入口、CORS、request-id、ルーティングを担う。`trusted-proxies` は `(?!)` で、下流へ渡す `X-Forwarded-For` は TCP の接続相手になる。auth のレート制限はこのヘッダの最後のホップを読む。詳細は [`docs/operations/SECURITY.md`](docs/operations/SECURITY.md)。

## 起動

手順の全体（local-stack のアカウント合わせ、二つの `.env`、JWT のパス、ヘルスチェック）は [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先に local-stack を起動する（現環境のコンテナ名は `local-dev-postgres-1` / `local-dev-redis-1`。LOCAL_DEVELOPMENT §1 を参照）。`services/website-services/.env` の `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` はコンテナと一致させる。`.env.example` の `mywebsite` / `CHANGE_ME` では今のデータベースに入れない。`services/agent-services/.env` は別ファイル。`AGENT_INTERNAL_SERVICE_TOKEN` は Java 側と同じ値にし、無いとプロセスは起動を拒否する。

`website-services` を起動する（ターミナル四つ）:

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl agent/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

フロントを起動する（ターミナル二つ）:

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

`agent-services` を起動する:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # AGENT_INTERNAL_SERVICE_TOKEN を設定する
uv run agent-services
```

## ローカル基盤

バックエンドのローカル依存は既存の **local-stack** に統一する（`website-services` の中で Postgres / Redis をもう一套立ち上げない）。

- PostgreSQL: `local-dev-postgres-1` → `localhost:5432`
- Redis: `local-dev-redis-1` → `localhost:6379`
- MinIO: `local-dev-minio-1` → API `http://localhost:9000`（コンテナ名は現在の local-stack（compose プロジェクト `local-dev`）に従う。詳細は LOCAL_DEVELOPMENT §1）

JDBC / Redis / MinIO は **`services/website-services/.env`**（雛形は `.env.example`）に書く。**local-stack** 配下の `.env` とは分け、**`services/agent-services/.env`** とも分ける。

続きは [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md) を参照（元の WSL/Docker 専用ドキュメントは 2026-10-29 に削除、git 履歴参照）。
