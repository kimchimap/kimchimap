package kr.kimchimap.ingestion.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient;
import kr.kimchimap.ingestion.client.SourceFailure;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import org.springframework.stereotype.Service;

@Service
public class IngestionWorker {
  private final IngestionJobRepository jobs;
  private final PublicDataRestaurantClient client;
  private final IngestionPageService pages;
  private final Clock clock;
  private final OriginCollectionPolicy collectionPolicy;

  public IngestionWorker(
      IngestionJobRepository jobs,
      PublicDataRestaurantClient client,
      IngestionPageService pages,
      Clock clock,
      OriginCollectionPolicy collectionPolicy) {
    this.jobs = jobs;
    this.client = client;
    this.pages = pages;
    this.clock = clock;
    this.collectionPolicy = collectionPolicy;
  }

  public boolean processOne(UUID jobId) {
    var acquired = jobs.acquire(UUID.randomUUID(), jobId);
    if (acquired.isEmpty()) return false;
    var job = acquired.get();
    if (!collectionPolicy.supportsDomesticQualification()) {
      jobs.failure(job, "ORIGIN_QUALIFIED_SOURCE_REQUIRED", clock.instant(), false);
      return true;
    }
    try {
      var page = client.fetch(job.nextPage(), job.pageSize(), job.sinceAt(), job.untilAt());
      var prepared = pages.prepare(job, page);
      pages.commit(job, page, prepared);

    } catch (SourceFailure failure) {
      boolean retry = failure.retryable() && job.retryCount() < 3;
      Duration delay = failure.retryAfter();
      if (delay == null)
        delay =
            Duration.ofMillis(
                (1L << Math.min(job.retryCount(), 10)) * 1000
                    + ThreadLocalRandom.current().nextLong(250));
      if (delay.compareTo(Duration.ofDays(7)) > 0) {
        retry = false;
        delay = Duration.ofDays(7);
      }
      jobs.failure(job, failure.code(), clock.instant().plus(delay), retry);
    } catch (RuntimeException exception) {
      jobs.failure(job, "INGESTION_PROCESSING_FAILED", clock.instant(), false);
      org.slf4j.LoggerFactory.getLogger(IngestionWorker.class)
          .error("수집 처리 실패: job={}, type={}", job.id(), exception.getClass().getSimpleName());
    }
    return true;
  }
}
