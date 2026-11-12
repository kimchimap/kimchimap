package kr.kimchimap;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import kr.kimchimap.member.service.MemberService;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 운영 산출물에 포함하지 않는 임시 저장소·통제된 공급자 실행기. */
public final class BrowserTestServer extends ApplicationIntegrationSupport {
  public static void main(String[] args) throws Exception {
    DATABASE.start();
    REDIS.start();
    var media = Files.createTempDirectory("kimchimap-browser-test-media-");
    KakaoLoginIntegrationTest.enableBrowserAuthorization();
    var app =
        new SpringApplicationBuilder(
                KimchimapApplication.class, KakaoLoginIntegrationTest.ProviderConfiguration.class)
            .profiles("test")
            .run(
                "--server.port=18080",
                "--JWT_PRIVATE_KEY_PATH=",
                "--spring.datasource.url=jdbc:postgresql://"
                    + DATABASE.getHost()
                    + ":"
                    + DATABASE.getMappedPort(5432)
                    + "/kimchimap",
                "--spring.datasource.username=kimchimap_app",
                "--spring.datasource.password=test-app",
                "--spring.flyway.user=kimchimap_migrator",
                "--spring.flyway.password=test-migrator",
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--spring.data.redis.password=test-redis",
                "--app.public-origin=http://localhost:5173",
                "--app.media.root=" + media,
                "--app.ingestion.worker-enabled=false",
                "--app.ingestion.scheduling-enabled=false");
    var members = app.getBean(MemberService.class);
    var admin = members.loginWithVerifiedKakaoSubject("test-browser-admin");
    members.changeSecurity(admin.id(), "ADMIN", "ACTIVE", "통합 브라우저 테스트 관리자", "test-fixture");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  app.close();
                  KakaoLoginIntegrationTest.stopProvider();
                  REDIS.stop();
                  DATABASE.stop();
                  try (var paths = Files.walk(media)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                      Files.deleteIfExists(path);
                  } catch (java.io.IOException ignored) {
                    System.err.println("테스트 임시 사진 정리가 완료되지 않았습니다.");
                  }
                }));
    new CountDownLatch(1).await();
  }
}
