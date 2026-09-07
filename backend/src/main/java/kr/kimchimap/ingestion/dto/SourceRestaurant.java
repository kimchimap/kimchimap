package kr.kimchimap.ingestion.dto;

import java.util.Map;

public record SourceRestaurant(Map<String, String> fields) {
  public SourceRestaurant {
    fields = Map.copyOf(fields);
  }

  public String get(String key) {
    return fields.getOrDefault(key, "").strip();
  }
}
