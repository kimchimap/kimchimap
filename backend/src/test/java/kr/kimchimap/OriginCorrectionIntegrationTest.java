package kr.kimchimap;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.kimchimap.auth.service.JwtService;
import kr.kimchimap.auth.service.SessionService;
import kr.kimchimap.member.service.MemberService;
import kr.kimchimap.origin.dto.OriginPublicationRequest;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.service.OriginPublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class OriginCorrectionIntegrationTest extends ApplicationIntegrationSupport {
  private static final UUID INGREDIENT = UUID.fromString("00000000-0000-4000-8000-000000000001");
  @Autowired OriginPublicationService origins;
  @Autowired MemberService members;
  @Autowired SessionService sessions;
  @Autowired JwtService jwt;
  private final JsonMapper json = JsonMapper.builder().build();

  private record Actor(UUID id, String token) {}

  @Test
  void correctionPreservesClaimsAndChangesPublicSearchWithAudit() throws Exception {
    Actor admin = actor(true);
    UUID restaurant = restaurant();
    UUID scope = approve(admin.id(), restaurant, null, "DOMESTIC");
    approve(admin.id(), restaurant, scope, "IMPORTED_SPECIFIED");
    UUID invalid = record(scope, "IMPORTED_SPECIFIED");
    assertThat(search(admin).body()).doesNotContain(restaurant.toString());
    var result = correct(admin, scope, 1, List.of(invalid));
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.body()).contains("CURRENT", "철회 테스트 사유", "withdrawn");
    assertThat(search(admin).body()).contains(restaurant.toString());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_record WHERE scope_id=?", Integer.class, scope))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_correction WHERE scope_id=?",
                Integer.class,
                scope))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE app.origin_correction SET reason='변조' WHERE scope_id=?", scope))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(correct(admin, scope, 1, List.of(record(scope, "DOMESTIC"))).statusCode())
        .isEqualTo(409);
    approve(admin.id(), restaurant, scope, "IMPORTED_SPECIFIED");
    assertThat(search(admin).body()).doesNotContain(restaurant.toString());
    assertThat(get("/api/v1/restaurants/" + restaurant).statusCode()).isEqualTo(404);
  }

  @Test
  void foreignScopeAndUnprivilegedCorrectionsCannotChangePublication() throws Exception {
    Actor admin = actor(true), user = actor(false);
    UUID restaurant = restaurant(), scope = approve(admin.id(), restaurant, null, "DOMESTIC");
    UUID other = approve(admin.id(), restaurant, null, "IMPORTED_SPECIFIED");
    assertThat(correct(user, scope, 0, List.of(record(scope, "DOMESTIC"))).statusCode())
        .isEqualTo(403);
    assertThat(correct(admin, scope, 0, List.of(record(other, "IMPORTED_SPECIFIED"))).statusCode())
        .isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.origin_withdrawal WHERE record_id=?",
                Integer.class,
                record(scope, "DOMESTIC")))
        .isZero();
    assertThat(correct(admin, scope, 0, List.of(record(scope, "DOMESTIC"))).statusCode())
        .isEqualTo(200);
    assertThat(get("/api/v1/restaurants/" + restaurant).statusCode()).isEqualTo(404);
    assertThat(request("GET", "/api/v1/admin/origins?limit=51", admin, null).statusCode())
        .isEqualTo(400);
    assertThat(
            request("GET", "/api/v1/admin/origins/" + scope + "/" + INGREDIENT, admin, null)
                .statusCode())
        .isEqualTo(200);
  }

  private UUID restaurant() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,location,published) VALUES (?,?,'테스트 주소','OPEN','VERIFIED',public.ST_SetSRID(public.ST_MakePoint(126.978,37.5665),4326),true)",
        id,
        "가상 테스트 정정 업소 " + id);
    return id;
  }

  private UUID approve(UUID actor, UUID restaurant, UUID scope, String classification) {
    var kind = OriginValue.Classification.valueOf(classification);
    var value =
        new OriginValue(
            kind,
            List.of(
                new OriginValue.Component(
                    kind, classification.equals("DOMESTIC") ? "KR" : "CN", null)));
    var now = Instant.now();
    return origins.approve(
        new OriginPublicationRequest(
            restaurant,
            scope,
            "테스트 반찬 김치",
            "SIDE_DISH",
            now.minusSeconds(86400),
            now,
            UUID.randomUUID(),
            null,
            List.of(
                new OriginPublicationRequest.Assertion(
                    INGREDIENT, "테스트 원문 " + classification, value))),
        actor);
  }

  private UUID record(UUID scope, String classification) {
    return jdbc.queryForObject(
        "SELECT id FROM app.origin_record WHERE scope_id=? AND classification=? ORDER BY revision DESC LIMIT 1",
        UUID.class,
        scope,
        classification);
  }

  private Actor actor(boolean admin) {
    var member =
        members.loginWithVerifiedKakaoSubject("test-origin-correction-" + UUID.randomUUID());
    if (admin) members.changeSecurity(member.id(), "ADMIN", "ACTIVE", "테스트 검수자", "test-fixture");
    return new Actor(
        member.id(), jwt.issue(sessions.create(members.requireActive(member.id())).session()));
  }

  private HttpResponse<String> correct(Actor actor, UUID scope, long version, List<UUID> records)
      throws Exception {
    return request(
        "POST",
        "/api/v1/admin/origin-corrections",
        actor,
        Map.of(
            "scopeId",
            scope,
            "ingredientId",
            INGREDIENT,
            "expectedVersion",
            version,
            "withdrawRecordIds",
            records,
            "reason",
            "철회 테스트 사유"));
  }

  private HttpResponse<String> search(Actor actor) throws Exception {
    var response =
        request(
            "POST",
            "/api/v1/restaurants/search",
            actor,
            Map.of(
                "center",
                Map.of("latitude", 37.5665, "longitude", 126.978),
                "radiusMeters",
                100,
                "limit",
                100,
                "groups",
                List.of(
                    Map.of(
                        "usage",
                        "SIDE_DISH",
                        "ingredients",
                        List.of(Map.of("ingredientId", INGREDIENT, "mode", "DOMESTIC"))))));
    assertThat(response.statusCode()).isEqualTo(200);
    return response;
  }

  private HttpResponse<String> request(String method, String path, Actor actor, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Authorization", "Bearer " + actor.token());
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
