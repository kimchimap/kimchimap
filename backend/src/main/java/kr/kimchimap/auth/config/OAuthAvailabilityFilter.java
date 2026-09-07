package kr.kimchimap.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.kimchimap.auth.repository.OAuthRequestRepository;
import kr.kimchimap.auth.service.TokenSecrets;
import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuthAvailabilityFilter extends OncePerRequestFilter {
  private final OAuthRequestRepository requests;
  private final ApiProblemWriter problems;

  public OAuthAvailabilityFilter(OAuthRequestRepository requests, ApiProblemWriter problems) {
    this.requests = requests;
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().equals("/api/v1/auth/login/kakao")
        && !request.getServletPath().equals("/api/v1/auth/callback/kakao");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    try {
      if (request.getServletPath().equals("/api/v1/auth/login/kakao")
          && !requests.allowStart(TokenSecrets.hash(request.getRemoteAddr()))) {
        response.setHeader("Retry-After", "60");
        problems.write(response, 429, "LOGIN_RATE_LIMITED", "로그인 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }
      chain.doFilter(request, response);
    } catch (DataAccessException exception) {
      problems.write(
          response, 503, "AUTHENTICATION_UNAVAILABLE", "로그인 연결을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
  }
}
