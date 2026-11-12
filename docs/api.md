# API 계약

기본 경로는 `/api/v1`이다. 실제 리소스·입력·응답·enum은 [생성 OpenAPI](api/openapi.json)와 frontend/src/api/generated.d.ts를 기준으로 한다. `api-generate`로 생성하고 `api-check`로 서버 계약과 비교한다. 아래 내용은 자체 서비스 계약이며 외부 데이터 제공 API 명세가 아니다.

| 기능 | 리소스·경계 |
| --- | --- |
| 공개 검색·상세 | POST /restaurants/search, GET /restaurants/{id}, GET /restaurants/{id}/likes |
| 카탈로그 | GET /catalogs/ingredients, /catalogs/countries |
| 인증 | GET /auth/login/kakao, /auth/callback/kakao, /auth/csrf; POST /auth/refresh, /auth/logout, /auth/logout-all |
| 회원 | GET/DELETE /members/me |
| 좋아요 | GET /likes, GET/PUT/DELETE /likes/{restaurantId} |
| 사진 | POST /media, GET /media/{id}, GET /media/{id}/public |
| 제보 | POST/GET /reports, GET/PATCH /reports/{id}, POST /reports/{id}/withdraw |
| 제보 검수 | GET /admin/reports 및 상세, POST /admin/reports/{id}/reviews |
| 오류 신고 | POST/GET /information-reports 및 GET 상세, 관리자 목록·상세와 POST /admin/information-reports/{id}/resolution |
| 업소 정정 | GET /admin/restaurants/{id}, POST /admin/restaurants/{id}/corrections |
| 원산지·지정·매칭 | /admin/origins, /admin/origin-corrections, /admin/designations, /admin/matches |
| 수집 | /admin/ingestion/sources, /jobs 및 상세·events·quarantines, POST /sources/{id}/runs |

## 공개 조회

지도 영역 또는 반경과 원산지 groups를 함께 받는다. 현재 화면은 동일 김치 scope의 배추/고춧가루 DOMESTIC 조건을 구성한다. 서로 다른 재료는 AND, 같은 조건 안 복수 국가는 OR다. 혼합·미확인·분쟁·만료는 순수 국내산 조건에 해당하지 않는다. SQL 파라미터를 바인딩하고 공간 계산은 DB에서 수행한다.

반경은 미터 단위이며 최대 50km, 페이지 limit은 최대 100이다. 지도 UI 누적 상한은 200이며 넓은 영역·잘린 결과를 전체 결과처럼 표시하지 않는다. 거리 동률은 업소 ID로 정렬한다. cursor는 조건·조회 기준 시점·마지막 거리/ID를 포함하고 변조·다른 조건 재사용을 거부한다. asOf는 완전한 DB snapshot 보장이 아니다.

상세는 원산지 관찰일·원문 갱신일·수집일·마지막 수집 성공일·검토일·승인일을 구분한다. Claim.approvedAt은 실제 제보 승인 이력이며 수집일로 대체하지 않는다. 지정 정보는 별도 적용 품목·기준·지정일을 유지한다. 연락처 contact는 검증된 display/number/sourceName이며 출처의 재게시 허가가 없으면 공개하지 않는다.

## 인증·오류

카카오 callback은 `/api/v1/auth/callback/kakao`, 로컬 Redirect URI는 `http://localhost:5173/api/v1/auth/callback/kakao`다. 인가 코드·state·PKCE·OIDC를 서버에서 처리하고 Refresh 쿠키를 설정한 뒤 토큰 없는 `/auth/complete`로 이동한다. 키가 없으면 시작을 503으로 거부한다.

갱신·로그아웃은 동일 Origin과 CSRF 토큰/쿠키가 필요하다. 전체 로그아웃은 서비스 Bearer도 필요하다. 보호 API는 JWT와 활성 Redis 세션·현재 계정 상태를 검사한다. 회원 탈퇴는 전체 세션 폐기와 공급자 식별자 연결 제거를 수행하며 동일 카카오 재가입은 별도 회원이다.

오류는 application/problem+json의 한국어 안내·안정적인 code·traceId·필드 오류를 제공한다. 400 입력, 401 인증, 403 인가/CSRF, 404 비공개/미존재, 409 버전, 413 크기, 415 형식, 422 처리 불가, 429 제한, 503 의존 장애를 구분한다. 내부 SQL·Entity·스택·토큰은 노출하지 않는다. 인증·개인 자료는 no-store다.

## 제보·신고·정정

제보 생성은 UUID Idempotency-Key와 기존 restaurantId 또는 newRestaurant 중 하나를 요구한다. 신규 정보는 이름·주소·선택 좌표·선택 업소 전화번호이며 승인 전에는 업소를 생성하지 않는다. 표시판 사진·공개 동의·관찰일·같은 품목의 원산지 주장을 받는다. UI는 현재 김치 두 재료만 선택하고 내부 모델의 확장 가능한 카탈로그는 유지한다.

검수는 expectedVersion·사유·결정이 필수다. 승인은 kimchiReviewed=true, 새 업소는 locationReviewed=true가 필요하다. 배추/고춧가루 중 하나의 국내산 근거가 있어야 공개한다. 동일 이름·주소의 기존 업소는 자동 병합하지 않고 검토 충돌을 반환한다. 생성·원산지 공개·검수 이력·사진 공개 전환은 같은 트랜잭션이다. 승인 후 restaurantId로 공개 업소를 연결한다.

사진은 실제 내용·형식·픽셀·크기를 검증하고 재인코딩한다. 원본과 미승인 정제본은 소유자/관리자만 접근한다. 공개 주소는 개인정보 검토 후 공개된 정제본만 반환한다. 첨부 ID의 소유권은 서버가 검사한다.

오류 신고는 UUID Idempotency-Key·업소·유형·설명·최대 3개 사진을 받는다. 본인 조회와 관리자 조회를 분리한다. resolution은 expectedVersion·RESOLVED/DISMISSED·사유이며 선택적 correction(원산지 철회)·restaurantCorrection(업소 정정)을 신고 대상에만 같은 트랜잭션으로 반영한다. 접수만으로 공개 정보는 바뀌지 않는다.

업소 정정은 전체 스냅샷·expectedVersion·사유를 받으며 이름/주소/좌표 변경은 동일 업소 확인, 전화 변경은 공개 가능한 업소 연락처 확인을 요구한다. 변경한 필드만 수집 덮어쓰기로부터 보호하고 이전/새 값·행위자·시각을 불변 기록한다. 관리자 조회는 공개 제외 업소도 최근 100건의 이력과 반환한다.

원산지 정정은 같은 scope·식재료의 기존 기록만 철회하고 원문과 감사 이력을 보존한다. 지정·매칭·수집은 현재 소스 자격과 이용 조건을 검사한다. 수집 소스의 adapterAvailable은 실제 등록 어댑터 여부이며 현재 실사용 어댑터는 없다. 부적합 일반음식점 소스는 재실행·과거 관찰본 반영도 차단한다.
