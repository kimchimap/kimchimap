package kr.kimchimap.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SearchRequest(
    Center center,
    Bounds bounds,
    Double radiusMeters,
    List<ScopeFilter> groups,
    Integer maxAgeDays,
    Set<EvidenceKind> evidenceKinds,
    Integer limit,
    String cursor) {
  public enum Usage {
    SIDE_DISH,
    STEW,
    MAIN_DISH,
    OTHER,
    UNKNOWN
  }

  public enum EvidenceKind {
    SIGNBOARD_OBSERVATION,
    USER_SUBMISSION,
    SUPPLY_VERIFICATION
  }

  public enum OriginMode {
    DOMESTIC,
    COUNTRIES,
    IMPORTED_UNSPECIFIED,
    UNKNOWN
  }

  public record Center(
      @JsonProperty(required = true) double latitude,
      @JsonProperty(required = true) double longitude) {
    public Center {
      require(
          Double.isFinite(latitude)
              && latitude >= -90
              && latitude <= 90
              && Double.isFinite(longitude)
              && longitude >= -180
              && longitude <= 180,
          "위도와 경도 범위를 확인해 주세요.");
    }
  }

  public record Bounds(
      @JsonProperty(required = true) double south,
      @JsonProperty(required = true) double west,
      @JsonProperty(required = true) double north,
      @JsonProperty(required = true) double east) {
    public Bounds {
      new Center(south, west);
      new Center(north, east);
      require(south < north && west < east, "지도 영역의 남북·동서 순서를 확인해 주세요.");
    }

    public Center center() {
      return new Center((south + north) / 2, (west + east) / 2);
    }

    public boolean tooWide() {
      return north - south > 1 || east - west > 1;
    }
  }

  public record IngredientFilter(
      UUID ingredientId, OriginMode mode, Set<String> countries, Boolean includeMixed) {
    public IngredientFilter {
      includeMixed = Boolean.TRUE.equals(includeMixed);
      require(ingredientId != null && mode != null, "식재료와 원산지 조건이 필요합니다.");
      countries = countries == null ? Set.of() : Set.copyOf(countries);
      require(
          countries.size() <= 20 && countries.stream().allMatch(code -> code.matches("[A-Z]{2}")),
          "국가 코드를 확인해 주세요.");
      require(
          mode == OriginMode.COUNTRIES ? !countries.isEmpty() : countries.isEmpty(),
          "국가 선택 조건을 확인해 주세요.");
      require(!includeMixed || mode == OriginMode.COUNTRIES, "혼합 포함은 국가 선택 조건에서만 사용할 수 있습니다.");
    }
  }

  public record ScopeFilter(Usage usage, List<IngredientFilter> ingredients) {
    public ScopeFilter {
      ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
      require(!ingredients.isEmpty() && ingredients.size() <= 10, "품목별 식재료 조건은 1~10개입니다.");
      require(
          ingredients.stream().map(IngredientFilter::ingredientId).distinct().count()
              == ingredients.size(),
          "같은 품목의 식재료 조건은 중복할 수 없습니다.");
    }
  }

  public SearchRequest {
    require((center == null) != (bounds == null), "중심 반경 또는 지도 영역 중 하나를 선택해 주세요.");
    require(
        center == null
            ? radiusMeters == null
            : radiusMeters != null
                && Double.isFinite(radiusMeters)
                && radiusMeters >= 1
                && radiusMeters <= 50000,
        "반경은 1~50,000미터입니다.");
    groups = groups == null ? List.of() : List.copyOf(groups);
    require(
        groups.size() <= 10 && groups.stream().mapToInt(g -> g.ingredients().size()).sum() <= 10,
        "전체 식재료 조건은 최대 10개입니다.");
    require(maxAgeDays == null || maxAgeDays >= 1 && maxAgeDays <= 3650, "확인일 범위를 확인해 주세요.");
    evidenceKinds = evidenceKinds == null ? Set.of() : Set.copyOf(evidenceKinds);
    require(
        (maxAgeDays == null && evidenceKinds.isEmpty()) || !groups.isEmpty(),
        "근거·확인일 필터와 함께 식재료 조건을 선택해 주세요.");
    limit = limit == null ? 20 : limit;
    require(limit >= 1 && limit <= 100, "페이지 크기는 1~100개입니다.");
    require(cursor == null || !cursor.isBlank() && cursor.length() <= 2048, "페이지 정보를 확인해 주세요.");
  }

  public Center distanceCenter() {
    return center != null ? center : bounds.center();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
