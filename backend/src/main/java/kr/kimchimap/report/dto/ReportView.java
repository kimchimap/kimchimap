package kr.kimchimap.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportView(
    UUID id,
    String state,
    long version,
    Instant createdAt,
    Instant updatedAt,
    ReportSubmission submission,
    List<Review> reviews) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportReviewHistory")
  public record Review(String decision, String reason, Instant createdAt) {}
}
