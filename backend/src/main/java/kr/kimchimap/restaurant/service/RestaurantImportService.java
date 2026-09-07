package kr.kimchimap.restaurant.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import kr.kimchimap.restaurant.repository.RestaurantImportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantImportService {
  public record Result(
      UUID restaurantId, boolean changed, String issue, List<UUID> matchCandidates) {}

  private final RestaurantImportRepository repository;

  public RestaurantImportService(RestaurantImportRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Result apply(UUID source, ImportedRestaurant row, UUID job, Instant fetchedAt) {
    repository.lockExternal(source, row.externalId());
    var existing = repository.find(source, row.externalId());
    if (existing == null) {
      var matches = repository.possibleMatches(row);
      if (!matches.isEmpty()) return new Result(null, false, "MATCH_REVIEW_REQUIRED", matches);
      UUID id = UUID.randomUUID();
      repository.create(id, source, row);
      repository.recordObservation(source, row, job, fetchedAt, false);
      return new Result(id, true, null, List.of());
    }
    boolean stale =
        existing.sourceUpdatedAt() != null
            && (row.sourceUpdatedAt() == null
                || row.sourceUpdatedAt().isBefore(existing.sourceUpdatedAt()));
    boolean changed = !stale && !row.contentHash().equals(existing.contentHash());
    if (changed) repository.update(existing.id(), source, row);
    repository.recordObservation(source, row, job, fetchedAt, stale);
    String issue =
        stale
            ? "SOURCE_STALE_VERSION"
            : changed && repository.hasOverride(existing.id()) ? "MANUAL_OVERRIDE_PRESERVED" : null;
    return new Result(existing.id(), changed, issue, List.of());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockExternal(UUID source, String external) {
    repository.lockExternal(source, external);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID resolveMatch(
      UUID source, ImportedRestaurant row, UUID target, UUID job, Instant observedAt) {
    repository.lockExternal(source, row.externalId());
    if (repository.find(source, row.externalId()) != null)
      throw new ApiException(
          HttpStatus.CONFLICT, "EXTERNAL_ID_ALREADY_LINKED", "외부 식별자가 이미 연결되었습니다. 최신 상태를 확인해 주세요.");
    UUID id = target == null ? UUID.randomUUID() : target;
    if (target == null) repository.create(id, source, row);
    else {
      if (!repository.canAttach(target, source))
        throw new ApiException(
            HttpStatus.CONFLICT,
            "RESTAURANT_SOURCE_CONFLICT",
            "대상 업소에 같은 출처의 다른 식별자가 있거나 공개 업소가 아닙니다.");
      repository.attach(id, source, row);
      repository.update(id, source, row);
    }
    repository.recordObservation(source, row, job, observedAt, false);
    return id;
  }
}
