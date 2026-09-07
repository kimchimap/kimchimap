package kr.kimchimap.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthOriginFilter extends OncePerRequestFilter {
  private final String origin;
  private final ApiProblemWriter problems;

  public AuthOriginFilter(String origin, ApiProblemWriter problems) {
    this.origin = origin;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean auth =
        Set.of(
                "/api/v1/auth/csrf",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/logout-all")
            .contains(request.getServletPath());
    boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    String supplied = request.getHeader("Origin");
    if (auth
        && (unsafe && !origin.equals(supplied)
            || supplied != null && !origin.equals(supplied)
            || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
      problems.write(response, 403, "ORIGIN_NOT_ALLOWED", "허용되지 않은 요청 출처입니다.");
      return;
    }
    chain.doFilter(request, response);
  }
}
