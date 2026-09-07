# 프론트엔드 작업 지침

루트 지침과 [화면](../docs/frontend.md), [API](../docs/api.md), [보안](../docs/security.md)을 먼저 읽는다.
- 기능별 UI·hooks, 공통 UI와 API 클라이언트 경계. 화면·오류·접근성 설명은 한국어.
- TanStack Query는 서버 상태, 지도 객체는 SDK 어댑터, 선택 필터는 URL/화면 상태로 관리한다.
- OpenAPI에서 생성한 타입 사용. 수동 복사 금지. `api-generate` 후 `api-check` 실행.
- Access Token은 메모리, Refresh는 HttpOnly 쿠키. 브라우저 저장소·URL에 토큰 저장 금지.
- 갱신 single-flight와 Web Locks/BroadcastChannel 경합 완화, 실패 재시도 제한, 비멱등 요청 자동 재전송 금지.
- 로그아웃·사용자 변경 시 사용자별 Query 캐시와 토큰을 제거한다.
- 카카오 SDK는 중앙 Promise 로더. StrictMode 정리·리스너 제거·debounce·취소·오래된 응답 무시.
- VITE_*에는 서버 키/비밀 금지. 지도 실패에도 목록과 오류 안내를 제공한다.
- 모바일 하단 시트·PC 사이드 목록, 키보드·focus·label·대비·터치 영역을 검증한다.
- `./scripts/harness test-frontend`, `test-e2e`, `typecheck`, `verify` 결과를 기록한다.
- 외부 SDK mock 성공은 실제 카카오 연동 성공이 아니다.
