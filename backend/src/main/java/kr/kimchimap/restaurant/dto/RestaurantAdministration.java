package kr.kimchimap.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RestaurantAdministration {
  private RestaurantAdministration() {}

  @Schema(name = "RestaurantCorrectionRequest")
  public record Correction(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 2000) String address,
      @NotBlank @Pattern(regexp = "OPEN|CLOSED|UNKNOWN") String businessStatus,
      @DecimalMin("32") @DecimalMax("39.5") Double latitude,
      @DecimalMin("124") @DecimalMax("132") Double longitude,
      @Size(max = 64) String phone,
      @NotBlank @Size(max = 2000) String reason,
      Boolean locationReviewed,
      Boolean businessPhoneReviewed) {}

  @Schema(name = "RestaurantAdminSnapshot")
  public record Snapshot(
      String name,
      String address,
      String businessStatus,
      Double latitude,
      Double longitude,
      String phone,
      boolean published) {}

  @Schema(name = "RestaurantCorrectionAudit")
  public record Audit(
      UUID id,
      UUID actorId,
      long previousVersion,
      Snapshot before,
      Snapshot after,
      String reason,
      Instant createdAt) {}

  @Schema(name = "RestaurantAdminView")
  public record View(UUID id, long version, Snapshot snapshot, List<Audit> corrections) {}
}
