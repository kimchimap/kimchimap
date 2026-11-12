package kr.kimchimap.feedback.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.feedback.dto.InformationReports;
import kr.kimchimap.feedback.service.InformationReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "serviceBearer")
@RequestMapping("/api/v1/admin/information-reports")
public class AdminInformationReportController {
  private final InformationReportService reports;

  public AdminInformationReportController(InformationReportService reports) {
    this.reports = reports;
  }

  @GetMapping
  public InformationReports.Page list(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return reports.list(null, state, cursor, limit);
  }

  @GetMapping("/{id}")
  public InformationReports.View detail(@PathVariable UUID id) {
    return reports.detail(null, id);
  }

  @PostMapping("/{id}/resolution")
  public InformationReports.View resolve(
      @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable UUID id,
      @Valid @RequestBody InformationReports.Resolution input) {
    return reports.resolve(member.memberId(), id, input);
  }
}
