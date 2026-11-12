package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

class RetiredDataMigrationIntegrationTest extends ApplicationIntegrationSupport {
  private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-000000000100");
  @Autowired DataSource dataSource;

  @Test
  @Transactional
  void removesOnlyUnusedSourceRowsAndPreservesMemberEvidenceAndReviewedReferences() {
    UUID disposable = restaurant(),
        liked = restaurant(),
        scoped = restaurant(),
        corrected = restaurant(),
        reported = restaurant(),
        audited = restaurant(),
        otherSource = restaurant(),
        pendingReview = restaurant();
    UUID member = UUID.randomUUID(), job = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.member(id,provider,provider_subject) VALUES (?,'KAKAO',?)",
        member,
        "test-cleanup-" + member);
    jdbc.update(
        "INSERT INTO app.restaurant_like(member_id,restaurant_id) VALUES (?,?)", member, liked);
    jdbc.update(
        "INSERT INTO app.serving_scope(id,restaurant_id,name,usage,scope_precision) VALUES (?,?, '테스트 보존 품목','OTHER','SPECIFIC_ITEM')",
        UUID.randomUUID(),
        scoped);
    jdbc.update(
        "INSERT INTO app.restaurant_manual_override(restaurant_id,field_name,reason,actor_reference) VALUES (?,'address','테스트 정정 보존','test-admin')",
        corrected);
    UUID report = UUID.randomUUID(), revision = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.report(id,owner_id,restaurant_id,current_revision_id,state) VALUES (?,?,?,?,'PENDING')",
        report,
        member,
        reported,
        revision);
    jdbc.update(
        "INSERT INTO app.report_revision(id,report_id,body,publication_consent,consent_version) VALUES (?,?,'{}',true,'test')",
        revision,
        report);
    jdbc.update(
        "INSERT INTO app.restaurant_match_audit(id,source_id,external_id,actor_id,decision,restaurant_id,observation,previous_version,reason) VALUES (?,?,?,?,'MATCHED',?,'{}',0,'테스트 검수 보존')",
        UUID.randomUUID(),
        SOURCE,
        "test-audit",
        member,
        audited);
    UUID other = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.data_source(id,code,name) VALUES (?,?,'테스트 다른 출처')",
        other,
        "test-other-" + other);
    jdbc.update(
        "INSERT INTO app.restaurant_external_id(source_id,external_id,restaurant_id) VALUES (?,'test-other',?)",
        other,
        otherSource);
    jdbc.update(
        "INSERT INTO app.ingestion_job(id,source_id,mode,status,until_at,page_size,max_pages) VALUES (?,?,'FULL','SUCCEEDED',CURRENT_TIMESTAMP,100,1)",
        job,
        SOURCE);
    jdbc.update(
        "INSERT INTO app.ingestion_issue(id,job_id,restaurant_id,code) VALUES (?,?,?,'TEST_COORDINATE')",
        UUID.randomUUID(),
        job,
        disposable);
    jdbc.update(
        "INSERT INTO app.restaurant_match_candidate(source_id,external_id,candidate_restaurant_id,job_id) VALUES (?,'test-pending',?,?)",
        SOURCE,
        disposable,
        job);

    jdbc.update(
        "INSERT INTO app.restaurant_source_record(source_id,external_id,content_hash,original_srid,first_collected_at,last_fetch_succeeded_at,last_seen_job_id) VALUES (?,?,'test-hash',5174,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
        SOURCE,
        "test-retired-" + disposable,
        job);
    jdbc.update(
        "INSERT INTO app.restaurant_match_review(source_id,external_id,observation,observed_at,job_id) VALUES (?,?,'{}',CURRENT_TIMESTAMP,?)",
        SOURCE,
        "test-retired-" + pendingReview,
        job);
    var script =
        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V17__remove_unused_general_restaurant_data.sql"));
    var connection = DataSourceUtils.getConnection(dataSource);
    try {
      script.populate(connection);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }

    assertThat(exists(disposable)).isFalse();
    for (UUID preserved :
        List.of(liked, scoped, corrected, reported, audited, otherSource, pendingReview))
      assertThat(exists(preserved)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_external_id WHERE restaurant_id=?",
                Integer.class,
                disposable))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.ingestion_issue WHERE restaurant_id=?",
                Integer.class,
                disposable))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_match_candidate WHERE candidate_restaurant_id=?",
                Integer.class,
                disposable))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.report_revision WHERE id=?", Integer.class, revision))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_match_audit WHERE restaurant_id=?",
                Integer.class,
                audited))
        .isEqualTo(1);
  }

  private UUID restaurant() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status) VALUES (?,'가상 테스트 정리 업소','테스트 주소','UNKNOWN','MISSING')",
        id);
    jdbc.update(
        "INSERT INTO app.restaurant_external_id(source_id,external_id,restaurant_id) VALUES (?,?,?)",
        SOURCE,
        "test-retired-" + id,
        id);
    return id;
  }

  private boolean exists(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE id=?)", Boolean.class, id));
  }
}
