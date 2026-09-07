package kr.kimchimap.ingestion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.ingestion.dto.IngestionAdmin;
import kr.kimchimap.ingestion.repository.IngestionAdminRepository;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import org.junit.jupiter.api.Test;

class IngestionAdminServiceTest {
  @Test
  void missingKeyDoesNotQueueAnExternalJob() {
    var repository = mock(IngestionAdminRepository.class);
    var jobs = mock(IngestionJobService.class);
    UUID actor = UUID.randomUUID(),
        key = UUID.randomUUID(),
        source = IngestionJobRepository.PUBLIC_DATA_SOURCE;
    when(repository.receipt(actor, key)).thenReturn(Optional.empty());
    when(repository.sources(false, false, false))
        .thenReturn(
            List.of(
                new IngestionAdmin.Source(
                    source, "테스트 소스", true, true, false, false, false, 86400, null, null)));
    var service =
        new IngestionAdminService(repository, jobs, new OriginCollectionPolicy(), "", false);
    assertThatThrownBy(
            () ->
                service.request(
                    actor, source, key, new IngestionAdmin.Run("INCREMENTAL", 2, "테스트 요청")))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.status().value()).isEqualTo(503));
    verifyNoInteractions(jobs);
  }

  @Test
  void manualQueueRunsOffCallerThreadWithoutEnablingPeriodicCollection() throws Exception {
    var jobs = mock(IngestionJobService.class);
    var worker = mock(IngestionWorker.class);
    var called = new CountDownLatch(1);
    var caller = Thread.currentThread();
    var actual = new java.util.concurrent.atomic.AtomicReference<Thread>();
    when(worker.processOne(null))
        .thenAnswer(
            invocation -> {
              actual.set(Thread.currentThread());
              called.countDown();
              return true;
            });
    var dispatcher = new IngestionDispatcher(jobs, worker, false);
    try {
      dispatcher.dispatch();
      assertThat(called.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(actual.get()).isNotEqualTo(caller);
      verifyNoInteractions(jobs);
    } finally {
      dispatcher.close();
    }
  }
}
