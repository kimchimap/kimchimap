package kr.kimchimap.search;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchRequest.*;
import org.junit.jupiter.api.Test;

class SearchRequestTest {
  @Test
  void rejectsInvalidCoordinatesGeometryChoicesAndComplexity() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Center(Double.NaN, 127));
    assertThatIllegalArgumentException().isThrownBy(() -> new Center(127, 37));
    assertThatIllegalArgumentException().isThrownBy(() -> new Bounds(38, 127, 37, 128));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SearchRequest(null, null, null, null, null, null, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new SearchRequest(new Center(37, 127), null, 50001d, null, null, null, 20, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new SearchRequest(new Center(37, 127), null, 100d, null, null, null, 101, null));
    var ingredient = new IngredientFilter(UUID.randomUUID(), OriginMode.DOMESTIC, null, false);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ScopeFilter(null, List.of(ingredient, ingredient)));
  }

  @Test
  void mixedCannotSatisfyPureDomesticAndCountryModesAreExplicit() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new IngredientFilter(UUID.randomUUID(), OriginMode.DOMESTIC, null, true));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new IngredientFilter(UUID.randomUUID(), OriginMode.COUNTRIES, Set.of(), false));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new IngredientFilter(UUID.randomUUID(), OriginMode.UNKNOWN, Set.of("KR"), false));
  }
}
