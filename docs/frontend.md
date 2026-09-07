# 화면과 클라이언트 계약

업소 상세는 공개 연락처와 출처, `전화 걸기` 링크를 제공한다. 연결 주소는 검증된 번호의 `tel:` 링크이며 번호가 없거나 형식이 잘못되면 생성하지 않는다. PC의 전화 앱 연결 지원 여부는 사용자 환경에 따른다. 번호 원문도 표시하여 직접 확인할 수 있게 한다. 현재 `/restaurants/:id/contact`에서 실제 상세 API를 조회하여 업소명·주소·번호·출처와 연결 링크를 표시한다. `RestaurantContact`를 전체 상세 화면에서도 재사용하며 지도·목록에서 상세로 이어지는 전체 동선은 P08에서 완료한다.

라우트: `/` 지도·목록, `/restaurants/:id` 상세, `/login`, `/bookmarks`, `/reports/new`, `/reports`, `/admin/reports`, `/admin/reports/:id`, `/admin/ingestion`, `/about/data`. 상세를 닫아도 검색 URL과 지도 위치를 복원한다. 관리자 라우트 보호는 UX이며 실제 권한은 서버에서 검증한다.

모바일은 지도와 드래그 가능한 하단 목록·상세 시트, PC는 지도/사이드 목록 동시 표시. 마커와 목록 선택 동기화, 터치 타깃 최소 44px, 시트 키보드 이동·닫기·focus 복원, 지도 제스처와 스크롤 영역 분리. 필터는 식재료/국가/근거/최신성, 적용 상태·초기화·일치 메뉴를 표시한다.

필수 상태: 로딩, 빈 결과, 위치 거부/시간 초과, 네트워크 오류, SDK 실패/키 미설정, 오래된 정보, 상충, 세션 만료, 업로드 실패, 검수 409. 키가 없어도 실제 API 목록은 동작하도록 설계하며 가짜 지도를 카카오처럼 표시하지 않는다. 위치는 사용자 클릭 뒤 요청하고 저장하지 않는다.

SDK 로더는 모듈 경계의 단일 Promise, 실패 재시도 시 기존 script 상태 정리. React StrictMode effect 재실행에도 map/marker/event cleanup. 지도 이동 300ms debounce, AbortSignal, request sequence로 오래된 응답 무시. SDK LatLng(latitude,longitude)와 DB Point(longitude,latitude)를 변환 함수 경계에서 분리한다. 지도 로고·출처 보존.

TanStack Query는 서버 상태만, 지도 객체는 adapter 내부. 사용자별 query key는 session scope를 포함하며 로그아웃 때 제거. API 타입은 backend OpenAPI에서 생성. 일반 전역 상태 라이브러리를 추가하지 않는다.

필수 label·focus·대비·키보드·오류 aria-live 검증. 색상만 상태 구분 금지. 확인일/수집일, 표시판/지정, 미확인/수입을 각각 한국어로 안내한다. Vitest/RTL에서 SDK 경계 mock, Playwright 390x844 및 1440x900에서 동선 검증.

P06b 구현 화면: `/login`, `/auth/complete`, 공통 회원 메뉴와 로그아웃. 토큰은 auth/session 모듈 메모리, private Query 접두사 캐시만 로그아웃에 제거한다. 공개 업소 캐시는 유지한다. Web Locks가 없으면 탭 간 회전 경합 시 재로그인이 필요할 수 있다. 인증 장애가 공개 조회를 막지 않는다.

P07a 구현 화면: `/bookmarks`, `/media/new`, 업소 연락처 화면의 즐겨찾기 추가/해제. 업로드 컴포넌트는 서버에서 반환한 미디어 ID를 다음 제보 양식에 전달할 수 있다. 사진 업로드 성공은 제보 접수·검수 승인이 아니며 원본·정제본 모두 비공개다.
