package kr.kimchimap.restaurant.controller;

import java.util.UUID;
import kr.kimchimap.restaurant.dto.RestaurantDetail;
import kr.kimchimap.restaurant.service.RestaurantQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {
  private final RestaurantQueryService service;

  public RestaurantController(RestaurantQueryService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public RestaurantDetail detail(@PathVariable UUID id) {
    return service.detail(id);
  }
}
