package kr.kimchimap.origin.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.kimchimap.origin.dto.OriginAdministration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OriginAdminRepository {
  private static final String GROUP_QUERY =
      """
      SELECT p.scope_id,p.ingredient_id,s.restaurant_id,r.name restaurant_name,
        s.name scope_name,s.usage,i.name ingredient_name,p.status,p.version,p.decided_at
      FROM app.origin_publication p JOIN app.serving_scope s ON s.id=p.scope_id
      JOIN app.restaurant r ON r.id=s.restaurant_id JOIN app.ingredient i ON i.id=p.ingredient_id
      """;
  private final JdbcClient jdbc;

  public OriginAdminRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<OriginAdministration.Group> groups(String status, int limit, int offset) {
    return jdbc.sql(
            GROUP_QUERY
                + " WHERE (:status::text IS NULL OR p.status=:status) ORDER BY p.decided_at DESC,p.scope_id,p.ingredient_id LIMIT :limit OFFSET :offset")
        .param("status", status)
        .param("limit", limit)
        .param("offset", offset)
        .query(OriginAdministration.Group.class)
        .list();
  }

  public Optional<OriginAdministration.Group> group(UUID scope, UUID ingredient, boolean lock) {
    if (lock)
      jdbc.sql("SELECT id FROM app.serving_scope WHERE id=:scope FOR UPDATE")
          .param("scope", scope)
          .query(UUID.class)
          .optional();
    return jdbc.sql(
            GROUP_QUERY
                + " WHERE p.scope_id=:scope AND p.ingredient_id=:ingredient"
                + (lock ? " FOR UPDATE OF p" : ""))
        .param("scope", scope)
        .param("ingredient", ingredient)
        .query(OriginAdministration.Group.class)
        .optional();
  }

  public List<OriginAdministration.Record> records(UUID scope, UUID ingredient) {
    return jdbc.sql(
            """
        SELECT r.id,r.classification,r.original_expression,r.observed_at,r.source_updated_at,r.reviewed_at,r.valid_until,
          (w.record_id IS NOT NULL) withdrawn,w.reason withdrawal_reason,d.name source_name,e.kind evidence_kind,rr.report_id
        FROM app.origin_record r JOIN app.evidence e ON e.id=r.evidence_id JOIN app.data_source d ON d.id=e.source_id
        LEFT JOIN app.origin_withdrawal w ON w.record_id=r.id AND w.withdrawn_at<=CURRENT_TIMESTAMP
        LEFT JOIN app.report_revision rr ON rr.id::text=e.source_identifier AND d.code='USER_REPORT'
        WHERE r.scope_id=:scope AND r.ingredient_id=:ingredient AND r.review_status='APPROVED'
        ORDER BY r.revision DESC,r.id LIMIT 2001
        """)
        .param("scope", scope)
        .param("ingredient", ingredient)
        .query(OriginAdministration.Record.class)
        .list();
  }

  public void withdraw(UUID id, String reason, UUID actor) {
    jdbc.sql(
            "INSERT INTO app.origin_withdrawal(record_id,reason,actor_reference,withdrawn_at) VALUES (:id,:reason,:actor,CURRENT_TIMESTAMP)")
        .param("id", id)
        .param("reason", reason)
        .param("actor", actor.toString())
        .update();
  }

  public void audit(OriginAdministration.Correction input, UUID actor) {
    jdbc.sql(
            "INSERT INTO app.origin_correction(id,scope_id,ingredient_id,actor_id,reason,previous_version,withdrawn_record_ids) VALUES (:id,:scope,:ingredient,:actor,:reason,:version,:records::uuid[])")
        .param("id", UUID.randomUUID())
        .param("scope", input.scopeId())
        .param("ingredient", input.ingredientId())
        .param("actor", actor)
        .param("reason", input.reason())
        .param("version", input.expectedVersion())
        .param(
            "records",
            "{"
                + input.withdrawRecordIds().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","))
                + "}")
        .update();
  }

  public List<OriginAdministration.Audit> audits(UUID scope, UUID ingredient) {
    return jdbc.sql(
            "SELECT id,reason,created_at FROM app.origin_correction WHERE scope_id=:scope AND ingredient_id=:ingredient ORDER BY created_at DESC,id LIMIT 100")
        .param("scope", scope)
        .param("ingredient", ingredient)
        .query(OriginAdministration.Audit.class)
        .list();
  }
}
