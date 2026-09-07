package kr.kimchimap.ingestion.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("contract")
class PublicDataRestaurantClientTest {
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void parsesOfficialEnvelopeWhitelistsFieldsAndEncodesDecodedKeyOnce() throws Exception {
    var uri = new AtomicReference<URI>();
    byte[] data = fixture();
    var client =
        client(
            request -> {
              uri.set(request);
              return new SourceTransport.Response(200, Map.of(), data);
            });
    var page = client.fetch(1, 2, NOW.minus(Duration.ofDays(7)), NOW);
    assertThat(page.totalCount()).isEqualTo(3);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items().getFirst().get("TELNO")).isEqualTo("02-0000-0000");
    assertThat(page.items().getFirst().fields()).doesNotContainKey("NOT_FOR_STORAGE");
    assertThat(uri.get().getHost()).isEqualTo("apis.data.go.kr");
    assertThat(uri.get().getRawQuery())
        .contains("serviceKey=test%2B%2F%3Dkey", "cond%5BDAT_UPDT_PNT%3A%3AGTE%5D=20260831090000")
        .doesNotContain("%252B");
  }

  @Test
  void paginationMismatchPartialPageAndSchemaDriftDoNotAdvanceSilently() throws Exception {
    String data = new String(fixture(), StandardCharsets.UTF_8);
    assertThatThrownBy(() -> client(response(200, data)).fetch(2, 2, null, NOW))
        .isInstanceOf(SourceFailure.class)
        .hasMessageContaining("SOURCE_PAGE_MISMATCH");
    assertThatThrownBy(
            () ->
                client(response(200, data.replace("\"totalCount\": 3", "\"totalCount\": \"3\"")))
                    .fetch(1, 2, null, NOW))
        .hasMessageContaining("SOURCE_SCHEMA_CHANGED");
    assertThatThrownBy(
            () ->
                client(
                        response(
                            200,
                            "{\"response\":{\"header\":{\"resultCode\":\"0\"},\"body\":{\"pageNo\":1,\"numOfRows\":2,\"totalCount\":3,\"items\":{\"item\":[{}]}}}}"))
                    .fetch(1, 2, null, NOW))
        .hasMessageContaining("SOURCE_PAGE_INCOMPLETE");
  }

  @Test
  void retryAfterAndAuthenticationErrorsAreClassifiedWithoutRawResponseLeak() {
    var rate =
        client(uri -> new SourceTransport.Response(429, Map.of("retry-after", "120"), new byte[0]));
    assertThatThrownBy(() -> rate.fetch(1, 2, null, NOW))
        .isInstanceOfSatisfying(
            SourceFailure.class,
            e -> {
              assertThat(e.retryable()).isTrue();
              assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(120));
            });
    var dated =
        client(
            uri ->
                new SourceTransport.Response(
                    429, Map.of("retry-after", "Mon, 7 Sep 2026 00:02:00 GMT"), new byte[0]));
    assertThatThrownBy(() -> dated.fetch(1, 2, null, NOW))
        .isInstanceOfSatisfying(
            SourceFailure.class,
            e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(120)));
    assertThatThrownBy(
            () -> client(response(403, "private-key-in-response")).fetch(1, 2, null, NOW))
        .hasMessageContaining("SOURCE_HTTP_403")
        .hasMessageNotContaining("private-key");
    assertThatThrownBy(() -> client(response(500, "backend failure")).fetch(1, 2, null, NOW))
        .isInstanceOfSatisfying(SourceFailure.class, e -> assertThat(e.retryable()).isTrue());
    assertThatThrownBy(
            () ->
                client(
                        response(
                            200,
                            "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"private-key\"}}}"))
                    .fetch(1, 2, null, NOW))
        .hasMessageContaining("SOURCE_RESULT_30")
        .hasMessageNotContaining("private-key");
  }

  @Test
  void missingKeyMakesNoRequestAndTransportRejectsUnapprovedTargets() {
    var client =
        new PublicDataRestaurantClient(
            uri -> {
              throw new AssertionError("호출하면 안 됨");
            },
            mapper,
            clock,
            "");
    assertThatThrownBy(() -> client.fetch(1, 2, null, NOW))
        .hasMessageContaining("SOURCE_KEY_MISSING");
    var transport = new PublicDataTransport();
    assertThatThrownBy(() -> transport.get(URI.create("http://127.0.0.1/admin")))
        .hasMessageContaining("SOURCE_URL_NOT_ALLOWED");
    assertThatThrownBy(() -> transport.get(URI.create("https://apis.data.go.kr/other")))
        .hasMessageContaining("SOURCE_URL_NOT_ALLOWED");
  }

  private byte[] fixture() throws Exception {
    try (var stream = getClass().getResourceAsStream("/contracts/public-data/page.json")) {
      return stream.readAllBytes();
    }
  }

  private SourceTransport response(int status, String body) {
    return uri ->
        new SourceTransport.Response(status, Map.of(), body.getBytes(StandardCharsets.UTF_8));
  }

  private PublicDataRestaurantClient client(SourceTransport transport) {
    return new PublicDataRestaurantClient(transport, mapper, clock, "test+/=key");
  }
}
