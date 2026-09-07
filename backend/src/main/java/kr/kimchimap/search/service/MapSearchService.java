package kr.kimchimap.search.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchResponse;
import kr.kimchimap.search.dto.SearchResponse.Item;
import kr.kimchimap.search.repository.MapSearchRepository;
import kr.kimchimap.search.repository.MapSearchRepository.MatchRow;
import kr.kimchimap.search.repository.MapSearchRepository.PageBoundary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MapSearchService {
  private final MapSearchRepository repository;
  private final SearchCursorService cursors;
  private final Clock clock;

  public MapSearchService(
      MapSearchRepository repository, SearchCursorService cursors, Clock clock) {
    this.repository = repository;
    this.cursors = cursors;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public SearchResponse search(SearchRequest request) {
    var position = cursors.decode(request);
    var asOf = position == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : position.asOf();
    if (request.bounds() != null && request.bounds().tooWide()) {
      return new SearchResponse(List.of(), null, true, true, asOf, "검색할 지역을 더 확대해 주세요.");
    }
    var rows =
        repository.search(
            request,
            asOf,
            position == null ? null : new PageBoundary(position.distanceMeters(), position.id()));
    boolean more = rows.size() > request.limit();
    var page = rows.stream().limit(request.limit()).toList();
    var matches =
        repository.matchedScopes(
            request, asOf, page.stream().map(MapSearchRepository.Row::id).toList());
    if (matches.size() > 2000)
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "MATCH_LIMIT_EXCEEDED",
          "일치하는 품목이 많습니다. 검색 조건을 좁혀 주세요.");
    var byRestaurant = matches.stream().collect(Collectors.groupingBy(MatchRow::restaurantId));
    var items =
        page.stream()
            .map(
                row ->
                    new Item(
                        row.id(),
                        row.name(),
                        row.address(),
                        row.latitude(),
                        row.longitude(),
                        row.businessStatus(),
                        row.distanceMeters(),
                        byRestaurant.getOrDefault(row.id(), List.of()).stream()
                            .map(MatchRow::toDto)
                            .toList()))
            .toList();
    String next = null;
    if (more) {
      var last = page.getLast();
      next =
          cursors.encode(
              request, new SearchCursorService.Position(last.distanceMeters(), last.id(), asOf));
    }
    return new SearchResponse(
        items, next, more, false, asOf, more ? "일부 결과입니다. 다음 결과를 불러올 수 있습니다." : "검색 조건에 맞는 결과입니다.");
  }
}
