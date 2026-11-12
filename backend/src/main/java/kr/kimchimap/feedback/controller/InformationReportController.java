package kr.kimchimap.feedback.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.feedback.dto.InformationReports;
import kr.kimchimap.feedback.service.InformationReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "serviceBearer")
@RequestMapping("/api/v1/information-reports")
public class InformationReportController {
  private final InformationReportService reports;

  public InformationReportController(InformationReportService reports) {
    this.reports = reports;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InformationReports.View create(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody InformationReports.Submission input) {
    return reports.create(member.memberId(), key, input);
  }

  @GetMapping
  public InformationReports.Page list(
      @AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return reports.list(member.memberId(), state, cursor, limit);
  }

  @GetMapping("/{id}")
  public InformationReports.View detail(
      @AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
    return reports.detail(member.memberId(), id);
  }
}
