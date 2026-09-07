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
- 공공 API 공식 명세를 확인하고 실제 키로 2개 레코드를 조회했다. HTTP 200, resultCode 0. 전화번호 등 불필요한 필드를 제외한 결과만 권한 600의 Git 제외 파일에 보관했다. 서비스 DB에 저장·공개 조회까지 완료한 상태가 아니며 실제 원산지 자료도 없다.

- P04 `verify`도 실행했다. 성능을 포함한 전체 검사 중 아직 미구현인 외부 수집 계약 `test-contract`만 실패(exit 2), 나머지 통과. 다음 수집 작업에서 실제 계약 검사를 구현한다. 기록: `/tmp/kimchimap-search-full-verify.log`.
