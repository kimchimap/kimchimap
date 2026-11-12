package kr.kimchimap.like.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LikePage(List<Item> items, UUID nextCursor) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "LikeItem")
  public record Item(
      UUID restaurantId, String name, String address, String businessStatus, Instant savedAt) {}
}
