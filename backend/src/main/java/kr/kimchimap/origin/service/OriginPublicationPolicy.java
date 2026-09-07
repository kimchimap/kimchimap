package kr.kimchimap.origin.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.origin.entity.OriginValue;
import org.springframework.stereotype.Component;

@Component
public class OriginPublicationPolicy {
  public static final int VERSION = 1;

  public enum Status {
    CURRENT,
    DISPUTED,
    EXPIRED,
    UNAVAILABLE
  }

  public record Candidate(
      UUID id,
      UUID scopeId,
      UUID ingredientId,
      OriginValue value,
      Instant observedAt,
      Instant sourceUpdatedAt,
      Instant reviewedAt,
      Instant validFrom,
      Instant validUntil,
      boolean approved,
      boolean withdrawn,
      long revision) {
    public boolean isActiveAt(Instant asOf) {
      return approved
          && !withdrawn
          && reviewedAt != null
          && !reviewedAt.isAfter(asOf)
          && (validFrom == null || !validFrom.isAfter(asOf))
          && (validUntil == null || asOf.isBefore(validUntil));
    }
  }

  public record Decision(Status status, UUID selectedRecordId, String reason) {}

  public Decision decide(List<Candidate> candidates, Instant asOf) {
    if (candidates.stream().map(c -> List.of(c.scopeId(), c.ingredientId())).distinct().count()
        > 1) {
      throw new IllegalArgumentException("동일 품목·용도와 식재료의 기록만 함께 판정할 수 있습니다.");
    }
    var active = candidates.stream().filter(c -> c.isActiveAt(asOf)).toList();
    if (active.isEmpty()) {
      boolean expired =
          candidates.stream()
              .anyMatch(
                  c ->
                      c.approved()
                          && !c.withdrawn()
                          && c.reviewedAt() != null
                          && !c.reviewedAt().isAfter(asOf)
                          && c.validUntil() != null
                          && !asOf.isBefore(c.validUntil()));
      return new Decision(
          expired ? Status.EXPIRED : Status.UNAVAILABLE,
          null,
          expired ? "유효기간이 지난 정보입니다." : "현재 공개할 원산지 정보가 없습니다.");
    }
    if (active.stream().map(Candidate::value).distinct().count() > 1) {
      return new Decision(Status.DISPUTED, null, "서로 다른 원산지 기록이 있어 검토가 필요합니다.");
    }
    var latest =
        Comparator.comparing(
                Candidate::observedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                Candidate::sourceUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingLong(Candidate::revision)
            .thenComparing(candidate -> candidate.id().toString());
    var selected = active.stream().max(latest).orElseThrow();
    return new Decision(Status.CURRENT, selected.id(), "동일 품목·용도의 유효한 승인 기록입니다.");
  }
}
