package kr.kimchimap.ingestion.service;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.IngestionJob;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionJobService {
  private final IngestionJobRepository repository;
  private final Clock clock;
  private final OriginCollectionPolicy collectionPolicy;

  public IngestionJobService(
      IngestionJobRepository repository, Clock clock, OriginCollectionPolicy collectionPolicy) {
    this.repository = repository;
    this.clock = clock;
    this.collectionPolicy = collectionPolicy;
  }

  @Transactional
  public UUID request(String mode, int pageSize, int pageBudget) {
    if (!List.of("INCREMENTAL", "FULL").contains(mode)
        || pageSize < 1
        || pageSize > 100
        || pageBudget < 1
        || pageBudget > 5000) {
      throw new IllegalArgumentException("수집 실행 범위를 확인해 주세요.");
    }
    collectionPolicy.requireQualifiedSource();
    repository.lockSource();
    var active = repository.resumable();
    if (active.isPresent()) {
      if (!active.get().mode().equals(mode))
        throw new IllegalStateException("진행 중인 다른 수집 범위를 먼저 완료해 주세요.");
      if (List.of("PARTIAL", "FAILED").contains(active.get().status()))
        repository.extendBudget(active.get().id(), pageBudget);
      return active.get().id();
    }
    var until = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    var last = repository.lastCompletedUntil();
    var since =
        mode.equals("FULL")
            ? null
            : last == null ? until.minus(Duration.ofDays(7)) : last.minus(Duration.ofDays(2));
    return repository.create(mode, since, until, pageSize, pageBudget);
  }

  @Transactional
  public void scheduleDue() {
    if (!collectionPolicy.supportsDomesticQualification()) return;
    repository.lockSource();
    if (!repository.scheduledDue()) return;
    var active = repository.resumable();
    String mode =
        active
            .map(IngestionJob::mode)
            .orElseGet(() -> repository.fullReconciliationDue() ? "FULL" : "INCREMENTAL");
    request(mode, 100, 1000);
    repository.deferSchedule();
  }

  @Transactional(readOnly = true)
  public IngestionJob get(UUID id) {
    return repository.find(id).orElseThrow(() -> new IllegalArgumentException("수집 작업을 찾을 수 없습니다."));
  }

  @Transactional(readOnly = true)
  public List<IngestionJob> recent() {
    return repository.recent();
  }
}
