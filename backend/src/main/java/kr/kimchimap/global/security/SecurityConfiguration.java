package kr.kimchimap.global.security;

import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, Environment environment, ApiProblemWriter problems) throws Exception {
    http.authorizeHttpRequests(
        requests -> {
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
    return http.build();
  }
}
