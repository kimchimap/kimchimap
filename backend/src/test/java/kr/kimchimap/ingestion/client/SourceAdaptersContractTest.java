package kr.kimchimap.ingestion.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.kimchimap.ingestion.dto.SourceRestaurant;
import kr.kimchimap.ingestion.service.RestaurantSourceNormalizer;
import kr.kimchimap.restaurant.entity.NormalizedCoordinate;
import kr.kimchimap.restaurant.service.CoordinateNormalizationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("contract")
class SourceAdaptersContractTest {
  private final UUID id = UUID.randomUUID();
  private final Instant until = Instant.parse("2026-11-12T00:00:00Z");

  @Test
  void missingAdapterFailsWithoutInventingAnEndpoint() {
    var registry = new SourceAdapters(List.of());
    assertThat(registry.available(id)).isFalse();
    assertThatThrownBy(() -> registry.fetch(id, 1, 20, null, until))
        .isInstanceOfSatisfying(
            SourceFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("SOURCE_ADAPTER_UNAVAILABLE"));
  }

  @Test
  void delegatesOnlyToRequestedRegisteredSource() {
    var adapter = adapter();
    var page = new SourceAdapter.Page(1, 20, 0, List.of());
    when(adapter.fetch(1, 20, null, until)).thenReturn(page);
    var registry = new SourceAdapters(List.of(adapter));
    assertThat(registry.available(id)).isTrue();
    assertThat(registry.fetch(id, 1, 20, null, until)).isSameAs(page);
    assertThatThrownBy(() -> registry.fetch(UUID.randomUUID(), 1, 20, null, until))
        .isInstanceOf(SourceFailure.class);
    verify(adapter, times(1)).fetch(1, 20, null, until);
  }

  @Test
  void duplicateSourceRegistrationFailsAtStartup() {
    var adapter = adapter();
    assertThatThrownBy(() -> new SourceAdapters(List.of(adapter, adapter)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void validatesBoundsBeforeCallingAndRejectsMismatchedPages() {
    var adapter = adapter();
    var registry = new SourceAdapters(List.of(adapter));
    assertThatThrownBy(() -> registry.fetch(id, 0, 20, null, until))
        .isInstanceOf(IllegalArgumentException.class);
    verify(adapter, never()).fetch(anyInt(), anyInt(), any(), any());
    for (var page :
        List.of(
            new SourceAdapter.Page(2, 20, 0, List.of()),
            new SourceAdapter.Page(1, 20, -1, List.of()),
            new SourceAdapter.Page(1, 20, 1, List.of()))) {
      when(adapter.fetch(1, 20, null, until)).thenReturn(page);
      assertThatThrownBy(() -> registry.fetch(id, 1, 20, null, until))
          .isInstanceOfSatisfying(
              SourceFailure.class,
              failure -> assertThat(failure.code()).isEqualTo("SOURCE_PAGE_MISMATCH"));
    }
  }

  @Test
  void preservesProviderFailureAndRetryDelayForTheWorker() {
    var adapter = adapter();
    var failure = new SourceFailure("SOURCE_RATE_LIMITED", true, Duration.ofSeconds(120));
    when(adapter.fetch(1, 20, null, until)).thenThrow(failure);
    assertThatThrownBy(() -> new SourceAdapters(List.of(adapter)).fetch(id, 1, 20, null, until))
        .isSameAs(failure);
  }

  @Test
  void normalizesOnlyInternalFieldsAndKeepsCoordinateSystemAndObservationDatesSeparate() {
    var coordinates = mock(CoordinateNormalizationService.class);
    when(coordinates.normalize(127d, 37d, 4326))
        .thenReturn(
            new NormalizedCoordinate(37d, 127d, NormalizedCoordinate.Status.VERIFIED, null));
    var normalizer = new RestaurantSourceNormalizer(coordinates, JsonMapper.builder().build());
    var fields =
        new java.util.HashMap<>(
            Map.of(
                "externalId",
                "test-source-id",
                "name",
                "가상 테스트 계약 업소",
                "address",
                "테스트 주소",
                "coordinateSrid",
                "4326",
                "x",
                "127",
                "y",
                "37",
                "sourceUpdatedAt",
                "2026-11-11T00:00:00Z"));
    var first = normalizer.normalize(new SourceRestaurant(fields));
    assertThat(first.originalSrid()).isEqualTo(4326);
    assertThat(first.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-11-11T00:00:00Z"));
    fields.put("sourceUpdatedAt", "2026-11-12T00:00:00Z");
    assertThat(normalizer.normalize(new SourceRestaurant(fields)).contentHash())
        .isEqualTo(first.contentHash());
    fields.put("sourceUpdatedAt", "날짜 아님");
    assertThatThrownBy(() -> normalizer.normalize(new SourceRestaurant(fields)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownPayloadFieldsAreRejectedInsteadOfStoringUnneededProviderData() {
    assertThatThrownBy(
            () -> new SourceRestaurant(Map.of("representativePersonalPhone", "test-private")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SourceAdapter adapter() {
    var adapter = mock(SourceAdapter.class);
    when(adapter.sourceId()).thenReturn(id);
    return adapter;
  }
}
