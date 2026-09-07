package kr.kimchimap.media.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import kr.kimchimap.media.repository.MediaRepository;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MediaCleanup {
  private final MediaRepository media;
  private final MediaStorage storage;
  private final Clock clock;

  public MediaCleanup(MediaRepository media, MediaStorage storage, Clock clock) {
    this.media = media;
    this.storage = storage;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.media.cleanup-delay:PT1H}", initialDelayString = "PT1H")
  public void clean() {
    try {
      for (var row : media.expiredTemporary()) {
        // ATTACHED로 바뀐 파일을 삭제하지 않도록 상태 변경을 DB에서 먼저 확정한다.
        if (media.markDeleting(row.id())) {
          storage.delete(row.storageKey());
          media.deleteMarked(row.id());
        }
      }
      for (var key : storage.olderThan(clock.instant().minus(Duration.ofDays(7)), 100)) {
        if (!media.storageReferenced(key)) storage.delete(key);
      }
    } catch (IOException | RuntimeException exception) {
      LoggerFactory.getLogger(MediaCleanup.class)
          .warn("이미지 정리 실패: type={}", exception.getClass().getSimpleName());
    }
  }
}
