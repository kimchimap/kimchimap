package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

@TestPropertySource(properties = "app.ingestion.public-data-key=test-contract-key")
class AdminIngestionIntegrationTest extends ApplicationIntegrationSupport {
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @Autowired kr.kimchimap.ingestion.service.IngestionAdminService administration;
  @Autowired kr.kimchimap.report.service.ReportService reports;
  private final JsonMapper json = JsonMapper.builder().build();
  private final String source = IngestionJobRepository.PUBLIC_DATA_SOURCE.toString();

  @Test
  void requiresCurrentAdminAndNeverExposesLeaseOrCredentials() throws Exception {
    String user = token(false), admin = token(true);
    assertThat(request("GET", "/api/v1/admin/ingestion/jobs", user, null, null).statusCode())
        .isEqualTo(403);
    var response = request("GET", "/api/v1/admin/ingestion/sources", admin, null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("credentialConfigured")
        .doesNotContain("test-contract-key");
    assertThat(
            request("GET", "/api/v1/admin/ingestion/jobs/" + UUID.randomUUID(), admin, null, null)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/ingestion/sources/" + UUID.randomUUID() + "/runs",
                    admin,
                    body(1),
                    UUID.randomUUID())
                .statusCode())
        .isEqualTo(404);
    var started =
        request(
            "POST",
            "/api/v1/admin/ingestion/sources/" + source + "/runs",
            admin,
            body(1),
            UUID.randomUUID());
    assertThat(started.statusCode()).isEqualTo(202);
    assertThat(started.body()).doesNotContain("leaseOwner", "fencingToken", "test-contract-key");
    String id = json.readTree(started.body()).path("id").asString();
    assertThat(
            request(
                    "GET",
                    "/api/v1/admin/ingestion/jobs/" + id + "/events?limit=101",
                    admin,
                    null,
                    null)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            request("GET", "/api/v1/admin/ingestion/jobs/" + id + "/quarantines", admin, null, null)
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void concurrentRepeatedRequestCreatesOneAuditAndDoesNotExtendBudgetAgain() throws Exception {
    String admin = token(true);
    UUID key = UUID.randomUUID();
    String path = "/api/v1/admin/ingestion/sources/" + source + "/runs";
    var start = new CountDownLatch(1);
    List<HttpResponse<String>> responses;
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                start.await();
                return request("POST", path, admin, body(2), key);
              });
      var two =
          executor.submit(
              () -> {
                start.await();
                return request("POST", path, admin, body(2), key);
              });
      start.countDown();
      responses = List.of(one.get(), two.get());
    }
    assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(202));
    String id = json.readTree(responses.getFirst().body()).path("id").asString();
    assertThat(json.readTree(responses.getLast().body()).path("id").asString()).isEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.ingestion_request WHERE request_key=?",
                Integer.class,
                key))
        .isEqualTo(1);
    assertThat(request("POST", path, admin, body(3), key).statusCode()).isEqualTo(409);
    jdbc.update("UPDATE app.ingestion_job SET status='PARTIAL' WHERE id=?", UUID.fromString(id));
    int budget =
        jdbc.queryForObject(
            "SELECT max_pages FROM app.ingestion_job WHERE id=?",
            Integer.class,
            UUID.fromString(id));
    assertThat(request("POST", path, admin, body(2), key).statusCode()).isEqualTo(202);
    assertThat(
            jdbc.queryForObject(
                "SELECT max_pages FROM app.ingestion_job WHERE id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(budget);
  }

  @Test
  void deniesMissingPermissionAndUnboundedInputs() throws Exception {
    String admin = token(true), path = "/api/v1/admin/ingestion/sources/" + source + "/runs";
    assertThat(request("POST", path, admin, body(101), UUID.randomUUID()).statusCode())
        .isEqualTo(400);
    assertThat(request("POST", path, admin, body(1), null).statusCode()).isEqualTo(400);
    jdbc.update(
        "UPDATE app.data_source SET collection_allowed=false WHERE id=?", UUID.fromString(source));
    try {
      assertThat(request("POST", path, admin, body(1), UUID.randomUUID()).statusCode())
          .isEqualTo(409);
    } finally {
      jdbc.update(
          "UPDATE app.data_source SET collection_allowed=true WHERE id=?", UUID.fromString(source));
    }
  }

  @Test
  void expiredRequestCleanupPreservesAuditAndRecentKeys() throws Exception {
    String admin = token(true), path = "/api/v1/admin/ingestion/sources/" + source + "/runs";
    UUID expired = UUID.randomUUID(), recent = UUID.randomUUID();
    assertThat(request("POST", path, admin, body(1), expired).statusCode()).isEqualTo(202);
    assertThat(request("POST", path, admin, body(1), recent).statusCode()).isEqualTo(202);
    int audits =
        jdbc.queryForObject("SELECT count(*) FROM app.ingestion_request_audit", Integer.class);
    jdbc.update(
        "UPDATE app.ingestion_request SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE request_key=?",
        expired);
    administration.cleanExpiredRequests();
    reports.cleanExpiredRequests();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.ingestion_request WHERE request_key=?",
                Integer.class,
                expired))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.ingestion_request WHERE request_key=?",
                Integer.class,
                recent))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM app.ingestion_request_audit", Integer.class))
        .isEqualTo(audits);
  }

  private Map<String, Object> body(int budget) {
    return Map.of("mode", "INCREMENTAL", "pageBudget", budget, "reason", "테스트 제한 수집 재실행");
  }

  private String token(boolean admin) {
    var member = members.loginWithVerifiedKakaoSubject("test-ingestion-admin-" + UUID.randomUUID());
    if (admin) members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 관리자", "test-fixture");
    return jwt.issue(sessions.create(members.requireActive(member.id())).session());
  }

  private HttpResponse<String> request(
      String method, String path, String token, Object body, UUID key) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + token);
    if (key != null) builder.header("Idempotency-Key", key.toString());
    if (body != null) builder.header("Content-Type", "application/json");
    return client.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
