package kr.kimchimap;

import static kr.kimchimap.search.dto.SearchRequest.OriginMode.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.kimchimap.restaurant.entity.NormalizedCoordinate.Status;
import kr.kimchimap.restaurant.service.CoordinateNormalizationService;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchRequest.*;
import kr.kimchimap.search.service.MapSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class SpatialSearchIntegrationTest extends ApplicationIntegrationSupport {
  private static final UUID CABBAGE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID PEPPER = UUID.fromString("00000000-0000-4000-8000-000000000002");
  @Autowired MapSearchService search;
  @Autowired CoordinateNormalizationService coordinates;
  @Autowired PlatformTransactionManager transactions;
  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

  private record Shop(UUID id, UUID source, UUID evidence, UUID scope) {}

  @Test
  void radiusIncludesInsideAndBoundaryAndExcludesOutsideUsingMeters() {
    double longitude = 127.1;
    var inside = shop(longitude);
    var boundary = shop(longitude);
    var outside = shop(longitude);
    for (var shop : List.of(inside, boundary, outside))
      claim(shop, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    project(inside.id(), longitude, 99);
    project(boundary.id(), longitude, 100);
    project(outside.id(), longitude, 100.001);
    var result = search.search(radius(longitude, 100, List.of(), 100, null));
    assertThat(result.items())
        .extracting(item -> item.id())
        .containsExactly(inside.id(), boundary.id());
    assertThat(result.items().getLast().distanceMeters())
        .isCloseTo(100d, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void boundingBoxIncludesEdgesAndUsesSameOriginMeaning() {
    double longitude = 127.2;
    var west = shop(longitude);
    var east = shop(longitude + 0.002);
    var outside = shop(longitude - 0.001);
    for (var s : List.of(west, east, outside))
      claim(s, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    var filter = List.of(group(new IngredientFilter(CABBAGE, DOMESTIC, null, false)));
    var result =
        search.search(
            new SearchRequest(
                null,
                new Bounds(37.49, longitude, 37.51, longitude + 0.002),
                null,
                filter,
                null,
                null,
                100,
                null));
    assertThat(result.items())
        .extracting(item -> item.id())
        .containsExactlyInAnyOrder(west.id(), east.id());
    assertThat(result.items()).allMatch(item -> item.matchedScopes().size() == 1);
  }

  @Test
  void differentKimchiItemsCannotContributePartsToOneScopeFilter() {
    double longitude = 127.3;
    var correct = shop(longitude);
    var split = shop(longitude);
    claim(correct, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    claim(correct, PEPPER, "DOMESTIC", List.of("KR"), null, true);
    claim(split, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    var stew = anotherScope(split, "STEW");
    claim(stew, PEPPER, "DOMESTIC", List.of("KR"), null, true);
    var request =
        radius(
            longitude,
            500,
            List.of(
                group(
                    new IngredientFilter(CABBAGE, DOMESTIC, null, false),
                    new IngredientFilter(PEPPER, DOMESTIC, null, false))),
            20,
            null);
    var result = search.search(request);
    assertThat(result.items()).extracting(item -> item.id()).containsExactly(correct.id());
    assertThat(result.items().getFirst().matchedScopes().getFirst().id())
        .isEqualTo(correct.scope());
    var independent =
        radius(
            longitude,
            500,
            List.of(
                group(new IngredientFilter(CABBAGE, DOMESTIC, null, false)),
                group(new IngredientFilter(PEPPER, DOMESTIC, null, false))),
            20,
            null);
    assertThat(search.search(independent).items())
        .extracting(item -> item.id())
        .containsExactlyInAnyOrder(correct.id(), split.id());
  }

  @Test
  void unknownMixedDisputedExpiredAndPrivateClaimsNeverSatisfyDomestic() {
    double longitude = 127.4;
    var domestic = shop(longitude);
    var unknown = shop(longitude);
    var mixed = shop(longitude);
    var disputed = shop(longitude);
    var expired = shop(longitude);
    var hidden = shop(longitude);
    var pending = shop(longitude);
    claim(domestic, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    claim(unknown, CABBAGE, "UNKNOWN", List.of(), null, true);
    claim(mixed, CABBAGE, "MIXED", List.of("KR", "CN"), null, true);
    claim(disputed, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    claim(disputed, CABBAGE, "IMPORTED_SPECIFIED", List.of("CN"), null, true);
    claim(expired, CABBAGE, "DOMESTIC", List.of("KR"), Instant.parse("2026-03-01T00:00:00Z"), true);
    claim(hidden, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    jdbc.update("UPDATE app.evidence SET publicly_visible = false WHERE id = ?", hidden.evidence());
    claim(pending, CABBAGE, "DOMESTIC", List.of("KR"), null, false);
    var result =
        search.search(
            radius(
                longitude,
                500,
                List.of(group(new IngredientFilter(CABBAGE, DOMESTIC, null, false))),
                100,
                null));
    assertThat(result.items()).extracting(item -> item.id()).containsExactly(domestic.id());
    assertThat(search.search(radius(longitude, 500, List.of(), 100, null)).items())
        .extracting(item -> item.id())
        .containsExactly(domestic.id());
    // 혼합 필터도 별도로 국내산 사용 항목이 확인된 업소 안에서 적용한다.
    claim(mixed, PEPPER, "DOMESTIC", List.of("KR"), null, true);
    var mixedAllowed =
        search.search(
            radius(
                longitude,
                500,
                List.of(group(new IngredientFilter(CABBAGE, COUNTRIES, Set.of("KR"), true))),
                100,
                null));
    assertThat(mixedAllowed.items())
        .extracting(item -> item.id())
        .containsExactlyInAnyOrder(domestic.id(), mixed.id());
  }

  @Test
  void countryOrAndUnspecifiedImportAreSeparateFromUnknown() {
    double longitude = 127.5;
    var china = shop(longitude);
    var usa = shop(longitude);
    var unspecified = shop(longitude);
    var unknown = shop(longitude);
    for (var shop : List.of(china, usa, unspecified, unknown))
      claim(shop, PEPPER, "DOMESTIC", List.of("KR"), null, true);
    claim(china, CABBAGE, "IMPORTED_SPECIFIED", List.of("CN"), null, true);
    claim(usa, CABBAGE, "IMPORTED_SPECIFIED", List.of("US"), null, true);
    claim(
        unspecified,
        CABBAGE,
        "IMPORTED_UNSPECIFIED",
        java.util.Arrays.asList((String) null),
        null,
        true);
    claim(unknown, CABBAGE, "UNKNOWN", List.of(), null, true);
    assertThat(
            search
                .search(
                    radius(
                        longitude,
                        500,
                        List.of(
                            group(
                                new IngredientFilter(
                                    CABBAGE, COUNTRIES, Set.of("CN", "US"), false))),
                        100,
                        null))
                .items())
        .extracting(item -> item.id())
        .containsExactlyInAnyOrder(china.id(), usa.id());
    assertThat(
            search
                .search(
                    radius(
                        longitude,
                        500,
                        List.of(
                            group(
                                new IngredientFilter(CABBAGE, IMPORTED_UNSPECIFIED, null, false))),
                        100,
                        null))
                .items())
        .extracting(item -> item.id())
        .containsExactly(unspecified.id());
    assertThat(
            search
                .search(
                    radius(
                        longitude,
                        500,
                        List.of(group(new IngredientFilter(CABBAGE, UNKNOWN, null, false))),
                        100,
                        null))
                .items())
        .extracting(item -> item.id())
        .containsExactly(unknown.id());
  }

  @Test
  void sameDistancePagesHaveStableOrderAndNoDuplicateRows() {
    double longitude = 127.6;
    var first = shop(longitude);
    var second = shop(longitude);
    var third = shop(longitude);
    for (var shop : List.of(first, second, third))
      claim(shop, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    var page1 = search.search(radius(longitude, 500, List.of(), 1, null));
    var page2 = search.search(radius(longitude, 500, List.of(), 1, page1.nextCursor()));
    var page3 = search.search(radius(longitude, 500, List.of(), 1, page2.nextCursor()));
    assertThat(page1.truncated()).isTrue();
    assertThat(page3.truncated()).isFalse();
    assertThat(page3.nextCursor()).isNull();
    assertThat(page1.asOf()).isEqualTo(page3.asOf());
    var actual =
        List.of(
            page1.items().getFirst().id().toString(),
            page2.items().getFirst().id().toString(),
            page3.items().getFirst().id().toString());
    var expected =
        List.of(first.id().toString(), second.id().toString(), third.id().toString()).stream()
            .sorted()
            .toList();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void evidenceAndObservationFiltersDoNotUseDownloadDate() {
    double longitude = 127.7;
    var s = shop(longitude);
    claim(s, CABBAGE, "DOMESTIC", List.of("KR"), null, true);
    var groups = List.of(group(new IngredientFilter(CABBAGE, DOMESTIC, null, false)));
    assertThat(
            search
                .search(
                    new SearchRequest(
                        new Center(37.5, longitude), null, 500d, groups, 30, null, 20, null))
                .items())
        .isEmpty();
    jdbc.update(
        "UPDATE app.evidence SET last_fetch_succeeded_at = CURRENT_TIMESTAMP WHERE id = ?",
        s.evidence());
    assertThat(
            search
                .search(
                    new SearchRequest(
                        new Center(37.5, longitude), null, 500d, groups, 30, null, 20, null))
                .items())
        .isEmpty();
    assertThat(
            search
                .search(
                    new SearchRequest(
                        new Center(37.5, longitude),
                        null,
                        500d,
                        groups,
                        null,
                        Set.of(EvidenceKind.SUPPLY_VERIFICATION),
                        20,
                        null))
                .items())
        .isEmpty();
    assertThat(
            search
                .search(
                    new SearchRequest(
                        new Center(37.5, longitude),
                        null,
                        500d,
                        groups,
                        null,
                        Set.of(EvidenceKind.SIGNBOARD_OBSERVATION),
                        20,
                        null))
                .items())
        .hasSize(1);
  }

  @Test
  void publicReadSearchWorksWithoutCsrfWhileRefreshRemainsProtected() throws Exception {
    var good =
        post(
            mapper.writeValueAsString(radius(127.8, 500, List.of(), 20, null)),
            "/api/v1/restaurants/search");
    assertThat(good.statusCode()).isEqualTo(200);
    assertThat(
            post(
                    "{\"center\":{\"latitude\":127,\"longitude\":37},\"radiusMeters\":100}",
                    "/api/v1/restaurants/search")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            post(
                    "{\"center\":{\"longitude\":127},\"radiusMeters\":100}",
                    "/api/v1/restaurants/search")
                .statusCode())
        .isEqualTo(400);
    assertThat(post("{}", "/api/v1/auth/refresh").statusCode()).isEqualTo(403);
    var wide =
        search.search(
            new SearchRequest(
                null, new Bounds(33, 124, 39, 132), null, null, null, null, 20, null));
    assertThat(wide.zoomRequired()).isTrue();
    assertThat(wide.truncated()).isTrue();
    assertThat(wide.items()).isEmpty();
  }

  @Test
  void sourceCoordinatesAreTransformedAndMissingInvalidInputsRemainSeparate() {
    var projected =
        jdbc.queryForMap(
            "SELECT public.ST_X(p) AS x, public.ST_Y(p) AS y FROM (SELECT public.ST_Transform(public.ST_SetSRID(public.ST_MakePoint(127, 37.5), 4326), 5174) AS p) source");
    var normalized =
        coordinates.normalize(
            ((Number) projected.get("x")).doubleValue(),
            ((Number) projected.get("y")).doubleValue(),
            5174);
    assertThat(normalized.status()).isEqualTo(Status.VERIFIED);
    assertThat(normalized.longitude())
        .isCloseTo(127d, org.assertj.core.data.Offset.offset(0.00001));
    assertThat(normalized.latitude())
        .isCloseTo(37.5d, org.assertj.core.data.Offset.offset(0.00001));
    assertThat(coordinates.normalize(null, null, 5174).status()).isEqualTo(Status.MISSING);
    assertThat(coordinates.normalize(37.5, 127d, 4326).status()).isEqualTo(Status.INVALID);
    assertThat(coordinates.normalize(Double.NaN, 37.5, 4326).status()).isEqualTo(Status.INVALID);
    assertThat(coordinates.normalize(0d, 0d, 4326).status()).isEqualTo(Status.REVIEW_REQUIRED);
    assertThat(coordinates.normalize(127d, 37.5, 9999).status()).isEqualTo(Status.REVIEW_REQUIRED);
  }

  @Test
  void publicSearchRateLimitUsesRealRedisAndReturnsRetryInformation() throws Exception {
    HttpResponse<String> response = null;
    String body = mapper.writeValueAsString(radius(127.9, 100, List.of(), 20, null));
    for (int request = 0; request < 61; request++) {
      response = post(body, "/api/v1/restaurants/search");
      if (response.statusCode() == 429) break;
      assertThat(response.statusCode()).isEqualTo(200);
    }
    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(response.body()).contains("SEARCH_RATE_LIMITED", "traceId");
    assertThat(response.headers().firstValue("Retry-After")).contains("60");
    // 다음 HTTP 계약 테스트와 요청 제한 창을 분리한다.
    var keys = redis.keys("rate:public-search:*");
    if (!keys.isEmpty()) redis.delete(keys);
  }

  private SearchRequest radius(
      double longitude, double meters, List<ScopeFilter> groups, int limit, String cursor) {
    return new SearchRequest(
        new Center(37.5, longitude), null, meters, groups, null, null, limit, cursor);
  }

  private ScopeFilter group(IngredientFilter... ingredients) {
    return new ScopeFilter(null, List.of(ingredients));
  }

  private Shop shop(double longitude) {
    var s = new Shop(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    jdbc.update(
        "INSERT INTO app.data_source(id, code, name, republication_allowed) VALUES (?, ?, '가상 검색 출처', true)",
        s.source(),
        s.source().toString());
    jdbc.update(
        "INSERT INTO app.restaurant(id, name, address, business_status, coordinate_status, published, location) VALUES (?, '가상 검색 업소', '테스트 주소', 'OPEN', 'VERIFIED', true, public.ST_SetSRID(public.ST_MakePoint(?, 37.5), 4326))",
        s.id(),
        longitude);
    jdbc.update(
        "INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision) VALUES (?, ?, '테스트 김치', 'SIDE_DISH', 'SPECIFIC_ITEM')",
        s.scope(),
        s.id());
    jdbc.update(
        "INSERT INTO app.evidence(id, restaurant_id, source_id, source_identifier, kind, publicly_visible, collected_at, last_fetch_succeeded_at) VALUES (?, ?, ?, 'test', 'SIGNBOARD_OBSERVATION', true, '2026-02-01Z', '2026-02-01Z')",
        s.evidence(),
        s.id(),
        s.source());
    return s;
  }

  private Shop anotherScope(Shop s, String usage) {
    var scope = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision) VALUES (?, ?, '테스트 별도 김치', ?, 'SPECIFIC_ITEM')",
        scope,
        s.id(),
        usage);
    return new Shop(s.id(), s.source(), s.evidence(), scope);
  }

  private void project(UUID id, double longitude, double meters) {
    jdbc.update(
        "UPDATE app.restaurant SET location = public.ST_Project(public.ST_SetSRID(public.ST_MakePoint(?, 37.5), 4326)::public.geography, ?, 0)::public.geometry WHERE id = ?",
        longitude,
        meters,
        id);
  }

  private void claim(
      Shop s,
      UUID ingredient,
      String classification,
      List<String> countries,
      Instant until,
      boolean approved) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              var id = UUID.randomUUID();
              jdbc.update(
                  """
          INSERT INTO app.origin_record(id, restaurant_id, scope_id, ingredient_id, evidence_id, classification,
              original_expression, observed_at, observed_precision, source_updated_precision, reviewed_at, review_status, valid_until, revision)
          VALUES (?, ?, ?, ?, ?, ?, '가상 원산지', '2026-01-01Z', 'DATE', 'UNKNOWN', '2026-02-01Z', ?, ?, 1)
          """,
                  id,
                  s.id(),
                  s.scope(),
                  ingredient,
                  s.evidence(),
                  classification,
                  approved ? "APPROVED" : "PENDING",
                  until == null ? null : java.sql.Timestamp.from(until));
              for (int i = 0; i < countries.size(); i++) {
                String code = countries.get(i);
                jdbc.update(
                    "INSERT INTO app.origin_component(record_id, component_index, country_code, origin_kind) VALUES (?, ?, ?, ?)",
                    id,
                    i,
                    code,
                    code == null
                        ? "IMPORTED_UNSPECIFIED"
                        : code.equals("KR") ? "DOMESTIC" : "IMPORTED_SPECIFIED");
              }
            });
  }

  private HttpResponse<String> post(String body, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base() + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
