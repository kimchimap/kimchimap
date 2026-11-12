package kr.kimchimap.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.origin.dto.OriginAdministration;

public final class InformationReports {
  private InformationReports() {}

  @Schema(name = "InformationReportSubmission")
  public record Submission(
      @NotNull UUID restaurantId,
      @NotBlank @Pattern(regexp = "ORIGIN_CHANGED|CLOSED|LOCATION|CONTACT|OTHER") String category,
      @NotBlank @Size(max = 2000) String description,
      @NotNull @Size(max = 3) List<@NotNull UUID> mediaIds) {}

  @Schema(name = "InformationReportResolution")
  public record Resolution(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "RESOLVED|DISMISSED") String decision,
      @NotBlank @Size(max = 2000) String reason,
      @Valid OriginAdministration.Correction correction,
      @Valid
          kr.kimchimap.restaurant.dto.RestaurantAdministration.Correction restaurantCorrection) {}

  @Schema(name = "InformationReportView")
  public record View(
      UUID id,
      UUID restaurantId,
      String restaurantName,
      String category,
      String description,
      String state,
      long version,
      Instant createdAt,
      List<UUID> mediaIds,
      String resolutionReason,
      Instant resolvedAt) {}

  @Schema(name = "InformationReportItem")
  public record Item(
      UUID id,
      UUID restaurantId,
      String restaurantName,
      String category,
      String state,
      Instant createdAt) {}

  @Schema(name = "InformationReportPage")
  public record Page(List<Item> items, UUID nextCursor) {}
}
