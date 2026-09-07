package kr.kimchimap.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IngestionAdmin {
  private IngestionAdmin() {}

  @Schema(name = "IngestionRunRequest")
  public record Run(
      @NotBlank @Pattern(regexp = "INCREMENTAL|FULL") String mode,
      @NotNull @Min(1) @Max(100) Integer pageBudget,
      @NotBlank @Size(max = 2000) String reason) {}

  @Schema(name = "IngestionSourceStatus")
  public record Source(
      UUID id,
      String name,
      boolean collectionAllowed,
      boolean republicationAllowed,
      boolean domesticQualificationSupported,
      boolean scheduled,
      boolean credentialConfigured,
      int intervalSeconds,
      Instant nextRunAt,
      Instant lastCompletedUntil) {}

  @Schema(name = "IngestionJobStatus")
  public record Job(
      UUID id,
      UUID sourceId,
      String mode,
      String status,
      int nextPage,
      int pagesProcessed,
      int pageBudget,
      Long totalCount,
      long readCount,
      long changedCount,
      long quarantinedCount,
      boolean fullListingCompleted,
      Instant nextAttemptAt,
      String errorCode,
      Instant requestedAt,
      Instant updatedAt) {
    public static Job from(IngestionJob job) {
      return new Job(
          job.id(),
          job.sourceId(),
          job.mode(),
          job.status(),
          job.nextPage(),
          job.pagesProcessed(),
          job.maxPages(),
          job.totalCount(),
          job.readCount(),
          job.changedCount(),
          job.quarantinedCount(),
          job.fullListingCompleted(),
          job.nextAttemptAt(),
          job.errorCode(),
          job.requestedAt(),
          job.updatedAt());
    }
  }

  @Schema(name = "IngestionEvent")
  public record Event(long id, int pageNo, String status, String errorCode, Instant createdAt) {}

  @Schema(name = "IngestionEventPage")
  public record Events(List<Event> items, Long nextCursor) {}

  @Schema(name = "IngestionQuarantineItem")
  public record Quarantine(
      UUID id, int pageNo, int rowIndex, String errorCode, Instant createdAt) {}

  @Schema(name = "IngestionQuarantinePage")
  public record Quarantines(List<Quarantine> items, UUID nextCursor) {}
}
