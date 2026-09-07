package kr.kimchimap.certification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DesignationAdministration {
  private DesignationAdministration() {}

  @Schema(name = "DesignationInput")
  public record Input(
      @NotNull UUID restaurantId,
      @NotNull UUID sourceId,
      @NotBlank @Size(max = 200) String externalId,
      @NotBlank @Size(max = 200) String schemeName,
      @NotBlank @Size(max = 2000) String applicableItems,
      @NotBlank @Size(max = 5000) String criteriaOriginal,
      LocalDate designatedOn,
      LocalDate expiresOn,
      LocalDate cancelledOn,
      @NotNull Boolean publiclyVisible) {}

  @Schema(name = "DesignationCreateRequest")
  public record Create(
      @NotNull @Valid Input designation, @NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "DesignationUpdateRequest")
  public record Update(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @Valid Input designation,
      @NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "DesignationAdminView")
  public record View(
      UUID id, long version, Instant reviewedAt, Input designation, List<Revision> history) {}

  @Schema(name = "DesignationRevision")
  public record Revision(long version, String reason, Instant createdAt) {}

  @Schema(name = "DesignationSource")
  public record Source(UUID id, String name, String officialUrl, String termsReference) {}

  @Schema(name = "DesignationListItem")
  public record Item(
      UUID id,
      UUID restaurantId,
      String restaurantName,
      String schemeName,
      long version,
      boolean publiclyVisible,
      boolean sourceAllowed) {}
}
