# 테스트 수용 목록과 결과 정책

현재 하네스와 앱 기반 단위·실제 DB/Redis·생성 계약·모바일/PC E2E를 구현했다. 아래 도메인별 수용 사례는 후속 작업에 추가해야 한다. 외부 키 없이 단위·실제 PostGIS/Redis·허용 계약 fixture·통제 E2E를 실행하며 키가 없다는 이유로 전부 skip하지 않는다. 실제 외부 smoke는 별도다.

| 묶음 | 필수 사례 | 도구/단계 |
| --- | --- | --- |
| 백엔드 기본 | 도메인·Service·입력 검증, Controller→Repository/Entity 노출/기능 순환 금지, 빈 DB Flyway, ddl validate | JUnit·ArchUnit·PostGIS Testcontainers |
| 공간 | 반경 안/밖/경계, 좌표 순서, EPSG5174 실제 변환·기준점 오차, 잘못된/누락 좌표, bbox 경계, 거리 동률, cursor/결과 상한, 필터 후 정렬 | 실제 PostGIS, P03/P04 |
| 원산지 | 미확인!=수입, 국가미표기 수입, 혼합!=순수 국내, 다른 김치 scope 합성 금지, 만료/분쟁, 재수집 확인일 불변, 지정→부재료 추정 금지, 결정적 publication | 단위+DB, P03/P04 |
| OAuth/JWT | 정상/실패/state/nonce/PKCE, 변조·만료·issuer/audience·alg none/혼동·kid·jti/sid 검증, 코드/토큰 URL 유출 없음 | 통제 공급자+단위, P06 |
| Redis 인증 | 회전/실제 이전 토큰 재사용/family 폐기, 임의 토큰 공격 불가, 동시 갱신/응답 유실, 로그아웃/전체 로그아웃 후 JWT 거부, 탈퇴·정지·현재 권한, TTL/절대 만료, Redis 장애/유실 | 실제 Redis 통합, P06 |
| CSRF/인가 | 쿠키 refresh/logout CSRF·Origin 거부, wildcard CORS 금지, USER→ADMIN 거부, prod 비밀 누락 실패, local 예외 차단 | Spring Security 통합 |
| 제보/미디어 | 본인만 수정/철회, 검수 상태별 전이, 형식·크기·픽셀·위장 확장자·SVG/HTML·EXIF, 비공개 접근·타인 연결 금지, path traversal, 중복 승인/동시 409, 승인 후 수정으로 변조 불가 | 실제 파일·DB, P07 |
| 수집 | 페이지/checkpoint 재개/멱등, ID 중복/모호 지점, 변경 감지, 비정상 형식·좌표 격리, timeout/429 Retry-After/제한 재시도, 부분 실패, lease 상실 fencing, 목록 누락!=폐업, 정정 보존 | 계약 fixture+실제 DB, P05 |
| 프론트 | 필터·빈 상태·오류·로그인 만료, map SDK 경계/cleanup/오래된 응답, single-flight/탭 경합, 사용자 캐시 제거, 접근성 | Vitest·RTL·mock, P08 |
| E2E | 지도/목록→필터→상세→테스트 로그인 제보→관리자 검수→공개 반영, 권한 거부, 업로드 실패·검수 충돌, 모바일/PC | Playwright, 통제 test provider |
| 성능 | 충분한 test/demo 가상 업소·필터 분포로 EXPLAIN ANALYZE BUFFERS, 실제 인덱스, 대표 query 시간과 환경/에뮬레이션 기록 | P04/P09, 미측정 성능 보장 금지 |
| 하네스 | 한국어 제목/본문·PR 템플릿/릴리스 경계, 임시 Git 실제 훅 main/dev commit·HEAD 대상 push·삭제/강제 push 거부, 작업 branch 정상 흐름, 변경 보존·cleanup OID·설정 dry-run | Python unittest, 현재 구현 |

미디어 테스트는 운영 개인정보 없는 자체 합성 파일. 합성 업소는 `[TEST] 가상김치식당`처럼 구분, fixture는 운영 자동 주입 금지. E2E 테스트 공급자는 test 소스셋/프로필에서만 가능하고 prod에 인증 우회 route가 없어야 한다.

api-generate는 실행된 Spring OpenAPI 문서를 deterministic snapshot으로 만든다. api-check는 새 생성 문서·타입을 임시 폴더에서 생성한 뒤 커밋된 계약과 비교하고 차이를 실패 처리한다. 단순 파일 존재 체크를 계약 테스트로 인정하지 않는다.

verify는 핵심 검사 전체를 실행하고 실패들을 모아서 비정상 종료한다. verify-harness 성공과 verify 실패는 동시에 정상적인 현재 상태다. 실행 로그는 날짜/환경/명령/exit/범위와 함께 validation.md에 기록한다. 실패를 없애기 위한 테스트 삭제·무조건 skip·범위 축소·일괄 규칙 해제·보안 약화·성공 코드 위장은 금지한다.

P03 검증: OriginValueTest/OriginPublicationPolicyTest는 분류·혼합비·품목 경계·상충·만료·철회·정렬을 검증한다. OriginModelIntegrationTest는 실제 PostGIS에 대해 공개/비공개/미승인 근거, 날짜 보존, 지정 독립성, 카탈로그 확장, 수정 금지, 외부 ID 중복, 잘못된 좌표와 상세 상한을 검증한다. ApplicationIntegrationSupport는 테스트 클래스별 컨테이너 생명주기에 맞춰 Spring 문맥을 폐기하여 이전 컨테이너 연결을 재사용하지 않는다.

P04 검증: 검색 입력/서명 cursor/Redis 장애 대체 단위 테스트, 실제 DB 반경·영역 경계/원산지 조합/동률 페이지/변환/실제 Redis 요청 제한을 검증한다. 성능은 독립 `performanceTest`로 실행하고 단위/통합에서 중복 실행하지 않는다. 전체 verify와 spatial-search 작업 단위에는 성능을 포함하며 테스트 skip이 아니다. [측정 결과](performance.md)를 참조한다.

P07b: ReportReviewIntegrationTest는 실제 PostgreSQL/Redis에서 멱등 제출·소유권·불변 제출본·상태 전이·동시 검수·상충 원산지·사진 공개 권한과 트랜잭션 rollback을 검사한다. 프론트엔드 테스트와 통제된 API 브라우저 테스트는 로그인 접근·원산지 구분·요청 키 유지·모바일 너비를 검사한다. 브라우저 mock 성공을 실제 백엔드 전체 연결이나 외부 로그인 성공으로 간주하지 않는다.

관리자 수집 검증: AdminIngestionIntegrationTest는 관리자 인가·내부 필드 비노출·소스 허가·입력 상한·동시 요청 키·중복 예산을 실제 DB/Redis로 검사한다. 단위 검증은 키 미설정 시 작업 생성 차단, 별도 스레드 처리·정기 수집 비활성 유지, 회원 권한 명령의 변경 계획·명시적 적용을 포함한다.

OriginCorrectionIntegrationTest는 실제 HTTP·PostGIS 검색으로 정정 전 분쟁 제외, 명시적 잘못된 기록 철회 후 국내산 매칭, 새 상충 기록 반영 후 재제외를 검사한다. 타 그룹·일반 사용자 거부와 stale version·불변 감사·전체 철회 상태도 확인한다. 혼합 옵션 생략의 false 기본값을 같은 실제 검색 요청으로 검증한다.

DataReviewIntegrationTest는 실제 DB·Redis·HTTP로 지정 전용 허가, 지정 원문·날짜·불변 이력, 원산지 비생성, 공개 허가 철회, 매칭 동시 결정, 서로 다른 외부 ID의 동일 지점 경합, 수집 시각 보존, 식별자 충돌과 별도 지점 생성을 검증한다. 프론트는 허가 없는 등록 차단·날짜/버전 전달과 매칭 선택·충돌 입력 보존을 검사한다. 합성 소스 성공을 협회 실연동으로 취급하지 않는다.
