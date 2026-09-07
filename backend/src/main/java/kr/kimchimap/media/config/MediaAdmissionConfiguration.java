package kr.kimchimap.media.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import kr.kimchimap.global.web.ApiProblemWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class MediaAdmissionConfiguration {
  @Bean
  FilterRegistrationBean<OncePerRequestFilter> mediaAdmission(ApiProblemWriter problems) {
    var capacity = new Semaphore(2);
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getMethod().equals("POST")
                || !request.getServletPath().equals("/api/v1/media");
          }

          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws IOException, ServletException {
            if (!capacity.tryAcquire()) {
              response.setHeader("Retry-After", "5");
              problems.write(response, 429, "UPLOAD_BUSY", "사진 업로드 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
              return;
            }
            try {
              chain.doFilter(request, response);
            } finally {
              capacity.release();
            }
          }
        };
    var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    // Security 필터 뒤, MVC multipart 파싱 전에 동시 업로드 수를 제한한다.
    registration.setOrder(0);
    return registration;
  }
}
