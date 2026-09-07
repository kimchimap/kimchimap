# 백엔드 작업 지침

루트 지침과 [설계](../docs/architecture.md), [모델](../docs/data-model.md), [보안](../docs/security.md)을 먼저 읽는다.
- `kr.kimchimap` 아래 기능별 controller/service/repository/entity/dto. 필요한 구체 Service만 만든다.
- Controller는 검증·주체 전달·DTO 변환, Service는 유스케이스·트랜잭션, Repository는 영속성·조회다.
- 다른 기능의 쓰기는 Service를 통한다. 조회 전용 SQL Repository는 허용하되 변경 우회 금지.
- OSIV off, ddl-auto validate, Flyway 불변 마이그레이션. DB 소유자·마이그레이션·앱 계정 분리.
- 조회 트랜잭션 readOnly, 명시적 fetch/projection으로 N+1 회피. 검수 승인과 공개 반영은 같은 트랜잭션.
- 원산지 품목·용도·국가 혼합·근거·각 날짜를 분리하고 상충 기록을 보존한다.
- 공간검색은 바인딩 SQL과 PostGIS. Point는 경도/위도, 5174는 실제 변환, 미터는 geography.
- OAuth state/PKCE/nonce, JWT 검증과 활성 Redis 세션을 함께 적용. Redis 장애는 보호 API 거부.
- 파일·소유권·CSRF·관리자 권한은 서버 검증. 운영 비밀 누락 시 시작 실패. 인증 우회 코드 금지.
- `./scripts/harness test-backend`, `test-integration`, `api-check`, `verify` 실행 결과를 기록한다.
- ArchUnit, 실제 PostGIS/Redis Testcontainers, 빈 DB Flyway, 경쟁 조건·외부 계약 테스트는 [테스트 목록](../docs/testing.md)을 따른다.
