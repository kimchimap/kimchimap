package kr.kimchimap.origin.service;

import java.util.List;
import kr.kimchimap.origin.dto.Catalogs.CountryItem;
import kr.kimchimap.origin.dto.Catalogs.IngredientItem;
import kr.kimchimap.origin.repository.CountryRepository;
import kr.kimchimap.origin.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {
  private final IngredientRepository ingredients;
  private final CountryRepository countries;

  public CatalogService(IngredientRepository ingredients, CountryRepository countries) {
    this.ingredients = ingredients;
    this.countries = countries;
  }

  public List<IngredientItem> ingredients() {
    return ingredients.findByActiveTrueOrderByCodeAsc().stream()
        .map(i -> new IngredientItem(i.getId(), i.getCode(), i.getName(), i.getCategory()))
        .toList();
  }

  public List<CountryItem> countries() {
    return countries.findActive();
  }
}
