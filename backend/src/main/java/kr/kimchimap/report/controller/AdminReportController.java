package kr.kimchimap.report.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.report.dto.*;
import kr.kimchimap.report.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name = "serviceBearer")
public class AdminReportController {
  private final ReportService reports;

  public AdminReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping
  public ReportPage list(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return reports.list(null, state, cursor, limit);
  }

  @GetMapping("/{id}")
  public ReportView detail(@PathVariable UUID id) {
    return reports.adminDetail(id);
  }

  @PostMapping("/{id}/reviews")
  public ReportView review(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable UUID id,
      @Valid @RequestBody ReportChanges.Review body) {
    return reports.review(member.memberId(), id, body);
  }
}
