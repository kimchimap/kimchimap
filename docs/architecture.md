# 아키텍처

React/Vite → 동일 사이트 `/api/v1` → 단일 Spring Boot MVC → PostgreSQL/PostGIS 및 Redis. 파일 저장은 웹 루트 밖의 로컬 저장소 어댑터. Spring scheduler는 별도 제한된 executor에서 소스 어댑터를 실행한다. 메시지 브로커는 도입하지 않는다.

기능 패키지: restaurant, origin, certification, search, auth, member, bookmark, report, media, ingestion, global. 각 패키지는 controller/service/repository/entity/dto 중 필요한 계층만 둔다. global은 설정·예외·보안 기반만 포함한다.

허용 호출은 Controller→Service→Repository다. Entity는 DTO로 변환하고 API에 노출하지 않는다. 타 기능 쓰기는 해당 Service를 통해야 한다. search는 여러 기능 테이블을 읽는 전용 Repository를 둘 수 있다. ArchUnit으로 Controller→Repository, Service→Controller, repository→service, 기능 순환을 차단한다. 거대한 공통 유틸·불필요한 BaseEntity/Service/Controller·Service/Impl 쌍 금지.

report 승인 유스케이스가 origin 서비스를 호출해 불변 revision을 만들고 publication을 갱신한다. origin은 report를 호출하지 않는다. ingestion이 restaurant/origin/certification 서비스를 조정하며 반대 의존은 금지한다. search는 쓰기를 하지 않는다. auth는 member의 현재 권한·정지를 확인한다. 공통 감사 기록은 쓰기 트랜잭션에 포함한다.

Service가 트랜잭션 경계다. 외부 HTTP·이미지 디코딩은 긴 DB 트랜잭션 밖에서 수행하고 검증 결과만 짧게 반영한다. 검수 상태·감사·공개 승격은 같은 DB 트랜잭션, version 낙관 잠금 충돌은 409다. OSIV=false, ddl-auto=validate. 상세와 목록은 명시적인 projection/fetch로 N+1을 막는다. 컬렉션 fetch join과 페이지네이션 결합을 피한다.

공간검색: location geometry(Point,4326), GiST(location), GiST((location::geography)). `ST_Intersects(location, ST_MakeEnvelope(:west,:south,:east,:north,4326))` 또는 `ST_DWithin(location::geography,ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326)::geography,:radiusMeters)` 사용. 원산지 조건을 적용한 후보를 `ST_Distance`와 id로 안정 정렬한다. 실제 인덱스 선택은 EXPLAIN (ANALYZE, BUFFERS)로 검증한다.

local/test/prod 분리, UTC 저장/Asia-Seoul 표시. 앱 계정에는 DML만, Flyway 계정은 schema DDL, extension은 초기 인프라 관리자만 생성한다. 마이그레이션은 추가하며 이미 적용된 파일을 수정하지 않는다. 운영 설정 누락은 시작 실패로 만든다.
