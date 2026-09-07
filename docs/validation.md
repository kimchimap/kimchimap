# 실제 검증 기록

확인일: 2026-09-07. 하네스와 로컬 인프라 검증이며 서비스 구현·실연동·운영 준비 완료가 아니다.

환경: macOS ARM64, Python3.9.6, Node22.13.1, Java17.0.16, Docker Desktop4.39/Engine28.0.1. 앱 후보 요구 Java25·Node>=22.22는 미충족.

| 실행 | 결과 | 범위·한계 |
| --- | --- | --- |
| hooks-install | exit0 | core.hooksPath 설치, 실제 원격 보호는 별개 |
| verify-harness | 24개 회귀 테스트 통과 | 임시 Git 훅·메시지·PR·보호 계획·cleanup·설정 보존 |
| verify | exit2 | 하네스 통과, 앱 미구현 12개 검사 실패; skip·성공 위장 없음 |
| doctor | exit2 | Java25·Node>=22.22 미충족, Docker·훅·하네스 도구 확인 |
| pnpm12.3.4 install --lockfile-only --ignore-scripts | exit0 | 실제 lockfile 생성, 앱 의존 없음 |
| env-init | exit0 | 무작위 로컬 DB/Redis 비밀 설정, 기존 .env 보존 |
| infra-up | exit0 | 고정 digest 이미지 pull·healthcheck, PostGIS는 AMD64 에뮬레이션 |
| infra-check | exit0 | PostgreSQL18.6/PostGIS3.6.4, 공간 함수·DML 허용·앱 DDL 거부, 트랜잭션 rollback |
| infra-down | exit0 | 검증 후 컨테이너/네트워크 종료, DB·Redis 볼륨 보존 |
| pr-check | exit0 | 실제 한국어 PR 초안 제목/본문 검증, GitHub PR 생성 아님 |
| Redis 인증 확인 | 성공 | 인증 PONG, 인증 없는 요청 NOAUTH 거부 |
| 역할 조회 | 성공 | 앱/마이그레이션 역할 superuser/createDB/createRole 모두 false |
| 좌표 변환 probe | 실행됨 | EPSG5174 (200000,500000)→(127.00078353894504,38.00274602478411). 공식 기준점 정확도 테스트는 아직 없음 |
| protection-check | exit2 | main effective rules 없음·classic 보호 없음, origin/dev 없음 |
| protection-plan | exit0, dry-run | admin 권한 조회·추가 계획 출력. 서버 변경·apply 미실행 |

서비스 단위·PostGIS/Redis Testcontainers·외부 계약·Playwright·성능은 아직 구현/실행하지 않았다. 인프라의 SQL probe를 서비스 통합 테스트로 보고하지 않는다. 카카오/공공 API/협회 실연동·실제 업소 원산지 데이터 없음. API 문서 열람은 수집→저장 검증이 아니다.

보안 검사의 한계: 하네스의 메시지 한국어 판정과 비밀 패턴은 보조 검사이며 완전한 의미 분석·비밀 탐지기가 아니다. GitHub 서버 규칙 실제 적용은 미검증. 보호 도구 apply 동작은 mock으로만 테스트했다. Linux/ARM64 네이티브 PostGIS 실행은 미검증이며 PostGIS 자체의 ARM 지원과 선택 이미지의 플랫폼 지원을 구분한다.

## 구현 착수 후 추가 검증

보호 응답 기본 필드·순서의 의미 비교 회귀를 포함해 하네스 25개 테스트 통과. protection-plan --apply 재실행 성공, protection-check exit0으로 main/dev 유효 규칙 확인. 초기 하네스 시점의 미적용 기록과 구분한다. 사용자 지시로 작업별 dev PR 병합이 승인됐다.

## P01/P02 애플리케이션 기반

- verify-unit application-foundation: exit0. 하네스28개, 환경·형식·린트·타입, 백엔드 단위/실제 DB·Redis 통합, API 문서/타입 재생성 비교, UI2개, 모바일/PC E2E2개, 양쪽 build 통과.
- Spring Boot4.1.1/Java25.0.4.1+1/Gradle9.7.1과 React19.2.8/Vite8.2.2/Node24.20.0 실행 확인. pnpm12.3.4 frozen lockfile 설치와 peer 검사 통과.
- 최초 E2E의 스크린샷 인자 누락을 수정하고 타입 검사·두 레이아웃 재실행 통과. 테스트를 삭제하지 않았으며 화면도 직접 확인했다.
- pnpm audit --prod: 보고된 취약점 0개. 이 결과는 모든 종류의 취약점이 없다는 보장이 아니다.
- JWT·로그인·검색·원산지·제보·외부 수집은 아직 미구현. 상태 API는 앱 생존 상태이며 모든 의존 서비스의 상시 readiness 보장은 아니다.
- Gradle Wrapper와 Java/Node 배포 SHA256 검증. 시스템 런타임과 개인 메모·로컬 비밀 파일을 보존했다.

## P03 업소와 원산지 모델 — 2026-09-07

- `verify-unit origin-model`: 통과. 하네스 28개, 단위·ArchUnit 12개, 실제 PostGIS·Redis 통합 13개, 프론트 2개, 모바일/PC E2E 2개. 실패·skip 0. 포맷·린트·타입·API 재생성 일치·백엔드/프론트 빌드 성공.
- 최초 통합에서 JDBC Instant 매개변수 처리 실패를 확인하고 UTC OffsetDateTime으로 수정했다. 전체 통합을 다시 실행하여 성공했다. 테스트 성공으로 위장하거나 범위를 줄이지 않았다.
- V1 보존, V2 빈 DB 초기화와 로컬 기존 볼륨 추가 적용 성공. 실제 앱 계정으로 원산지 구성 불일치·사후 수정·외부 ID 중복·잘못된 좌표를 거부했다.
- 공개 상세의 품목 분리·원산지 확인일/마지막 수집일 분리·만료/상충/철회·미승인/비공개 근거 비노출·지정에서 원산지 생성 금지·상한 초과 422를 확인했다.
- 실제 원산지 데이터 없음. 공간 검색, 수집 계약, 로그인, 관리자 정정은 후속 작업이며 전체 서비스 검증 완료가 아니다.
- 실행 기록: `/tmp/kimchimap-origin-final-domain.log`, `/tmp/kimchimap-origin-unit-gate.log`. 임시 로그가 없어도 위 명령과 커밋된 테스트로 재현할 수 있다.

- `verify` 전체 서비스 검사도 실행했다. 현재 아직 등록되지 않은 외부 수집 계약 `contractTest`에서만 실패(exit 2)했고 나머지는 통과했다. P03 완료와 전체 서비스 미완료를 구분한다. 기록: `/tmp/kimchimap-origin-full-verify.log`.

## P04 공간 검색 — 2026-09-07

- `verify-unit spatial-search`: 성공. 하네스 28개, 단위/계층 18개, 실제 DB/Redis 통합 23개, 성능 1개, 프론트 2개, 모바일/PC E2E 2개. 실패·skip 0. API 생성 일치·포맷·린트·타입·양쪽 빌드 통과.
- 반경 100미터 경계의 수치 오차를 발견해 1마이크로미터 허용치를 명시하고 경계 포함/1밀리미터 바깥 제외를 검증했다. 국가 OR·그룹 AND·같은 김치 scope·혼합/미확인/분쟁/만료·최신성·근거·동률 cursor·넓은 지도 확대 안내를 실제 DB에서 확인했다.
- 실제 검색 쿼리로 가상 업소·원산지 각 20,000개에서 두 공간 인덱스를 확인했다. [측정 결과](performance.md). 처리량이나 운영 지연시간 보장은 하지 않는다.
- V1/V2를 보존하고 V3 인덱스를 추가했다. 로컬 DB 볼륨은 유지했다.
- 기록: `/tmp/kimchimap-search-unit-gate.log`, `/tmp/kimchimap-search-complete-tests.log`. 성능 상세 산출물은 `backend/build/reports/performance/spatial.json`으로 재현한다.
- 공공 API 공식 명세를 확인하고 실제 키로 2개 레코드를 조회했다. HTTP 200, resultCode 0. 최초 조회 당시 전화번호를 제외한 최소 필드 결과만 권한 600의 Git 제외 파일에 보관했다. 서비스 DB에 저장·공개 조회까지 완료한 상태가 아니며 실제 원산지 자료도 없다.

- P04 `verify`도 실행했다. 성능을 포함한 전체 검사 중 아직 미구현인 외부 수집 계약 `test-contract`만 실패(exit 2), 나머지 통과. 다음 수집 작업에서 실제 계약 검사를 구현한다. 기록: `/tmp/kimchimap-search-full-verify.log`.

## 업소 전화번호 추가 검증 (2026-09-07, P05 진행 중)

- 공개 업소 연락처의 표시 원문·연결용 번호·출처를 저장하고 상세 API에 반영했다. 출처 재게시 권한이 없으면 번호를 반환하지 않는다.
- `/restaurants/:id/contact`에서 실제 상세 API를 사용하는 연락처 화면과 `tel:` 링크를 연결했다. 전체 지도·목록·상세 동선 연결은 P08에 남아 있다.
- `pnpm --dir frontend test:run`: 9개 통과. 번호 없음·잘못된 번호·404·정상 전화 링크와 출처 표시를 검증했다. typecheck, lint, build, api:check도 통과했다. 명령은 모두 `./scripts/runtime`으로 실행했다.
- `backend/gradlew -p backend spotlessApply integrationTest --tests kr.kimchimap.OriginModelIntegrationTest`: 기존 상세 검증과 전화번호 공개 권한·DB 형식 제한 포함 11개 통과.
- `backend/gradlew -p backend spotlessApply integrationTest --tests kr.kimchimap.IngestionIntegrationTest`: 실제 PostGIS DB에서 수집·전화번호 공개·멱등 재실행·부분 수집 재개·비정상 레코드 격리·429 지연·오래된 실행 임대의 쓰기 거부를 검증하는 4개 테스트 통과. 외부 응답은 테스트 대역이다.
- 실행 임대 상실 테스트의 첫 실행은 Spring Repository 예외 변환으로 실패했다. 기대한 원인 예외와 메시지를 명시적으로 검증하도록 수정했고, 임대 소유자 변경 및 체크포인트 검사는 유지했다.
- 실제 업소 전화번호의 수집→서비스 DB→공개 조회 실연동과 수집 기능 전체 검증·PR 병합은 아직 완료하지 않았다.

## 공공데이터 실수집 (2026-09-07)

- 런타임의 Gradle ingestRun 실행: 실제 HTTPS API 2페이지, 200건 읽음·199개 저장·애매한 매칭 1건 격리. 상태 PARTIAL, 다음 페이지 3. 로컬 V4 마이그레이션 적용 성공.
- DB 조회: 전화번호 7개, 유효 좌표 196개, 원산지 기록 0개. 실업소 원문과 키를 공개 저장소에 추가하지 않았다.
- 실행 중인 실제 서버에서 수집한 업소 상세·1km 반경 검색 모두 HTTP 200. 해당 업소 포함, 전화번호·출처 반환, 원산지 scope 0 확인.
- 전체 원천 수집·카카오·원산지 실연동 완료를 의미하지 않는다.

## P05 전체 검증

`./scripts/harness verify` 최종 exit 0. 하네스 28개, 백엔드 단위·계층 20개, 실제 PostGIS/Redis 통합 32개, 공식 합성 계약 5개, 성능 1개, 프론트 9개, 모바일/PC E2E 2개 통과. 포맷·린트·타입·API 계약 일치·양쪽 빌드 통과. 기존 성공 결과는 Gradle의 변경 감지 캐시를 활용했고 새 수집 테스트는 실제 실행했다.

첫 전체 검사에서는 실연동 확인용 서버가 8080 포트를 점유해 E2E가 실패했다. 확인용 서버를 종료하고 원래 E2E 설정 그대로 전체 검사를 재실행하여 통과했다. 검사 범위·보안·서버 재사용 규칙을 완화하지 않았다. 최종 실행 로그는 로컬 `/tmp/kimchimap-ingestion-verify.log`에 있다.

## P06a 서비스 세션 검증

전체 `./scripts/harness verify` exit 0. 검증 로그는 로컬 /tmp/kimchimap-auth-verify.log에 있다. 실제 PostGIS/Redis에서 회전·실발급 토큰 재사용·임의 토큰·동시 갱신·로그아웃·현재 권한·정지·탈퇴·Redis 유실/일시 중단·CSRF/Origin/CORS·쿠키·상한을 검사했다. JWT의 서명 변조·만료·잘못된 issuer/audience/kid·nbf·미래 iat·필수 클레임·알고리즘 혼동과 운영 키 누락을 거부했다.

초기 JWT 시간 테스트는 Nimbus와 Spring이 서로 다른 Clock을 사용해 실패했다. 서명·알고리즘은 Nimbus에 두고 모든 시간·클레임 검증을 동일한 Clock의 Spring validator로 통합했다. 거부 테스트를 유지한 상태로 재실행해 통과했다. 검사 축소나 유예 확대는 없다.

이번 회원은 테스트 내부 Service가 만든 합성 데이터다. 실제 카카오 로그인 성공을 확인한 것이 아니며 이를 위한 운영 로그인 우회 endpoint도 없다. P06b에서 OAuth/OIDC와 브라우저 흐름을 연결한다.

커밋 검사에서 개인 키 파일 헤더를 파싱하는 문자열이 비밀키로 탐지되었다. 검사기를 완화하지 않고 Nimbus의 표준 개인 JWK 읽기로 교체했다. 실제 키를 코드에 추가하거나 문자열을 분할하여 검사를 피하지 않았다. 관련 키 로딩·서명 테스트와 전체 검증을 다시 수행한다.

## 카카오 로그인·브라우저 인증 작업

- 브랜치 feat/kakao-login, 기준 dev bee2709. P06a PR #6 병합·로컬/원격 임시 브랜치 삭제 확인.
- `./scripts/harness verify` exit 0. `/tmp/kimchimap-kakao-verify.log`에 실행 기록. 하네스 28, 백엔드 단위/계층 26, 실제 PostGIS·Redis 통합 45, 외부 계약 5, 성능 1, Vitest 16, Playwright 모바일·PC 6건 통과. 실패·오류·skip 0. 변경 없는 작업은 Gradle 캐시를 사용했다.
- 통제된 OIDC 서버에서 코드 교환/PKCE, state·브라우저 결합, nonce/issuer/audience/만료/서명 거부, 일회성·TTL, Redis 장애 거부를 검증했다. 이 공급자는 test 소스에만 있다. 실제 카카오 사용자 동의·로그인은 미확인이다.
- 프론트 갱신 single-flight, 탭 잠금, 캐시 정리, 늦은 응답 차단, 읽기 1회 재시도와 쓰기/403 재전송 금지, 장애 시 재시도 제한을 검증했다.
- 회원 메뉴 추가로 기존 API mock의 동일 Response 객체가 두 요청에서 재사용되는 문제가 드러났다. 테스트 요청별 새 응답을 반환하도록 수정했으며 기존 연락처 검증을 유지했다.
- 로그인 진입 경로의 실제 302/429/503 계약을 추가하고 생성 타입을 갱신했다. PR 준비 검증에서 최종 산출물을 다시 확인한다.

## 즐겨찾기·비공개 증빙 작업

- 브랜치 feat/bookmarks-media, 기준 dev 843a3a5. PR #7 병합·임시 브랜치 삭제·원래 dev 동기화 완료.
- 최초 전체 verify는 새 브라우저 테스트 파일의 포맷 검사만 실패했다. 나머지 검사는 통과했지만 전체 성공으로 보고하지 않았다. 해당 파일 포맷을 수정하고 JPEG EXIF 제거·관리자 증빙 접근 검증을 보강한 뒤 `./scripts/harness verify`를 다시 실행해 exit 0을 확인했다. 기록: `/tmp/kimchimap-media-verify-final.log`.
- 단위/계층 33, 실제 PostGIS·Redis 통합 49, 외부 계약 5, 성능 1, 프론트 22, 모바일·PC 브라우저 8, 하네스 28건 통과. 실패·오류·skip 0. 변경 없는 Gradle 작업은 캐시를 사용했다.
- 사진 위장·크기·픽셀 폭탄, PNG 메타데이터/JPEG EXIF 제거, 저장 충돌·심볼릭 링크, 소유권/관리자 조회, 요청 제한, 임시/고아 정리와 ATTACHED 보존을 확인했다. 실제 제보 사진은 테스트나 Git에 사용하지 않았다.
- 자동 타입 검사에서 기존 검색 Item과 즐겨찾기 Item의 OpenAPI 이름 충돌을 발견했다. BookmarkItem 이름을 명시하고 계약을 재생성했으며 수동 타입 복사로 우회하지 않았다. 프론트 테스트의 mock 초기화 훅이 mock 함수를 반환해 종료 훅으로 실행되는 오류도 수정했다.
- 저장된 카카오 앱 설정으로 공식 인가 URL을 1회 호출했다. HTTP 302와 accounts.kakao.com 이동, KOE 오류 없음까지만 확인했으며 키·전체 URL·응답 본문은 출력하지 않았다. 사용자 동의·로그인 완료는 미확인이다.
- 사용자가 협회 데이터 자동 수집·재게시 허가가 아직 없다고 확인했다. 해당 소스는 이용 허락 대기이며 자동 수집하지 않는다. 이 조건은 나머지 기능 구현을 중단시키지 않는다.

## 제보·검수 작업 진행

- 브랜치 feat/reports-review, 기준 dev 9ab3a3f. PR #8 병합·임시 브랜치 삭제·원래 dev 동기화 완료.
- 개별 실행에서 제보 통합 6건과 프론트 27건이 통과했다. 첫 전체 검증은 포맷, 기존 이미지 통합 DB 연결 시간 초과, 새 브라우저 테스트의 불완전한 인증 응답 때문에 실패했다. 성공으로 처리하지 않았다.
- 테스트 DB 준비 조건을 포트 열림에서 실제 앱 계정의 TCP SQL 성공으로 강화했다. 대기 제한 2분을 늘리거나 테스트를 생략하지 않았다. 브라우저 합성 인증 응답에 실제 계약의 tokenType을 추가했으며 서비스 인증 검증은 변경하지 않았다. 포맷을 적용한 뒤 전체 재검증 중이다.
- 모바일·PC 제보 재전송 키 보존과 관리자 검수 충돌 안내를 브라우저 테스트에 포함했다. 실제 외부 로그인 또는 브라우저에서 실제 백엔드 승인까지 연결된 검증과는 구분한다.

- 최종 `./scripts/harness verify` exit 0. `/tmp/kimchimap-reports-verify-final.log`에 기록했다. 단위·계층 33, 실제 PostGIS/Redis 통합 55, 외부 계약 5, 성능 1, 프론트 27, 모바일·PC 브라우저 12, 하네스 28건 통과. 실패·오류·skip 0이며 변경 없는 Gradle 작업은 캐시를 사용했다. API 생성 계약·포맷·린트·타입·양쪽 빌드도 통과했다.

## 관리자 수집 관리 작업

- 브랜치 feat/admin-ingestion, 기준 dev b5ad33a. 제보·검수 PR #9 병합·임시 브랜치 삭제·원래 dev 동기화 완료.
- 개별 관리자 통합 3건과 프론트 30건 통과. 권한·허가·소스 식별자·요청 상한·동시 요청 키·추가 예산 중복 방지·내부 필드 비노출을 검증했다.
- 전체 검증의 단위·계층 37건, 실제 DB/Redis 통합 58건이 통과했다. 키 미설정 503과 별도 스레드·정기 수집 비활성 유지, 권한 명령의 변경 계획·명시적 적용을 단위 검증했다. 최종 실행 결과는 /tmp/kimchimap-admin-ingestion-verify.log에서 확인 중이다.
- 정기 수집이 꺼지면 사진 정리도 등록되지 않던 설정을 수정했다. 스케줄 등록을 유지하되 외부 정기 수집 생성은 기존 활성 조건으로 제한한다. 테스트는 자동 작업 실행을 끄고 통제된 호출을 사용한다. 실행하지 않은 외부 수집을 성공으로 보고하지 않는다.

- 최종 전체 verify exit 0. 단위·계층 37, 통합 58, 외부 계약 5, 성능 1, 프론트 30, 브라우저 12, 하네스 28건, 실패·오류·skip 0. 포맷·린트·타입·계약 일치·양쪽 빌드 통과. 변경 없는 Gradle 결과는 캐시를 사용했다. 실제 회원 권한 변경과 신규 외부 수집은 실행하지 않았다.
- 전체 검증 후 요청 키 정리의 실제 SQL 검증을 추가했다. 관리자 통합 테스트 4건을 다시 실행해 통과했으며 만료 키 삭제·최근 키·불변 감사 보존과 제보 만료 정리 SQL 실행을 확인했다. 기록: /tmp/kimchimap-admin-ingestion-cleanup-tests.log. PR 준비에서 추가 테스트를 포함한 전체 검증을 다시 수행한다.
