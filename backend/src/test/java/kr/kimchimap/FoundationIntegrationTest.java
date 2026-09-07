package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FoundationIntegrationTest extends ApplicationIntegrationSupport {
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
        .isEqualTo(8L);
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
}
