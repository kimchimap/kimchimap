package kr.kimchimap.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchRequest.Center;
import kr.kimchimap.search.service.SearchCursorService;
import kr.kimchimap.search.service.SearchCursorService.Position;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class SearchCursorServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
  private final SearchCursorService cursors = service(NOW);

  @Test
  void signedCursorPreservesExactPositionAndRejectsTamperingAndDifferentFilters() {
    var position = new Position(12.12345678, UUID.randomUUID(), NOW);
    var encoded = cursors.encode(request(100d, null), position);
    assertThat(cursors.decode(request(100d, encoded))).isEqualTo(position);
    var parts = encoded.split("\\.");
    var tampered = (parts[0].startsWith("A") ? "B" : "A") + parts[0].substring(1) + "." + parts[1];
    assertThatThrownBy(() -> cursors.decode(request(100d, tampered)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> cursors.decode(request(101d, encoded)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service(NOW.plusSeconds(601)).decode(request(100d, encoded)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void productionCannotStartWithTemporaryOrWeakSigningKey() {
    var env = new MockEnvironment();
    env.setActiveProfiles("prod");
    assertThatThrownBy(
            () ->
                new SearchCursorService(
                    JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC(), env, ""))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new SearchCursorService(
                    JsonMapper.builder().findAndAddModules().build(),
                    Clock.systemUTC(),
                    env,
                    "short"))
        .isInstanceOf(IllegalStateException.class);
  }

  private SearchCursorService service(Instant now) {
    return new SearchCursorService(
        JsonMapper.builder().findAndAddModules().build(),
        Clock.fixed(now, ZoneOffset.UTC),
        new MockEnvironment(),
        "test-cursor-key-with-at-least-32-bytes");
  }

  private SearchRequest request(double radius, String cursor) {
    return new SearchRequest(new Center(37.5, 127), null, radius, null, null, null, 20, cursor);
  }
}
