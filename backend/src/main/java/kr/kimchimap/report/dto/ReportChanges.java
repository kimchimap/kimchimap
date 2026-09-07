package kr.kimchimap.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class ReportChanges {
  private ReportChanges() {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportUpdateRequest")
  public record Update(
      @NotNull @PositiveOrZero Long expectedVersion, @NotNull @Valid ReportSubmission submission) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportWithdrawRequest")
  public record Withdraw(
      @NotNull @PositiveOrZero Long expectedVersion, @NotBlank @Size(max = 2000) String reason) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportReviewRequest")
  public record Review(
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED|NEEDS_MORE_INFO") String decision,
      @NotBlank @Size(max = 2000) String reason,
      UUID approvedScopeId,
      @NotNull @Size(max = 5) List<@NotNull UUID> privacyReviewedMediaIds) {}
}
