package kr.kimchimap.ingestion.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.scheduling-enabled", havingValue = "true")
public class IngestionDispatcher {
  private final IngestionJobService jobs;
  private final IngestionWorker worker;
  private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Semaphore capacity = new Semaphore(1);

  public IngestionDispatcher(IngestionJobService jobs, IngestionWorker worker) {
    this.jobs = jobs;
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 1000)
  public void dispatch() {
    if (!capacity.tryAcquire()) return;
    executor.execute(
        () -> {
          try {
            jobs.scheduleDue();
            worker.processOne(null);
          } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(IngestionDispatcher.class)
                .error("수집 실행 점검 실패: type={}", exception.getClass().getSimpleName());
          } finally {
            capacity.release();
          }
        });
  }

  @PreDestroy
  public void close() {
    executor.close();
  }
}
