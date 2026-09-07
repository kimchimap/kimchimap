package kr.kimchimap.ingestion.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.IngestionJob;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionJobRepository {
  public static final UUID PUBLIC_DATA_SOURCE =
      UUID.fromString("00000000-0000-4000-8000-000000000100");
  private final JdbcClient jdbc;

  public IngestionJobRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<IngestionJob> find(UUID id) {
    return jdbc.sql("SELECT * FROM app.ingestion_job WHERE id = :id")
        .param("id", id)
        .query(IngestionJob.class)
        .optional();
  }

  public List<IngestionJob> recent() {
    return jdbc.sql("SELECT * FROM app.ingestion_job ORDER BY requested_at DESC, id DESC LIMIT 50")
        .query(IngestionJob.class)
        .list();
  }

  public void lockSource() {
    jdbc.sql(
            """
        SELECT s.source_id FROM app.ingestion_source s JOIN app.data_source d ON d.id = s.source_id
        WHERE s.source_id = :source AND d.collection_allowed AND d.republication_allowed FOR UPDATE OF s
        """)
        .param("source", PUBLIC_DATA_SOURCE)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> new IllegalStateException("수집과 공개 이용 허가가 필요합니다."));
  }

  public Optional<IngestionJob> resumable() {
    return jdbc.sql(
            """
        SELECT * FROM (SELECT * FROM app.ingestion_job WHERE source_id=:source
          ORDER BY requested_at DESC, id DESC LIMIT 1) latest
        WHERE status IN ('QUEUED','RUNNING','WAITING','PARTIAL')
          OR (status='FAILED' AND error_code <> 'SOURCE_TOTAL_CHANGED')
        """)
        .param("source", PUBLIC_DATA_SOURCE)
        .query(IngestionJob.class)
        .optional();
  }

  public void extendBudget(UUID id, int pages) {
    jdbc.sql(
            "UPDATE app.ingestion_job SET status='QUEUED', max_pages=pages_processed + :pages, retry_count=0, error_code=NULL, next_attempt_at=clock_timestamp(), updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status IN ('PARTIAL','FAILED')")
        .param("id", id)
        .param("pages", pages)
        .update();
  }

  public Instant lastCompletedUntil() {
    return jdbc.sql("SELECT last_completed_until FROM app.ingestion_source WHERE source_id=:source")
        .param("source", PUBLIC_DATA_SOURCE)
        .query(Instant.class)
        .optional()
        .orElse(null);
  }

  public UUID create(String mode, Instant since, Instant until, int pageSize, int maxPages) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
        INSERT INTO app.ingestion_job(id, source_id, mode, status, since_at, until_at, page_size, max_pages)
        VALUES (:id, :source, :mode, 'QUEUED', :since, :until, :size, :max)
        """)
        .param("id", id)
        .param("source", PUBLIC_DATA_SOURCE)
        .param("mode", mode)
        .param("since", since == null ? null : since.atOffset(ZoneOffset.UTC))
        .param("until", until.atOffset(ZoneOffset.UTC))
        .param("size", pageSize)
        .param("max", maxPages)
        .update();
    return id;
  }

  public Optional<IngestionJob> acquire(UUID owner, UUID specificJob) {
    return jdbc.sql(
            """
        WITH candidate AS (
          SELECT j.id FROM app.ingestion_job j JOIN app.data_source s ON s.id=j.source_id
          WHERE j.status IN ('QUEUED','RUNNING','WAITING') AND j.next_attempt_at <= clock_timestamp()
            AND (j.lease_until IS NULL OR j.lease_until <= clock_timestamp())
            AND s.collection_allowed AND s.republication_allowed
            AND (:specific::uuid IS NULL OR j.id=:specific)
          ORDER BY j.requested_at, j.id FOR UPDATE OF j SKIP LOCKED LIMIT 1
        )
        UPDATE app.ingestion_job j SET status='RUNNING', lease_owner=:owner,
          fencing_token=fencing_token+1, lease_until=clock_timestamp()+interval '60 seconds', updated_at=clock_timestamp()
        FROM candidate c WHERE j.id=c.id RETURNING j.*
        """)
        .param("owner", owner)
        .param("specific", specificJob)
        .query(IngestionJob.class)
        .optional();
  }

  public void renewLease(IngestionJob job) {
    int updated =
        jdbc.sql(
                """
        UPDATE app.ingestion_job SET lease_until=clock_timestamp()+interval '60 seconds'
        WHERE id=:id AND lease_owner=:owner AND fencing_token=:token AND status='RUNNING'
          AND lease_until > clock_timestamp()
        """)
            .param("id", job.id())
            .param("owner", job.leaseOwner())
            .param("token", job.fencingToken())
            .update();
    if (updated != 1) throw new IllegalStateException("수집 실행 임대를 잃었습니다.");
  }

  public boolean fullReconciliationDue() {
    return jdbc.sql(
            """
        SELECT last_completed_until IS NOT NULL AND
          (last_full_completed_at IS NULL OR last_full_completed_at < clock_timestamp()-interval '30 days')
        FROM app.ingestion_source WHERE source_id=:source
        """)
        .param("source", PUBLIC_DATA_SOURCE)
        .query(Boolean.class)
        .single();
  }

  public void deferSchedule() {
    jdbc.sql(
            "UPDATE app.ingestion_source SET next_run_at=clock_timestamp()+make_interval(secs=>interval_seconds) WHERE source_id=:source")
        .param("source", PUBLIC_DATA_SOURCE)
        .update();
  }

  public void lockLease(IngestionJob job) {
    jdbc.sql(
            """
        SELECT id FROM app.ingestion_job WHERE id=:id AND status='RUNNING' AND lease_owner=:owner
          AND fencing_token=:token AND lease_until > clock_timestamp() FOR UPDATE
        """)
        .param("id", job.id())
        .param("owner", job.leaseOwner())
        .param("token", job.fencingToken())
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> new IllegalStateException("수집 실행 임대를 잃었습니다."));
  }

  public void completePage(
      IngestionJob job, long total, int read, int changed, int quarantined, boolean last) {
    boolean partial = !last && job.pagesProcessed() + 1 >= job.maxPages();
    String status = last ? "SUCCEEDED" : partial ? "PARTIAL" : "WAITING";
    jdbc.sql(
            "INSERT INTO app.ingestion_job_event(job_id,page_no,fencing_token,status) VALUES (:id,:page,:token,:status)")
        .param("id", job.id())
        .param("page", job.nextPage())
        .param("token", job.fencingToken())
        .param("status", status)
        .update();
    jdbc.sql(
            """
        UPDATE app.ingestion_job SET status=:status, next_page=next_page+1, pages_processed=pages_processed+1,
          total_count=:total, read_count=read_count+:read, changed_count=changed_count+:changed,
          quarantined_count=quarantined_count+:quarantine, full_listing_completed=:full,
          lease_owner=NULL, lease_until=NULL, next_attempt_at=clock_timestamp()+interval '1 second', retry_count=0,
          error_code=NULL, updated_at=clock_timestamp() WHERE id=:id
        """)
        .param("status", status)
        .param("total", total)
        .param("read", read)
        .param("changed", changed)
        .param("quarantine", quarantined)
        .param("full", last && job.mode().equals("FULL"))
        .param("id", job.id())
        .update();
    if (last) {
      jdbc.sql(
              """
          UPDATE app.ingestion_source SET last_completed_until=:until,
            last_full_completed_at=CASE WHEN :full THEN clock_timestamp() ELSE last_full_completed_at END,
            next_run_at=clock_timestamp() + make_interval(secs => interval_seconds) WHERE source_id=:source
          """)
          .param("until", job.untilAt().atOffset(ZoneOffset.UTC))
          .param("full", job.mode().equals("FULL"))
          .param("source", job.sourceId())
          .update();
      if (job.mode().equals("FULL"))
        jdbc.sql(
                """
          UPDATE app.restaurant_source_record SET missing_since_job_id=coalesce(missing_since_job_id,:job)
          WHERE source_id=:source AND last_seen_job_id<>:job
          """)
            .param("job", job.id())
            .param("source", job.sourceId())
            .update();
    }
  }

  public void failure(IngestionJob job, String code, Instant nextAttempt, boolean retryable) {
    jdbc.sql(
            """
        WITH failed AS (UPDATE app.ingestion_job SET status=:status, error_code=:code, retry_count=retry_count+1,
          next_attempt_at=:next, lease_owner=NULL, lease_until=NULL, updated_at=clock_timestamp()
        WHERE id=:id AND lease_owner=:owner AND fencing_token=:token AND status='RUNNING'
        RETURNING id,next_page,fencing_token,status,error_code)
        INSERT INTO app.ingestion_job_event(job_id,page_no,fencing_token,status,error_code)
        SELECT id,next_page,fencing_token,status,error_code FROM failed
        """)
        .param("status", retryable ? "WAITING" : "FAILED")
        .param("code", code)
        .param("next", nextAttempt.atOffset(ZoneOffset.UTC))
        .param("id", job.id())
        .param("owner", job.leaseOwner())
        .param("token", job.fencingToken())
        .update();
  }

  public boolean scheduledDue() {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.ingestion_source WHERE source_id=:source AND enabled AND next_run_at <= clock_timestamp())")
        .param("source", PUBLIC_DATA_SOURCE)
        .query(Boolean.class)
        .single();
  }
}
