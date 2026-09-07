package kr.kimchimap.bookmark.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookmarkPage(List<Item> items, UUID nextCursor) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "BookmarkItem")
  public record Item(
      UUID restaurantId, String name, String address, String businessStatus, Instant savedAt) {}
}
