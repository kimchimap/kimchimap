package kr.kimchimap.restaurant.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.entity.OriginValue.Classification;
import kr.kimchimap.origin.service.OriginPublicationPolicy;
import kr.kimchimap.origin.service.OriginPublicationPolicy.Candidate;
import kr.kimchimap.restaurant.dto.RestaurantDetail;
import kr.kimchimap.restaurant.dto.RestaurantDetail.Claim;
import kr.kimchimap.restaurant.dto.RestaurantDetail.Origin;
import kr.kimchimap.restaurant.dto.RestaurantDetail.Scope;
import kr.kimchimap.restaurant.repository.RestaurantReadRepository;
import kr.kimchimap.restaurant.repository.RestaurantReadRepository.ClaimRow;
import kr.kimchimap.restaurant.repository.RestaurantReadRepository.ComponentRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantQueryService {
  private final RestaurantReadRepository repository;
  private final OriginPublicationPolicy policy;
  private final Clock clock;

  public RestaurantQueryService(
      RestaurantReadRepository repository, OriginPublicationPolicy policy, Clock clock) {
    this.repository = repository;
    this.policy = policy;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RestaurantDetail detail(UUID id) {
    var asOf = clock.instant();
    var restaurant =
        repository
            .findPublished(id, asOf)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "공개된 업소를 찾을 수 없습니다."));
    var scopes = repository.findScopes(id);
    var claims = repository.findPublicClaims(id, asOf);
    var designations = repository.findDesignations(id);
    if (scopes.size() > 200 || claims.size() > 2000 || designations.size() > 200) {
      // 일부 기록만 판정하면 누락된 상충 정보가 국내산으로 오인될 수 있다.
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "DETAIL_LIMIT_EXCEEDED",
          "표시할 정보가 많아 상세 조회를 처리할 수 없습니다.");
    }
    var components =
        repository.findComponents(claims.stream().map(ClaimRow::id).toList()).stream()
            .collect(Collectors.groupingBy(ComponentRow::recordId));
    var byScope = claims.stream().collect(Collectors.groupingBy(ClaimRow::scopeId));
    var scopeDtos =
        scopes.stream()
            .map(
                scope ->
                    new Scope(
                        scope.id(),
                        scope.name(),
                        scope.usage(),
                        scope.precision(),
                        origins(byScope.getOrDefault(scope.id(), List.of()), components, asOf)))
            .toList();
    return new RestaurantDetail(
        restaurant.id(),
        restaurant.name(),
        restaurant.address(),
        restaurant.businessStatus(),
        restaurant.latitude(),
        restaurant.longitude(),
        restaurant.coordinateStatus(),
        restaurant.phoneNumber() == null
            ? null
            : new RestaurantDetail.Contact(
                restaurant.phoneDisplay(), restaurant.phoneNumber(), restaurant.phoneSourceName()),
        scopeDtos,
        designations,
        asOf,
        "원산지 표시는 실제 납품·위생·안전성 인증을 뜻하지 않습니다. 정보 없음은 수입산을 뜻하지 않습니다.");
  }

  private List<Origin> origins(
      List<ClaimRow> rows, Map<UUID, List<ComponentRow>> components, Instant asOf) {
    return rows.stream().collect(Collectors.groupingBy(ClaimRow::ingredientId)).entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry -> {
              var candidates =
                  entry.getValue().stream()
                      .map(row -> candidate(row, components.getOrDefault(row.id(), List.of())))
                      .toList();
              var decision = policy.decide(candidates, asOf);
              if (decision.status() == OriginPublicationPolicy.Status.CURRENT) {
                var publicIds =
                    entry.getValue().stream()
                        .filter(ClaimRow::publiclyVisible)
                        .map(ClaimRow::id)
                        .collect(Collectors.toSet());
                decision =
                    policy.decide(
                        candidates.stream().filter(c -> publicIds.contains(c.id())).toList(), asOf);
              }
              var claims =
                  entry.getValue().stream()
                      .filter(row -> !row.withdrawn() && row.publiclyVisible())
                      .map(row -> claim(row, components.getOrDefault(row.id(), List.of()), asOf))
                      .toList();
              return new Origin(
                  entry.getKey(),
                  entry.getValue().getFirst().ingredientName(),
                  decision.status().name(),
                  decision.reason(),
                  decision.selectedRecordId(),
                  claims);
            })
        .toList();
  }

  private Candidate candidate(ClaimRow row, List<ComponentRow> components) {
    var value =
        new OriginValue(
            Classification.valueOf(row.classification()),
            components.stream()
                .map(
                    c ->
                        new OriginValue.Component(
                            Classification.valueOf(c.kind()), c.countryCode(), c.ratio()))
                .toList());
    return new Candidate(
        row.id(),
        row.scopeId(),
        row.ingredientId(),
        value,
        row.observedAt(),
        row.sourceUpdatedAt(),
        row.reviewedAt(),
        row.validFrom(),
        row.validUntil(),
        true,
        row.withdrawn(),
        row.revision());
  }

  private Claim claim(ClaimRow row, List<ComponentRow> components, Instant asOf) {
    String freshness =
        row.observedAt() == null
            ? "UNKNOWN"
            : row.observedAt().isBefore(asOf.minus(Duration.ofDays(180))) ? "STALE" : "RECENT";
    return new Claim(
        row.id(),
        row.classification(),
        components.stream().map(ComponentRow::toDto).toList(),
        row.originalExpression(),
        row.evidenceKind(),
        row.sourceName(),
        row.publicReference(),
        row.publicSummary(),
        row.observedAt(),
        row.observedPrecision(),
        row.sourceUpdatedAt(),
        row.sourceUpdatedPrecision(),
        row.collectedAt(),
        row.lastFetchSucceededAt(),
        row.reviewedAt(),
        row.validFrom(),
        row.validUntil(),
        freshness);
  }
}
