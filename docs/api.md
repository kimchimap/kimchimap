# API 구현 계약 초안

이 문서는 자체 서비스의 설계이며 외부 제공 API 명세가 아니다. 구현 단계에서 springdoc OpenAPI 산출물을 커밋하고 타입을 생성한다. 현재 /api/v1/system/status가 실행되며 [생성 계약](api/openapi.json)과 frontend 생성 타입을 비교한다. 업소 상세와 식재료·국가 카탈로그 GET도 구현한다. 검색·인증·제보·관리자 endpoint는 후속 구현 계약이다.

| 메서드·경로 (/api/v1 기준) | 권한·의미 |
| --- | --- |
| POST /restaurants/search | 공개, bbox 또는 center/radiusMeters 중 하나와 filters·cursor·limit |
| GET /restaurants/{id} | 공개 상세, matched scope·공개 origin/evidence/designation |
| GET /catalogs/ingredients, /catalogs/countries, /catalogs/filters | 공개 활성 카탈로그 |
| GET /auth/login/kakao | OAuth 시작; callback 경로는 Security 설정에서 고정 |
| GET /auth/csrf | CSRF 획득, Cache-Control no-store |
| POST /auth/refresh, /auth/logout, /auth/logout-all | 쿠키+CSRF+Origin, 마지막 두 개는 세션 폐기 |
| GET /members/me; DELETE /members/me | 활성 세션, 탈퇴는 세션 전부 폐기 |
| GET /bookmarks; PUT/DELETE /bookmarks/{restaurantId} | 본인, PUT 멱등 |
| POST /media; GET /media/{id} | multipart 본인 업로드, 비공개는 소유자/관리자만 |
| POST/GET /reports; GET/PATCH /reports/{id} | 본인 제보, version 필수 수정 |
| POST /reports/{id}/withdraw | 본인 대기 상태 철회 |
| GET /admin/reports; GET /admin/reports/{id} | ADMIN |
| POST /admin/reports/{id}/reviews | ADMIN, decision/reason/expectedVersion, 충돌 409 |
| POST /admin/origin-corrections; POST /admin/designations | ADMIN, 사유·불변 revision |
| GET /admin/matches; POST /admin/matches/{id}/reviews | ADMIN 매칭 검토 |
| GET /admin/ingestion/jobs; GET /admin/ingestion/jobs/{id} | ADMIN 수집 상태 |
| POST /admin/ingestion/sources/{id}/runs | ADMIN, 허용된 소스만, 202 + jobId |

검색 입력: 위도 [-90,90], 경도 [-180,180], south<north/west<east, 한반도 서비스 영역 밖 데이터는 안내. 날짜변경선 bbox는 초기 미지원 400. radiusMeters 1~50000, limit 1~100, 지도 최대 200, ingredient 조건 최대 10/국가 20. 너무 넓은 bbox는 확대 안내와 truncated=true, 전체 결과처럼 표시하지 않는다. 거리순 tie-break id, cursor는 검색 조건 해시와 마지막 거리/id 및 asOf를 포함하고 변조·다른 조건 재사용을 거부한다. radius/bbox의 필터 의미는 동일하다.

응답에는 items, nextCursor, truncated, asOf, matchedScopes. 근거에 observedAt/sourceUpdatedAt/collectedAt/lastFetchSucceededAt/reviewedAt 및 상태를 분리한다. UNKNOWN을 IMPORTED로 직렬화하지 않는다. 정렬 중 데이터 변경 시 snapshot이 아닌 asOf 일관성의 한계를 명시하고 엄격 snapshot은 필요성이 있을 때 도입한다.

오류는 application/problem+json: type, title(한국어), status, code(안정 영어), traceId, fieldErrors. 400 검증, 401 인증, 403 권한/CSRF, 404 미노출, 409 version, 413 파일, 415 형식, 422 처리 불가, 429 제한, 503 의존 장애. 내부 SQL/Entity/stack은 제외. 캐시 가능한 공개 GET과 인증 no-store 분리.

수집 실행과 제보 생성은 Idempotency-Key를 구현하여 동일 사용자의 같은 키/본문 재전송을 재사용하고 다른 본문은 409. 보관 기본 24시간. 요청 제한 기본안: 공개 검색 IP 60/min, 갱신 세션 10/min, 업로드 사용자 10/hour, 관리자 수집 소스 동시 1개. 공유망 오탐·실측 후 조정하고 보호 기능 Redis 장애 시 거부한다.

카카오 OAuth callback은 `/api/v1/auth/callback/kakao`, local redirect URI는 `http://localhost:5173/api/v1/auth/callback/kakao`로 고정했다. 로그인 구현 단계에서 Spring Security의 callback base URI와 일치시킨다.
