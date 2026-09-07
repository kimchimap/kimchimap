package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.media.service.MediaService;
import kr.kimchimap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

class ReportReviewIntegrationTest extends ApplicationIntegrationSupport {
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  private static final UUID CABBAGE = UUID.fromString("00000000-0000-4000-8000-000000000001");

  @DynamicPropertySource
  static void imageStorage(DynamicPropertyRegistry properties) {
    try {
      String root = Files.createTempDirectory("kimchimap-report-test-").toString();
      properties.add("app.media.root", () -> root);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  @Autowired MediaService media;
  private final JsonMapper json = JsonMapper.builder().build();

  private record User(UUID id, String token) {}

  @Test
  void submissionsAreIdempotentOwnedAndImmutableAfterApproval() throws Exception {
    User owner = user(false), other = user(false), admin = user(true);
    UUID restaurant = restaurant(), image = image(owner);
    var body = submission(restaurant, null, image, "DOMESTIC");
    UUID key = UUID.randomUUID();
    var created = request("POST", "/api/v1/reports", owner, body, key);
    assertThat(created.statusCode()).isEqualTo(201);
    String id = json.readTree(created.body()).path("id").asString();
    assertThat(
            json.readTree(request("POST", "/api/v1/reports", owner, body, key).body())
                .path("id")
                .asString())
        .isEqualTo(id);
    assertThat(
            request(
                    "POST",
                    "/api/v1/reports",
                    owner,
                    submission(restaurant, null, image, "UNKNOWN"),
                    key)
                .statusCode())
        .isEqualTo(409);
    assertThat(request("GET", "/api/v1/reports/" + id, other, null, null).statusCode())
        .isEqualTo(404);
    assertThat(
            request(
                    "PATCH",
                    "/api/v1/reports/" + id,
                    other,
                    Map.of("expectedVersion", 0, "submission", body),
                    null)
                .statusCode())
        .isEqualTo(404);
    assertThat(request("GET", "/api/v1/admin/reports", owner, null, null).statusCode())
        .isEqualTo(403);
    var approved =
        request(
            "POST",
            "/api/v1/admin/reports/" + id + "/reviews",
            admin,
            review("APPROVED", 0, null, List.of()),
            null);
    assertThat(approved.statusCode()).isEqualTo(200);
    assertThat(json.readTree(approved.body()).path("state").asString()).isEqualTo("APPROVED");
    assertThat(
            request(
                    "PATCH",
                    "/api/v1/reports/" + id,
                    owner,
                    Map.of("expectedVersion", 1, "submission", body),
                    null)
                .statusCode())
        .isEqualTo(409);
    assertThat(get("/api/v1/restaurants/" + restaurant).body())
        .contains("SIGNBOARD_OBSERVATION", "DOMESTIC", "실제 납품");
    assertThat(get("/api/v1/media/" + image + "/public").statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.report WHERE owner_id=?", Integer.class, owner.id()))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app.report_revision SET body='{}' WHERE report_id=?",
                    UUID.fromString(id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void rejectsForeignAttachmentsAndMissingConsentDateOrVersion() throws Exception {
    User owner = user(false), other = user(false);
    UUID restaurant = restaurant(), foreign = image(other);
    assertThat(
            request(
                    "POST",
                    "/api/v1/reports",
                    owner,
                    submission(restaurant, null, foreign, "DOMESTIC"),
                    UUID.randomUUID())
                .statusCode())
        .isEqualTo(404);
    var body = new java.util.HashMap<>(submission(restaurant, null, image(owner), "DOMESTIC"));
    body.put("publicationConsent", false);
    assertThat(request("POST", "/api/v1/reports", owner, body, UUID.randomUUID()).statusCode())
        .isEqualTo(400);
    body.put("publicationConsent", true);
    body.put("observedOn", LocalDate.now().plusDays(2).toString());
    assertThat(request("POST", "/api/v1/reports", owner, body, UUID.randomUUID()).statusCode())
        .isEqualTo(400);
    body.put("observedOn", LocalDate.now().toString());
    assertThat(request("POST", "/api/v1/reports", owner, body, null).statusCode()).isEqualTo(400);
    String id = create(owner, body);
    assertThat(
            request("PATCH", "/api/v1/reports/" + id, owner, Map.of("submission", body), null)
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void moreInformationCreatesNewRevisionAndWithdrawalPreventsReview() throws Exception {
    User owner = user(false), admin = user(true);
    var body = submission(restaurant(), null, image(owner), "UNKNOWN");
    String id = create(owner, body);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + id + "/reviews",
                    admin,
                    review("NEEDS_MORE_INFO", 0, null, List.of()),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "PATCH",
                    "/api/v1/reports/" + id,
                    owner,
                    Map.of("expectedVersion", 0, "submission", body),
                    null)
                .statusCode())
        .isEqualTo(409);
    var updated =
        request(
            "PATCH",
            "/api/v1/reports/" + id,
            owner,
            Map.of("expectedVersion", 1, "submission", body),
            null);
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThat(json.readTree(updated.body()).path("state").asString()).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.report_revision WHERE report_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(2);
    assertThat(
            request(
                    "POST",
                    "/api/v1/reports/" + id + "/withdraw",
                    owner,
                    Map.of("expectedVersion", 2, "reason", "테스트 철회"),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + id + "/reviews",
                    admin,
                    review("APPROVED", 3, null, List.of()),
                    null)
                .statusCode())
        .isEqualTo(409);
  }

  @Test
  void simultaneousReviewPublishesOnceAndConflictingClaimsRemainDisputed() throws Exception {
    User owner = user(false), admin = user(true), secondAdmin = user(true);
    UUID restaurant = restaurant();
    String id = create(owner, submission(restaurant, null, image(owner), "DOMESTIC"));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return request(
                        "POST",
                        "/api/v1/admin/reports/" + id + "/reviews",
                        admin,
                        review("APPROVED", 0, null, List.of()),
                        null)
                    .statusCode();
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return request(
                        "POST",
                        "/api/v1/admin/reports/" + id + "/reviews",
                        secondAdmin,
                        review("APPROVED", 0, null, List.of()),
                        null)
                    .statusCode();
              });
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE restaurant_id=? AND ingredient_id<>(SELECT id FROM app.ingredient WHERE code='rice')",
                Integer.class,
                restaurant))
        .isEqualTo(1);
    UUID scope =
        jdbc.queryForObject(
            "SELECT id FROM app.serving_scope WHERE restaurant_id=? AND usage='SIDE_DISH'",
            UUID.class,
            restaurant);
    String conflicting =
        create(owner, submission(restaurant, scope, image(owner), "IMPORTED_SPECIFIED"));
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + conflicting + "/reviews",
                    admin,
                    review("APPROVED", 0, scope, List.of()),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(get("/api/v1/restaurants/" + restaurant).body()).contains("DISPUTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM app.origin_publication WHERE scope_id=?", String.class, scope))
        .isEqualTo("DISPUTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE restaurant_id=? AND ingredient_id<>(SELECT id FROM app.ingredient WHERE code='rice')",
                Integer.class,
                restaurant))
        .isEqualTo(2);
  }

  @Test
  void failedScopeMappingRollsBackImagePublicationAndReview() throws Exception {
    User owner = user(false), admin = user(true);
    UUID image = image(owner);
    UUID restaurant = restaurant();
    String id = create(owner, submission(restaurant, null, image, "DOMESTIC"));
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + id + "/reviews",
                    admin,
                    review("APPROVED", 0, UUID.randomUUID(), List.of(image)),
                    null)
                .statusCode())
        .isEqualTo(400);
    assertThat(get("/api/v1/media/" + image + "/public").statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM app.report WHERE id=?", String.class, UUID.fromString(id)))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.report_review WHERE report_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isZero();
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + id + "/reviews",
                    admin,
                    review("APPROVED", 0, null, List.of(image)),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(get("/api/v1/media/" + image + "/public").statusCode()).isEqualTo(200);
    assertThat(get("/api/v1/media/" + image + "?original=true").statusCode()).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE restaurant_id=? AND ingredient_id<>(SELECT id FROM app.ingredient WHERE code='rice')",
                Integer.class,
                restaurant))
        .isEqualTo(1);
    jdbc.update(
        "INSERT INTO app.origin_withdrawal(record_id,reason,actor_reference,withdrawn_at) SELECT id,'테스트 공개 자격 상실','test-admin',CURRENT_TIMESTAMP FROM app.origin_record WHERE restaurant_id=?",
        restaurant);
    assertThat(get("/api/v1/media/" + image + "/public").statusCode()).isEqualTo(404);
    assertThat(request("GET", "/api/v1/media/" + image, owner, null, null).statusCode())
        .isEqualTo(200);
  }

  @Test
  void rejectionDoesNotPublishAndListsAreOwnerScoped() throws Exception {
    User owner = user(false), other = user(false), admin = user(true);
    UUID restaurant = restaurant();
    String id = create(owner, submission(restaurant, null, image(owner), "UNKNOWN"));
    assertThat(
            request(
                    "POST",
                    "/api/v1/admin/reports/" + id + "/reviews",
                    admin,
                    review("REJECTED", 0, null, List.of()),
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/api/v1/reports?state=REJECTED", owner, null, null).body())
        .contains(id);
    assertThat(request("GET", "/api/v1/reports", other, null, null).body()).doesNotContain(id);
    assertThat(request("GET", "/api/v1/reports?limit=51", owner, null, null).statusCode())
        .isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE restaurant_id=? AND ingredient_id<>(SELECT id FROM app.ingredient WHERE code='rice')",
                Integer.class,
                restaurant))
        .isZero();
  }

  private User user(boolean admin) {
    var member = members.loginWithVerifiedKakaoSubject("test-report-" + UUID.randomUUID());
    if (admin)
      members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 검수자 지정", "test-operator");
    return new User(
        member.id(), jwt.issue(sessions.create(members.requireActive(member.id())).session()));
  }

  private UUID restaurant() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,published) VALUES (?,?,'테스트 주소','OPEN','MISSING',true)",
        id,
        "가상 테스트 제보 업소 " + id);
    DomesticOriginFixture.addRice(jdbc, transactions, id);
    return id;
  }

  private UUID image(User user) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return media.upload(user.id(), bytes.toByteArray(), "image/png", "test-proof.png").id();
  }

  private Map<String, Object> submission(
      UUID restaurant, UUID scope, UUID image, String classification) {
    var body = new java.util.HashMap<String, Object>();
    body.put("restaurantId", restaurant);
    if (scope != null) body.put("scopeId", scope);
    body.put("scopeName", "테스트 반찬 김치");
    body.put("usage", "SIDE_DISH");
    body.put("observedOn", LocalDate.now().toString());
    body.put("mediaIds", List.of(image));
    body.put("publicationConsent", true);
    var components =
        classification.equals("UNKNOWN")
            ? List.of()
            : List.of(
                Map.of(
                    "kind",
                    classification,
                    "countryCode",
                    classification.equals("DOMESTIC") ? "KR" : "CN"));
    body.put(
        "claims",
        List.of(
            Map.of(
                "ingredientId",
                CABBAGE,
                "classification",
                classification,
                "originalExpression",
                "테스트 원산지 표시",
                "components",
                components)));
    return body;
  }

  private Map<String, Object> review(String decision, long version, UUID scope, List<UUID> images) {
    var result = new java.util.HashMap<String, Object>();
    result.put("decision", decision);
    result.put("expectedVersion", version);
    result.put("reason", "테스트 표시판 검토");
    result.put("privacyReviewedMediaIds", images);
    if (scope != null) result.put("approvedScopeId", scope);
    return result;
  }

  private String create(User owner, Map<String, Object> body) throws Exception {
    var response = request("POST", "/api/v1/reports", owner, body, UUID.randomUUID());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).path("id").asString();
  }

  private HttpResponse<String> request(String method, String path, User user, Object body, UUID key)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + user.token());
    if (key != null) request.header("Idempotency-Key", key.toString());
    if (body != null) request.header("Content-Type", "application/json");
    return client.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
