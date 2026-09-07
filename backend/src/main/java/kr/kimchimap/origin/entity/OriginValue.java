package kr.kimchimap.origin.entity;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record OriginValue(Classification classification, List<Component> components) {
  public enum Classification {
    DOMESTIC,
    IMPORTED_SPECIFIED,
    IMPORTED_UNSPECIFIED,
    MIXED,
    UNKNOWN
  }

  public record Component(Classification kind, String countryCode, BigDecimal ratio) {
    public Component {
      Objects.requireNonNull(kind);
      boolean valid =
          switch (kind) {
            case DOMESTIC -> "KR".equals(countryCode);
            case IMPORTED_SPECIFIED ->
                countryCode != null && countryCode.matches("[A-Z]{2}") && !countryCode.equals("KR");
            case IMPORTED_UNSPECIFIED -> countryCode == null;
            default -> false;
          };
      if (!valid
          || ratio != null
              && (ratio.signum() <= 0
                  || ratio.compareTo(BigDecimal.ONE) > 0
                  || ratio.stripTrailingZeros().scale() > 6)) {
        throw new IllegalArgumentException("원산지 구성 국가와 비율을 확인해 주세요.");
      }
      if (ratio != null) ratio = ratio.stripTrailingZeros();
    }
  }

  public OriginValue {
    Objects.requireNonNull(classification);
    components =
        List.copyOf(components).stream()
            .sorted(
                Comparator.comparing(
                    Component::countryCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    boolean valid =
        switch (classification) {
          case UNKNOWN -> components.isEmpty();
          case MIXED -> components.size() >= 2;
          default -> components.size() == 1 && components.getFirst().kind() == classification;
        };
    var countries = new HashSet<String>();
    var total = BigDecimal.ZERO;
    int knownRatios = 0;
    for (var component : components) {
      if (!countries.add(component.countryCode())) valid = false;
      if (component.ratio() != null) {
        knownRatios++;
        total = total.add(component.ratio());
      }
    }
    if (total.compareTo(BigDecimal.ONE) > 0
        || !components.isEmpty()
            && knownRatios == components.size()
            && total.compareTo(BigDecimal.ONE) != 0
        || knownRatios < components.size() && total.compareTo(BigDecimal.ONE) >= 0) valid = false;
    if (!valid) throw new IllegalArgumentException("원산지 분류와 구성 정보가 일치하지 않습니다.");
  }

  public boolean isPureDomestic() {
    return classification == Classification.DOMESTIC;
  }
}
