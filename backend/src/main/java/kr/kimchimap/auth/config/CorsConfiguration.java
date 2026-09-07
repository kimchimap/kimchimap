package kr.kimchimap.auth.config;

import java.io.IOException;
import java.util.List;
import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfiguration {
  @Bean
  CorsFilter corsFilter(Environment environment, ApiProblemWriter problems) {
    var configuration = new org.springframework.web.cors.CorsConfiguration();
    configuration.setAllowedOrigins(
        List.of(environment.getProperty("app.public-origin", "http://localhost:5173")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
    configuration.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    var filter = new CorsFilter(source);
    filter.setCorsProcessor(
        new DefaultCorsProcessor() {
          @Override
          protected void rejectRequest(ServerHttpResponse response) throws IOException {
            problems.write(
                ((ServletServerHttpResponse) response).getServletResponse(),
                403,
                "CORS_NOT_ALLOWED",
                "허용되지 않은 교차 출처 요청입니다.");
          }
        });
    return filter;
  }

  @Bean
  FilterRegistrationBean<CorsFilter> disableContainerCorsFilter(CorsFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
