package kr.kimchimap.ingestion.service;

import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.service.TokenSecrets;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.ingestion.dto.IngestionAdmin;
import kr.kimchimap.ingestion.repository.IngestionAdminRepository;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionAdminService {
  private final IngestionAdminRepository repository;
  private final IngestionJobService jobs;
  private final boolean credentialConfigured;
  private final boolean scheduling;
  private final OriginCollectionPolicy collectionPolicy;

  public IngestionAdminService(
      IngestionAdminRepository repository,
      IngestionJobService jobs,
      OriginCollectionPolicy collectionPolicy,
      @Value("${app.ingestion.public-data-key:}") String key,
      @Value("${app.ingestion.scheduling-enabled:false}") boolean scheduling) {
    this.repository = repository;
    this.jobs = jobs;
    this.collectionPolicy = collectionPolicy;
    this.credentialConfigured = !key.isBlank();
    this.scheduling = scheduling;
  }

  @Transactional(readOnly = true)
  public List<IngestionAdmin.Source> sources() {
    return repository.sources(
        credentialConfigured, scheduling, collectionPolicy.supportsDomesticQualification());
  }

  @Transactional(readOnly = true)
  public List<IngestionAdmin.Job> recent() {
    return jobs.recent().stream().map(IngestionAdmin.Job::from).toList();
  }

  @Transactional(readOnly = true)
  public IngestionAdmin.Job detail(UUID id) {
    try {
      return IngestionAdmin.Job.from(jobs.get(id));
    } catch (IllegalArgumentException exception) {
      throw new ApiException(HttpStatus.NOT_FOUND, "INGESTION_JOB_NOT_FOUND", "수집 작업을 찾을 수 없습니다.");
    }
  }

  @Transactional
  public IngestionAdmin.Job request(UUID actor, UUID source, UUID key, IngestionAdmin.Run input) {
    if (!source.equals(IngestionJobRepository.PUBLIC_DATA_SOURCE))
      throw new ApiException(
          HttpStatus.NOT_FOUND, "INGESTION_SOURCE_NOT_FOUND", "구현된 수집 소스를 찾을 수 없습니다.");
    repository.lockRequest(actor, key);
    String hash =
        TokenSecrets.hash(
            source + "\n" + input.mode() + "\n" + input.pageBudget() + "\n" + input.reason());
    var receipt = repository.receipt(actor, key);
    if (receipt.isPresent()) {
      if (!receipt.get().requestHash().equals(hash))
        throw new ApiException(
            HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 수집 내용이 전달되었습니다.");
      return detail(receipt.get().jobId());
    }
    var configured =
        sources().stream()
            .filter(item -> item.id().equals(source))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INGESTION_SOURCE_NOT_FOUND", "수집 소스를 찾을 수 없습니다."));
    if (!configured.collectionAllowed() || !configured.republicationAllowed())
      throw new ApiException(
          HttpStatus.CONFLICT, "SOURCE_PERMISSION_REQUIRED", "수집·저장·재게시 이용 조건을 먼저 확인해 주세요.");
    if (!credentialConfigured)
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_KEY_MISSING", "수집 인증키가 설정되지 않았습니다.");
    if (!repository.allowRequest(actor))
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "INGESTION_REQUEST_LIMIT",
          "수집 재실행 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
    UUID job;
    try {
      job = jobs.request(input.mode(), 100, input.pageBudget());
    } catch (IllegalStateException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "INGESTION_RUN_CONFLICT", "진행 중인 수집이나 이용 조건을 확인해 주세요.");
    }
    repository.remember(actor, key, hash, job, source, input.reason(), input.pageBudget());
    return detail(job);
  }

  @Transactional(readOnly = true)
  public IngestionAdmin.Events events(UUID job, long cursor, int limit) {
    validateLimit(limit);
    if (cursor < 0) throw invalid();
    detail(job);
    var rows = repository.events(job, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var page = more ? rows.subList(0, limit) : rows;
    return new IngestionAdmin.Events(page, more ? page.getLast().id() : null);
  }

  @Transactional(readOnly = true)
  public IngestionAdmin.Quarantines quarantines(UUID job, UUID cursor, int limit) {
    validateLimit(limit);
    detail(job);
    var rows = repository.quarantines(job, cursor, limit + 1);
    boolean more = rows.size() > limit;
    var page = more ? rows.subList(0, limit) : rows;
    return new IngestionAdmin.Quarantines(page, more ? page.getLast().id() : null);
  }

  private void validateLimit(int limit) {
    if (limit < 1 || limit > 100) throw invalid();
  }

  private ApiException invalid() {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "INVALID_INGESTION_PAGE", "조회 개수와 다음 페이지를 확인해 주세요.");
  }

  @org.springframework.scheduling.annotation.Scheduled(
      fixedDelayString = "PT1H",
      initialDelayString = "PT1H")
  @Transactional
  public void cleanExpiredRequests() {
    repository.removeExpiredRequests();
  }
}
