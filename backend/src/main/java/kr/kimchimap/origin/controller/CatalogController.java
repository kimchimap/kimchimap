package kr.kimchimap.origin.controller;

import java.util.List;
import kr.kimchimap.origin.dto.Catalogs.CountryItem;
import kr.kimchimap.origin.dto.Catalogs.IngredientItem;
import kr.kimchimap.origin.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogs")
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService service) {
    this.service = service;
  }

  @GetMapping("/ingredients")
  public List<IngredientItem> ingredients() {
    return service.ingredients();
  }

  @GetMapping("/countries")
  public List<CountryItem> countries() {
    return service.countries();
  }
}
