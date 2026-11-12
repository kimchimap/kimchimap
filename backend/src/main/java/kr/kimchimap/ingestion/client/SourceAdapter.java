package kr.kimchimap.ingestion.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.SourceRestaurant;

public interface SourceAdapter {
  record Page(int number, int pageSize, long totalCount, List<SourceRestaurant> items) {
    public Page {
      items = List.copyOf(items);
    }
  }

  UUID sourceId();

  Page fetch(int page, int pageSize, Instant since, Instant until);
}
