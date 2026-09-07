package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient;
import kr.kimchimap.ingestion.dto.SourceRestaurant;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import kr.kimchimap.ingestion.service.IngestionJobService;
import kr.kimchimap.ingestion.service.IngestionWorker;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

class DataReviewIntegrationTest extends ApplicationIntegrationSupport {
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @Autowired IngestionJobService jobs;
  @Autowired IngestionWorker worker;
  @MockitoBean PublicDataRestaurantClient source;
  private final JsonMapper json = JsonMapper.builder().build();
  private static final UUID PUBLIC_SOURCE = IngestionJobRepository.PUBLIC_DATA_SOURCE;

  @BeforeEach
  void isolateJobs() {
    jdbc.update(
        "UPDATE app.ingestion_job SET status='CANCELLED',lease_owner=NULL,lease_until=NULL WHERE status NOT IN ('SUCCEEDED','CANCELLED')");
  }

  @Test
  void designationNeedsSpecificPermissionAndPreservesCriteriaHistoryWithoutOrigins()
      throws Exception {
    String admin = token(true), user = token(false);
    UUID restaurant = restaurant("가상 테스트 지정 업소 " + UUID.randomUUID(), "테스트 주소");
    UUID permitted = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.data_source(id,code,name,collection_allowed,republication_allowed,designation_allowed) VALUES (?,?,?,true,true,true)",
        permitted,
        "test-designation-" + permitted,
        "테스트 지정 제공기관");
    var body = designation(restaurant, permitted);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/designations",
                    user,
                    Map.of("designation", body, "reason", "테스트 사유"))
                .statusCode())
        .isEqualTo(403);
    var forbidden = new HashMap<>(body);
    forbidden.put("sourceId", PUBLIC_SOURCE);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/designations",
                    admin,
                    Map.of("designation", forbidden, "reason", "테스트 사유"))
                .statusCode())
        .isEqualTo(409);
    var created =
        request(
            "POST",
            "/api/v1/admin/designations",
            admin,
            Map.of("designation", body, "reason", "테스트 최초 확인"));
    assertThat(created.statusCode()).isEqualTo(201);
    String id = json.readTree(created.body()).path("id").asString();
    assertThat(get("/api/v1/restaurants/" + restaurant).body())
        .contains("테스트 지정 제도", "반찬 김치에만 적용")
        .doesNotContain("DOMESTIC");
    var changed = new HashMap<>(body);
    changed.put("cancelledOn", "2026-09-01");
    var updated =
        request(
            "PUT",
            "/api/v1/admin/designations/" + id,
            admin,
            Map.of("expectedVersion", 0, "designation", changed, "reason", "테스트 지정 취소 확인"));
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThat(updated.body()).contains("테스트 최초 확인", "테스트 지정 취소 확인");
    assertThat(
            request(
                    "PUT",
                    "/api/v1/admin/designations/" + id,
                    admin,
                    Map.of("expectedVersion", 0, "designation", changed, "reason", "테스트 충돌"))
                .statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE restaurant_id=?",
                Integer.class,
                restaurant))
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM app.designation_revision WHERE designation_id=?",
                    UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    jdbc.update("UPDATE app.data_source SET designation_allowed=false WHERE id=?", permitted);
    assertThat(get("/api/v1/restaurants/" + restaurant).body()).doesNotContain("테스트 지정 제도");
  }

  @Test
  void concurrentMatchingUsesObservedSourceAndPreservesFetchDate() throws Exception {
    String admin = token(true), other = token(true), user = token(false);
    var fixture = match();
    String path = "/api/v1/admin/matches/" + fixture.id() + "/reviews";
    var body =
        Map.of(
            "expectedVersion",
            0,
            "decision",
            "MATCHED",
            "restaurantId",
            fixture.target(),
            "reason",
            "테스트 주소와 지점 대조");
    assertThat(request("POST", path, user, body).statusCode()).isEqualTo(403);
    var observed =
        jdbc.queryForObject(
            "SELECT observed_at FROM app.restaurant_match_review WHERE id=?",
            java.sql.Timestamp.class,
            fixture.id());
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                start.await();
                return request("POST", path, admin, body).statusCode();
              });
      var two =
          executor.submit(
              () -> {
                start.await();
                return request("POST", path, other, body).statusCode();
              });
      start.countDown();
      assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT restaurant_id FROM app.restaurant_external_id WHERE source_id=? AND external_id=?",
                UUID.class,
                PUBLIC_SOURCE,
                fixture.external()))
        .isEqualTo(fixture.target());
    assertThat(
            jdbc.queryForObject(
                "SELECT last_fetch_succeeded_at FROM app.restaurant_source_record WHERE source_id=? AND external_id=?",
                java.sql.Timestamp.class,
                PUBLIC_SOURCE,
                fixture.external()))
        .isEqualTo(observed);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_match_audit WHERE external_id=?",
                Integer.class,
                fixture.external()))
        .isEqualTo(1);
    assertThat(jobs.get(fixture.job()).quarantinedCount()).isEqualTo(1);
  }

  @Test
  void conflictingStableIdentifierRequiresDistinctRestaurantAndMissingObservationIsBlocked()
      throws Exception {
    String admin = token(true);
    var fixture = match();
    jdbc.update(
        "INSERT INTO app.restaurant_external_id(source_id,external_id,restaurant_id) VALUES (?,?,?)",
        PUBLIC_SOURCE,
        "test-existing-" + UUID.randomUUID(),
        fixture.target());
    String path = "/api/v1/admin/matches/" + fixture.id() + "/reviews";
    assertThat(
            request(
                    "POST",
                    path,
                    admin,
                    Map.of(
                        "expectedVersion",
                        0,
                        "decision",
                        "MATCHED",
                        "restaurantId",
                        fixture.target(),
                        "reason",
                        "테스트 충돌 확인"))
                .statusCode())
        .isEqualTo(409);
    var result =
        request(
            "POST",
            path,
            admin,
            Map.of("expectedVersion", 0, "decision", "DISTINCT", "reason", "테스트 별도 지점 확인"));
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT restaurant_id FROM app.restaurant_external_id WHERE source_id=? AND external_id=?",
                UUID.class,
                PUBLIC_SOURCE,
                fixture.external()))
        .isNotEqualTo(fixture.target());
    assertThat(
            request("GET", "/api/v1/admin/matches/" + UUID.randomUUID(), admin, null).statusCode())
        .isEqualTo(404);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM app.restaurant_match_audit WHERE external_id=?",
                    fixture.external()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void differentExternalIdsCannotConcurrentlyAttachToSameSourceAndRestaurant() throws Exception {
    String admin = token(true);
    var first = match();
    String name =
        jdbc.queryForObject(
            "SELECT name FROM app.restaurant WHERE id=?", String.class, first.target());
    String address =
        jdbc.queryForObject(
            "SELECT address FROM app.restaurant WHERE id=?", String.class, first.target());
    var second = match(first.target(), name, address);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results =
          List.of(first, second).stream()
              .map(
                  fixture ->
                      executor.submit(
                          () -> {
                            start.await();
                            return request(
                                    "POST",
                                    "/api/v1/admin/matches/" + fixture.id() + "/reviews",
                                    admin,
                                    Map.of(
                                        "expectedVersion",
                                        0,
                                        "decision",
                                        "MATCHED",
                                        "restaurantId",
                                        fixture.target(),
                                        "reason",
                                        "테스트 지점 동시 대조"))
                                .statusCode();
                          }))
              .toList();
      start.countDown();
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_external_id WHERE source_id=? AND restaurant_id=?",
                Integer.class,
                PUBLIC_SOURCE,
                first.target()))
        .isEqualTo(1);
  }

  private record Fixture(UUID id, UUID target, UUID job, String external) {}

  private Fixture match() {
    String suffix = UUID.randomUUID().toString(),
        name = "가상 테스트 매칭 업소 " + suffix,
        address = "테스트 비교 주소 " + suffix;
    UUID target = restaurant(name, address);
    return match(target, name, address);
  }

  private Fixture match(UUID target, String name, String address) {
    String suffix = UUID.randomUUID().toString();
    var row =
        new SourceRestaurant(
            Map.of(
                "OPN_ATMY_GRP_CD",
                "test-municipal",
                "MNG_NO",
                suffix,
                "BPLC_NM",
                name,
                "ROAD_NM_ADDR",
                address,
                "SALS_STTS_NM",
                "영업/정상",
                "DAT_UPDT_PNT",
                "20260901000000"));
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new PublicDataRestaurantClient.Page(1, 1, 1, List.of(row)));
    UUID job = jobs.request("INCREMENTAL", 1, 1);
    assertThat(worker.processOne(job)).isTrue();
    assertThat(jobs.get(job).status()).isEqualTo("SUCCEEDED");
    String external = "test-municipal:" + suffix;
    UUID id =
        jdbc.queryForObject(
            "SELECT id FROM app.restaurant_match_review WHERE source_id=? AND external_id=?",
            UUID.class,
            PUBLIC_SOURCE,
            external);
    return new Fixture(id, target, job, external);
  }

  private Map<String, Object> designation(UUID restaurant, UUID sourceId) {
    return Map.of(
        "restaurantId",
        restaurant,
        "sourceId",
        sourceId,
        "externalId",
        "test-designation-" + UUID.randomUUID(),
        "schemeName",
        "테스트 지정 제도",
        "applicableItems",
        "반찬 김치에만 적용",
        "criteriaOriginal",
        "테스트 제도의 실제 기준 원문",
        "designatedOn",
        "2026-01-01",
        "publiclyVisible",
        true);
  }

  private UUID restaurant(String name, String address) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,published) VALUES (?,?,?,'OPEN','MISSING',true)",
        id,
        name,
        address);
    return id;
  }

  private String token(boolean admin) {
    var member = members.loginWithVerifiedKakaoSubject("test-data-review-" + UUID.randomUUID());
    if (admin) members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 관리자", "test-fixture");
    return jwt.issue(sessions.create(members.requireActive(member.id())).session());
  }

  private HttpResponse<String> request(String method, String path, String token, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + token);
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
