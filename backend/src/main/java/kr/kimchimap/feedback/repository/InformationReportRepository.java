package kr.kimchimap.feedback.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.feedback.dto.InformationReports;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InformationReportRepository {
  public record Row(
      UUID id,
      UUID ownerId,
      UUID restaurantId,
      String restaurantName,
      String requestHash,
      String category,
      String description,
      String state,
      long version,
      Instant createdAt,
      String resolutionReason,
      Instant resolvedAt) {}

  private static final String SELECT =
      """
      SELECT f.id,f.owner_id,f.restaurant_id,r.name restaurant_name,f.request_hash,f.category,
             f.description,f.state,f.version,f.created_at,v.reason resolution_reason,v.created_at resolved_at
      FROM app.information_report f JOIN app.restaurant r ON r.id=f.restaurant_id
      LEFT JOIN app.information_report_review v ON v.report_id=f.id
      """;
  private final JdbcClient jdbc;

  public InformationReportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void lockOwner(UUID owner) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
        .param("key", "information-report:" + owner)
        .query(Object.class)
        .single();
  }

  public Optional<Row> repeated(UUID owner, UUID key) {
    return jdbc.sql(SELECT + " WHERE f.owner_id=:owner AND f.request_key=:key")
        .param("owner", owner)
        .param("key", key)
        .query(Row.class)
        .optional();
  }

  public boolean published(UUID restaurant) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE id=:id AND published AND app.has_domestic_origin(id,CURRENT_TIMESTAMP))")
        .param("id", restaurant)
        .query(Boolean.class)
        .single();
  }

  public long recentCount(UUID owner) {
    return jdbc.sql(
            "SELECT count(*) FROM app.information_report WHERE owner_id=:owner AND created_at > CURRENT_TIMESTAMP - interval '1 hour'")
        .param("owner", owner)
        .query(Long.class)
        .single();
  }

  public UUID create(UUID owner, UUID key, String hash, InformationReports.Submission input) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO app.information_report(id,owner_id,restaurant_id,request_key,request_hash,category,description) VALUES (:id,:owner,:restaurant,:key,:hash,:category,:description)")
        .param("id", id)
        .param("owner", owner)
        .param("restaurant", input.restaurantId())
        .param("key", key)
        .param("hash", hash)
        .param("category", input.category())
        .param("description", input.description())
        .update();
    for (UUID media : input.mediaIds())
      jdbc.sql("INSERT INTO app.information_report_media(report_id,media_id) VALUES (:id,:media)")
          .param("id", id)
          .param("media", media)
          .update();
    return id;
  }

  public Optional<Row> find(UUID id, boolean lock) {
    return jdbc.sql(SELECT + " WHERE f.id=:id" + (lock ? " FOR UPDATE OF f" : ""))
        .param("id", id)
        .query(Row.class)
        .optional();
  }

  public List<UUID> media(UUID id) {
    return jdbc.sql(
            "SELECT media_id FROM app.information_report_media WHERE report_id=:id ORDER BY media_id")
        .param("id", id)
        .query(UUID.class)
        .list();
  }

  public boolean cursorExists(UUID owner, UUID cursor, String state) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.information_report WHERE id=:id AND (:owner::uuid IS NULL OR owner_id=:owner) AND (:state::text IS NULL OR state=:state))")
        .param("id", cursor)
        .param("owner", owner)
        .param("state", state)
        .query(Boolean.class)
        .single();
  }

  public List<Row> list(UUID owner, String state, UUID cursor, int limit) {
    return jdbc.sql(
            SELECT
                + """
        WHERE (:owner::uuid IS NULL OR f.owner_id=:owner) AND (:state::text IS NULL OR f.state=:state)
        AND (:cursor::uuid IS NULL OR f.created_at < (SELECT created_at FROM app.information_report WHERE id=:cursor)
          OR (f.created_at=(SELECT created_at FROM app.information_report WHERE id=:cursor) AND f.id>:cursor))
        ORDER BY f.created_at DESC,f.id LIMIT :limit
        """)
        .param("owner", owner)
        .param("state", state)
        .param("cursor", cursor)
        .param("limit", limit)
        .query(Row.class)
        .list();
  }

  public void resolve(UUID id, UUID actor, InformationReports.Resolution input) {
    jdbc.sql(
            "INSERT INTO app.information_report_review(report_id,actor_id,decision,reason,previous_version) VALUES (:id,:actor,:decision,:reason,:version)")
        .param("id", id)
        .param("actor", actor)
        .param("decision", input.decision())
        .param("reason", input.reason())
        .param("version", input.expectedVersion())
        .update();
    jdbc.sql("UPDATE app.information_report SET state=:state,version=version+1 WHERE id=:id")
        .param("state", input.decision())
        .param("id", id)
        .update();
  }
}
