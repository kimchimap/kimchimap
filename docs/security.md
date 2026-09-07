# 보안 설계와 검증 경계

위협: 토큰 탈취·재사용/경합, IDOR, CSRF, 관리자 권한 상승, 이미지 실행/폭탄, SQL 주입, SSRF, 외부 데이터 지시문, 개인정보·위치 로그 노출. 최소 권한·실패 시 거부를 기본으로 한다. P06a에서 서비스 JWT·Redis 세션·쿠키/CSRF·현재 회원 인가를 구현했다. 카카오 로그인과 비공개 이미지 업로드도 구현 중이며 제보·검수 관련 내용은 아직 후속 설계다. 전체 서비스 보안 완료가 아니다.

카카오: Spring Security OAuth2 Client Authorization Code, PKCE S256, 일회성 state, OIDC를 사용하면 nonce·issuer·audience·서명·만료 검증. 서버 코드 교환, Redirect URI allowlist, 최소 동의. provider+subject로 식별, 이메일 병합 금지. 공급자 토큰은 서비스 토큰으로 사용하지 않고 필요한 기능이 없으면 장기 저장하지 않는다. 콜백은 토큰 없는 `/auth/complete`로 이동하고 CSRF 보호된 갱신으로 Access Token을 받는다.

서비스 Access Token은 RS256 allowlist 기본, 외부 RSA 키와 kid, iss/aud/sub/exp/iat/jti/sid 필수 및 nbf가 있으면 검증. 수명 10분, 시계 허용차 최대 30초. 개인정보·프로필 포함 금지. 서명키는 파일/비밀관리자 외부 주입, 기존 kid 검증을 최대 토큰 수명 동안 유지 후 제거, 유출 시 해당 세션 전부 폐기한다.

Refresh Token은 32바이트 이상 CSPRNG 불투명 난수, 서버에는 SHA-256 해시만 저장한다. 세션 절대 수명 14일, 사용자 복수 세션·family·현재 해시·발급 이력 해시·폐기·만료를 Redis TTL과 절대 시각으로 관리한다. Lua 원자 처리로 현재 해시 확인→이전 발급 이력으로 이동→새 해시 저장. 해시 인덱스에서 실제 발급 이력이 확인된 이전 토큰 재사용만 family 폐기. 임의 문자열은 family를 찾거나 폐기할 수 없다. 발급 이력은 절대 만료까지 보존한다.

상태 보유 인증이다. 모든 보호 API는 서명 검증 후 활성 sid와 현재 회원 정지/권한을 확인한다. 현재/전체 로그아웃·탈퇴·정지·권한 변경은 즉시 기존 JWT에도 반영한다. Redis 장애/유실 시 로그인·갱신·보호 API 503 또는 인증 실패, JWT만으로 세션 복원 금지. 공개 조회는 Redis 없이 가능한 범위에서 계속한다.

브라우저 Access Token은 메모리만. Refresh 쿠키는 HttpOnly, prod Secure, SameSite=Lax, Path=/api/v1/auth, host-only. Path 제한 때문에 __Host- 접두사 조건을 만족하지 않으므로 __Secure- 접두사를 prod에서 사용한다. local HTTP 쿠키는 별도 이름·프로필, prod로 유출되지 않게 테스트한다. localStorage/sessionStorage/IndexedDB/URL 토큰 금지.

갱신 single-flight, Web Locks로 탭 간 직렬화하고 BroadcastChannel은 토큰 없이 상태만 공유한다. 잠금 불가 환경은 충돌 시 재로그인. 엄격 재사용 탐지, grace 없음. 응답 유실 뒤 예전 토큰 재시도는 family를 폐기할 수 있음을 문서·UI에 안내한다. 갱신 실패 무한 재시도 금지, 비멱등 POST 자동 재전송 금지. 로그아웃 때 사용자 Query 캐시와 메모리 제거.

Spring CSRF 지원과 정확한 Origin allowlist로 쿠키 기반 갱신·로그아웃 보호. 초기 CSRF 획득 GET은 토큰과 session 연결을 제공하며 외부 Origin 거부. OAuth 콜백은 state/nonce로 보호하는 좁은 예외만 둔다. Bearer API에 대한 면제는 쿠키 인증을 사용하지 않음이 검증된 matcher에만 적용. 전역 disable 금지. CORS credentials+wildcard 금지. Vite /api 프록시와 prod 동일 사이트가 기본이다.

USER/ADMIN만 사용, 최초 가입자 관리자 금지. 서버 관리 명령의 대상 회원·사유·실행자 감사로 관리자 부여. 사용자 role/userId 입력 신뢰 금지. 다른 사용자 즐겨찾기/제보/파일은 404 또는 403 정책 일관화, 관리자 모든 endpoint에 서버 인가.

이미지 JPEG/PNG만 (WebP는 디코더 검증 후 검토), 10MiB·변당 8192px·총 20MP 상한(설정 가능). 헤더/실제 디코딩 검증, 처리 시간·메모리·동시성 제한, SVG/HTML 거부, EXIF 제거·재인코딩. 랜덤 저장 키·웹 루트 외부·경로 정규화, 원본 비공개, 정제본만 개인정보 검토 뒤 공개. 파일 연결 전 소유권과 상태 확인. 임시 파일 24시간·고아 파일 7일 정리 기본안, DB 참조 재확인 후 제거. OCR/LLM은 확정 근거가 아니다.

외부 수집은 등록된 HTTPS host/path/port allowlist, redirect 각 단계 검증, private/link-local/metadata/loopback 차단 및 DNS 재바인딩 방어. 사용자 URL을 fetch하지 않는다. timeout/응답 크기/파서 복잡도 제한. HTML·CSV·사진에 포함된 지시를 실행하지 않는다. 대표자·개인 전화·위치 이력을 기본 수집하지 않는다.

응답·로그에 SQL/stack/token/인가 코드/정밀 좌표 금지. 추적 ID는 서버 생성 또는 형식 제한. CSP는 카카오 필요한 출처만, nosniff, frame-ancestors, Referrer-Policy, prod HSTS. 의존성 취약점과 라이선스 검토를 출시 전 실제 실행한다. 최고 수준이라는 추상 주장 대신 검증 결과를 기록한다.

세션 구현 상세와 검증 시계 통합·키 교체·실패 경계는 [결정 기록](decisions/0007-stateful-sessions.md)에 정리했다.

Bearer 헤더 추출은 Spring의 DefaultBearerTokenResolver, 서명·클레임은 검증된 JWT 라이브러리에 맡긴다. 세션 확인 필터는 Redis·DB의 현재 상태를 추가로 검증한다. 쿠키 인증 endpoint에 Bearer 헤더가 함께 있어도 CSRF가 면제되지 않도록 범위를 명시적으로 유지했다.

P06b에서 카카오 Authorization Code/OIDC·PKCE와 브라우저 메모리 인증을 연결했다. 구현 경계·쿠키와 탭 경합·테스트 공급자 분리는 [카카오 로그인 결정](decisions/0008-kakao-login.md)을 따른다. 현재 검증한 통제된 공급자 성공은 실제 카카오 사용자 로그인 성공을 의미하지 않는다.

이미지 처리·동시성·파일/DB 정합성 경계는 [비공개 증빙 결정](decisions/0009-private-evidence-images.md)을 따른다.

서버 관리자 권한 지정은 memberAdmin 명령의 기본 변경 계획과 명시적 적용을 사용한다. 현재 권한·회원 활성 상태를 검사하고 OS 실행자·사유를 감사에 남긴다. 자동 승격과 HTTP 우회 경로는 없다. 실제 사용자의 권한은 이번 도구 구현만으로 변경하지 않는다.
