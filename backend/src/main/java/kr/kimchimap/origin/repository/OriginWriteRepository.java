package kr.kimchimap.origin.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.origin.dto.OriginPublicationRequest;
import kr.kimchimap.origin.entity.OriginValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OriginWriteRepository {
  public record Revision(
      UUID id,
      UUID scopeId,
      UUID ingredientId,
      String classification,
      Instant observedAt,
      Instant sourceUpdatedAt,
      Instant reviewedAt,
      Instant validFrom,
      Instant validUntil,
      boolean withdrawn,
      long revision) {}

  public record Part(
      UUID recordId, String originKind, String countryCode, java.math.BigDecimal ratio) {}

  private final JdbcClient jdbc;

  public OriginWriteRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void lockPublicationScope(UUID scope) {
    jdbc.sql("SELECT id FROM app.serving_scope WHERE id=:scope FOR UPDATE")
        .param("scope", scope)
        .query(UUID.class)
        .single();
  }

  public Optional<UUID> lockScope(UUID scope, UUID restaurant, String usage) {
    return jdbc.sql(
            "SELECT id FROM app.serving_scope WHERE id=:scope AND restaurant_id=:restaurant AND usage=:usage FOR UPDATE")
        .param("scope", scope)
        .param("restaurant", restaurant)
        .param("usage", usage)
        .query(UUID.class)
        .optional();
  }

  public UUID createScope(UUID restaurant, String name, String usage) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO app.serving_scope(id,restaurant_id,name,original_name,usage,scope_precision) VALUES (:id,:restaurant,:name,:name,:usage,'SPECIFIC_ITEM')")
        .param("id", id)
        .param("restaurant", restaurant)
        .param("name", name)
        .param("usage", usage)
        .update();
    return id;
  }

  public UUID evidence(OriginPublicationRequest input) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
        INSERT INTO app.evidence(id,restaurant_id,source_id,source_identifier,kind,public_reference,public_summary,publicly_visible,collected_at,last_fetch_succeeded_at)
        VALUES (:id,:restaurant,'dea158a9-07a9-4db9-8531-6096193af1ac',:submission,'SIGNBOARD_OBSERVATION',:reference,
          '이용자 제출 표시판을 관리자가 검토했습니다. 실제 납품 검증이 아닙니다.',true,:collected,:collected)
        """)
        .param("id", id)
        .param("restaurant", input.restaurantId())
        .param("submission", input.submissionId().toString())
        .param("reference", input.publicReference())
        .param("collected", time(input.collectedAt()))
        .update();
    return id;
  }

  public void insert(
      UUID restaurant,
      UUID scope,
      UUID evidence,
      OriginPublicationRequest.Assertion assertion,
      Instant observed,
      Instant reviewed) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
        INSERT INTO app.origin_record(id,restaurant_id,scope_id,ingredient_id,evidence_id,classification,original_expression,
          observed_at,observed_precision,source_updated_precision,reviewed_at,review_status,revision)
        VALUES (:id,:restaurant,:scope,:ingredient,:evidence,:classification,:expression,:observed,'DATE','UNKNOWN',:reviewed,'APPROVED',
          (SELECT coalesce(max(revision),0)+1 FROM app.origin_record WHERE scope_id=:scope AND ingredient_id=:ingredient))
        """)
        .param("id", id)
        .param("restaurant", restaurant)
        .param("scope", scope)
        .param("ingredient", assertion.ingredientId())
        .param("evidence", evidence)
        .param("classification", assertion.value().classification().name())
        .param("expression", assertion.originalExpression())
        .param("observed", time(observed))
        .param("reviewed", time(reviewed))
        .update();
    int index = 0;
    for (OriginValue.Component component : assertion.value().components()) {
      jdbc.sql(
              "INSERT INTO app.origin_component(record_id,component_index,country_code,origin_kind,ratio) VALUES (:id,:index,:country,:kind,:ratio)")
          .param("id", id)
          .param("index", index++)
          .param("country", component.countryCode())
          .param("kind", component.kind().name())
          .param("ratio", component.ratio())
          .update();
    }
  }

  public List<Revision> revisions(UUID scope, UUID ingredient) {
    return jdbc.sql(
            """
        SELECT r.id,r.scope_id,r.ingredient_id,r.classification,r.observed_at,r.source_updated_at,r.reviewed_at,r.valid_from,r.valid_until,
          EXISTS(SELECT 1 FROM app.origin_withdrawal w WHERE w.record_id=r.id AND w.withdrawn_at<=CURRENT_TIMESTAMP) withdrawn,r.revision
        FROM app.origin_record r WHERE r.scope_id=:scope AND r.ingredient_id=:ingredient AND r.review_status='APPROVED' LIMIT 2001
        """)
        .param("scope", scope)
        .param("ingredient", ingredient)
        .query(Revision.class)
        .list();
  }

  public List<Part> components(UUID scope, UUID ingredient) {
    return jdbc.sql(
            "SELECT c.record_id,c.origin_kind,c.country_code,c.ratio FROM app.origin_component c JOIN app.origin_record r ON r.id=c.record_id WHERE r.scope_id=:scope AND r.ingredient_id=:ingredient AND r.review_status='APPROVED'")
        .param("scope", scope)
        .param("ingredient", ingredient)
        .query(Part.class)
        .list();
  }

  public void publish(
      UUID scope,
      UUID ingredient,
      UUID selected,
      String status,
      String reason,
      int policy,
      UUID actor,
      Instant now) {
    jdbc.sql(
            """
        INSERT INTO app.origin_publication(scope_id,ingredient_id,selected_record_id,status,policy_version,reason,decided_at)
        VALUES (:scope,:ingredient,:selected,:status,:policy,:reason,:now)
        ON CONFLICT(scope_id,ingredient_id) DO UPDATE SET selected_record_id=excluded.selected_record_id,status=excluded.status,
          policy_version=excluded.policy_version,reason=excluded.reason,decided_at=excluded.decided_at,version=app.origin_publication.version+1
        """)
        .param("scope", scope)
        .param("ingredient", ingredient)
        .param("selected", selected)
        .param("status", status)
        .param("policy", policy)
        .param("reason", reason)
        .param("now", time(now))
        .update();
    jdbc.sql(
            "INSERT INTO app.origin_publication_history(id,scope_id,ingredient_id,selected_record_id,status,reason,actor_reference,decided_at,policy_version) VALUES (:id,:scope,:ingredient,:selected,:status,:reason,:actor,:now,:policy)")
        .param("id", UUID.randomUUID())
        .param("scope", scope)
        .param("ingredient", ingredient)
        .param("selected", selected)
        .param("status", status)
        .param("reason", reason)
        .param("actor", actor.toString())
        .param("now", time(now))
        .param("policy", policy)
        .update();
  }

  private static OffsetDateTime time(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
