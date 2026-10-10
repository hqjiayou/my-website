# my-website

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md)

여러 사이트를 올리는 플랫폼입니다. 기능은 업종 사이트 단위로 늘어납니다(자동차 정비, 케이크·제과, 이후 미용실, 클리닉, 중고차 매장, 행정서사 사무소 등). 이 작업 공간에는 업종 스토어프론트가 없습니다.

## 위치

- 하나의 플랫폼이 여러 업종 사이트를 담습니다. 단일 회사 홈페이지 전용이 아닙니다.
- 업종 스토어프론트는 이 작업 공간에 없습니다. 다시 넣을 때는 `apps/site-web/` 아래에 두고, 업종마다 독립 앱으로 나눕니다.
- `apps/admin-web`는 전체 관리와 사이트 관리를 담당합니다.
- `services/website-services`가 현재 Java 백엔드 본체입니다. **공통 게이트웨이**, **독립 인증 서비스(`auth`)**, 관리 API, 사이트 API, 인프라 연결, 이후 업무 모듈 골격을 포함합니다. **JWT는 RS256 + JWKS**입니다(Auth가 서명하고, 업무 서비스는 JWKS로 검증합니다).
- `services/agent-services`는 **Python 3.13 + FastAPI + Pydantic AI 2.54** AI Execution Runtime입니다(internal only, gateway로 노출하지 않음). 업무의 원본은 Java가 갖고, Python은 AI 실행만 합니다(ADR-0005). `AI_MODEL`이 없어도 프로세스는 뜨고, 호출은 정직하게 실패합니다(ADR-0004).

## 디렉터리

```text
my-website/
├── apps
│   └── admin-web         # 이 작업 공간의 프론트는 관리 화면뿐
├── docs                  # 세 칸: 현재 / 규격 / 이력. docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**지금 무엇이 있는지**는 [`docs/overview/CURRENT_STATE.md`](docs/overview/CURRENT_STATE.md)를 봅니다. 문서는 [`docs/README.md`](docs/README.md)부터 엽니다. 끝나지 않은 일은 GitHub Issues에 있습니다([`docs/overview/ROADMAP.md`](docs/overview/ROADMAP.md)에는 Issue 번호만 있습니다). 테이블 사전은 [`docs/website-services/数据字典/`](docs/website-services/数据字典/)입니다.

## 하위 프로젝트

### 업종 스토어프론트

이 작업 공간에는 업종 프론트가 없습니다(이전 `car-web`, `cake-web` 골격은 제거되었고, `apps/site-web/`도 트리에 없습니다).

다시 넣은 뒤에는 `website-services/gateway`로 사이트 API를 호출합니다.  
**Site Auth 백엔드는 완료**되었습니다(`/api/auth/site/**`가 로그인 / 갱신 / 로그아웃, `GET /api/site/auth/me`는 site-app). **프론트는 아직 연결되지 않았습니다**([`docs/site-web/CURRENT_STATE.md`](docs/site-web/CURRENT_STATE.md)).

### apps/admin-web

관리 콘솔입니다. 세로 본류(Site / User / Template / Appearance / Media / Content / Reservation / RBAC)와 사이트 AI 관리는 실제 API를 호출합니다. `AI_MODEL`이 없으면 미리보기는 정직하게 실패합니다. Analytics / System 등의 페이지는 아직 자리표시자입니다.

문서:

- [`docs/admin-web/CURRENT_STATE.md`](docs/admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](docs/admin-web/开发规范.md)
- [`docs/contracts/`](docs/contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 Maven 멀티 모듈 백엔드입니다.

실행 모듈:

- `gateway`: 공통 API 게이트웨이, 포트 `8080`
- `auth`: **공통 인증**(Admin 로그인 / 갱신 / 로그아웃, JWT 발급), 포트 `8083`
- `admin`: 관리 API(OAuth2 Resource Server와 업무), 포트 `8081`
- `site/site-app`: 사이트 API, 포트 `8082`

공통 모듈과 예약 모듈:

- `common`: 공통 응답, 오류 코드, 예외, 페이지네이션. **`common.security`**는 JWT / 신원용 얇은 계약
- `infra`: PostgreSQL, Redis, MinIO / S3, MyBatis-Plus, Flyway, **시스템 메시지 i18n**
- `agent`: `HttpAgentClient`와 Java↔Python 계약 DTO. 업무 오케스트레이션은 하지 않음
- `site/site-core`: 사이트 공유 엔티티와 Mapper(사용자, 콘텐츠, 예약, 사이트 AI)
- `site/site-car`, `site/site-cake`: 업종 모듈 골격. 구체 업무는 아직 없음

문서:

- [`docs/website-services/README.md`](docs/website-services/README.md) (문서가 어느 칸에 있는지)
- [`docs/website-services/ARCHITECTURE.md`](docs/website-services/ARCHITECTURE.md) (아키텍처 본문으로 이동)
- [`docs/website-services/设计文档/基础架构/架构设计.md`](docs/website-services/设计文档/基础架构/架构设计.md)
- [`docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md`](docs/website-services/设计文档/数据与持久层/Java持久层设计V1.md)
- [`docs/website-services/数据字典/`](docs/website-services/数据字典/) (테이블 사전, Flyway와 맞춤)
- [`services/website-services/README.md`](services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 AI Execution Runtime입니다. 기본 포트 `3001`. LangChain과 LiteLLM은 쓰지 않습니다.

내부 API(`X-Internal-Service-Token` 필요. Java `HttpAgentClient`만 호출):

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream` (SSE)

공개 생존 확인: `GET /health`(프로세스 생존만). `AGENT_INTERNAL_SERVICE_TOKEN`이 없으면 기동을 거부합니다. `AI_MODEL`이 없어도 기동하고, chat은 503을 반환합니다.

자세한 내용은 [`services/agent-services/README.md`](services/agent-services/README.md)와 [`docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md`](docs/website-services/设计文档/AI/Agent服务技术选型与架构设计V1.md)를 봅니다.

## 호출 관계

권장 요청 경로:

```text
admin-web ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081 (GET /api/admin/auth/me 포함)
                                              └──> /api/site/**   → site-app:8082 (GET /api/site/auth/me 포함)
```

업종 스토어프론트를 다시 넣어도 같은 gateway를 탑니다. 로그인은 auth의 `/api/auth/admin/**`, `/api/auth/site/**`입니다. `/me`는 auth에 없고, 대응하는 업무 서비스에 있습니다.

Maven 내부 의존(HTTP 토폴로지가 아님):

```text
admin / site-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent(필요할 때) -> services/agent-services
```

이 단계에서는 레지스트리, 서비스 디스커버리, 메시지 큐, 게이트웨이 인가를 넣지 않습니다. `gateway`는 단일 진입점, CORS, request-id, 라우팅을 담당하고, `trusted-proxies`(기본 `.*`)와 `X-Forwarded-For` 추가를 켭니다. auth 속도 제한은 이 헤더의 마지막 홉을 읽습니다. 클라이언트 IP 통합 단언은 아직 없습니다. [GitHub Issue #1](https://github.com/hqjiayou/my-website/issues/1).

## 실행

전체 절차(local-stack 계정 맞추기, `.env` 두 벌, JWT 경로, 헬스 체크)는 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)에 있습니다.

먼저 local-stack의 `local-postgres`와 `local-redis`를 띄웁니다. `services/website-services/.env`의 `POSTGRES_*`, `REDIS_PASSWORD`, `MINIO_*`는 컨테이너와 같아야 합니다. `.env.example`의 `mywebsite` / `CHANGE_ME`로는 현재 데이터베이스에 들어가지 않습니다. `services/agent-services/.env`는 별도 파일입니다. `AGENT_INTERNAL_SERVICE_TOKEN`은 Java 쪽과 같아야 하며, 없으면 프로세스가 기동을 거부합니다.

`website-services` 실행(터미널 네 개):

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl site/site-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

`admin-web` 실행:

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

이 작업 공간에는 site-web 스토어프론트가 없습니다. `car-web`과 `cake-web`은 실행하지 않습니다.

`agent-services` 실행:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # AGENT_INTERNAL_SERVICE_TOKEN 설정
uv run agent-services
```

## 로컬 인프라

백엔드 로컬 의존성은 기존 **local-stack**으로 통일합니다(`website-services` 안에서 Postgres/Redis를 또 한 벌 띄우지 않습니다).

- PostgreSQL: `local-postgres` → `localhost:5432`
- Redis: `local-redis` → `localhost:6379`
- MinIO / AIStor: `local-aistor` → API `http://localhost:9000`(콘솔 포트는 스택 설정을 따름)

JDBC / Redis / MinIO는 **`services/website-services/.env`**(본보기 `.env.example`)에 둡니다. **local-stack** 디렉터리의 `.env`와 따로 두고, **`services/agent-services/.env`**와도 따로 둡니다.

자세한 내용은 [`docs/operations/LOCAL_DEVELOPMENT.md`](docs/operations/LOCAL_DEVELOPMENT.md)와 [`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`](docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md)를 봅니다.
