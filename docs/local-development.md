# 로컬 개발과 명령

PostGIS 자체는 ARM64에서 사용할 수 있다. 여기서 AMD64 한정은 선택한 `postgis/postgis:18-3.6` 이미지의 manifest에 대한 설명이다. PostGIS 소프트웨어 전체의 아키텍처 제한이 아니다.

하네스는 macOS/Linux의 Git과 Python3.9+로 실행한다. pnpm 명령은 선택적인 루트 별칭이다. toolchain-install은 Java25·Node24·pnpm12를 사용자 캐시에 준비하고 scripts/runtime과 하네스가 해당 버전을 선택한다. 시스템 Java/Node는 변경하지 않는다. Gradle Wrapper는 공식 checksum과 함께 고정됐다.

```sh
./scripts/harness doctor
./scripts/harness hooks-install
./scripts/harness verify-harness
./scripts/harness env-init
./scripts/harness infra-up
./scripts/harness infra-check
./scripts/harness infra-down
```

env-init은 .local/infra.env의 DB·Redis 비밀번호를 무작위 생성하고 기존 .env/기존 설정을 보존한다. 비밀값은 출력하지 않는다. Redis 설정은 mode700 .local 아래 redis.conf에 저장하고 컨테이너 읽기용 파일 권한은 644다. Redis는 비밀번호 인증을 요구한다. compose는 이 파일을 명시 로딩한다. DB 127.0.0.1:54329, Redis 127.0.0.1:63799. PostGIS는 AMD64 에뮬레이션, Redis는 해당 CPU 이미지. Linux ARM64는 에뮬레이션 준비 필요. infra-down은 볼륨을 삭제하지 않는다. `down -v`나 파괴적 초기화 명령은 기본 제공하지 않는다.

PostGIS upstream 초기화 후 init-roles.sh가 앱 schema와 migrator/app 역할을 생성한다. 스크립트는 빈 볼륨에서만 실행된다. 비밀번호 파일만 바꿔 기존 DB 비밀번호가 변경된다고 생각하지 않는다. 앱은 kimchimap_app, Flyway는 kimchimap_migrator, 확장은 postgres 관리자가 생성한다. 초기 extension은 public schema, app 테이블은 app schema다. 데이터 보존 변경은 별도 마이그레이션과 복구 검토가 필요하다.

| 명령 (`./scripts/harness` 뒤) | 현재 실행/후속 연결 |
| --- | --- |
| doctor | 도구·Docker·Java25·Node·훅 진단, 불충족 exit2 |
| env-init / infra-up / infra-down / infra-check | 안전한 로컬 설정·Compose 시작/종료·실제 DB/Redis 확인 |
| hooks-install | 저장소 로컬 훅 설치, 다른 설정 덮어쓰기 금지 |
| format-check / lint-harness / test-harness / verify-harness | 텍스트 형식·Python AST/JSON/문서 링크/금지파일·하네스 회귀 테스트 |
| dev-backend / dev-frontend | gradlew bootRun / frontend dev 실행 |
| format-backend / format-frontend | Spotless/프론트 formatter 검사 |
| lint / typecheck | frontend lint/typecheck 실행 |
| test-backend / test-frontend | 실제 단위·UI 테스트 실행 |
| test-integration / test-contract | DB/Redis 통합, 공식 구조의 합성 외부 API 계약 검사 |
| test-e2e / test-performance | Playwright 실행, 실제 공간 실행계획 검사 |
| api-generate / api-check | OpenAPI 생성 및 프론트 생성 타입/계약 비교 |
| build / build-backend | 프론트 build / 백엔드 bootJar 실행 |
| ingest-run / ingest-status | 허용된 일반음식점 실제 수집·상태 조회, 사용자 입력 URL 금지 |
| smoke-external | 키·허가 있는 실제 연동 별도 task 필요 |
| verify | 하네스+서비스 모든 핵심 검사, 현재 미구현이면 실패 |
| branch-start / commit-check / pr-check / pr-prepare | Git 절차 문서 참조 |
| branch-cleanup / protection-check / protection-plan | 기본 조회/dry-run, 명시 apply 분리 |

서비스 연결 명세는 tools/harness/commands.json이다. requires 파일만 만들면 성공하는 구조가 아니며 해당 task의 실제 종료 코드가 반영된다. 없는 task·누락 앱·실패 검사는 exit2로 보고한다. ingest task는 고정된 허용 소스와 이용 허가를 확인한다. 로컬 실행자 명령이며 관리자 HTTP 인가는 인증 구현 후 연결한다.

후속 로컬 frontend는 localhost:5173, backend는 localhost:8080으로 고정하고 Vite /api 프록시를 구성한다. 카카오 JS 키는 브라우저 공개 키로 취급하되 REST/Client Secret은 VITE_*에 넣지 않는다. OAuth redirect/도메인 등록·쿼터·실제 smoke는 별도 문서 결과로 남긴다.

verify-harness는 하네스 전용이다. 현재 앱 기반 검증은 verify-unit application-foundation으로 실행하고 실제 formatter·linter·타입·단위·DB/Redis·API·E2E·build를 모두 확인한다. 전체 verify에는 외부 API 계약 테스트도 포함한다. 실제 외부 연동은 별도로 실행한다.


## 현재 실행 가능한 앱 명령

dev-backend/dev-frontend, format-backend/format-frontend, lint/typecheck, test-backend/test-frontend/test-integration/test-e2e, api-generate/api-check, build/build-backend를 구현했다. 위 표의 연결 계약은 실제 이 작업에 연결됐다. test-contract/test-performance/ingest-run/ingest-status도 연결했다. 통합 smoke-external 명령은 카카오 실연동 단계 전까지 미구현 실패를 유지한다.

`toolchain-install` → `scripts/runtime pnpm install --frozen-lockfile --ignore-scripts` → `infra-up` 후 서버/UI를 실행한다. E2E 전 `scripts/runtime pnpm --dir frontend exec playwright install chromium`이 필요하다. E2E는 기존 서버를 재사용하지 않고 직접 실행·종료한다.

`.local/integrations.env`에는 KAKAO_JAVASCRIPT_KEY/KAKAO_CLIENT_ID(REST API 키)/KAKAO_CLIENT_SECRET/PUBLIC_DATA_SERVICE_KEY(Decoding)를 입력한다. 카카오 도메인은 http://localhost:5173, 로그인 callback 계약은 http://localhost:5173/api/v1/auth/callback/kakao다. 파일 권한600, git-common-dir 기준 .local을 읽는다. env 파일은 shell로 실행하지 않고 허용 키만 파싱한다.

공간 검색 검증: `./scripts/harness verify-unit spatial-search`. 실제 2만 건 검색 실행 계획은 `./scripts/harness test-performance`로 재현한다. SEARCH_CURSOR_SECRET은 운영에서 32바이트 이상으로 설정하며 로컬 미설정 시 프로세스 메모리에만 생성된다. 키 변경·로컬 재시작은 기존 검색 cursor를 무효화한다.

## 공공데이터 수집

`./scripts/harness ingest-run`은 등록된 Decoding 키로 최근 7일 범위의 업소를 최대 2페이지 수집한다. 재실행하면 이전 PARTIAL의 다음 페이지부터 같은 창을 이어간다. `./scripts/harness ingest-status`로 상태·페이지·반영/격리 건수를 조회한다. 키는 `.local/integrations.env`에만 두며 조회 결과는 로컬 DB에 저장한다.

기본값을 바꿀 때는 실행 환경의 `INGEST_MODE=FULL` 또는 `INGEST_MAX_PAGES=5`를 지정한다. FULL도 페이지 예산만큼 처리하며 전국 수집 완료로 간주하지 않는다. 예약 수집은 `INGEST_SCHEDULING_ENABLED=true`와 DB source.enabled 설정을 함께 사용한다. [실행 정책](ingestion.md)을 먼저 확인한다. 예약 활성화 시 새 비용이나 이용 허가를 대신 승인하지 않는다.

## 서비스 세션 설정

P06a는 로그인 이후의 JWT·Refresh 세션 기반을 제공한다. 실제 카카오 로그인 화면은 P06b에서 연결하며 개발용 로그인 우회 주소는 없다. 자동 검증은 합성 회원을 내부 Service로 생성한다.

로컬에서 JWT_PRIVATE_KEY_PATH를 생략하면 메모리 RSA 키를 사용한다. 재시작 시 기존 Access Token은 무효가 되며 정상 Refresh 세션은 갱신으로 복구한다. 운영은 JWT_PRIVATE_KEY_PATH(RSA 개인 JWK JSON)와 JWT_KEY_ID가 필수다. 파일은 Git 제외된 안전한 위치에 두고 접근 권한을 제한한다. JWT_VERIFICATION_KEYS_PATH에는 교체 전 공개키만 JWK Set으로 지정할 수 있다. JWT_ISSUER/JWT_AUDIENCE, ACCESS_TOKEN_LIFETIME(기본10m·최대10분), SESSION_MAX_LIFETIME(기본14d·최대14일)을 설정할 수 있다.

쿠키를 사용하는 갱신·로그아웃은 GET /api/v1/auth/csrf에서 받은 토큰을 X-CSRF-TOKEN 헤더로 보내고 PUBLIC_ORIGIN과 같은 Origin을 전송해야 한다. 브라우저에서는 같은 사이트 요청과 credentials를 사용한다. 우리 토큰과 카카오 토큰은 별개다.

## 카카오 로그인 실행

카카오 앱에서 지도·로그인·OIDC를 활성화하고 웹 도메인 `http://localhost:5173`, 로그인 Redirect URI `http://localhost:5173/api/v1/auth/callback/kakao`를 등록한다. 브라우저도 `localhost`로 접속한다. `127.0.0.1`은 서로 다른 Origin이라 인증 요청이 거부된다. 키는 공통 저장소의 `.local/integrations.env`에서 런타임이 읽는다. KAKAO_CLIENT_ID는 REST API 키, KAKAO_CLIENT_SECRET은 로그인 시크릿이며 VITE 변수로 전달하지 않는다.

인프라와 백엔드·프론트엔드를 실행한 뒤 `/login`에서 카카오로 이동한다. 성공 후 `/auth/complete`가 CSRF 보호 요청으로 서비스 세션을 복구한다. 키 미설정이면 로그인 시작은 503이고 공개 조회는 계속 제공한다. 외부 계정 로그인이 필요한 실제 검증과 키 없이 실행되는 통제된 OIDC 통합 테스트를 구분한다.
