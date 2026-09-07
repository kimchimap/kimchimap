package kr.kimchimap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient.Page;
import kr.kimchimap.ingestion.client.SourceFailure;
import kr.kimchimap.ingestion.dto.SourceRestaurant;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import kr.kimchimap.ingestion.service.IngestionJobService;
import kr.kimchimap.ingestion.service.IngestionPageService;
import kr.kimchimap.ingestion.service.IngestionWorker;
import kr.kimchimap.ingestion.service.OriginCollectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class IngestionIntegrationTest extends ApplicationIntegrationSupport {
  @MockitoBean OriginCollectionPolicy collectionPolicy;

  @BeforeEach
  void allowSyntheticPipelineContract() {
    // 실서비스의 수집 차단은 DomesticPublicationIntegrationTest에서 실제 정책으로 검증한다.
    when(collectionPolicy.supportsDomesticQualification()).thenReturn(true);
  }

  @Autowired IngestionJobService jobs;
  @Autowired IngestionJobRepository repository;
  @Autowired IngestionWorker worker;
  @Autowired IngestionPageService pages;
  @MockitoBean PublicDataRestaurantClient source;

  @BeforeEach
  void isolateJobLifecycle() {
    jdbc.update(
        "UPDATE app.ingestion_job SET status='CANCELLED', lease_owner=NULL, lease_until=NULL WHERE status NOT IN ('SUCCEEDED','CANCELLED')");
    jdbc.update("UPDATE app.ingestion_source SET last_completed_until=NULL");
  }

  @Test
  void partialResumeIsIdempotentAndOriginlessRecordsRemainPrivate() throws Exception {
    var first = row("가상 테스트 첫 업소");
    var second = row("가상 테스트 둘째 업소");
    var third = row("가상 테스트 셋째 업소");
    when(source.fetch(eq(1), eq(2), any(), any()))
        .thenReturn(new Page(1, 2, 3, List.of(first, second)));
    when(source.fetch(eq(2), eq(2), any(), any())).thenReturn(new Page(2, 2, 3, List.of(third)));
    UUID id = jobs.request("INCREMENTAL", 2, 1);
    assertThat(worker.processOne(id)).isTrue();
    assertThat(jobs.get(id).status()).isEqualTo("PARTIAL");
    assertThat(jobs.get(id).nextPage()).isEqualTo(2);
    assertThat(jobs.request("INCREMENTAL", 2, 1)).isEqualTo(id);
    assertThat(worker.processOne(id)).isTrue();
    assertThat(jobs.get(id).status()).isEqualTo("SUCCEEDED");
    assertThat(jobs.get(id).readCount()).isEqualTo(3);
    assertThat(jobs.get(id).changedCount()).isEqualTo(3);
    var restaurant = restaurantId(first);
    var response = get("/api/v1/restaurants/" + restaurant);
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).doesNotContain("0200000000", "02-0000-0000");
    assertThat(response.body()).doesNotContain("DOMESTIC");
    var collected =
        jdbc.queryForObject(
            "SELECT first_collected_at FROM app.restaurant_source_record WHERE external_id=?",
            java.sql.Timestamp.class,
            externalId(first));
    UUID replay = jobs.request("INCREMENTAL", 2, 2);
    assertThat(worker.processOne(replay)).isTrue();
    ready(replay);
    assertThat(worker.processOne(replay)).isTrue();
    assertThat(jobs.get(replay).status()).isEqualTo("SUCCEEDED");
    assertThat(jobs.get(replay).changedCount()).isZero();
    assertThat(restaurantId(first)).isEqualTo(restaurant);
    assertThat(
            jdbc.queryForObject(
                "SELECT first_collected_at FROM app.restaurant_source_record WHERE external_id=?",
                java.sql.Timestamp.class,
                externalId(first)))
        .isEqualTo(collected);
  }

  @Test
  void invalidRecordsAreQuarantinedAndInvalidPhoneIsNeverPublished() throws Exception {
    var invalid = changed(row("가상 테스트 오류 업소"), "DAT_UPDT_PNT", "not-a-date");
    var usable =
        changed(
            changed(row("가상 테스트 좌표 누락"), "CRD_INFO_X", "invalid"), "TELNO", "javascript:alert(1)");
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 2, List.of(invalid, usable)));
    UUID id = jobs.request("INCREMENTAL", 2, 1);
    worker.processOne(id);
    assertThat(jobs.get(id).status()).isEqualTo("SUCCEEDED");
    assertThat(jobs.get(id).quarantinedCount()).isEqualTo(1);
    assertThat(jobs.get(id).changedCount()).isEqualTo(1);
    var response = get("/api/v1/restaurants/" + restaurantId(usable));
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT coordinate_status FROM app.restaurant WHERE id=?",
                String.class,
                restaurantId(usable)))
        .isEqualTo("INVALID");
    assertThat(
            jdbc.queryForObject(
                "SELECT phone_number FROM app.restaurant WHERE id=?",
                String.class,
                restaurantId(usable)))
        .isNull();
    assertThat(response.body()).doesNotContain("javascript");
  }

  @Test
  void rateLimitPreservesCheckpointAndHonorsRetryAfter() {
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenThrow(new SourceFailure("SOURCE_RATE_LIMITED", true, Duration.ofSeconds(120)));
    UUID id = jobs.request("INCREMENTAL", 2, 1);
    Instant before = Instant.now();
    worker.processOne(id);
    assertThat(jobs.get(id).status()).isEqualTo("WAITING");
    assertThat(jobs.get(id).nextPage()).isEqualTo(1);
    assertThat(jobs.get(id).nextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(120));
    assertThat(worker.processOne(id)).isFalse();
    for (int attempt = 0; attempt < 3; attempt++) {
      ready(id);
      worker.processOne(id);
    }
    assertThat(jobs.get(id).status()).isEqualTo("FAILED");
    assertThat(jobs.get(id).retryCount()).isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.ingestion_job_event WHERE job_id=? AND error_code='SOURCE_RATE_LIMITED'",
                Integer.class,
                id))
        .isEqualTo(4);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM app.ingestion_job_event WHERE job_id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(jobs.get(id).readCount()).isZero();
  }

  @Test
  void staleWorkerCannotCommitAfterLeaseIsReassigned() {
    UUID id = jobs.request("INCREMENTAL", 2, 1);
    var oldLease = repository.acquire(UUID.randomUUID(), id).orElseThrow();
    assertThat(repository.acquire(UUID.randomUUID(), id)).isEmpty();
    jdbc.update(
        "UPDATE app.ingestion_job SET lease_until=clock_timestamp()-interval '1 second' WHERE id=?",
        id);
    var newLease = repository.acquire(UUID.randomUUID(), id).orElseThrow();
    assertThat(newLease.fencingToken()).isGreaterThan(oldLease.fencingToken());
    assertThatThrownBy(() -> pages.commit(oldLease, new Page(1, 2, 0, List.of()), List.of()))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("수집 실행 임대를 잃었습니다.");
    repository.failure(oldLease, "STALE_WORKER", Instant.now(), false);
    assertThat(jobs.get(id).status()).isEqualTo("RUNNING");
    pages.commit(newLease, new Page(1, 2, 0, List.of()), List.of());
    assertThat(jobs.get(id).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void manualPhoneCorrectionSurvivesChangesAndStaleVersions() {
    var original = row("가상 테스트 정정 업소");
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 1, List.of(original)));
    worker.processOne(jobs.request("INCREMENTAL", 2, 1));
    UUID restaurant = restaurantId(original);
    jdbc.update(
        "INSERT INTO app.restaurant_manual_override(restaurant_id,field_name,reason,actor_reference) VALUES (?,'phone','테스트 번호 정정','test-admin')",
        restaurant);
    jdbc.update(
        "UPDATE app.restaurant SET phone_number='0200000001',phone_display='02-0000-0001' WHERE id=?",
        restaurant);
    var updated =
        changed(
            changed(changed(original, "TELNO", "02-0000-0002"), "BPLC_NM", "가상 테스트 변경된 업소"),
            "DAT_UPDT_PNT",
            "2026-09-06 12:00:00");
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 1, List.of(updated)));
    UUID job = jobs.request("INCREMENTAL", 2, 1);
    worker.processOne(job);
    assertThat(jobs.get(job).status()).isEqualTo("SUCCEEDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT phone_number FROM app.restaurant WHERE id=?", String.class, restaurant))
        .isEqualTo("0200000001");
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM app.restaurant WHERE id=?", String.class, restaurant))
        .isEqualTo("가상 테스트 변경된 업소");
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM app.ingestion_issue WHERE job_id=?", String.class, job))
        .contains("MANUAL_OVERRIDE_PRESERVED");
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 1, List.of(original)));
    UUID stale = jobs.request("INCREMENTAL", 2, 1);
    worker.processOne(stale);
    assertThat(jobs.get(stale).changedCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM app.restaurant WHERE id=?", String.class, restaurant))
        .isEqualTo("가상 테스트 변경된 업소");
  }

  @Test
  void ambiguousIdentifiersRequireReviewAndCompletedFullListingDoesNotCloseMissingBusiness() {
    var original = row("가상 테스트 대조 업소");
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 1, List.of(original)));
    worker.processOne(jobs.request("INCREMENTAL", 2, 1));
    UUID restaurant = restaurantId(original);
    var ambiguous = changed(original, "MNG_NO", UUID.randomUUID().toString());
    when(source.fetch(anyInt(), anyInt(), any(), any()))
        .thenReturn(new Page(1, 2, 1, List.of(ambiguous)));
    UUID review = jobs.request("INCREMENTAL", 2, 1);
    worker.processOne(review);
    assertThat(jobs.get(review).quarantinedCount()).isEqualTo(1);
    assertThat(jobs.get(review).changedCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_match_candidate WHERE job_id=?",
                Integer.class,
                review))
        .isEqualTo(1);
    when(source.fetch(anyInt(), anyInt(), nullable(Instant.class), any()))
        .thenReturn(new Page(1, 2, 0, List.of()));
    UUID full = jobs.request("FULL", 2, 1);
    worker.processOne(full);
    assertThat(jobs.get(full).status()).isEqualTo("SUCCEEDED");
    assertThat(jobs.get(full).fullListingCompleted()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT missing_since_job_id FROM app.restaurant_source_record WHERE external_id=?",
                UUID.class,
                externalId(original)))
        .isEqualTo(full);
    assertThat(
            jdbc.queryForObject(
                "SELECT business_status FROM app.restaurant WHERE id=?", String.class, restaurant))
        .isEqualTo("OPEN");
  }

  @Test
  void failedPageRollsBackBusinessWritesAndCheckpointTogether() {
    var valid = row("가상 테스트 롤백 업소");
    UUID id = jobs.request("INCREMENTAL", 2, 1);
    var lease = repository.acquire(UUID.randomUUID(), id).orElseThrow();
    var page = new Page(1, 2, 2, List.of(valid, row("가상 테스트 오류 업소")));
    var prepared = new java.util.ArrayList<>(pages.prepare(lease, page));
    // 원천 정규화를 통과한 뒤 DB 쓰기가 실패하는 경계를 검증한다.
    var bad = prepared.get(1).restaurant();
    var invalid =
        new kr.kimchimap.restaurant.dto.ImportedRestaurant(
            bad.externalId(),
            bad.name(),
            bad.address(),
            "INVALID_STATE",
            bad.originalStatus(),
            bad.coordinate(),
            bad.originalX(),
            bad.originalY(),
            bad.phone(),
            bad.originalPhone(),
            bad.sourceUpdatedAt(),
            bad.sourceModifiedAt(),
            bad.contentHash());
    prepared.set(1, new IngestionPageService.PreparedRow(1, invalid, null));
    assertThatThrownBy(() -> pages.commit(lease, page, prepared))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app.restaurant_external_id WHERE external_id=?",
                Integer.class,
                externalId(valid)))
        .isZero();
    assertThat(jobs.get(id).nextPage()).isEqualTo(1);
    assertThat(jobs.get(id).readCount()).isZero();
  }

  @Test
  void changedSourceTotalDoesNotAdvanceCheckpoint() {
    when(source.fetch(eq(1), eq(2), any(), any()))
        .thenReturn(new Page(1, 2, 3, List.of(row("가상 테스트 페이지 하나"), row("가상 테스트 페이지 둘"))));
    when(source.fetch(eq(2), eq(2), any(), any()))
        .thenReturn(new Page(2, 2, 4, List.of(row("가상 테스트 변경 페이지"), row("가상 테스트 변경 페이지 둘"))));
    UUID id = jobs.request("INCREMENTAL", 2, 2);
    worker.processOne(id);
    ready(id);
    worker.processOne(id);
    assertThat(jobs.get(id).status()).isEqualTo("FAILED");
    assertThat(jobs.get(id).errorCode()).isEqualTo("SOURCE_TOTAL_CHANGED");
    assertThat(jobs.get(id).nextPage()).isEqualTo(2);
    assertThat(jobs.get(id).readCount()).isEqualTo(2);
    assertThat(jobs.request("INCREMENTAL", 2, 2)).isNotEqualTo(id);
  }

  private SourceRestaurant row(String name) {
    return new SourceRestaurant(
        Map.of(
            "OPN_ATMY_GRP_CD",
            "TEST",
            "MNG_NO",
            UUID.randomUUID().toString(),
            "BPLC_NM",
            name + " " + UUID.randomUUID(),
            "ROAD_NM_ADDR",
            "가상 테스트 주소",
            "SALS_STTS_NM",
            "영업/정상",
            "CRD_INFO_X",
            "200000",
            "CRD_INFO_Y",
            "450000",
            "DAT_UPDT_PNT",
            "2026-09-05 12:00:00",
            "TELNO",
            "02-0000-0000"));
  }

  private SourceRestaurant changed(SourceRestaurant row, String field, String value) {
    var fields = new HashMap<>(row.fields());
    fields.put(field, value);
    return new SourceRestaurant(fields);
  }

  private String externalId(SourceRestaurant row) {
    return row.get("OPN_ATMY_GRP_CD") + ":" + row.get("MNG_NO");
  }

  private UUID restaurantId(SourceRestaurant row) {
    return jdbc.queryForObject(
        "SELECT restaurant_id FROM app.restaurant_external_id WHERE external_id=?",
        UUID.class,
        externalId(row));
  }

  private void ready(UUID id) {
    jdbc.update("UPDATE app.ingestion_job SET next_attempt_at=clock_timestamp() WHERE id=?", id);
  }
}
