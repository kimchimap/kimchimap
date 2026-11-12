package kr.kimchimap.ingestion.dto;

import java.util.Map;
import java.util.Set;

public record SourceRestaurant(Map<String, String> fields) {
  private static final Set<String> FIELDS =
      Set.of(
          "externalId",
          "name",
          "address",
          "businessStatus",
          "originalStatus",
          "x",
          "y",
          "coordinateSrid",
          "phone",
          "sourceUpdatedAt",
          "sourceModifiedAt");

  public SourceRestaurant {
    if (!FIELDS.containsAll(fields.keySet()))
      throw new IllegalArgumentException("지원하지 않는 내부 수집 필드입니다.");
    fields = Map.copyOf(fields);
  }

  public String get(String key) {
    return fields.getOrDefault(key, "").strip();
  }
}
