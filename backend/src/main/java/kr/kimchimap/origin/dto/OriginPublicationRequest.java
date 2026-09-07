package kr.kimchimap.origin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.origin.entity.OriginValue;

public record OriginPublicationRequest(
    UUID restaurantId,
    UUID scopeId,
    String scopeName,
    String usage,
    Instant observedAt,
    Instant collectedAt,
    UUID submissionId,
    String publicReference,
    List<Assertion> assertions) {
  public record Assertion(UUID ingredientId, String originalExpression, OriginValue value) {}
}
