package kr.kimchimap.report.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.report.dto.*;
import kr.kimchimap.report.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "serviceBearer")
public class ReportController {
  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReportView create(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody ReportSubmission body) {
    return reports.create(member.memberId(), key, body);
  }

  @GetMapping
  public ReportPage list(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return reports.list(member.memberId(), state, cursor, limit);
  }

  @GetMapping("/{id}")
  public ReportView detail(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
    return reports.ownDetail(member.memberId(), id);
  }

  @PatchMapping("/{id}")
  public ReportView update(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable UUID id,
      @Valid @RequestBody ReportChanges.Update body) {
    return reports.update(member.memberId(), id, body);
  }

  @PostMapping("/{id}/withdraw")
  public ReportView withdraw(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable UUID id,
      @Valid @RequestBody ReportChanges.Withdraw body) {
    return reports.withdraw(member.memberId(), id, body);
  }
}
