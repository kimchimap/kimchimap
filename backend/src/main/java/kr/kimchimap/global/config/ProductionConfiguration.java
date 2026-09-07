package kr.kimchimap.global.config;

import java.net.URI;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfiguration {
  @Bean
  SmartInitializingSingleton requireProductionSettings(Environment environment) {
    return () -> {
      for (String property :
          new String[] {
            "APP_PASSWORD",
            "MIGRATOR_PASSWORD",
            "REDIS_PASSWORD",
            "JWT_PRIVATE_KEY_PATH",
            "JWT_KEY_ID",
            "KAKAO_CLIENT_ID",
            "KAKAO_CLIENT_SECRET"
          }) {
        if (environment.getProperty(property, "").isBlank()) {
          throw new IllegalStateException("운영 필수 설정 누락: " + property);
        }
      }
      URI origin = URI.create(environment.getRequiredProperty("PUBLIC_ORIGIN"));
      if (!"https".equals(origin.getScheme())
          || origin.getHost() == null
          || origin.getUserInfo() != null) {
        throw new IllegalStateException("운영 공개 주소는 HTTPS origin이어야 합니다.");
      }
    };
  }
}
