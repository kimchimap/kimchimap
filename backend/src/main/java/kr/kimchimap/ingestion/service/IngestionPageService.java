package kr.kimchimap.ingestion.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import kr.kimchimap.ingestion.client.PublicDataRestaurantClient.Page;
import kr.kimchimap.ingestion.client.SourceFailure;
import kr.kimchimap.ingestion.dto.IngestionJob;
import kr.kimchimap.ingestion.repository.IngestionIssueRepository;
import kr.kimchimap.ingestion.repository.IngestionJobRepository;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import kr.kimchimap.restaurant.entity.NormalizedCoordinate.Status;
import kr.kimchimap.restaurant.service.RestaurantImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPageService {
  public record PreparedRow(int index, ImportedRestaurant restaurant, String error) {}

  private final IngestionJobRepository jobs;
  private final IngestionIssueRepository issues;
  private final RestaurantSourceNormalizer normalizer;
  private final RestaurantImportService restaurants;
  private final Clock clock;

  public IngestionPageService(
      IngestionJobRepository jobs,
      IngestionIssueRepository issues,
      RestaurantSourceNormalizer normalizer,
      RestaurantImportService restaurants,
      Clock clock) {
    this.jobs = jobs;
    this.issues = issues;
    this.normalizer = normalizer;
    this.restaurants = restaurants;
    this.clock = clock;
  }

  public List<PreparedRow> prepare(IngestionJob job, Page page) {
    var rows = new ArrayList<PreparedRow>();
    for (int index = 0; index < page.items().size(); index++) {
      if (index % 20 == 0) jobs.renewLease(job);
      try {
        rows.add(new PreparedRow(index, normalizer.normalize(page.items().get(index)), null));
      } catch (IllegalArgumentException exception) {
        String code =
            java.util.Set.of("SOURCE_REQUIRED_FIELD_INVALID", "SOURCE_DATE_INVALID")
                    .contains(exception.getMessage())
                ? exception.getMessage()
                : "SOURCE_RECORD_INVALID";
        rows.add(new PreparedRow(index, null, code));
      }
    }
    return List.copyOf(rows);
  }

  @Transactional(timeout = 30)
  public void commit(IngestionJob job, Page page, List<PreparedRow> rows) {
    jobs.lockLease(job);
    if (job.totalCount() != null && job.totalCount() != page.totalCount())
      throw new SourceFailure("SOURCE_TOTAL_CHANGED", false, null);
    int changed = 0;
    int quarantined = 0;
    for (var row : rows) {
      if (row.error() != null) {
        issues.quarantine(job.id(), page.number(), row.index(), null, row.error());
        quarantined++;
        continue;
      }
      var item = row.restaurant();
      var result = restaurants.apply(job.sourceId(), item, job.id(), clock.instant());
      if (result.changed()) changed++;
      if (result.issue() != null) issues.issue(job.id(), result.restaurantId(), result.issue());
      for (var candidate : result.matchCandidates())
        issues.match(job.sourceId(), item.externalId(), candidate, job.id());
      if (!result.matchCandidates().isEmpty()) {
        issues.quarantine(
            job.id(), page.number(), row.index(), item.externalId(), "MATCH_REVIEW_REQUIRED");
        quarantined++;
      }
      if (item.coordinate().status() != Status.VERIFIED)
        issues.issue(
            job.id(), result.restaurantId(), "COORDINATE_" + item.coordinate().status().name());
      if (item.phone() == null && item.originalPhone() != null && !item.originalPhone().isBlank())
        issues.issue(job.id(), result.restaurantId(), "PHONE_FORMAT_INVALID");
    }
    boolean last = (long) page.number() * page.pageSize() >= page.totalCount();
    jobs.completePage(job, page.totalCount(), page.items().size(), changed, quarantined, last);
  }
}
