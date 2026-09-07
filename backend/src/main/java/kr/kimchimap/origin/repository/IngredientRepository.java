package kr.kimchimap.origin.repository;

import java.util.List;
import java.util.UUID;
import kr.kimchimap.origin.entity.Ingredient;
import org.springframework.data.repository.Repository;

public interface IngredientRepository extends Repository<Ingredient, UUID> {
  List<Ingredient> findByActiveTrueOrderByCodeAsc();
}
