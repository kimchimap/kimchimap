package kr.kimchimap.ingestion.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.MatchAdministration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MatchReviewRepository {
  public record Row(
      UUID id,
      UUID sourceId,
      String sourceName,
      String externalId,
      String observation,
      Instant observedAt,
      UUID jobId,
      long version,
      String state,
      UUID restaurantId) {}

  private final JdbcClient jdbc;

  public MatchReviewRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<MatchAdministration.Item> recent() {
    return jdbc.sql(
            """
        SELECT DISTINCT r.id,c.source_id,s.name source_name,c.external_id,r.observation->>'name' name,
          r.observation->>'address' address,COALESCE(r.state,'PENDING') state,(r.id IS NOT NULL) reviewable
        FROM app.restaurant_match_candidate c JOIN app.data_source s ON s.id=c.source_id
        LEFT JOIN app.restaurant_match_review r ON r.source_id=c.source_id AND r.external_id=c.external_id
        WHERE r.state='PENDING' OR r.id IS NULL ORDER BY c.source_id,c.external_id LIMIT 50
        """)
        .query(MatchAdministration.Item.class)
        .list();
  }

  public Optional<Row> find(UUID id, boolean lock) {
    return jdbc.sql(
            "SELECT r.id,r.source_id,s.name source_name,r.external_id,r.observation::text,r.observed_at,r.job_id,r.version,r.state,r.restaurant_id FROM app.restaurant_match_review r JOIN app.data_source s ON s.id=r.source_id WHERE r.id=:id"
                + (lock ? " FOR UPDATE OF r" : ""))
        .param("id", id)
        .query(Row.class)
        .optional();
  }

  public void lockJob(UUID id) {
    jdbc.sql("SELECT id FROM app.ingestion_job WHERE id=:id FOR KEY SHARE")
        .param("id", id)
        .query(UUID.class)
        .single();
  }

  public boolean sourceAllowed(UUID id) {
    return jdbc.sql(
            "SELECT id FROM app.data_source WHERE id=:id AND collection_allowed AND republication_allowed FOR SHARE")
        .param("id", id)
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  public List<MatchAdministration.Candidate> candidates(UUID source, String external) {
    return jdbc.sql(
            """
        SELECT r.id,r.name,r.address,public.ST_Y(r.location) latitude,public.ST_X(r.location) longitude
        FROM app.restaurant_match_candidate c JOIN app.restaurant r ON r.id=c.candidate_restaurant_id
        WHERE c.source_id=:source AND c.external_id=:external AND r.published ORDER BY r.id LIMIT 50
        """)
        .param("source", source)
        .param("external", external)
        .query(MatchAdministration.Candidate.class)
        .list();
  }

  public void resolve(Row row, UUID target, UUID actor, MatchAdministration.Review input) {
    jdbc.sql(
            "UPDATE app.restaurant_match_review SET state=:state,restaurant_id=:target,version=version+1 WHERE id=:id")
        .param("id", row.id())
        .param("state", input.decision())
        .param("target", target)
        .update();
    jdbc.sql(
            "UPDATE app.restaurant_match_candidate SET status=CASE WHEN candidate_restaurant_id=:target THEN 'MATCHED' ELSE 'REJECTED' END WHERE source_id=:source AND external_id=:external")
        .param("target", target)
        .param("source", row.sourceId())
        .param("external", row.externalId())
        .update();
    jdbc.sql(
            "INSERT INTO app.restaurant_match_audit(id,source_id,external_id,actor_id,decision,restaurant_id,observation,previous_version,reason) VALUES (:id,:source,:external,:actor,:decision,:target,:observation::jsonb,:version,:reason)")
        .param("id", UUID.randomUUID())
        .param("source", row.sourceId())
        .param("external", row.externalId())
        .param("actor", actor)
        .param("decision", input.decision())
        .param("target", target)
        .param("observation", row.observation())
        .param("version", row.version())
        .param("reason", input.reason())
        .update();
  }
}
