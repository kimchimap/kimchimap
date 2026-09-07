package kr.kimchimap.report.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.report.dto.ReportPage.Item;
import kr.kimchimap.report.dto.ReportView.Review;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
  public record Row(
      UUID id,
      UUID ownerId,
      UUID restaurantId,
      UUID currentRevisionId,
      String state,
      long version,
      Instant createdAt,
      Instant updatedAt,
      Instant submittedAt,
      String body) {}

  public record Repeated(String requestHash, UUID reportId) {}

  private final JdbcClient jdbc;

  public ReportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void lockRequest(UUID owner, UUID key) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
        .param("key", owner + ":" + key)
        .query(Object.class)
        .single();
    jdbc.sql(
            "DELETE FROM app.report_request WHERE owner_id=:owner AND request_key=:key AND expires_at <= CURRENT_TIMESTAMP")
        .param("owner", owner)
        .param("key", key)
        .update();
  }

  public Optional<Repeated> repeated(UUID owner, UUID key) {
    return jdbc.sql(
            "SELECT request_hash,report_id FROM app.report_request WHERE owner_id=:owner AND request_key=:key")
        .param("owner", owner)
        .param("key", key)
        .query(Repeated.class)
        .optional();
  }

  public void remember(UUID owner, UUID key, String hash, UUID id) {
    jdbc.sql(
            "INSERT INTO app.report_request(owner_id,request_key,request_hash,report_id) VALUES (:owner,:key,:hash,:id)")
        .param("owner", owner)
        .param("key", key)
        .param("hash", hash)
        .param("id", id)
        .update();
  }

  public boolean publishedRestaurant(UUID id) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE id=:id AND published AND app.has_domestic_origin(id,CURRENT_TIMESTAMP))")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  public boolean matchingScope(UUID scope, UUID restaurant, String name, String usage) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.serving_scope WHERE id=:scope AND restaurant_id=:restaurant AND name=:name AND usage=:usage)")
        .param("scope", scope)
        .param("restaurant", restaurant)
        .param("name", name)
        .param("usage", usage)
        .query(Boolean.class)
        .single();
  }

  public boolean ingredientExists(UUID id) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app.ingredient WHERE id=:id AND active)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  public boolean countryExists(String code) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app.country WHERE code=:code AND active)")
        .param("code", code)
        .query(Boolean.class)
        .single();
  }

  public void create(UUID id, UUID owner, UUID restaurant, UUID revision) {
    jdbc.sql(
            "INSERT INTO app.report(id,owner_id,restaurant_id,current_revision_id,state) VALUES (:id,:owner,:restaurant,:revision,'PENDING')")
        .param("id", id)
        .param("owner", owner)
        .param("restaurant", restaurant)
        .param("revision", revision)
        .update();
  }

  public void revision(UUID id, UUID report, String body, List<UUID> media) {
    jdbc.sql(
            "INSERT INTO app.report_revision(id,report_id,body,publication_consent,consent_version) VALUES (:id,:report,:body::jsonb,true,'submission-v1')")
        .param("id", id)
        .param("report", report)
        .param("body", body)
        .update();
    for (UUID image : media)
      jdbc.sql("INSERT INTO app.report_media(revision_id,media_id) VALUES (:revision,:media)")
          .param("revision", id)
          .param("media", image)
          .update();
  }

  public Optional<Row> find(UUID id, boolean lock) {
    return jdbc.sql(
            """
        SELECT r.id,r.owner_id,r.restaurant_id,r.current_revision_id,r.state,r.version,r.created_at,r.updated_at,v.created_at submitted_at,v.body::text
        FROM app.report r JOIN app.report_revision v ON v.id=r.current_revision_id WHERE r.id=:id
        """
                + (lock ? " FOR UPDATE OF r" : ""))
        .param("id", id)
        .query(Row.class)
        .optional();
  }

  public void update(UUID id, UUID revision, String state) {
    jdbc.sql(
            "UPDATE app.report SET current_revision_id=:revision,state=:state,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
        .param("id", id)
        .param("revision", revision)
        .param("state", state)
        .update();
  }

  public void review(
      UUID report,
      UUID revision,
      UUID actor,
      String decision,
      String reason,
      long version,
      UUID scope,
      List<UUID> publicMedia) {
    jdbc.sql(
            "INSERT INTO app.report_review(id,report_id,revision_id,actor_id,decision,reason,previous_version,approved_scope_id,public_media_ids) VALUES (:id,:report,:revision,:actor,:decision,:reason,:version,:scope,:media::uuid[])")
        .param("id", UUID.randomUUID())
        .param("report", report)
        .param("revision", revision)
        .param("actor", actor)
        .param("decision", decision)
        .param("reason", reason)
        .param("version", version)
        .param("scope", scope)
        .param(
            "media",
            "{"
                + publicMedia.stream()
                    .map(UUID::toString)
                    .collect(java.util.stream.Collectors.joining(","))
                + "}")
        .update();
  }

  public List<Review> reviews(UUID id) {
    return jdbc.sql(
            "SELECT decision,reason,created_at FROM app.report_review WHERE report_id=:id ORDER BY created_at,id")
        .param("id", id)
        .query(Review.class)
        .list();
  }

  public boolean cursorExists(UUID cursor, UUID owner, String state) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.report WHERE id=:id AND (:owner::uuid IS NULL OR owner_id=:owner) AND (:state::text IS NULL OR state=:state))")
        .param("id", cursor)
        .param("owner", owner)
        .param("state", state)
        .query(Boolean.class)
        .single();
  }

  public List<Item> list(UUID owner, String state, UUID cursor, int limit) {
    return jdbc.sql(
            """
        SELECT r.id,r.restaurant_id,p.name restaurant_name,r.state,r.version,r.created_at
        FROM app.report r JOIN app.restaurant p ON p.id=r.restaurant_id
        WHERE (:owner::uuid IS NULL OR r.owner_id=:owner) AND (:state::text IS NULL OR r.state=:state)
          AND (:cursor::uuid IS NULL OR r.created_at < (SELECT created_at FROM app.report WHERE id=:cursor)
            OR (r.created_at=(SELECT created_at FROM app.report WHERE id=:cursor) AND r.id > :cursor))
        ORDER BY r.created_at DESC,r.id LIMIT :limit
        """)
        .param("owner", owner)
        .param("state", state)
        .param("cursor", cursor)
        .param("limit", limit)
        .query(Item.class)
        .list();
  }

  public void removeExpiredRequests() {
    jdbc.sql(
            "DELETE FROM app.report_request WHERE (owner_id,request_key) IN (SELECT owner_id,request_key FROM app.report_request WHERE expires_at<=CURRENT_TIMESTAMP ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)")
        .update();
  }
}
