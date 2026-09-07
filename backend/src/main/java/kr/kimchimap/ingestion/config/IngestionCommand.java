package kr.kimchimap.ingestion.config;

import java.util.List;
import kr.kimchimap.ingestion.service.IngestionJobService;
import kr.kimchimap.ingestion.service.IngestionWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingestion.command")
public class IngestionCommand implements ApplicationRunner {
  private final IngestionJobService jobs;
  private final IngestionWorker worker;
  private final ConfigurableApplicationContext context;
  private final String command;
  private final String mode;
  private final int maxPages;

  public IngestionCommand(
      IngestionJobService jobs,
      IngestionWorker worker,
      ConfigurableApplicationContext context,
      @Value("${app.ingestion.command}") String command,
      @Value("${app.ingestion.mode:INCREMENTAL}") String mode,
      @Value("${app.ingestion.max-pages:2}") int maxPages) {
    this.jobs = jobs;
    this.worker = worker;
    this.context = context;
    this.command = command;
    this.mode = mode;
    this.maxPages = maxPages;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try {
      if (command.equals("status")) {
        for (var job : jobs.recent()) print(job);
      } else if (command.equals("run")) {
        var id = jobs.request(mode, 100, maxPages);
        for (; ; ) {
          var job = jobs.get(id);
          if (!List.of("QUEUED", "RUNNING", "WAITING").contains(job.status())) break;
          if (job.leaseUntil() != null && job.leaseUntil().isAfter(java.time.Instant.now())) break;
          if (job.nextAttemptAt().isAfter(java.time.Instant.now().plusSeconds(2))) break;
          if (!worker.processOne(id)) Thread.sleep(250);
        }
        var result = jobs.get(id);
        print(result);
        if (result.status().equals("FAILED"))
          throw new IllegalStateException("수집 실패 코드: " + result.errorCode());
      } else throw new IllegalArgumentException("지원하지 않는 수집 명령입니다.");
    } finally {
      context.close();
    }
  }

  private void print(kr.kimchimap.ingestion.dto.IngestionJob job) {
    System.out.printf(
        "수집 작업=%s 상태=%s 다음페이지=%d 읽음=%d 변경=%d 격리=%d 전체대조완료=%s 오류=%s%n",
        job.id(),
        job.status(),
        job.nextPage(),
        job.readCount(),
        job.changedCount(),
        job.quarantinedCount(),
        job.fullListingCompleted(),
        job.errorCode());
  }
}
