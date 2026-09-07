# 공간 검색과 페이지 일관성

2026-09-07. 검색은 PostGIS DB에서 공간 필터 후 원산지 조건을 적용하고 거리·UUID 순으로 정렬한다. radius는 geography 미터 단위, bounds는 geometry 4326 교차 판정이다. 반경 경계에서 측지 투영 오차를 흡수하는 허용치는 0.000001미터다. 100미터 경계는 포함하고 100.001미터 위치는 제외하는 실제 DB 테스트를 유지한다.

`groups`는 AND, 각 그룹의 `ingredients`도 AND이며 그룹 안에서 같은 serving_scope를 요구한다. 같은 식재료의 `countries`는 OR다. 예를 들어 반찬 김치의 배추+고춧가루는 한 그룹, 밥의 쌀은 다른 그룹에 넣는다. 반환 `matchedScopes`에는 groupIndex와 실제 품목·용도가 있다. 조건 없는 업소 조회에서는 matchedScopes가 비어 있으며 원산지를 추정하지 않는다.

승인·효력·철회를 asOf에서 판정하고 비공개를 포함한 활성 주장이 상충하면 검색 후보에서 제외한다. 공개 허용된 동등 주장 중 관찰일·원문 갱신일·revision·UUID 내림차순으로 대표값을 정한다. Java UUID의 부호 있는 비교와 PostgreSQL UUID 비교가 달라질 수 있으므로 Java 정책은 표준 UUID 문자열 순서를 사용한다. 국내산 boolean으로 이 규칙을 대체하지 않는다. 성분별 비율 표기 차이도 현재는 보수적으로 상충으로 취급하며 임의 추정해 통합하지 않는다.

기본 한 페이지 20개, 최대 100개다. 추가 1개 조회로 truncated/nextCursor를 결정한다. bounds의 위도 또는 경도 폭이 1도를 넘으면 확대 안내를 반환하며 전체 결과처럼 표시하지 않는다. 상세의 matched scope가 2,000개를 넘으면 명시적인 오류로 조건 축소를 안내한다. SQL은 바인딩하며 최대 식재료 조건 10개, 조건별 국가 20개, 서버 트랜잭션 제한 5초다.

cursor에는 조건 해시·거리·UUID·asOf만 넣고 HMAC-SHA256으로 서명한다. TTL 10분이며 조건 변경·변조·미래 시각은 400이다. 정확한 좌표를 cursor에 저장하지 않는다. 한 요청은 REPEATABLE READ snapshot을 사용하지만 서로 다른 페이지는 DB snapshot을 공유하지 않는다. 페이지 사이의 업소 좌표·승인·공개 상태 변경으로 목록 변화가 가능하며 사용자는 다시 검색할 수 있다. 운영 SEARCH_CURSOR_SECRET은 32바이트 이상으로 외부 설정한다. 로컬은 프로세스별 메모리 키이며 재시작 시 이전 cursor가 무효화된다.

읽기 전용 POST /api/v1/restaurants/search만 CSRF 예외로 둔다. 갱신·로그아웃 등 쿠키 인증 경로는 보호를 유지한다. 검색은 실제 접속 IP의 SHA-256 식별자로 Redis에서 60회/60초 원자적 요청 제한을 한다. IP 해시는 익명화를 보장하지 않으며 Redis TTL 뒤 제거된다. 전달 헤더를 임의로 신뢰하지 않는다. 운영 프록시를 도입할 때 신뢰할 프록시와 IP 복원 설정을 별도 검증해야 한다.

Redis 연결 장애/시간 초과 때 공개 검색은 프로세스별 최대 10,000개 창을 가진 메모리 제한으로 계속 제공한다. 5초 동안 Redis 재시도를 억제한다. 이 장애 대체는 여러 인스턴스의 전역 제한을 보장하지 않는다. 인증 세션 검증의 fail-closed 정책은 바꾸지 않는다.

좌표 정규화는 확인된 SRID 4326/5174만 수용하고 ST_Transform으로 실제 변환한다. 누락·오류·미확인 좌표계·국내 영역 밖을 구분한다. 초기 국내 범위 124~132도/32~39도는 품질 점검용 경계이며 행정구역 소속의 증명이 아니다. 위치를 발명하거나 주소만으로 추정하지 않는다. 변환 왕복 테스트는 수학적 회귀이며 실제 공공 원천 좌표의 정확성 검증은 수집 단계에서도 수행한다.

공식 근거: [ST_DWithin](https://postgis.net/docs/ST_DWithin.html), [ST_Intersects](https://postgis.net/docs/ST_Intersects.html), [ST_Transform](https://postgis.net/docs/ST_Transform.html), [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html). EPSG 레지스트리 직접 조회는 403으로 실패했으며 PostGIS의 좌표계 등록과 변환 결과를 실제 DB에서 확인했다.
