package kr.kimchimap.origin.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.origin.dto.OriginPublicationRequest;
import kr.kimchimap.origin.entity.OriginValue;
import kr.kimchimap.origin.repository.OriginWriteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OriginPublicationService {
  private final OriginWriteRepository origins;
  private final OriginPublicationPolicy policy;
  private final Clock clock;

  public OriginPublicationService(
      OriginWriteRepository origins, OriginPublicationPolicy policy, Clock clock) {
    this.origins = origins;
    this.policy = policy;
    this.clock = clock;
  }

  @Transactional
  public UUID approve(OriginPublicationRequest input, UUID actor) {
    UUID scope =
        input.scopeId() == null
            ? origins.createScope(input.restaurantId(), input.scopeName(), input.usage())
            : origins
                .lockScope(input.scopeId(), input.restaurantId(), input.usage())
                .orElseThrow(
                    () ->
                        new ApiException(
                            HttpStatus.BAD_REQUEST, "INVALID_SCOPE", "업소에 해당하는 메뉴·용도를 선택해 주세요."));
    var now = clock.instant();
    UUID evidence = origins.evidence(input);
    for (var assertion : input.assertions()) {
      origins.insert(input.restaurantId(), scope, evidence, assertion, input.observedAt(), now);
      republish(scope, assertion.ingredientId(), actor);
    }
    return scope;
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void republish(UUID scope, UUID ingredient, UUID actor) {
    origins.lockPublicationScope(scope);
    var now = clock.instant();
    var revisions = origins.revisions(scope, ingredient);
    if (revisions.size() > 2000) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "ORIGIN_HISTORY_LIMIT",
          "원산지 이력이 많아 자동 공개 판정을 중단했습니다. 관리자의 이력 정리가 필요합니다.");
    }
    var components =
        origins.components(scope, ingredient).stream()
            .collect(Collectors.groupingBy(OriginWriteRepository.Part::recordId));
    var candidates =
        revisions.stream()
            .map(
                row ->
                    new OriginPublicationPolicy.Candidate(
                        row.id(),
                        row.scopeId(),
                        row.ingredientId(),
                        new OriginValue(
                            OriginValue.Classification.valueOf(row.classification()),
                            components.getOrDefault(row.id(), List.of()).stream()
                                .map(
                                    part ->
                                        new OriginValue.Component(
                                            OriginValue.Classification.valueOf(part.originKind()),
                                            part.countryCode(),
                                            part.ratio()))
                                .toList()),
                        row.observedAt(),
                        row.sourceUpdatedAt(),
                        row.reviewedAt(),
                        row.validFrom(),
                        row.validUntil(),
                        true,
                        row.withdrawn(),
                        row.revision()))
            .toList();
    var decision = policy.decide(candidates, now);
    String status =
        decision.status() == OriginPublicationPolicy.Status.EXPIRED
            ? "UNAVAILABLE"
            : decision.status().name();
    origins.publish(
        scope,
        ingredient,
        decision.selectedRecordId(),
        status,
        decision.reason(),
        OriginPublicationPolicy.VERSION,
        actor,
        now);
  }
}
