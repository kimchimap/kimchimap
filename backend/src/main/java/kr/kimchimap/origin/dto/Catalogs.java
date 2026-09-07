package kr.kimchimap.origin.dto;

import java.util.UUID;

public final class Catalogs {
  private Catalogs() {}

  public record IngredientItem(UUID id, String code, String name, String category) {}

  public record CountryItem(String code, String name) {}
}
