# 상태 보유 세션과 JWT

결정일 2026-09-07. P06a는 서비스 토큰·세션 기반이며 카카오 인증 검증·콜백 연결은 P06b이다. 테스트에서 Service로 생성한 합성 회원을 실제 로그인 성공으로 보고하지 않는다.

JWT는 RS256, 필수 issuer/audience/subject/expiry/issuedAt/jti/sid/security version을 검증한다. 수명은 최대 10분, 세션 절대 만료는 최대 14일이다. Nimbus는 서명과 알고리즘 선택을, Spring validator는 시간·필수 클레임·대상·발급자를 검증한다. Nimbus의 별도 기본 시계 검사와 Spring 검사를 중복 실행하지 않는다. 고정 Clock 테스트에서 두 시계가 달라 실패하여 검증을 한 정책으로 모았다. 만료·nbf·미래 iat·필수 필드·서명·알고리즘 혼동 거부 테스트를 유지하며 허용차는 30초다.

Redis에는 32바이트 난수 Refresh Token의 SHA-256만 저장한다. 발급된 해시→세션 인덱스를 절대 만료까지 유지한다. Lua에서 현재 토큰 비교·회전·이전 발급 토큰 재사용에 따른 폐기를 원자적으로 실행한다. 임의 토큰은 실제 인덱스와 연결되지 않아 다른 세션을 폐기하지 않는다. 정상적인 동시 갱신도 엄격한 재사용 정책에 따라 해당 세션이 폐기될 수 있다. 허용 유예 시간은 없다. 응답 유실 뒤 이전 토큰 재시도에도 같은 정책을 적용한다.

Redis는 단일 인스턴스를 전제로 하며 인증 키에 같은 hash tag를 사용한다. 논리적인 family는 session ID와 같다. 회원당 활성 세션은 최대 20개, 갱신 요청은 원격 주소의 해시별 분당 20회다. 해시는 익명화가 아니며 원문 주소를 기록하지 않는다. 로그아웃으로 폐기된 세션은 다음 생성 시 활성 세션 인덱스에서 제거하며 발급 이력은 남은 TTL까지 보존한다.

JWT의 유효한 서명만으로 API를 허용하지 않는다. Redis의 현재 세션과 DB의 ACTIVE 상태·현재 역할·security version이 일치해야 한다. 권한 변경·정지·탈퇴는 이전 JWT와 갱신을 즉시 거부하게 만든다. 상태 확인은 요청 시작 시 수행되며 이미 진행 중인 요청을 소급 취소한다고 보장하지 않는다. 탈퇴는 세션 폐기 후 회원 상태를 바꾸며 공급자 식별 연결을 제거한다. 이후 재가입은 새 회원 식별자로 처리한다.

HttpSession을 사용하지 않는다는 설정은 무상태 인증이라는 뜻이 아니다. Refresh 쿠키는 HttpOnly·SameSite=Lax·인증 경로 제한·host-only이며 운영에서는 Secure를 요구한다. CSRF는 Spring 쿠키 저장소의 무작위 토큰과 XOR 요청 처리를 사용하고, JSON으로 얻은 토큰을 헤더로 보낸다. 쿠키 인증 endpoint는 정확한 Origin도 요구한다. 쿠키로 인증하지 않는 Bearer 요청만 CSRF 면제이며 JWT·활성 세션 검사는 유지한다.

로컬·테스트에서 서명키를 지정하지 않으면 메모리 RSA 키를 생성한다. 재시작하면 이전 Access Token은 무효이며 Refresh 세션은 정상 갱신으로 새 토큰을 받을 수 있다. 운영은 외부 RSA 개인 JWK 서명키와 kid 없이는 시작하지 않는다. 키 교체 시 이전 공개키를 JWT_VERIFICATION_KEYS_PATH의 JWK Set에 넣고 새 kid로 발급한다. 최소 10분과 시계 허용차가 지나면 이전 공개키를 제거한다. 유출 시 대기 없이 키 제거·세션 폐기 절차를 수행한다.

검증 근거: [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [카카오 OIDC](https://developers.kakao.com/docs/ko/kakaologin/rest-api). Boot BOM의 Spring Security 7.1.1과 Nimbus JOSE JWT 10.9.1을 사용하며 임의 버전 덮어쓰기를 하지 않는다.
