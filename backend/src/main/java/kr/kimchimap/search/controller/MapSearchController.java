package kr.kimchimap.search.controller;

import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchResponse;
import kr.kimchimap.search.service.MapSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapSearchController {
  private final MapSearchService service;

  public MapSearchController(MapSearchService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/restaurants/search")
  public SearchResponse search(@RequestBody SearchRequest request) {
    return service.search(request);
  }
}
