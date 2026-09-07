package kr.kimchimap.ingestion.service;

import java.util.List;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.ingestion.dto.MatchAdministration;
import kr.kimchimap.ingestion.repository.MatchReviewRepository;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import kr.kimchimap.restaurant.service.RestaurantImportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MatchReviewService {
  private final MatchReviewRepository repository;
  private final RestaurantImportService restaurants;
  private final ObjectMapper json;

  public MatchReviewService(
      MatchReviewRepository repository, RestaurantImportService restaurants, ObjectMapper json) {
    this.repository = repository;
    this.restaurants = restaurants;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public List<MatchAdministration.Item> recent() {
    return repository.recent();
  }

  @Transactional(readOnly = true)
  public MatchAdministration.Detail detail(UUID id) {
    return view(repository.find(id, false).orElseThrow(MatchReviewService::missing));
  }

  @Transactional
  public MatchAdministration.Detail review(UUID actor, UUID id, MatchAdministration.Review input) {
    var before = repository.find(id, false).orElseThrow(MatchReviewService::missing);
    repository.lockJob(before.jobId());
    restaurants.lockExternal(before.sourceId(), before.externalId());
    var row = repository.find(id, true).orElseThrow(MatchReviewService::missing);
    if (!row.jobId().equals(before.jobId())
        || row.version() != input.expectedVersion()
        || !row.state().equals("PENDING"))
      throw new ApiException(
          HttpStatus.CONFLICT, "MATCH_VERSION_CONFLICT", "매칭 관찰본이나 검토 상태가 변경되었습니다. 다시 확인해 주세요.");
    if (!repository.sourceAllowed(row.sourceId()))
      throw new ApiException(
          HttpStatus.CONFLICT, "SOURCE_PERMISSION_REQUIRED", "소스의 수집·저장·재게시 이용 조건을 확인해 주세요.");
    boolean matched = input.decision().equals("MATCHED");
    if (matched
            && (input.restaurantId() == null
                || repository.candidates(row.sourceId(), row.externalId()).stream()
                    .noneMatch(candidate -> candidate.id().equals(input.restaurantId())))
        || !matched && input.restaurantId() != null)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_MATCH_TARGET", "확인한 후보 업소 또는 별도 업소 생성을 선택해 주세요.");
    var observation = json.readValue(row.observation(), ImportedRestaurant.class);
    UUID target =
        restaurants.resolveMatch(
            row.sourceId(), observation, input.restaurantId(), row.jobId(), row.observedAt());
    repository.resolve(row, target, actor, input);
    return detail(id);
  }

  private MatchAdministration.Detail view(MatchReviewRepository.Row row) {
    var item = json.readValue(row.observation(), ImportedRestaurant.class);
    return new MatchAdministration.Detail(
        row.id(),
        row.sourceId(),
        row.sourceName(),
        row.externalId(),
        item.name(),
        item.address(),
        item.coordinate().latitude(),
        item.coordinate().longitude(),
        item.coordinate().status().name(),
        row.observedAt(),
        item.sourceUpdatedAt(),
        row.version(),
        row.state(),
        row.restaurantId(),
        repository.candidates(row.sourceId(), row.externalId()));
  }

  private static ApiException missing() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "MATCH_OBSERVATION_NOT_FOUND",
        "검토 가능한 수집 관찰본이 없습니다. 다음 허용 수집에서 갱신해 주세요.");
  }
}
