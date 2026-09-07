package kr.kimchimap.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MatchAdministration {
  private MatchAdministration() {}

  @Schema(name = "MatchReviewRequest")
  public record Review(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "MATCHED|DISTINCT") String decision,
      UUID restaurantId,
      @NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "MatchListItem")
  public record Item(
      UUID id,
      UUID sourceId,
      String sourceName,
      String externalId,
      String name,
      String address,
      String state,
      boolean reviewable) {}

  @Schema(name = "MatchCandidate")
  public record Candidate(
      UUID id, String name, String address, Double latitude, Double longitude) {}

  @Schema(name = "MatchReviewDetail")
  public record Detail(
      UUID id,
      UUID sourceId,
      String sourceName,
      String externalId,
      String name,
      String address,
      Double latitude,
      Double longitude,
      String coordinateStatus,
      Instant observedAt,
      Instant sourceUpdatedAt,
      long version,
      String state,
      UUID restaurantId,
      List<Candidate> candidates) {}
}
