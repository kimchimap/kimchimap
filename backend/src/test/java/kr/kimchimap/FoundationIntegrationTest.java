package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class FoundationIntegrationTest {
  @Container
  static final GenericContainer<?> DATABASE =
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
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Test
  void publicStatusAndPrivateResourcesHaveSafeContracts() throws Exception {
    var status = get("/api/v1/system/status");
    assertThat(status.statusCode()).isEqualTo(200);
    assertThat(status.body()).contains("국산김치맵", "UP");
    assertThat(status.headers().firstValue("X-Request-Id")).isPresent();
    var denied = get("/api/v1/admin/reports");
    assertThat(denied.statusCode()).isEqualTo(401);
    assertThat(denied.body())
        .contains("AUTHENTICATION_REQUIRED", "traceId")
        .doesNotContain("Exception", "password");
    var csrf =
        client.send(
            HttpRequest.newBuilder(URI.create(base() + "/api/v1/auth/refresh"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(csrf.statusCode()).isEqualTo(403);
  }

  @Test
  void realInfrastructureAndMigrationAreAvailable() {
    assertThat(jdbc.queryForObject("SELECT PostGIS_Version()", String.class)).startsWith("3.6");
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("kimchimap_app");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.flyway_schema_history WHERE success", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT has_schema_privilege(current_user, 'app', 'CREATE')", Boolean.class))
        .isFalse();
    redis.opsForValue().set("test:foundation", "연결 확인", Duration.ofSeconds(10));
    assertThat(redis.opsForValue().getAndDelete("test:foundation")).isEqualTo("연결 확인");
  }

  @Test
  void exportsOpenApi() throws Exception {
    var response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    var mapper = JsonMapper.builder().build();
    var document = mapper.readTree(response.body());
    assertThat(document.path("paths").has("/api/v1/system/status")).isTrue();
    var output = Path.of("build/contracts/openapi.json");
    Files.createDirectories(output.getParent());
    Files.writeString(
        output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n");
  }

  private String base() {
    return "http://127.0.0.1:" + port;
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
