# 카카오 OIDC와 브라우저 인증

확인일: 2026-09-07. Spring Security 7.1.1 OAuth2 Client를 Boot BOM으로 사용한다.
공식 근거: [카카오 REST 로그인](https://developers.kakao.com/docs/ko/kakaologin/rest-api), [OIDC 메타데이터](https://kauth.kakao.com/.well-known/openid-configuration), [Spring OAuth2 Login 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html).

인가·토큰·공개 키·issuer는 공식 고정 주소다. 테스트는 test 소스의 별도 ClientRegistrationRepository만 대체하며 운영 설정으로 임의 공급자 주소를 받지 않는다. scope는 openid만 요구한다. ID Token의 sub로 회원을 식별하고 프로필·이메일을 조회하거나 공급자 토큰을 저장하지 않는다. OIDC 활성화와 등록 Redirect URI는 외부 앱 설정이다.

Spring의 Authorization Code 처리와 PKCE S256 customizer를 사용한다. 인가 요청의 state·nonce·code_verifier는 제한된 JSON으로 Redis에 10분 보관한다. 임의 Java 객체 역직렬화는 하지 않는다. 키는 state와 256비트 브라우저 비밀의 해시다. HttpOnly·SameSite=Lax 브라우저 쿠키가 일치할 때만 Redis GETDEL로 원자 소비한다. 잘못된 브라우저·state는 정상 요청을 소비하지 못한다. 재사용·만료 콜백은 거부한다. 같은 브라우저에서 여러 로그인 화면을 동시에 시작하면 마지막 흐름의 쿠키가 우선되며 이전 흐름은 다시 시작해야 한다.

OIDC RS256 서명·issuer·audience·시간과 nonce를 Spring/Nimbus로 검증한다. HTTP 연결 3초·응답 10초 제한, 외부 redirect 비활성화. OAuth 전용 공급자 토큰 응답 변환기를 사용한다. 로그인 시작은 IP 해시별 분당 20회로 제한하고 Redis 장애는 거부한다. HttpSession과 공급자 AuthorizedClient 저장소를 사용하지 않는다. 성공은 서비스 Redis 세션/HttpOnly Refresh 쿠키 발급 후 토큰 없는 /auth/complete로 이동한다. 실패는 고정 오류 분류만 전달하고 공급자 오류·토큰·인가 코드를 로그나 화면에 출력하지 않는다.

브라우저는 CSRF JSON과 쿠키로 세션을 복구하고 Access Token을 모듈 메모리에만 둔다. 한 탭은 Promise single-flight, 여러 탭은 Web Locks로 쿠키 회전을 직렬화한다. BroadcastChannel은 토큰 없이 로그아웃 상태만 전달한다. 잠금 미지원 브라우저의 충돌·응답 유실은 엄격 재사용 탐지로 재로그인이 필요할 수 있으며 grace를 두지 않는다. 네트워크 요청은 15초 이내 중단하고 redirect를 따라가지 않는다.

읽기 요청의 401만 한 번 갱신·재전송한다. 쓰기 요청·403은 자동 재전송하지 않는다. 로그아웃은 메모리·private Query 캐시를 즉시 제거하고 서버 폐기를 확인한다. 서버 장애 시 로컬 정리만 성공했다고 안내하고 재시도할 수 있다. 로그아웃 전에 시작한 늦은 응답은 generation 검사로 폐기한다. 사용자별 Query 키는 반드시 private 접두사와 사용자 식별자를 사용한다.

통제된 공급자 테스트는 실제 HTTP 코드 교환·PKCE·OIDC 검증을 수행한다. Playwright의 인증 응답 대체 테스트는 브라우저 복구/로그아웃 검증이며 실제 카카오 동의·로그인 성공과 구분한다. 실제 카카오 사용자 로그인을 아직 완료했다고 보고하지 않는다.
