package kr.kimchimap;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class DomesticOriginFixture {
  private DomesticOriginFixture() {}

  static UUID addRice(JdbcTemplate jdbc, PlatformTransactionManager transactions, UUID restaurant) {
    UUID id = UUID.randomUUID();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              jdbc.update(
                  "INSERT INTO app.data_source(id,code,name,republication_allowed) VALUES (?,?,'테스트 국내산 근거 출처',true)",
                  id,
                  "test-domestic-" + id);
              jdbc.update(
                  "INSERT INTO app.serving_scope(id,restaurant_id,name,usage,scope_precision) VALUES (?,?,'테스트 국내산 쌀밥','OTHER','SPECIFIC_ITEM')",
                  id,
                  restaurant);
              jdbc.update(
                  "INSERT INTO app.evidence(id,restaurant_id,source_id,source_identifier,kind,publicly_visible,collected_at,last_fetch_succeeded_at) VALUES (?,?,?,'test','SIGNBOARD_OBSERVATION',true,'2026-02-01Z','2026-02-01Z')",
                  id,
                  restaurant,
                  id);
              jdbc.update(
                  """
          INSERT INTO app.origin_record(id,restaurant_id,scope_id,ingredient_id,evidence_id,classification,
            original_expression,observed_at,observed_precision,source_updated_precision,reviewed_at,review_status,revision)
          VALUES (?,?,?,(SELECT id FROM app.ingredient WHERE code='rice'),?,'DOMESTIC',
            '테스트 쌀 국내산','2026-01-01Z','DATE','UNKNOWN','2026-02-01Z','APPROVED',1)
          """,
                  id,
                  restaurant,
                  id,
                  id);
              jdbc.update(
                  "INSERT INTO app.origin_component(record_id,component_index,country_code,origin_kind) VALUES (?,0,'KR','DOMESTIC')",
                  id);
            });
    return id;
  }
}
