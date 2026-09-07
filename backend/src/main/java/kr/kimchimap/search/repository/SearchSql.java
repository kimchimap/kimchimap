package kr.kimchimap.search.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.kimchimap.search.dto.SearchRequest;

final class SearchSql {
  private final SearchRequest request;
  private final Map<String, Object> parameters = new LinkedHashMap<>();

  SearchSql(SearchRequest request, Instant asOf) {
    this.request = request;
    var center = request.distanceCenter();
    parameters.put("latitude", center.latitude());
    parameters.put("longitude", center.longitude());
    parameters.put("asOf", asOf.atOffset(ZoneOffset.UTC));
    if (request.maxAgeDays() != null)
      parameters.put(
          "confirmedSince",
          asOf.minus(java.time.Duration.ofDays(request.maxAgeDays())).atOffset(ZoneOffset.UTC));
    if (!request.evidenceKinds().isEmpty())
      parameters.put("evidenceKinds", request.evidenceKinds().stream().map(Enum::name).toList());
  }

  Map<String, Object> parameters() {
    return parameters;
  }

  String common(String restriction) {
    String spatial;
    if (request.center() != null) {
      // 측지 투영의 부동소수점 오차로 경계점이 제외되지 않도록 1마이크로미터를 허용한다.
      parameters.put("radius", request.radiusMeters() + 0.000001d);
      spatial =
          "public.ST_DWithin(r.location::public.geography, center.point::public.geography, :radius)";
    } else {
      var b = request.bounds();
      parameters.put("west", b.west());
      parameters.put("south", b.south());
      parameters.put("east", b.east());
      parameters.put("north", b.north());
      spatial =
          "public.ST_Intersects(r.location, public.ST_MakeEnvelope(:west, :south, :east, :north, 4326))";
    }
    return """
        WITH center AS (SELECT public.ST_SetSRID(public.ST_MakePoint(:longitude, :latitude), 4326) AS point),
        spatial AS MATERIALIZED (
          SELECT r.id, r.name, r.address, r.business_status, public.ST_Y(r.location) AS latitude,
                 public.ST_X(r.location) AS longitude,
                 public.ST_Distance(r.location::public.geography, center.point::public.geography) AS distance_meters
          FROM app.restaurant r CROSS JOIN center
          WHERE r.published AND r.business_status <> 'CLOSED' AND r.coordinate_status = 'VERIFIED'
            AND %s %s
        ),
        active AS MATERIALIZED (
          SELECT o.*, e.kind AS evidence_kind, (e.publicly_visible AND ds.republication_allowed) AS publicly_visible,
            jsonb_build_object('classification', o.classification, 'components',
              (SELECT coalesce(jsonb_agg(jsonb_build_array(c.origin_kind, c.country_code, c.ratio)
                  ORDER BY c.country_code NULLS LAST), '[]'::jsonb)
               FROM app.origin_component c WHERE c.record_id = o.id)) AS signature
          FROM app.origin_record o JOIN spatial r ON r.id = o.restaurant_id
          JOIN app.evidence e ON e.id = o.evidence_id JOIN app.data_source ds ON ds.id = e.source_id
          WHERE o.review_status = 'APPROVED' AND o.reviewed_at <= :asOf
            AND (o.valid_from IS NULL OR o.valid_from <= :asOf)
            AND (o.valid_until IS NULL OR o.valid_until > :asOf)
            AND NOT EXISTS (SELECT 1 FROM app.origin_withdrawal w WHERE w.record_id = o.id AND w.withdrawn_at <= :asOf)
        ),
        consistent AS (
          SELECT scope_id, ingredient_id FROM active GROUP BY scope_id, ingredient_id
          HAVING count(DISTINCT signature) = 1
        ),
        current_origins AS (
          SELECT DISTINCT ON (a.scope_id, a.ingredient_id) a.*
          FROM active a JOIN consistent c USING(scope_id, ingredient_id)
          WHERE a.publicly_visible
          ORDER BY a.scope_id, a.ingredient_id, a.observed_at DESC NULLS LAST,
              a.source_updated_at DESC NULLS LAST, a.revision DESC, a.id DESC
        )
        """
        .formatted(spatial, restriction);
  }

  String groupsPredicate() {
    var sql = new StringBuilder();
    for (int group = 0; group < request.groups().size(); group++) {
      sql.append(
              " AND EXISTS (SELECT 1 FROM app.serving_scope ss WHERE ss.restaurant_id = r.id AND ")
          .append(scopePredicate(group))
          .append(")");
    }
    return sql.toString();
  }

  String scopePredicate(int groupIndex) {
    var group = request.groups().get(groupIndex);
    var sql = new StringBuilder("ss.scope_precision = 'SPECIFIC_ITEM'");
    if (group.usage() != null) {
      String name = "usage" + groupIndex;
      parameters.put(name, group.usage().name());
      sql.append(" AND ss.usage = :").append(name);
    }
    for (int i = 0; i < group.ingredients().size(); i++) {
      var ingredient = group.ingredients().get(i);
      String prefix = "g" + groupIndex + "i" + i;
      parameters.put(prefix, ingredient.ingredientId());
      sql.append(
              " AND EXISTS (SELECT 1 FROM current_origins co WHERE co.scope_id = ss.id AND co.ingredient_id = :")
          .append(prefix)
          .append(" AND ");
      switch (ingredient.mode()) {
        case DOMESTIC -> sql.append("co.classification = 'DOMESTIC'");
        case IMPORTED_UNSPECIFIED -> sql.append("co.classification = 'IMPORTED_UNSPECIFIED'");
        case UNKNOWN -> sql.append("co.classification = 'UNKNOWN'");
        case COUNTRIES -> {
          parameters.put(prefix + "countries", ingredient.countries());
          sql.append(
              ingredient.includeMixed()
                  ? "co.classification IN ('DOMESTIC', 'IMPORTED_SPECIFIED', 'MIXED')"
                  : "co.classification IN ('DOMESTIC', 'IMPORTED_SPECIFIED')");
          sql.append(
                  " AND EXISTS (SELECT 1 FROM app.origin_component oc WHERE oc.record_id = co.id AND oc.country_code IN (:")
              .append(prefix)
              .append("countries))");
        }
      }
      if (request.maxAgeDays() != null)
        sql.append(" AND co.observed_at >= :confirmedSince AND co.observed_at <= :asOf");
      if (!request.evidenceKinds().isEmpty())
        sql.append(" AND co.evidence_kind IN (:evidenceKinds)");
      sql.append(")");
    }
    return sql.toString();
  }
}
