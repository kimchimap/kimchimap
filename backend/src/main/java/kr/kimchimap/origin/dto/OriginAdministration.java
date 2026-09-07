package kr.kimchimap.origin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OriginAdministration {
  private OriginAdministration() {}

  @Schema(name = "OriginCorrectionRequest")
  public record Correction(
      @NotNull UUID scopeId,
      @NotNull UUID ingredientId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> withdrawRecordIds,
      @NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "OriginAdminGroup")
  public record Group(
      UUID scopeId,
      UUID ingredientId,
      UUID restaurantId,
      String restaurantName,
      String scopeName,
      String usage,
      String ingredientName,
      String status,
      long version,
      Instant decidedAt) {}

  @Schema(name = "OriginAdminGroupPage")
  public record Groups(List<Group> items, Integer nextOffset, boolean truncated) {}

  @Schema(name = "OriginAdminRecord")
  public record Record(
      UUID id,
      String classification,
      String originalExpression,
      Instant observedAt,
      Instant sourceUpdatedAt,
      Instant reviewedAt,
      Instant validUntil,
      boolean withdrawn,
      String withdrawalReason,
      String sourceName,
      String evidenceKind,
      UUID reportId) {}

  @Schema(name = "OriginCorrectionAudit")
  public record Audit(UUID id, String reason, Instant createdAt) {}

  @Schema(name = "OriginAdminDetail")
  public record Detail(Group group, List<Record> records, List<Audit> corrections) {}
}
