package kr.kimchimap.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookies {
  private final boolean secure;
  private final String name;
  private final Clock clock;

  public AuthCookies(Environment environment, Clock clock) {
    secure = environment.matchesProfiles("prod");
    name = secure ? "__Secure-km-refresh" : "km-local-refresh";
    this.clock = clock;
  }

  public String read(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    return Arrays.stream(request.getCookies())
        .filter(c -> c.getName().equals(name))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  public void set(HttpServletResponse response, SessionService.Issued issued) {
    write(
        response,
        issued.refreshToken(),
        Duration.between(clock.instant(), issued.session().expiresAt()));
  }

  public void clear(HttpServletResponse response) {
    write(response, "", Duration.ZERO);
  }

  private void write(HttpServletResponse response, String token, Duration lifetime) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(lifetime.isNegative() ? Duration.ZERO : lifetime)
            .build()
            .toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }
}
