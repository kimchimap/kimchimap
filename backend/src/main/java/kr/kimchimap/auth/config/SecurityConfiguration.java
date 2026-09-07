package kr.kimchimap.auth.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import kr.kimchimap.auth.repository.OAuthRequestRepository;
import kr.kimchimap.auth.service.AuthCookies;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.global.web.ApiProblemWriter;
import kr.kimchimap.member.service.MemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@SecurityScheme(
    name = "serviceBearer",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class SecurityConfiguration {
  @Bean
  org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(JwtService jwt) {
    return jwt.decoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      Environment environment,
      ApiProblemWriter problems,
      JwtService jwt,
      SessionService sessions,
      KakaoLoginConfiguration kakao,
      ClientRegistrationRepository clients,
      RedisAuthorizationRequests authorizationRequests,
      MemberService members,
      AuthCookies cookies,
      OAuthRequestRepository oauthRequests)
      throws Exception {
    String origin = environment.getProperty("app.public-origin", "http://localhost:5173");
    var csrfTokens = new CookieCsrfTokenRepository();
    csrfTokens.setHeaderName("X-CSRF-TOKEN");
    csrfTokens.setCookieName(
        environment.matchesProfiles("prod") ? "__Secure-km-csrf" : "km-local-csrf");
    csrfTokens.setCookieCustomizer(
        cookie ->
            cookie
                .httpOnly(true)
                .secure(environment.matchesProfiles("prod"))
                .sameSite("Lax")
                .path("/api/v1/auth"));
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.requestCache(cache -> cache.disable());
    http.addFilterBefore(new AuthOriginFilter(origin, problems), CsrfFilter.class);
    http.addFilterBefore(
        new BearerSessionFilter(jwt, sessions, problems), BasicAuthenticationFilter.class);
    http.cors(Customizer.withDefaults());
    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(csrfTokens)
                .ignoringRequestMatchers(
                    request ->
                        request.getMethod().equals("POST")
                                && request.getServletPath().equals("/api/v1/restaurants/search")
                            || !request.getServletPath().startsWith("/api/v1/auth/")
                                && request.getHeader("Authorization") != null
                                && request.getHeader("Authorization").startsWith("Bearer ")));
    http.authorizeHttpRequests(
        requests -> {
          requests
              .requestMatchers(
                  HttpMethod.GET,
                  "/api/v1/auth/csrf",
                  "/api/v1/auth/login/kakao",
                  "/api/v1/auth/callback/kakao")
              .permitAll();
          requests
              .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout")
              .permitAll();
          requests.requestMatchers("/api/v1/members/me", "/api/v1/auth/logout-all").authenticated();
          requests.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
          requests.requestMatchers(HttpMethod.GET, "/api/v1/media/{id}/public").permitAll();
          requests.requestMatchers("/api/v1/reports", "/api/v1/reports/**").authenticated();
          requests
              .requestMatchers(
                  "/api/v1/bookmarks", "/api/v1/bookmarks/**", "/api/v1/media", "/api/v1/media/**")
              .authenticated();
          requests.requestMatchers(HttpMethod.POST, "/api/v1/restaurants/search").permitAll();
          requests
              .requestMatchers(
                  HttpMethod.GET,
                  "/api/v1/system/status",
                  "/api/v1/catalogs/ingredients",
                  "/api/v1/catalogs/countries",
                  "/api/v1/restaurants/{id}")
              .permitAll();
          if (environment.matchesProfiles("local", "test")
              && !environment.matchesProfiles("prod")) {
            requests.requestMatchers("/v3/api-docs/**").permitAll();
          }
          requests.anyRequest().denyAll();
        });
    http.exceptionHandling(
        errors ->
            errors
                .authenticationEntryPoint(
                    (request, response, exception) ->
                        problems.write(response, 401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."))
                .accessDeniedHandler(
                    (request, response, exception) ->
                        problems.write(response, 403, "ACCESS_DENIED", "요청 권한을 확인해 주세요.")));
    http.headers(
        headers ->
            headers.contentSecurityPolicy(
                policy -> policy.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
    kakao.configure(http, clients, authorizationRequests, members, sessions, cookies, environment);
    http.addFilterBefore(
        new OAuthAvailabilityFilter(oauthRequests, problems),
        OAuth2AuthorizationRequestRedirectFilter.class);
    return http.build();
  }
}
