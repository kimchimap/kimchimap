package kr.kimchimap.search.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.kimchimap.global.web.ApiProblemWriter;
import kr.kimchimap.search.service.SearchRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SearchRateLimitFilter extends OncePerRequestFilter {
  private final SearchRateLimiter limiter;
  private final ApiProblemWriter problems;

  public SearchRateLimitFilter(SearchRateLimiter limiter, ApiProblemWriter problems) {
    this.limiter = limiter;
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getMethod().equals("POST")
        || !request.getServletPath().equals("/api/v1/restaurants/search");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!limiter.allow(request.getRemoteAddr())) {
      response.setHeader("Retry-After", "60");
      problems.write(response, 429, "SEARCH_RATE_LIMITED", "검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
      return;
    }
    chain.doFilter(request, response);
  }
}
