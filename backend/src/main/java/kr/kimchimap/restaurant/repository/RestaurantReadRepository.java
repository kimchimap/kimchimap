package kr.kimchimap.restaurant.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.restaurant.dto.RestaurantDetail.Component;
import kr.kimchimap.restaurant.dto.RestaurantDetail.Designation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantReadRepository {
  public record RestaurantRow(
      UUID id,
      String name,
      String address,
      String businessStatus,
      Double latitude,
      Double longitude,
      String coordinateStatus,
      String phoneDisplay,
      String phoneNumber,
      String phoneSourceName) {}

  public record ScopeRow(UUID id, String name, String usage, String precision) {}

  public record ClaimRow(
      UUID id,
      UUID scopeId,
      UUID ingredientId,
      String ingredientName,
      String classification,
      String originalExpression,
      String evidenceKind,
      String sourceName,
      String publicReference,
      String publicSummary,
      Instant observedAt,
      String observedPrecision,
      Instant sourceUpdatedAt,
      String sourceUpdatedPrecision,
      Instant collectedAt,
      Instant lastFetchSucceededAt,
      Instant reviewedAt,
      Instant validFrom,
      Instant validUntil,
      long revision,
      boolean withdrawn,
      boolean publiclyVisible) {}

  public record ComponentRow(
      UUID recordId,
      String kind,
      String countryCode,
      String countryName,
      java.math.BigDecimal ratio) {
    public Component toDto() {
      return new Component(kind, countryCode, countryName, ratio);
    }
  }

  private final JdbcClient jdbc;

  public RestaurantReadRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<RestaurantRow> findPublished(UUID id) {
    return jdbc.sql(
            """
        SELECT r.id, r.name, r.address, r.business_status, public.ST_Y(r.location) AS latitude,
               public.ST_X(r.location) AS longitude, r.coordinate_status,
               CASE WHEN s.republication_allowed THEN r.phone_display END AS phone_display,
               CASE WHEN s.republication_allowed THEN r.phone_number END AS phone_number,
               CASE WHEN s.republication_allowed THEN s.name END AS phone_source_name
        FROM app.restaurant r LEFT JOIN app.data_source s ON s.id=r.phone_source_id WHERE r.id = :id AND r.published
        """)
        .param("id", id)
        .query(RestaurantRow.class)
        .optional();
  }

  public List<ScopeRow> findScopes(UUID id) {
    return jdbc.sql(
            """
        SELECT id, name, usage, scope_precision AS precision FROM app.serving_scope
        WHERE restaurant_id = :id ORDER BY id LIMIT 201
        """)
        .param("id", id)
        .query(ScopeRow.class)
        .list();
  }

  public List<ClaimRow> findPublicClaims(UUID id, Instant asOf) {
    return jdbc.sql(
            """
        SELECT r.id, r.scope_id, r.ingredient_id, i.name AS ingredient_name,
               r.classification, r.original_expression, e.kind AS evidence_kind,
               s.name AS source_name, e.public_reference, e.public_summary,
               r.observed_at, r.observed_precision, r.source_updated_at, r.source_updated_precision,
               e.collected_at, e.last_fetch_succeeded_at, r.reviewed_at, r.valid_from, r.valid_until,
               r.revision, EXISTS (SELECT 1 FROM app.origin_withdrawal w WHERE w.record_id = r.id
                 AND w.withdrawn_at <= :asOf) AS withdrawn, (e.publicly_visible AND s.republication_allowed) AS publicly_visible
        FROM app.origin_record r JOIN app.ingredient i ON i.id = r.ingredient_id
        JOIN app.evidence e ON e.id = r.evidence_id
        JOIN app.data_source s ON s.id = e.source_id
        WHERE r.restaurant_id = :id AND r.review_status = 'APPROVED'
          AND r.reviewed_at <= :asOf
        ORDER BY r.scope_id, r.ingredient_id, r.id LIMIT 2001
        """)
        .param("id", id)
        .param("asOf", asOf.atOffset(java.time.ZoneOffset.UTC))
        .query(ClaimRow.class)
        .list();
  }

  public List<ComponentRow> findComponents(List<UUID> recordIds) {
    if (recordIds.isEmpty()) return List.of();
    return jdbc.sql(
            """
        SELECT c.record_id, c.origin_kind AS kind, c.country_code, n.name AS country_name, c.ratio
        FROM app.origin_component c LEFT JOIN app.country n ON n.code = c.country_code
        WHERE c.record_id IN (:ids) ORDER BY c.record_id, c.component_index
        """)
        .param("ids", recordIds)
        .query(ComponentRow.class)
        .list();
  }

  public List<Designation> findDesignations(UUID id) {
    return jdbc.sql(
            """
        SELECT d.id, d.scheme_name, d.applicable_items, d.criteria_original, s.name AS source_name,
               d.designated_on, d.expires_on, d.cancelled_on
        FROM app.designation d JOIN app.data_source s ON s.id = d.source_id
        WHERE d.restaurant_id = :id AND d.publicly_visible AND s.republication_allowed AND s.designation_allowed
        ORDER BY d.id LIMIT 201
        """)
        .param("id", id)
        .query(Designation.class)
        .list();
  }
}
