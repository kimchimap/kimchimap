package kr.kimchimap.search.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchResponse(
    List<Item> items,
    String nextCursor,
    boolean truncated,
    boolean zoomRequired,
    Instant asOf,
    String notice) {
  public record Item(
      UUID id,
      String name,
      String address,
      double latitude,
      double longitude,
      String businessStatus,
      double distanceMeters,
      List<MatchedScope> matchedScopes) {}

  public record MatchedScope(int groupIndex, UUID id, String name, String usage) {}
}
