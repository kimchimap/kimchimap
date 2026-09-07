package kr.kimchimap.ingestion.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.ingestion.dto.IngestionAdmin;
import kr.kimchimap.ingestion.service.IngestionAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
@SecurityRequirement(name = "serviceBearer")
public class AdminIngestionController {
  private final IngestionAdminService ingestion;

  public AdminIngestionController(IngestionAdminService ingestion) {
    this.ingestion = ingestion;
  }

  @GetMapping("/sources")
  public List<IngestionAdmin.Source> sources() {
    return ingestion.sources();
  }

  @GetMapping("/jobs")
  public List<IngestionAdmin.Job> jobs() {
    return ingestion.recent();
  }

  @GetMapping("/jobs/{id}")
  public IngestionAdmin.Job detail(@PathVariable UUID id) {
    return ingestion.detail(id);
  }

  @GetMapping("/jobs/{id}/events")
  public IngestionAdmin.Events events(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") long cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return ingestion.events(id, cursor, limit);
  }

  @GetMapping("/jobs/{id}/quarantines")
  public IngestionAdmin.Quarantines quarantines(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return ingestion.quarantines(id, cursor, limit);
  }

  @PostMapping("/sources/{id}/runs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public IngestionAdmin.Job run(
      @AuthenticationPrincipal AuthenticatedMember actor,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody IngestionAdmin.Run input) {
    return ingestion.request(actor.memberId(), id, key, input);
  }
}
