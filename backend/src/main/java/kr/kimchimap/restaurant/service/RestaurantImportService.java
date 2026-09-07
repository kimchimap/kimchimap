package kr.kimchimap.restaurant.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import kr.kimchimap.restaurant.repository.RestaurantImportRepository;
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
}
