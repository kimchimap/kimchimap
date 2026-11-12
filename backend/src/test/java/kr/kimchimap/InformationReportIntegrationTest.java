package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.member.service.MemberService;
import kr.kimchimap.origin.dto.OriginPublicationRequest;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.service.OriginPublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class InformationReportIntegrationTest extends ApplicationIntegrationSupport {
  private static final UUID CABBAGE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @Autowired OriginPublicationService origins;
  @Autowired kr.kimchimap.media.service.MediaService media;
  private final JsonMapper json = JsonMapper.builder().build();

  private record Actor(UUID id, String token) {}

  private record Shop(UUID id, UUID scope) {}

  @Test
  void reportsArePrivateIdempotentAndDoNotChangePublicOriginsOnSubmission() throws Exception {
    var owner = actor(false);
    var other = actor(false);
    var shop = shop(owner);
    var body = body(shop.id());
    var key = UUID.randomUUID();
    var created = request("POST", "/api/v1/information-reports", owner, body, key);
    assertThat(created.statusCode()).isEqualTo(201);
    var id = json.readTree(created.body()).path("id").asString();
    assertThat(request("POST", "/api/v1/information-reports", owner, body, key).body())
        .isEqualTo(created.body());
    var changed = new java.util.HashMap<>(body);
    changed.put("description", "다른 테스트 내용");
    assertThat(request("POST", "/api/v1/information-reports", owner, changed, key).statusCode())
        .isEqualTo(409);
    assertThat(request("GET", "/api/v1/information-reports/" + id, other, null, null).statusCode())
        .isEqualTo(404);
    assertThat(request("GET", "/api/v1/information-reports", other, null, null).body())
        .doesNotContain(id);
    assertThat(request("GET", "/api/v1/admin/information-reports", owner, null, null).statusCode())
        .isEqualTo(403);
    assertThat(get("/api/v1/information-reports").statusCode()).isEqualTo(401);
    assertThat(get("/api/v1/restaurants/" + shop.id()).statusCode()).isEqualTo(200);
    assertThat(created.body()).doesNotContain("ownerId", "requestHash", "requestKey");
    assertThat(
            request("GET", "/api/v1/information-reports?cursor=" + id, other, null, null)
                .statusCode())
        .isEqualTo(400);
    changed = new java.util.HashMap<>(body);
    changed.put("mediaIds", List.of(UUID.randomUUID()));
    assertThat(
            request("POST", "/api/v1/information-reports", owner, changed, UUID.randomUUID())
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void attachmentsRequireOwnershipAndRemainPrivateAfterResolution() throws Exception {
    var owner = actor(false);
    var other = actor(false);
    var admin = actor(true);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var photo = media.upload(owner.id(), bytes.toByteArray(), "image/png", "test-proof.png");
    var input = new java.util.HashMap<>(body(shop(owner).id()));
    input.put("mediaIds", List.of(photo.id()));
    assertThat(
            request("POST", "/api/v1/information-reports", other, input, UUID.randomUUID())
                .statusCode())
        .isEqualTo(404);
    var response = request("POST", "/api/v1/information-reports", owner, input, UUID.randomUUID());
    assertThat(response.statusCode()).isEqualTo(201);
    var id = json.readTree(response.body()).path("id").asString();
    assertThat(media.download(photo.id(), owner.id(), false, false).bytes()).isNotEmpty();
    assertThat(media.download(photo.id(), admin.id(), true, false).bytes()).isNotEmpty();
    assertThatThrownBy(() -> media.download(photo.id(), other.id(), false, false))
        .isInstanceOf(kr.kimchimap.global.web.ApiException.class);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/information-reports/" + id + "/resolution",
                    admin,
                    resolution(),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThatThrownBy(() -> media.publicImage(photo.id()))
        .isInstanceOf(kr.kimchimap.global.web.ApiException.class);
  }

  @Test
  void competingAdministratorsResolveOnceAndAuditCannotBeEdited() throws Exception {
    var owner = actor(false);
    var admin = actor(true);
    var secondAdmin = actor(true);
    String id = create(owner, shop(owner).id());
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results =
          List.of(admin, secondAdmin).stream()
              .map(
                  actor ->
                      executor.submit(
                          () -> {
                            start.await();
                            return request(
                                    "POST",
                                    "/api/v1/admin/information-reports/" + id + "/resolution",
                                    actor,
                                    resolution(),
                                    null)
                                .statusCode();
                          }))
              .toList();
      start.countDown();
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(request("GET", "/api/v1/information-reports/" + id, owner, null, null).body())
        .contains("DISMISSED", "테스트 확인 사유", "resolvedAt");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.information_report_review WHERE report_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app.information_report_review SET reason='변조' WHERE report_id=?",
                    UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void originCorrectionAndResolutionShareOneTransactionAndRejectForeignShop() throws Exception {
    var owner = actor(false);
    var admin = actor(true);
    var shop = shop(owner);
    var other = shop(owner);
    String id = create(owner, shop.id());
    var resolution =
        new java.util.HashMap<String, Object>(
            Map.of("expectedVersion", 0, "decision", "RESOLVED", "reason", "테스트 오류 근거 철회"));
    resolution.put("correction", correction(other));
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/information-reports/" + id + "/resolution",
                    admin,
                    resolution,
                    null)
                .statusCode())
        .isEqualTo(400);
    assertThat(request("GET", "/api/v1/information-reports/" + id, owner, null, null).body())
        .contains("PENDING");
    assertThat(get("/api/v1/restaurants/" + other.id()).statusCode()).isEqualTo(200);
    var stale = new java.util.HashMap<>(correction(shop));
    stale.put("expectedVersion", 99);
    resolution.put("correction", stale);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/information-reports/" + id + "/resolution",
                    admin,
                    resolution,
                    null)
                .statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.information_report_review WHERE report_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isZero();
    resolution.put("correction", correction(shop));
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/information-reports/" + id + "/resolution",
                    admin,
                    resolution,
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(get("/api/v1/restaurants/" + shop.id()).statusCode()).isEqualTo(404);
    assertThat(request("GET", "/api/v1/information-reports/" + id, owner, null, null).body())
        .contains("RESOLVED");
  }

  @Test
  void boundsAndHourlyRateAreEnforcedWithoutSkippingOtherMembers() throws Exception {
    var owner = actor(false);
    var shop = shop(owner);
    assertThat(
            request("GET", "/api/v1/information-reports?limit=51", owner, null, null).statusCode())
        .isEqualTo(400);
    var invalid = new java.util.HashMap<>(body(shop.id()));
    invalid.put("category", "ADMIN");
    assertThat(
            request("POST", "/api/v1/information-reports", owner, invalid, UUID.randomUUID())
                .statusCode())
        .isEqualTo(400);
    for (int i = 0; i < 20; i++)
      assertThat(
              request(
                      "POST",
                      "/api/v1/information-reports",
                      owner,
                      body(shop.id()),
                      UUID.randomUUID())
                  .statusCode())
          .isEqualTo(201);
    assertThat(
            request(
                    "POST",
                    "/api/v1/information-reports",
                    owner,
                    body(shop.id()),
                    UUID.randomUUID())
                .statusCode())
        .isEqualTo(429);
    assertThat(
            request(
                    "POST",
                    "/api/v1/information-reports",
                    actor(false),
                    body(shop.id()),
                    UUID.randomUUID())
                .statusCode())
        .isEqualTo(201);
  }

  private Map<String, Object> correction(Shop shop) {
    UUID record =
        jdbc.queryForObject(
            "SELECT id FROM app.origin_record WHERE scope_id=?", UUID.class, shop.scope());
    return Map.of(
        "scopeId",
        shop.scope(),
        "ingredientId",
        CABBAGE,
        "expectedVersion",
        0,
        "withdrawRecordIds",
        List.of(record),
        "reason",
        "테스트 근거 철회");
  }

  private Map<String, Object> resolution() {
    return Map.of("expectedVersion", 0, "decision", "DISMISSED", "reason", "테스트 확인 사유");
  }

  private Map<String, Object> body(UUID restaurant) {
    return Map.of(
        "restaurantId",
        restaurant,
        "category",
        "ORIGIN_CHANGED",
        "description",
        "테스트 표시판 내용이 다릅니다",
        "mediaIds",
        List.of());
  }

  private String create(Actor owner, UUID restaurant) throws Exception {
    var response =
        request("POST", "/api/v1/information-reports", owner, body(restaurant), UUID.randomUUID());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).path("id").asString();
  }

  private Actor actor(boolean admin) {
    var member = members.loginWithVerifiedKakaoSubject("test-feedback-" + UUID.randomUUID());
    if (admin) members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 신고 검수자", "test-fixture");
    return new Actor(
        member.id(), jwt.issue(sessions.create(members.requireActive(member.id())).session()));
  }

  private Shop shop(Actor owner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,published) VALUES (?,?,'테스트 주소','UNKNOWN','MISSING',true)",
        id,
        "가상 테스트 신고 가게 " + id);
    var now = Instant.now();
    UUID scope =
        origins.approve(
            new OriginPublicationRequest(
                id,
                null,
                "테스트 반찬 김치",
                "SIDE_DISH",
                now.minusSeconds(60),
                now,
                UUID.randomUUID(),
                null,
                List.of(
                    new OriginPublicationRequest.Assertion(
                        CABBAGE,
                        "배추 국내산 테스트",
                        new OriginValue(
                            OriginValue.Classification.DOMESTIC,
                            List.of(
                                new OriginValue.Component(
                                    OriginValue.Classification.DOMESTIC, "KR", null)))))),
            owner.id());
    return new Shop(id, scope);
  }

  private HttpResponse<String> request(
      String method, String path, Actor actor, Object body, UUID key) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + actor.token());
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
