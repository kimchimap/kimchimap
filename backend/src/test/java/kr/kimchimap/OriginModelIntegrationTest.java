package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class OriginModelIntegrationTest extends ApplicationIntegrationSupport {
  private static final UUID CABBAGE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID PEPPER = UUID.fromString("00000000-0000-4000-8000-000000000002");
  @Autowired PlatformTransactionManager transactions;
  private final JsonMapper mapper = JsonMapper.builder().build();

  private record Fixture(UUID restaurant, UUID source, UUID evidence, UUID scope) {}

  @Test
  void contactRequiresUsableNumberAndPermissionToRepublish() throws Exception {
    var f = fixture(true, true);
    String path = "/api/v1/restaurants/" + f.restaurant();
    assertThat(mapper.readTree(get(path).body()).path("contact").isNull()).isTrue();
    jdbc.update(
        "UPDATE app.restaurant SET phone_display = '02-0000-0000', phone_number = '0200000000', phone_source_id = ? WHERE id = ?",
        f.source(),
        f.restaurant());
    var contact = mapper.readTree(get(path).body()).path("contact");
    assertThat(contact.path("display").asString()).isEqualTo("02-0000-0000");
    assertThat(contact.path("number").asString()).isEqualTo("0200000000");
    assertThat(contact.path("sourceName").asString()).isEqualTo("테스트 출처");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app.restaurant SET phone_number = 'javascript:alert(1)' WHERE id = ?",
                    f.restaurant()))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    jdbc.update(
        "UPDATE app.data_source SET republication_allowed = false WHERE id = ?", f.source());
    var hidden = get(path).body();
    assertThat(mapper.readTree(hidden).path("contact").isNull()).isTrue();
    assertThat(hidden).doesNotContain("0200000000", "02-0000-0000");
  }

  @Test
  void catalogsUseExtensibleReferenceDataWithoutInventingRestaurants() throws Exception {
    var ingredients = get("/api/v1/catalogs/ingredients");
    assertThat(ingredients.statusCode()).isEqualTo(200);
    assertThat(ingredients.body())
        .contains("배추", "고춧가루", "rice")
        .doesNotContain("hibernate", "active");
    assertThat(get("/api/v1/catalogs/countries").body()).contains("KR", "대한민국");
    jdbc.update(
        "INSERT INTO app.ingredient(id, code, name, category) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        "test-garlic",
        "테스트 마늘",
        "vegetable");
    assertThat(get("/api/v1/catalogs/ingredients").body()).contains("테스트 마늘");
  }

  @Test
  void publishedDetailPreservesCoordinatesScopesDatesAndEvidence() throws Exception {
    var f = fixture(true, true);
    UUID cabbage = claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    var stew = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision) VALUES (?, ?, ?, 'STEW', 'SPECIFIC_ITEM')",
        stew,
        f.restaurant(),
        "테스트 찌개 김치");
    claim(
        new Fixture(f.restaurant(), f.source(), f.evidence(), stew),
        PEPPER,
        "IMPORTED_SPECIFIED",
        "CN",
        "APPROVED",
        null);
    var response = get("/api/v1/restaurants/" + f.restaurant());
    assertThat(response.statusCode()).isEqualTo(200);
    var doc = mapper.readTree(response.body());
    assertThat(doc.path("longitude").asDouble()).isEqualTo(127.0);
    assertThat(doc.path("latitude").asDouble()).isEqualTo(37.5);
    assertThat(doc.path("scopes").size()).isEqualTo(2);
    assertThat(response.body())
        .contains(
            cabbage.toString(),
            "SIDE_DISH",
            "STEW",
            "SIGNBOARD_OBSERVATION",
            "2026-01-01T00:00:00Z",
            "STALE");
    assertThat(response.body())
        .doesNotContain("source-secret-test", "version", "createdTransaction");
    jdbc.update(
        "UPDATE app.evidence SET last_fetch_succeeded_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now()),
        f.evidence());
    assertThat(get("/api/v1/restaurants/" + f.restaurant()).body())
        .contains("2026-01-01T00:00:00Z", "STALE");
  }

  @Test
  void conflictsDoNotSelectDomesticAndExpiryDoesNotBecomeCurrent() throws Exception {
    var f = fixture(true, true);
    claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    claim(f, CABBAGE, "IMPORTED_SPECIFIED", "CN", "APPROVED", null);
    var response = mapper.readTree(get("/api/v1/restaurants/" + f.restaurant()).body());
    var origin = response.path("scopes").get(0).path("origins").get(0);
    assertThat(origin.path("status").asString()).isEqualTo("DISPUTED");
    assertThat(origin.path("selectedRecordId").isNull()).isTrue();
    var expired = fixture(true, true);
    claim(expired, CABBAGE, "DOMESTIC", "KR", "APPROVED", Instant.parse("2026-03-01T00:00:00Z"));
    assertThat(get("/api/v1/restaurants/" + expired.restaurant()).body()).contains("EXPIRED");
  }

  @Test
  void privateAndUnapprovedEvidenceNeverLeaksAndDesignationCreatesNoOrigin() throws Exception {
    var f = fixture(true, false);
    claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    assertThat(get("/api/v1/restaurants/" + f.restaurant()).body())
        .doesNotContain("비공개 테스트 근거", "DOMESTIC");
    var pending = fixture(true, true);
    claim(pending, CABBAGE, "DOMESTIC", "KR", "PENDING", null);
    jdbc.update(
        """
        INSERT INTO app.designation(id, restaurant_id, source_id, external_id, scheme_name,
            applicable_items, criteria_original, publicly_visible)
        VALUES (?, ?, ?, ?, ?, ?, ?, true)
        """,
        UUID.randomUUID(),
        pending.restaurant(),
        pending.source(),
        UUID.randomUUID().toString(),
        "테스트 지정",
        "반찬 김치",
        "테스트 제도 원문 기준");
    var response = get("/api/v1/restaurants/" + pending.restaurant());
    assertThat(response.body()).contains("테스트 지정", "반찬 김치").doesNotContain("DOMESTIC");
    assertThat(mapper.readTree(response.body()).path("scopes").get(0).path("origins").isEmpty())
        .isTrue();
    jdbc.update(
        "UPDATE app.data_source SET republication_allowed = false WHERE id = ?", pending.source());
    assertThat(get("/api/v1/restaurants/" + pending.restaurant()).body()).doesNotContain("테스트 지정");
  }

  @Test
  void invalidAndUnpublishedIdsUseSafeErrors() throws Exception {
    assertThat(get("/api/v1/restaurants/not-a-uuid").statusCode()).isEqualTo(400);
    var missing = get("/api/v1/restaurants/" + UUID.randomUUID());
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.body())
        .contains("RESTAURANT_NOT_FOUND", "traceId")
        .doesNotContain("SELECT", "Exception");
    assertThat(get("/api/v1/restaurants/" + fixture(false, true).restaurant()).statusCode())
        .isEqualTo(404);
  }

  @Test
  void databaseRejectsContradictoryComponentsCrossRestaurantEvidenceAndRevisionsMutation() {
    var f = fixture(true, true);
    assertThatThrownBy(() -> claim(f, CABBAGE, "DOMESTIC", "CN", "APPROVED", null))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    assertThatThrownBy(() -> claim(f, CABBAGE, "UNKNOWN", "KR", "APPROVED", null))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    var other = fixture(true, true);
    assertThatThrownBy(
            () ->
                claim(
                    new Fixture(f.restaurant(), f.source(), other.evidence(), f.scope()),
                    CABBAGE,
                    "DOMESTIC",
                    "KR",
                    "APPROVED",
                    null))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    var id = claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app.origin_record SET original_expression = '변조' WHERE id = ?", id))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM app.origin_component WHERE record_id = ?", id))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO app.origin_component(record_id, component_index, country_code, origin_kind) VALUES (?, 1, 'CN', 'IMPORTED_SPECIFIED')",
                    id))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
  }

  @Test
  void spatialConstraintsAndExternalIdentifiersPreventInvalidWrites() {
    var f = fixture(true, true);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app.restaurant SET location = public.ST_SetSRID(public.ST_MakePoint(37.5, 127), 4326) WHERE id = ?",
                    f.restaurant()))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
    jdbc.update(
        "INSERT INTO app.restaurant_external_id(source_id, external_id, restaurant_id) VALUES (?, 'test-id', ?)",
        f.source(),
        f.restaurant());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO app.restaurant_external_id(source_id, external_id, restaurant_id) VALUES (?, 'test-id', ?)",
                    f.source(),
                    f.restaurant()))
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class);
  }

  @Test
  void privateConflictBlocksPositiveSelectionWithoutExposingEvidence() throws Exception {
    var f = fixture(true, true);
    claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    var secret = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO app.evidence(id, restaurant_id, source_id, source_identifier, kind, public_summary,
            publicly_visible, collected_at, last_fetch_succeeded_at)
        VALUES (?, ?, ?, '비공개 식별자', 'SIGNBOARD_OBSERVATION', '숨겨야 하는 근거', false,
            '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z')
        """,
        secret,
        f.restaurant(),
        f.source());
    claim(
        new Fixture(f.restaurant(), f.source(), secret, f.scope()),
        CABBAGE,
        "IMPORTED_SPECIFIED",
        "CN",
        "APPROVED",
        null);
    var response = get("/api/v1/restaurants/" + f.restaurant());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("DISPUTED")
        .doesNotContain("숨겨야 하는 근거", "비공개 식별자", "IMPORTED_SPECIFIED");
  }

  @Test
  void withdrawingRecordExcludesItWithoutChangingOriginalRevision() throws Exception {
    var f = fixture(true, true);
    var id = claim(f, CABBAGE, "DOMESTIC", "KR", "APPROVED", null);
    jdbc.update(
        "INSERT INTO app.origin_withdrawal(record_id, reason, actor_reference, withdrawn_at) VALUES (?, '테스트 정정', 'test-admin', CURRENT_TIMESTAMP)",
        id);
    assertThat(get("/api/v1/restaurants/" + f.restaurant()).body())
        .contains("UNAVAILABLE")
        .doesNotContain("DOMESTIC");
    assertThat(
            jdbc.queryForObject(
                "SELECT original_expression FROM app.origin_record WHERE id = ?", String.class, id))
        .isEqualTo("테스트 원산지 원문");
  }

  @Test
  void excessiveScopeCountReturnsExplicitErrorInsteadOfPartialTruth() throws Exception {
    var f = fixture(true, true);
    jdbc.update(
        """
        INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision)
        SELECT gen_random_uuid(), ?, '테스트 대량 품목', 'OTHER', 'SPECIFIC_ITEM' FROM generate_series(1, 200)
        """,
        f.restaurant());
    var response = get("/api/v1/restaurants/" + f.restaurant());
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.body()).contains("DETAIL_LIMIT_EXCEEDED");
  }

  private Fixture fixture(boolean published, boolean publicEvidence) {
    var f = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    transaction(
        () -> {
          jdbc.update(
              "INSERT INTO app.data_source(id, code, name, republication_allowed) VALUES (?, ?, '테스트 출처', true)",
              f.source(),
              UUID.randomUUID().toString());
          jdbc.update(
              """
          INSERT INTO app.restaurant(id, name, address, business_status, coordinate_status, location, published)
          VALUES (?, '가상 테스트 업소', '테스트 주소', 'OPEN', 'VERIFIED', public.ST_SetSRID(public.ST_MakePoint(127, 37.5), 4326), ?)
          """,
              f.restaurant(),
              published);
          jdbc.update(
              "INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision) VALUES (?, ?, '테스트 반찬 김치', 'SIDE_DISH', 'SPECIFIC_ITEM')",
              f.scope(),
              f.restaurant());
          jdbc.update(
              """
          INSERT INTO app.evidence(id, restaurant_id, source_id, source_identifier, kind, public_summary,
              publicly_visible, collected_at, last_fetch_succeeded_at)
          VALUES (?, ?, ?, 'source-secret-test', 'SIGNBOARD_OBSERVATION', ?, ?, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z')
          """,
              f.evidence(),
              f.restaurant(),
              f.source(),
              publicEvidence ? "테스트 표시판 내용" : "비공개 테스트 근거",
              publicEvidence);
        });
    return f;
  }

  private UUID claim(
      Fixture f,
      UUID ingredient,
      String classification,
      String country,
      String review,
      Instant until) {
    var id = UUID.randomUUID();
    transaction(
        () -> {
          jdbc.update(
              """
          INSERT INTO app.origin_record(id, restaurant_id, scope_id, ingredient_id, evidence_id,
              classification, original_expression, observed_at, observed_precision, source_updated_precision,
              reviewed_at, review_status, valid_until, revision)
          VALUES (?, ?, ?, ?, ?, ?, '테스트 원산지 원문', '2026-01-01T00:00:00Z', 'DATE', 'UNKNOWN',
              '2026-02-01T00:00:00Z', ?, ?, 1)
          """,
              id,
              f.restaurant(),
              f.scope(),
              ingredient,
              f.evidence(),
              classification,
              review,
              until == null ? null : java.sql.Timestamp.from(until));
          if (country != null)
            jdbc.update(
                "INSERT INTO app.origin_component(record_id, component_index, country_code, origin_kind) VALUES (?, 0, ?, ?)",
                id,
                country,
                country.equals("KR") ? "DOMESTIC" : "IMPORTED_SPECIFIED");
        });
    return id;
  }

  private void transaction(Runnable action) {
    new TransactionTemplate(transactions).executeWithoutResult(status -> action.run());
  }
}
