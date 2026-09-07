# 공간 검색 성능 측정

2026-09-07, 개발기 aarch64 Java 프로세스·Docker linux/amd64 에뮬레이션. 단독 Testcontainers DB에 실제 업소와 혼동하지 않는 가상 업소 20,000개와 원산지 20,000개를 생성했다. 통계 ANALYZE 뒤 애플리케이션과 동일한 검색 SQL을 바인딩하여 EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)으로 측정했다. 순차 스캔을 강제로 끄지 않았다.

| 검색 | 관측 실행 시간 | 확인 인덱스 |
| --- | --- | --- |
| 반경 1,000미터 + 배추 국내산 | 55.894 ms | restaurant_geography_gist |
| 지도 영역 + 배추 국내산 | 1.183 ms | restaurant_location_gist |

이 결과는 한 환경의 개별 실행 측정이며 처리량·상한 지연시간·운영 SLA를 보장하지 않는다. 반경 검색은 첫 측지 연산의 초기화와 캐시 영향을 포함한다. 반복 분포·동시성·실제 자료 밀집도는 출시 전 추가 측정 대상이다.

재현: `./scripts/harness test-performance`. 매번 실제 성능 테스트를 실행하고 `backend/build/reports/performance/spatial.json`에 DB/PROJ 버전·실제 실행 계획·측정값을 남긴다. 가상 데이터는 테스트 컨테이너에만 존재한다. 일반 통합 검증과 독립 task로 분리했으며 `verify-unit spatial-search`와 전체 `verify`에 포함했다.

DB: PostgreSQL 18.6 (Debian 18.6-1.pgdg13+2) on x86_64-pc-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit

PostGIS: POSTGIS="3.6.4 94d984b" [EXTENSION] PGSQL="180" GEOS="3.14.1-CAPI-1.20.5" (compiled against GEOS 3.13.1) PROJ="9.8.1 NETWORK_ENABLED=OFF URL_ENDPOINT=https://cdn.proj.org USER_WRITABLE_DIRECTORY=/var/lib/postgresql/.local/share/proj DATABASE_PATH=/usr/share/proj/proj.db" (compiled against PROJ 9.6.0) LIBXML="2.9.14" LIBJSON="0.18" LIBPROTOBUF="1.5.1" WAGYU="0.5.0 (Internal)" TOPOLOGY
