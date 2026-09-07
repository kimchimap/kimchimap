package kr.kimchimap.origin;

import static kr.kimchimap.origin.entity.OriginValue.Classification.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.entity.OriginValue.Component;
import org.junit.jupiter.api.Test;

class OriginValueTest {
  @Test
  void unknownImportAndMixedNeverMeanPureDomestic() {
    var unknown = new OriginValue(UNKNOWN, List.of());
    var imported =
        new OriginValue(
            IMPORTED_UNSPECIFIED, List.of(new Component(IMPORTED_UNSPECIFIED, null, null)));
    var mixed =
        new OriginValue(
            MIXED,
            List.of(
                new Component(DOMESTIC, "KR", null),
                new Component(IMPORTED_SPECIFIED, "CN", null)));
    assertThat(List.of(unknown, imported, mixed)).allMatch(value -> !value.isPureDomestic());
    assertThat(unknown).isNotEqualTo(imported);
    assertThat(mixed.components()).allMatch(c -> c.ratio() == null);
    assertThat(
            new OriginValue(DOMESTIC, List.of(new Component(DOMESTIC, "KR", null)))
                .isPureDomestic())
        .isTrue();
  }

  @Test
  void rejectsInconsistentCountryAndClassification() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Component(DOMESTIC, null, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Component(IMPORTED_SPECIFIED, "KR", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OriginValue(UNKNOWN, List.of(new Component(DOMESTIC, "KR", null))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OriginValue(MIXED, List.of(new Component(DOMESTIC, "KR", null))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OriginValue(
                    MIXED,
                    List.of(
                        new Component(DOMESTIC, "KR", null), new Component(DOMESTIC, "KR", null))));
  }

  @Test
  void ratiosAreCanonicalAndNeverInferred() {
    var domestic = new Component(DOMESTIC, "KR", new BigDecimal("0.500000"));
    var imported = new Component(IMPORTED_SPECIFIED, "CN", new BigDecimal("0.5"));
    assertThat(new OriginValue(MIXED, List.of(domestic, imported)))
        .isEqualTo(
            new OriginValue(
                MIXED, List.of(imported, new Component(DOMESTIC, "KR", new BigDecimal("0.5")))));
    var partial =
        new OriginValue(MIXED, List.of(domestic, new Component(IMPORTED_SPECIFIED, "CN", null)));
    assertThat(partial.components().getFirst().ratio()).isNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OriginValue(DOMESTIC, List.of(domestic)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OriginValue(
                    MIXED,
                    List.of(
                        new Component(DOMESTIC, "KR", BigDecimal.ONE),
                        new Component(IMPORTED_SPECIFIED, "CN", null))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Component(DOMESTIC, "KR", new BigDecimal("0.0000001")));
  }
}
