package kr.kimchimap.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.kimchimap.ApplicationIntegrationSupport;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchRequest.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Tag("performance")
class SearchPerformanceTest extends ApplicationIntegrationSupport {
  @Autowired MapSearchRepository repository;
  @Autowired JdbcClient client;
  @Autowired PlatformTransactionManager transactions;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void actualRadiusAndBoundsQueriesUseSpatialIndexesOnSyntheticDataset() throws Exception {
    var source = UUID.randomUUID();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              jdbc.update(
                  "INSERT INTO app.data_source(id, code, name, republication_allowed) VALUES (?, 'test-performance', '가상 성능 출처', true)",
                  source);
              jdbc.update(
                  """
          INSERT INTO app.restaurant(id, name, address, business_status, coordinate_status, published, location)
          SELECT gen_random_uuid(), '테스트 성능 업소 ' || n, '가상 주소', 'OPEN', 'VERIFIED', true,
            public.ST_SetSRID(public.ST_MakePoint(126 + (n %% 200) * 0.01, 36 + (n / 200) * 0.01), 4326)
          FROM generate_series(0, 19999) n
          """
                      .replace("%%", "%"));
              jdbc.update(
                  "INSERT INTO app.serving_scope(id, restaurant_id, name, usage, scope_precision) SELECT id, id, '테스트 반찬 김치', 'SIDE_DISH', 'SPECIFIC_ITEM' FROM app.restaurant");
              jdbc.update(
                  """
          INSERT INTO app.evidence(id, restaurant_id, source_id, source_identifier, kind, publicly_visible, collected_at, last_fetch_succeeded_at)
          SELECT id, id, ?, id::text, 'SIGNBOARD_OBSERVATION', true, '2026-02-01Z', '2026-02-01Z' FROM app.restaurant
          """,
                  source);
              jdbc.update(
                  """
          INSERT INTO app.origin_record(id, restaurant_id, scope_id, ingredient_id, evidence_id, classification,
              original_expression, observed_at, observed_precision, source_updated_precision, reviewed_at, review_status, revision)
          SELECT id, id, id, '00000000-0000-4000-8000-000000000001'::uuid, id, 'DOMESTIC', '가상 원산지',
              '2026-01-01Z', 'DATE', 'UNKNOWN', '2026-02-01Z', 'APPROVED', 1 FROM app.restaurant
          """);
              jdbc.update(
                  "INSERT INTO app.origin_component(record_id, component_index, country_code, origin_kind) SELECT id, 0, 'KR', 'DOMESTIC' FROM app.restaurant");
            });
    for (String table :
        List.of(
            "restaurant",
            "origin_record",
            "origin_component",
            "serving_scope",
            "evidence",
            "data_source")) {
      // 통계 갱신은 테스트 DB 소유자로만 실행한다.
      var result =
          DATABASE.execInContainer(
              "psql",
              "-U",
              "postgres",
              "-d",
              "kimchimap",
              "-v",
              "ON_ERROR_STOP=1",
              "-c",
              "ANALYZE app." + table);
      assertThat(result.getExitCode()).isZero();
    }
    var group =
        new ScopeFilter(
            null,
            List.of(
                new IngredientFilter(
                    UUID.fromString("00000000-0000-4000-8000-000000000001"),
                    OriginMode.DOMESTIC,
                    null,
                    false)));
    var radius =
        new SearchRequest(new Center(36.5, 127), null, 1000d, List.of(group), null, null, 20, null);
    var bounds =
        new SearchRequest(
            null,
            new Bounds(36.49, 126.99, 36.51, 127.01),
            null,
            List.of(group),
            null,
            null,
            20,
            null);
    var measurements = new ArrayList<Map<String, Object>>();
    for (var request : List.of(radius, bounds)) {
      var query = repository.searchQuery(request, Instant.parse("2026-09-07T00:00:00Z"), null);
      var planText =
          client
              .sql("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query.sql())
              .params(query.parameters())
              .query(String.class)
              .single();
      String requiredIndex =
          request.center() == null ? "restaurant_location_gist" : "restaurant_geography_gist";
      assertThat(planText).contains(requiredIndex);
      var plan = mapper.readTree(planText).get(0);
      assertThat(plan.path("Plan").path("Actual Rows").asInt()).isGreaterThan(0);
      measurements.add(
          Map.of(
              "mode",
              request.center() == null ? "bounds" : "radius",
              "executionMilliseconds",
              plan.path("Execution Time").asDouble(),
              "plan",
              plan));
    }
    var output = Path.of("build/reports/performance/spatial.json");
    Files.createDirectories(output.getParent());
    Files.writeString(
        output,
        mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    Map.of(
                        "recordCount",
                        20000,
                        "originCount",
                        20000,
                        "hostArchitecture",
                        System.getProperty("os.arch"),
                        "database",
                        jdbc.queryForObject("SELECT version()", String.class),
                        "postgis",
                        jdbc.queryForObject("SELECT PostGIS_Full_Version()", String.class),
                        "measurements",
                        measurements))
            + "\n");
  }
}
