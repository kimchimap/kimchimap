package kr.kimchimap.certification.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.certification.dto.DesignationAdministration;
import kr.kimchimap.certification.service.DesignationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/designations")
@SecurityRequirement(name = "serviceBearer")
public class AdminDesignationController {
  private final DesignationService designations;

  public AdminDesignationController(DesignationService designations) {
    this.designations = designations;
  }

  @GetMapping("/sources")
  public List<DesignationAdministration.Source> sources() {
    return designations.sources();
  }

  @GetMapping
  public List<DesignationAdministration.Item> list(
      @RequestParam(required = false) UUID restaurantId) {
    return designations.recent(restaurantId);
  }

  @GetMapping("/{id}")
  public DesignationAdministration.View detail(@PathVariable UUID id) {
    return designations.detail(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DesignationAdministration.View create(
      @AuthenticationPrincipal AuthenticatedMember actor,
      @Valid @RequestBody DesignationAdministration.Create input) {
    return designations.create(actor.memberId(), input);
  }

  @PutMapping("/{id}")
  public DesignationAdministration.View update(
      @AuthenticationPrincipal AuthenticatedMember actor,
      @PathVariable UUID id,
      @Valid @RequestBody DesignationAdministration.Update input) {
    return designations.update(actor.memberId(), id, input);
  }
}
