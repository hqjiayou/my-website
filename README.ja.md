# my-website

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

複数サイトを載せるプラットフォームです。機能は業種サイト単位で広がります（自動車整備、ケーキ・製菓、その後は美容院、クリニック、中古車販売、行政書士事務所など）。このワークスペースに業種向けストアフロントはありません。

## 位置づけ

- 一つのプラットフォームで複数の業種サイトを載せる。単体のコーポレートサイト専用ではない。
- 業種ストアフロントはこのワークスペースにない。戻すときは `apps/site-web/` に置き、業種ごとに独立したアプリにする。
- `apps/admin-web` は全体管理とサイト管理の両方を担う。
- `services/website-services` が現在の Java バックエンド本体。**共通ゲートウェイ**、**独立した認証サービス（`auth`）**、管理 API、サイト API、インフラ接続、今後の業務モジュールの骨組みを含む。**JWT は RS256 + JWKS**（Auth が署名し、業務サービスは JWKS で検証する）。
- `services/agent-services` は **Python 3.13 + FastAPI + Pydantic AI 2.54** の AI Execution Runtime（internal only。gateway には出さない）。業務の正は Java、AI 実行だけが Python（ADR-0005）。`AI_MODEL` がなくてもプロセスは起動し、呼び出しは正直に失敗する（ADR-0004）。

## ディレクトリ

```text
my-website/
├── apps
│   └── admin-web         # このワークスペースにあるフロントは管理画面だけ
├── docs                  # 三つの棚：いま / 仕様 / 履歴。docs/README.md を見る
└── services
    ├── agent-services
    └── website-services
```

**いま何があるか**は [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)。文書は [`docs/README.md`](docs/README.md) から入る。未完了は GitHub Issues（[`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md) は Issue 番号だけ）。テーブル辞書は [`docs/website-services/数据字典/`](docs/website-services/数据字典/)。

## 各プロジェクト

### 業種ストアフロント

このワークスペースに業種向けフロントはない（以前の `car-web` と `cake-web` の骨組みは削除済みで、`apps/site-web/` もツリーにない）。

戻したあとは `website-services/gateway` 経由でサイト API を呼ぶ。  
**Site Auth のバックエンドは完了**（`/api/auth/site/**` がログイン / リフレッシュ / ログアウト、`GET /api/site/auth/me` は site-app）。**フロントは未接続**（[`docs/site-web/CURRENT_STATE.md`](docs/site-web/CURRENT_STATE.md)）。

### apps/admin-web

管理コンソール。縦割り本流（Site / User / Template / Appearance / Media / Content / Reservation / RBAC）とサイト AI 管理は実 API。`AI_MODEL` がないとき、プレビューは正直に失敗する。Analytics / System などのページはまだプレースホルダ。

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
- `site/site-app`: サイト API。ポート `8082`

共通モジュールと予約モジュール:

- `common`: 共通レスポンス、エラーコード、例外、ページング。**`common.security`** は JWT / 身分の薄い契約
- `infra`: PostgreSQL、Redis、MinIO / S3、MyBatis-Plus、Flyway、**システムメッセージの i18n**
- `agent`: `HttpAgentClient` と Java↔Python 契約 DTO。業務オーケストレーションはしない
- `site/site-core`: サイト共通のエンティティと Mapper（ユーザー、コンテンツ、予約、サイト AI）
- `site/site-car`、`site/site-cake`: 業種モジュールの骨組み。具体的な業務はまだない

文書:

- [`docs/website-services/README.md`](docs/website-services/README.md)（文書がどの棚に属するか）
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md)（アーキテクチャ本文へのジャンプ）
- [`docs/website-services/设计文档/基础架构/架构设计.md`](docs/website-services/设计文档/基础架构/架构设计.md)
- [`docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md`](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)
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

詳細は [`services/agent-services/README.md`](services/agent-services/README.md) と [`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)。

## 呼び出し関係

推奨するリクエスト経路:

```text
admin-web ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（GET /api/admin/auth/me を含む）
                                              └──> /api/site/**   → site-app:8082（GET /api/site/auth/me を含む）
```

業種ストアフロントを戻したあとも同じ gateway を使う。ログインは auth の `/api/auth/admin/**` と `/api/auth/site/**`。`/me` は auth にはなく、対応する業務サービスにある。

Maven の内部依存（HTTP トポロジではない）:

```text
admin / site-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent（必要なとき） -> services/agent-services
```

この段階ではレジストリ、サービスディスカバリ、メッセージキュー、ゲートウェイ側の認可は入れない。`gateway` は単一入口、CORS、request-id、ルーティングを担い、`trusted-proxies`（既定 `.*`）と `X-Forwarded-For` の追記を有効にする。auth のレート制限はこのヘッダの最後のホップを読む。クライアント IP の結合アサーションはまだない。[GitHub Issue #1](https://github.com/hqjiayou/my-website/issues/1)。

## 起動

手順の全体（local-stack のアカウント合わせ、二つの `.env`、JWT のパス、ヘルスチェック）は [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)。

先に local-stack の `local-postgres` と `local-redis` を起動する。`services/website-services/.env` の `POSTGRES_*`、`REDIS_PASSWORD`、`MINIO_*` はコンテナと一致させる。`.env.example` の `mywebsite` / `CHANGE_ME` では今のデータベースに入れない。`services/agent-services/.env` は別ファイル。`AGENT_INTERNAL_SERVICE_TOKEN` は Java 側と同じ値にし、無いとプロセスは起動を拒否する。

`website-services` を起動する（ターミナル四つ）:

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl site/site-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

`admin-web` を起動する:

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

このワークスペースに site-web のストアフロントはない。`car-web` と `cake-web` は起動しない。

`agent-services` を起動する:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # AGENT_INTERNAL_SERVICE_TOKEN を設定する
uv run agent-services
```

## ローカル基盤

バックエンドのローカル依存は既存の **local-stack** に統一する（`website-services` の中で Postgres / Redis をもう一套立ち上げない）。

- PostgreSQL: `local-postgres` → `localhost:5432`
- Redis: `local-redis` → `localhost:6379`
- MinIO / AIStor: `local-aistor` → API `http://localhost:9000`（コンソールのポートはスタック設定に従う）

JDBC / Redis / MinIO は **`services/website-services/.env`**（雛形は `.env.example`）に書く。**local-stack** 配下の `.env` とは分け、**`services/agent-services/.env`** とも分ける。

続きは [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md) と [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)。
