package kr.kimchimap.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.kimchimap.auth.dto.ActiveSession;
import kr.kimchimap.auth.service.AuthCookies;
import kr.kimchimap.auth.service.SessionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookiesTest {
  @ParameterizedTest
  @ValueSource(strings = {"local", "prod"})
  void cookieFlagsAndDeletionUseSameScope(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    Instant now = Instant.parse("2026-09-07T00:00:00Z");
    var cookies = new AuthCookies(environment, Clock.fixed(now, ZoneOffset.UTC));
    var response = new MockHttpServletResponse();
    cookies.set(
        response,
        new SessionService.Issued(
            new ActiveSession(UUID.randomUUID(), UUID.randomUUID(), 0, now.plusSeconds(600)),
            "test-token"));
    String header = response.getHeader("Set-Cookie");
    assertThat(header)
        .contains("Path=/api/v1/auth", "HttpOnly", "SameSite=Lax", "Max-Age=600")
        .doesNotContain("Domain=");
    if (profile.equals("prod"))
      assertThat(header).startsWith("__Secure-km-refresh=").contains("; Secure");
    else assertThat(header).startsWith("km-local-refresh=").doesNotContain("; Secure");
    var cleared = new MockHttpServletResponse();
    cookies.clear(cleared);
    assertThat(cleared.getHeader("Set-Cookie"))
        .startsWith(header.split("=", 2)[0] + "=")
        .contains("Max-Age=0", "Path=/api/v1/auth");
  }
}
