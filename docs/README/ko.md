# my-website

언어 목록: [`docs/README.md`](../README.md#项目介绍多语言). 이 문서: 한국어

사이트를 하나의 관리 화면으로 중앙 제어하는 집합입니다. 총괄은 `apps/admin-web`, Java 서비스는 `services/website-services`, AI 실행은 Python `services/agent-services`, 사이트 사용자 클라이언트는 `apps/agent-portal`입니다. 방문자용 공개 프론트(구 `apps/site-web`)는 2026-10-28에 제거되었습니다.

## 포지셔닝

- 하나의 총괄이 집합 안의 각 사이트를 관리합니다.
- 사이트 사용자 클라이언트는 `apps/agent-portal`(로그인 + AI 채팅). 사이트 API는 Java `agent-app`(구 site-app, 2026-10-28 개명·통합).
- `apps/admin-web`이 그 총괄(플랫폼과 각 사이트).
- `services/website-services`가 이 시스템의 Java 서비스입니다. **공용 게이트웨이**, **독립 인증 서비스(`auth`)**, 관리 API, 사이트 API, 인프라 접속을 포함합니다. **JWT는 RS256 + JWKS**(Auth가 서명, 업무 서비스는 JWKS로 검증).
- `services/agent-services`는 **Python 3.13 + FastAPI + Pydantic AI 2.54** AI Execution Runtime입니다(internal only, gateway 미노출). 업무 진실은 Java, AI 실행만 Python(ADR-0005). `AI_MODEL`이 없어도 프로세스는 기동하고, 호출은 정직하게 실패합니다(ADR-0004).

## 디렉터리

```text
my-website/
├── apps
│   ├── admin-web         # 관리 콘솔
│   └── agent-portal      # 사이트 사용자 클라이언트
├── docs                  # 세 선반: 지금 / 스펙 / 이력 — docs/README.md
└── services
    ├── agent-services
    └── website-services
```

**지금 무엇이 있는지**는 [`docs/overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md). 문서는 [`docs/README.md`](../README.md)부터. 미완료 작업은 GitHub Issues([`docs/overview/ROADMAP.md`](../overview/ROADMAP.md)는 Issue 번호만). 테이블 사전은 [`docs/website-services/数据字典/`](../website-services/数据字典/).

## 하위 프로젝트

### apps/agent-portal (사이트 사용자)

사이트 사용자 클라이언트(로그인 / AI 채팅). 방문자용 공개 프론트 `apps/site-web/`은 2026-10-28에 제거됨 — 찾지 마세요.

사이트 API는 `website-services/gateway`를 통합니다.  
**Agent Auth(구 Site Auth) 백엔드 완료**(`/api/auth/agent/**` 로그인 / 갱신 / 로그아웃, `GET /api/agent/auth/me`는 agent-app). **프론트는 agent-portal이 연동됨**(현황 [`docs/overview/CURRENT_STATE.md`](../overview/CURRENT_STATE.md)).

### apps/admin-web

관리 콘솔. 주 세로 도메인(Agent / User / Appearance / Media / Content / RBAC)과 사이트 AI 관리는 실 API.(Template / Reservation은 2026-10-27 삭제.) `AI_MODEL`이 없으면 미리보기는 정직하게 실패. Analytics / System 등은 아직 placeholder.

문서:

- [`docs/admin-web/CURRENT_STATE.md`](../admin-web/CURRENT_STATE.md)
- [`docs/admin-web/开发规范.md`](../admin-web/开发规范.md)
- [`docs/contracts/`](../contracts/)

### services/website-services

Java 21 + Spring Boot 3.5.14 Maven 멀티 모듈 백엔드.

실행 모듈:

- `gateway`: 공용 API 게이트웨이, 포트 `8080`
- `auth`: **공용 인증**(Admin 로그인/갱신/로그아웃, JWT 발급), 포트 `8083`
- `admin`: 관리 API(OAuth2 Resource Server + 업무), 포트 `8081`
- `agent/agent-app`: 사이트 업무 API, 포트 `8082`

공용 모듈:

- `common`: 공통 응답, 에러 코드, 예외, 페이징; **`common.security`** JWT/신분 경량 계약
- `infra`: PostgreSQL, Redis, MinIO / S3, MyBatis-Plus, Flyway, **시스템 메시지 i18n**
- `agent/agent-core`: 사이트 공유 엔티티·Mapper(사용자, 콘텐츠, 사이트 AI) + `HttpAgentClient`와 Java↔Python 계약 DTO(구 site-core와 agent 클라이언트 라이브러리를 2026-10-28에 여기로 병합)

문서:

- [`docs/website-services/README.md`](../website-services/README.md)(문서가 어느 선반인지)
- [`docs/website-services/ARCHITECTURE.md`](../website-services/ARCHITECTURE.md)(아키텍처 본문으로 점프)
- [`docs/website-services/数据字典/`](../website-services/数据字典/)(테이블 사전, Flyway와 맞춤)
- [`services/website-services/README.md`](../../services/website-services/README.md)

### services/agent-services

Python 3.13 + FastAPI + Pydantic AI 2.54 AI Execution Runtime, 기본 포트 `3001`. LangChain / LiteLLM 금지.

내부 API(`X-Internal-Service-Token` 필요, Java `HttpAgentClient`만 호출):

- `GET /internal/v1/health`
- `GET /internal/v1/mcp/ready`
- `GET /internal/v1/skills`
- `POST /internal/v1/chat`
- `POST /internal/v1/chat/stream`(SSE)

공개 생존 확인: `GET /health`(프로세스 생존만). `AGENT_INTERNAL_SERVICE_TOKEN` 없으면 기동 거부; `AI_MODEL` 없어도 기동, chat은 503.

자세한 내용: [`services/agent-services/README.md`](../../services/agent-services/README.md)(구 Agent 설계 문서는 2026-10-29 제거, git 이력 참고).

## 호출 관계

권장 요청 경로:

```text
admin-web ／ agent-portal ──> website-services/gateway:8080 ──> /api/auth/**   → auth:8083
                                              ──> /api/admin/**  → admin:8081（GET /api/admin/auth/me 포함）
                                              └──> /api/agent/**  → agent-app:8082（GET /api/agent/auth/me 포함）
```

요청은 같은 gateway를 탑니다. 로그인은 auth의 `/api/auth/admin/**`, `/api/auth/agent/**`. `/me`는 auth가 아니라 해당 업무 서비스에 있습니다.

내부 Maven 의존(HTTP 토폴로지 아님):

```text
admin / agent-app / auth
  ├── infra  -> PostgreSQL / Redis / MinIO
  └── agent-core（HttpAgentClient） -> services/agent-services
```

이 단계에서는 레지스트리, 서비스 디스커버리, 메시지 큐, 무거운 게이트웨이 인가를 넣지 않습니다. `gateway`는 단일 진입, CORS, request-id, 라우팅을 담당합니다. `trusted-proxies`는 `(?!)`, 하류 `X-Forwarded-For`는 TCP 피어입니다. auth 제한은 이 헤더의 마지막 hop을 읽습니다. [`docs/operations/SECURITY.md`](../operations/SECURITY.md).

## 기동

전체 절차(local-stack 계정 맞춤, `.env` 두 개, JWT 경로, 헬스 체크): [`docs/operations/LOCAL_DEVELOPMENT.md`](../operations/LOCAL_DEVELOPMENT.md).

먼저 local-stack을 기동합니다(현재 컨테이너명 `local-dev-postgres-1` / `local-dev-redis-1`, LOCAL_DEVELOPMENT §1). `services/website-services/.env`의 `POSTGRES_*`, `REDIS_PASSWORD`, `MINIO_*`는 컨테이너와 일치해야 합니다. `.env.example`의 `mywebsite` / `CHANGE_ME`로는 현재 DB에 못 붙습니다. `services/agent-services/.env`는 별도이며, `AGENT_INTERNAL_SERVICE_TOKEN`은 Java 측과 같아야 하고 없으면 기동을 거부합니다.

`website-services` 기동(터미널 네 개):

```bash
cd services/website-services
mvn clean install -DskipTests
mvn -pl auth -am spring-boot:run
mvn -pl admin -am spring-boot:run
mvn -pl agent/agent-app -am spring-boot:run
mvn -pl gateway spring-boot:run
```

프론트 기동(터미널 두 개):

```bash
cd apps/admin-web && pnpm install && pnpm dev    # http://localhost:5173
cd apps/agent-portal && pnpm install && pnpm dev # http://localhost:5174
```

`agent-services` 기동:

```bash
cd services/agent-services
uv sync --extra dev
cp .env.example .env   # AGENT_INTERNAL_SERVICE_TOKEN 설정
uv run agent-services
```

## 로컬 인프라

백엔드 로컬 의존은 기존 **local-stack**으로 통일합니다(`website-services` 안에 Postgres/Redis를 또 띄우지 않음).

- PostgreSQL: `local-dev-postgres-1` → `localhost:5432`
- Redis: `local-dev-redis-1` → `localhost:6379`
- MinIO: `local-dev-minio-1` → API `http://localhost:9000`(이름은 현재 local-stack / compose 프로젝트 `local-dev` 기준, LOCAL_DEVELOPMENT §1)

JDBC / Redis / MinIO는 **`services/website-services/.env`**(템플릿 `.env.example`)에 둡니다. **local-stack** `.env`와 **`services/agent-services/.env`**와도 분리합니다.

이어지는 설명: [`docs/operations/LOCAL_DEVELOPMENT.md`](../operations/LOCAL_DEVELOPMENT.md)(구 WSL/Docker 전용 문서는 2026-10-29 제거, git 이력 참고).
