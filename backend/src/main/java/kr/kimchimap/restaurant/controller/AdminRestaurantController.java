package kr.kimchimap.restaurant.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.kimchimap.auth.dto.AuthenticatedMember;
import kr.kimchimap.restaurant.dto.RestaurantAdministration;
import kr.kimchimap.restaurant.service.RestaurantAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
@SecurityRequirement(name = "serviceBearer")
public class AdminRestaurantController {
  private final RestaurantAdminService restaurants;

  public AdminRestaurantController(RestaurantAdminService restaurants) {
    this.restaurants = restaurants;
  }

  @GetMapping("/{id}")
  public RestaurantAdministration.View detail(@PathVariable UUID id) {
    return restaurants.detail(id);
  }

  @PostMapping("/{id}/corrections")
  public RestaurantAdministration.View correct(
      @AuthenticationPrincipal AuthenticatedMember actor,
      @PathVariable UUID id,
      @Valid @RequestBody RestaurantAdministration.Correction input) {
    return restaurants.correct(actor.memberId(), id, input);
  }
}
