package kr.kimchimap.origin.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.origin.dto.OriginAdministration;
import kr.kimchimap.origin.service.OriginAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "serviceBearer")
public class AdminOriginController {
  private final OriginAdminService origins;

  public AdminOriginController(OriginAdminService origins) {
    this.origins = origins;
  }

  @GetMapping("/origins")
  public OriginAdministration.Groups list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return origins.list(status, limit, offset);
  }

  @GetMapping("/origins/{scope}/{ingredient}")
  public OriginAdministration.Detail detail(
      @PathVariable UUID scope, @PathVariable UUID ingredient) {
    return origins.detail(scope, ingredient);
  }

  @PostMapping("/origin-corrections")
  public OriginAdministration.Detail correct(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody OriginAdministration.Correction input) {
    return origins.correct(member.memberId(), input);
  }
}
