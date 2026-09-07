# 인증·세션 기반 구현

- 목표: 서비스 JWT와 Redis 상태 보유 세션의 회전·폐기·권한 확인을 실제 API와 연결한다.
- 범위: P06을 두 작업으로 나눈다. P06a는 회원 식별·세션·JWT·CSRF·인가·갱신·로그아웃·탈퇴, P06b는 카카오 OAuth/OIDC/PKCE와 브라우저 로그인 흐름이다. 로그인 우회 endpoint는 만들지 않는다.
- 수용 기준: 키 없이 실제 Redis/PostGIS 테스트, JWT 변조·만료·issuer/audience·회전·발급 이력 기반 재사용·임의 토큰·경쟁·폐기·정지·권한·장애·CSRF/CORS 검증. 전체 verify와 PR 병합·브랜치 정리.
- 현재 브랜치: feat/auth-sessions
- 기준 dev: b108ee219497a4b0b38ef316aa426a5512b5eb53
- 완료: P05 PR #5 squash 병합, 임시 브랜치 로컬·원격 삭제, 원래 dev 동기화.
- 결정: auth → member → global 의존 방향. 보호 API는 JWT와 Redis 세션 및 DB의 현재 회원 상태를 모두 확인한다.
- 검증: 시작 시 Git clean 확인. 이번 구현 테스트는 아직 미실행.
- 외부 조건: 카카오 키·설정 준비됨. 실제 카카오 인증은 P06b에서 확인한다.
- 다음 작업: 회원 스키마, Redis Lua 원자 회전·발급 이력, JWT 설정과 API 보안.

## 구현·검증 결과

- 회원 식별·현재 역할/상태/보안 버전, Redis 원자 회전·폐기·발급 이력·TTL·상한, JWT 발급/검증, CSRF·Origin/CORS·쿠키와 보호 API 구현.
- HTTP API: csrf, refresh, logout, logout-all, members/me 조회·탈퇴. 카카오 로그인은 다음 작업이며 우회 로그인 API는 없다.
- 전체 verify exit 0. 코드·계층·실제 Redis/PostGIS·브라우저·성능·계약·빌드 검사 통과. 상세 증거는 validation.md 참조.
- 의존성: Boot BOM의 Spring Security7.1.1/Nimbus10.9.1 사용. 초기 이중 시계 검증 문제는 결정 기록에 근거를 남기고 엄격한 단일 정책으로 수정했다.
- 남은 절차: 커밋·PR 검사·dev 병합·임시 브랜치 삭제. 다음 구현은 카카오 OAuth/OIDC·PKCE·일회성 state·nonce와 프론트 single-flight 갱신이다.
