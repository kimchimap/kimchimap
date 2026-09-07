package kr.kimchimap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApplicationIntegrationSupport {
  @Container
  protected static final GenericContainer<?> DATABASE =
      new GenericContainer<>(
              "postgis/postgis:18-3.6@sha256:60f6ad1d21ea86a67d47780b9a0d1e1d200500f62b19293fa834d0dea80b8677")
          .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"))
          .withEnv(
              Map.of(
                  "POSTGRES_DB",
                  "kimchimap",
                  "POSTGRES_PASSWORD",
                  "test-admin",
                  "APP_PASSWORD",
                  "test-app",
                  "MIGRATOR_PASSWORD",
                  "test-migrator"))
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("../infra/init-roles.sh")),
              "/docker-entrypoint-initdb.d/20-roles.sh")
          .withExposedPorts(5432)
          .waitingFor(Wait.forListeningPort())
          .withStartupTimeout(Duration.ofMinutes(2));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(
              "redis:8.10.1-alpine@sha256:becdda6c7f4b3fb42e42fd7f120bbf5c54c4caaaf16f26da24e4563d2c1f0576")
          .withCommand("redis-server", "--requirepass", "test-redis")
          .withExposedPorts(6379);

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://"
                + DATABASE.getHost()
                + ":"
                + DATABASE.getMappedPort(5432)
                + "/kimchimap");
    properties.add("spring.datasource.password", () -> "test-app");
    properties.add("spring.flyway.password", () -> "test-migrator");
    properties.add("spring.data.redis.host", REDIS::getHost);
    properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    properties.add("spring.data.redis.password", () -> "test-redis");
  }

  @LocalServerPort int port;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected StringRedisTemplate redis;
  protected final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  protected String base() {
    return "http://127.0.0.1:" + port;
  }

  protected HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
