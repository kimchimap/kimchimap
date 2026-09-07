package kr.kimchimap.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportPage(List<Item> items, UUID nextCursor) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportListItem")
  public record Item(
      UUID id,
      UUID restaurantId,
      String restaurantName,
      String state,
      long version,
      Instant createdAt) {}
}
