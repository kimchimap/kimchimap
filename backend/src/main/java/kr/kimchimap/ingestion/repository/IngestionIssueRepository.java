package kr.kimchimap.ingestion.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionIssueRepository {
  private final JdbcClient jdbc;

  public IngestionIssueRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void quarantine(UUID job, int page, int index, String external, String code) {
    jdbc.sql(
            """
        INSERT INTO app.ingestion_quarantine(id, job_id, page_no, row_index, external_id, error_code)
        VALUES (:id,:job,:page,:index,:external,:code) ON CONFLICT(job_id,page_no,row_index) DO NOTHING
        """)
        .param("id", UUID.randomUUID())
        .param("job", job)
        .param("page", page)
        .param("index", index)
        .param("external", external)
        .param("code", code)
        .update();
  }

  public void issue(UUID job, UUID restaurant, String code) {
    jdbc.sql(
            "INSERT INTO app.ingestion_issue(id, job_id, restaurant_id, code) VALUES (:id,:job,:restaurant,:code)")
        .param("id", UUID.randomUUID())
        .param("job", job)
        .param("restaurant", restaurant)
        .param("code", code)
        .update();
  }

  public void match(UUID source, String external, UUID candidate, UUID job) {
    jdbc.sql(
            """
        INSERT INTO app.restaurant_match_candidate(source_id, external_id, candidate_restaurant_id, job_id)
        VALUES (:source,:external,:candidate,:job) ON CONFLICT(source_id,external_id,candidate_restaurant_id) DO NOTHING
        """)
        .param("source", source)
        .param("external", external)
        .param("candidate", candidate)
        .param("job", job)
        .update();
  }
}
