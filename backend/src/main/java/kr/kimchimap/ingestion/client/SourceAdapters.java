package kr.kimchimap.ingestion.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SourceAdapters {
  private final Map<UUID, SourceAdapter> adapters;

  public SourceAdapters(List<SourceAdapter> adapters) {
    this.adapters =
        adapters.stream()
            .collect(Collectors.toUnmodifiableMap(SourceAdapter::sourceId, Function.identity()));
  }

  public boolean available(UUID source) {
    return adapters.containsKey(source);
  }

  public SourceAdapter.Page fetch(
      UUID source, int page, int pageSize, Instant since, Instant until) {
    var adapter = adapters.get(source);
    if (adapter == null) throw new SourceFailure("SOURCE_ADAPTER_UNAVAILABLE", false, null);
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("수집 페이지 범위를 확인해 주세요.");
    var result = adapter.fetch(page, pageSize, since, until);
    if (result.number() != page
        || result.pageSize() != pageSize
        || result.totalCount() < 0
        || result.items().size() > pageSize
        || result.items().isEmpty() && (long) (page - 1) * pageSize < result.totalCount())
      throw new SourceFailure("SOURCE_PAGE_MISMATCH", false, null);
    return result;
  }
}
