package kr.kimchimap.auth.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.auth.service.AuthCookies;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  public record CsrfResponse(String token, String headerName) {}

  public record MemberResponse(UUID id, String role) {}

  public record AccessTokenResponse(String accessToken, String tokenType) {}

  private final SessionService sessions;
  private final JwtService jwt;
  private final AuthCookies cookies;

  public AuthController(SessionService sessions, JwtService jwt, AuthCookies cookies) {
    this.sessions = sessions;
    this.jwt = jwt;
    this.cookies = cookies;
  }

  @GetMapping("/api/v1/auth/csrf")
  public CsrfResponse csrf(
      @Parameter(hidden = true) CsrfToken token, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return new CsrfResponse(token.getToken(), token.getHeaderName());
  }

  @PostMapping("/api/v1/auth/refresh")
  public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    if (!sessions.allowRefresh(request.getRemoteAddr())) {
      response.setHeader("Retry-After", "60");
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "REFRESH_RATE_LIMITED",
          "로그인 갱신 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    }
    var issued = sessions.rotate(cookies.read(request));
    String access = jwt.issue(issued.session());
    cookies.set(response, issued);
    return new AccessTokenResponse(access, "Bearer");
  }

  @PostMapping("/api/v1/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      HttpServletRequest request,
      HttpServletResponse response,
      @AuthenticationPrincipal AuthenticatedMember member) {
    sessions.logout(cookies.read(request));
    if (member != null) sessions.revoke(member.sessionId());
    cookies.clear(response);
  }

  @PostMapping("/api/v1/auth/logout-all")
  @SecurityRequirement(name = "serviceBearer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logoutAll(
      @AuthenticationPrincipal AuthenticatedMember member, HttpServletResponse response) {
    sessions.revokeAll(member.memberId());
    cookies.clear(response);
  }

  @GetMapping("/api/v1/members/me")
  @SecurityRequirement(name = "serviceBearer")
  public MemberResponse me(
      @AuthenticationPrincipal AuthenticatedMember member, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return new MemberResponse(member.memberId(), member.role());
  }

  @DeleteMapping("/api/v1/members/me")
  @SecurityRequirement(name = "serviceBearer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw(
      @AuthenticationPrincipal AuthenticatedMember member, HttpServletResponse response) {
    sessions.withdraw(member.memberId());
    cookies.clear(response);
  }
}
