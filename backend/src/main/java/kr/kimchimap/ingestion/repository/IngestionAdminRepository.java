package kr.kimchimap.ingestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.IngestionAdmin;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionAdminRepository {
  public record Receipt(String requestHash, UUID jobId) {}

  private final JdbcClient jdbc;

  public IngestionAdminRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<IngestionAdmin.Source> sources(
      boolean configured, boolean scheduling, boolean qualified) {
    return jdbc.sql(
            """
        SELECT s.source_id id,d.name,d.collection_allowed,d.republication_allowed,
          :qualified domestic_qualification_supported,
          (s.enabled AND :scheduling AND :qualified) scheduled,:configured credential_configured,
          s.interval_seconds,s.next_run_at,s.last_completed_until
        FROM app.ingestion_source s JOIN app.data_source d ON d.id=s.source_id ORDER BY s.source_id
        """)
        .param("qualified", qualified)
        .param("configured", configured)
        .param("scheduling", scheduling)
        .query(IngestionAdmin.Source.class)
        .list();
  }

  public void lockRequest(UUID actor, UUID key) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
        .param("key", "ingestion-actor:" + actor)
        .query(Object.class)
        .optional();
    jdbc.sql(
            "DELETE FROM app.ingestion_request WHERE actor_id=:actor AND request_key=:key AND expires_at<=CURRENT_TIMESTAMP")
        .param("actor", actor)
        .param("key", key)
        .update();
  }

  public Optional<Receipt> receipt(UUID actor, UUID key) {
    return jdbc.sql(
            "SELECT request_hash,job_id FROM app.ingestion_request WHERE actor_id=:actor AND request_key=:key")
        .param("actor", actor)
        .param("key", key)
        .query(Receipt.class)
        .optional();
  }

  public void remember(
      UUID actor, UUID key, String hash, UUID job, UUID source, String reason, int pages) {
    jdbc.sql(
            "INSERT INTO app.ingestion_request(actor_id,request_key,request_hash,job_id) VALUES (:actor,:key,:hash,:job)")
        .param("actor", actor)
        .param("key", key)
        .param("hash", hash)
        .param("job", job)
        .update();
    jdbc.sql(
            "INSERT INTO app.ingestion_request_audit(id,actor_id,job_id,source_id,reason,page_budget) VALUES (:id,:actor,:job,:source,:reason,:pages)")
        .param("id", UUID.randomUUID())
        .param("actor", actor)
        .param("job", job)
        .param("source", source)
        .param("reason", reason)
        .param("pages", pages)
        .update();
  }

  public boolean allowRequest(UUID actor) {
    return jdbc.sql(
            "SELECT count(*)<10 FROM app.ingestion_request_audit WHERE actor_id=:actor AND created_at>CURRENT_TIMESTAMP-interval '1 hour'")
        .param("actor", actor)
        .query(Boolean.class)
        .single();
  }

  public List<IngestionAdmin.Event> events(UUID job, long after, int limit) {
    return jdbc.sql(
            "SELECT id,page_no,status,error_code,created_at FROM app.ingestion_job_event WHERE job_id=:job AND id>:after ORDER BY id LIMIT :limit")
        .param("job", job)
        .param("after", after)
        .param("limit", limit)
        .query(IngestionAdmin.Event.class)
        .list();
  }

  public List<IngestionAdmin.Quarantine> quarantines(UUID job, UUID after, int limit) {
    return jdbc.sql(
            "SELECT id,page_no,row_index,error_code,created_at FROM app.ingestion_quarantine WHERE job_id=:job AND (:after::uuid IS NULL OR id>:after) ORDER BY id LIMIT :limit")
        .param("job", job)
        .param("after", after)
        .param("limit", limit)
        .query(IngestionAdmin.Quarantine.class)
        .list();
  }

  public void removeExpiredRequests() {
    jdbc.sql(
            "DELETE FROM app.ingestion_request WHERE (actor_id,request_key) IN (SELECT actor_id,request_key FROM app.ingestion_request WHERE expires_at<=CURRENT_TIMESTAMP ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)")
        .update();
  }
}
