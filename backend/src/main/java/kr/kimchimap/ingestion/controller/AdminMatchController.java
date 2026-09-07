package kr.kimchimap.ingestion.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.ingestion.dto.MatchAdministration;
import kr.kimchimap.ingestion.service.MatchReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/matches")
@SecurityRequirement(name = "serviceBearer")
public class AdminMatchController {
  private final MatchReviewService matches;

  public AdminMatchController(MatchReviewService matches) {
    this.matches = matches;
  }

  @GetMapping
  public List<MatchAdministration.Item> list() {
    return matches.recent();
  }

  @GetMapping("/{id}")
  public MatchAdministration.Detail detail(@PathVariable UUID id) {
    return matches.detail(id);
  }

  @PostMapping("/{id}/reviews")
  public MatchAdministration.Detail review(
      @AuthenticationPrincipal AuthenticatedMember actor,
      @PathVariable UUID id,
      @Valid @RequestBody MatchAdministration.Review input) {
    return matches.review(actor.memberId(), id, input);
  }
}
