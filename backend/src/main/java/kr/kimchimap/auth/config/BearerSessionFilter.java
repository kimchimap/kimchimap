package kr.kimchimap.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerSessionFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final SessionService sessions;
  private final ApiProblemWriter problems;
  private final DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();

  public BearerSessionFilter(JwtService jwt, SessionService sessions, ApiProblemWriter problems) {
    this.jwt = jwt;
    this.sessions = sessions;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      if (header.length() > 8192) throw new IllegalArgumentException();
      String encoded = resolver.resolve(request);
      if (encoded == null) throw new IllegalArgumentException();
      var token = jwt.decoder().decode(encoded);
      var member =
          sessions.authenticate(
              UUID.fromString(token.getClaimAsString("sid")),
              UUID.fromString(token.getSubject()),
              ((Number) token.getClaim("ver")).longValue());
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          new UsernamePasswordAuthenticationToken(
              member, null, List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))));
      SecurityContextHolder.setContext(context);
    } catch (JwtException | IllegalArgumentException | OAuth2AuthenticationException exception) {
      problems.write(response, 401, "TOKEN_INVALID", "로그인 정보를 확인할 수 없습니다.");
      return;
    } catch (ApiException exception) {
      problems.write(
          response, exception.status().value(), exception.code(), exception.getMessage());
      return;
    } catch (DataAccessException exception) {
      problems.write(
          response, 503, "AUTHENTICATION_UNAVAILABLE", "로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      return;
    }
    chain.doFilter(request, response);
  }
}
