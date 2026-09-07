package kr.kimchimap.ingestion.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import kr.kimchimap.ingestion.dto.SourceRestaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicDataRestaurantClient {
  public record Page(int number, int pageSize, long totalCount, List<SourceRestaurant> items) {}

  private static final Set<String> FIELDS =
      Set.of(
          "OPN_ATMY_GRP_CD",
          "MNG_NO",
          "BPLC_NM",
          "ROAD_NM_ADDR",
          "LOTNO_ADDR",
          "SALS_STTS_CD",
          "SALS_STTS_NM",
          "DTL_SALS_STTS_CD",
          "DTL_SALS_STTS_NM",
          "CRD_INFO_X",
          "CRD_INFO_Y",
          "DAT_UPDT_PNT",
          "LAST_MDFCN_PNT",
          "CLSBIZ_YMD",
          "TELNO");
  private static final DateTimeFormatter SOURCE_TIME =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneId.of("Asia/Seoul"));
  private final SourceTransport transport;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final String serviceKey;

  public PublicDataRestaurantClient(
      SourceTransport transport,
      ObjectMapper mapper,
      Clock clock,
      @Value("${app.ingestion.public-data-key:}") String serviceKey) {
    this.transport = transport;
    this.mapper = mapper;
    this.clock = clock;
    this.serviceKey = serviceKey;
  }

  public Page fetch(int page, int pageSize, Instant since, Instant until) {
    if (serviceKey.isBlank()) throw new SourceFailure("SOURCE_KEY_MISSING", false, null);
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("수집 페이지 범위를 확인해 주세요.");
    var params = new LinkedHashMap<String, String>();
    params.put("serviceKey", serviceKey);
    params.put("pageNo", Integer.toString(page));
    params.put("numOfRows", Integer.toString(pageSize));
    params.put("returnType", "json");
    if (since != null) params.put("cond[DAT_UPDT_PNT::GTE]", SOURCE_TIME.format(since));
    if (until != null) params.put("cond[DAT_UPDT_PNT::LT]", SOURCE_TIME.format(until));
    String query =
        params.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    var response =
        transport.get(
            URI.create("https://apis.data.go.kr/1741000/general_restaurants/info?" + query));
    if (response.status() == 429)
      throw new SourceFailure(
          "SOURCE_RATE_LIMITED", true, retryAfter(response.headers().get("retry-after")));
    if (response.status() >= 500) throw new SourceFailure("SOURCE_SERVER_ERROR", true, null);
    if (response.status() != 200)
      throw new SourceFailure("SOURCE_HTTP_" + response.status(), false, null);
    try {
      var root = mapper.readTree(response.body()).path("response");
      var header = root.path("header");
      if (!header.path("resultCode").isString())
        throw new SourceFailure("SOURCE_SCHEMA_CHANGED", false, null);
      String code = header.path("resultCode").asString();
      if (!code.equals("0"))
        throw new SourceFailure(
            "SOURCE_RESULT_" + safeCode(code),
            Set.of("01", "05", "22", "23", "-5", "-10").contains(code),
            null);
      var body = root.path("body");
      if (!body.path("totalCount").isIntegralNumber()
          || !body.path("pageNo").isIntegralNumber()
          || !body.path("numOfRows").isIntegralNumber())
        throw new SourceFailure("SOURCE_SCHEMA_CHANGED", false, null);
      long total = body.path("totalCount").asLong();
      if (total < 0
          || body.path("pageNo").asInt() != page
          || body.path("numOfRows").asInt() != pageSize)
        throw new SourceFailure("SOURCE_PAGE_MISMATCH", false, null);
      var rows = body.path("items").path("item");
      if (total == 0 && (rows.isMissingNode() || rows.isNull() || rows.isArray() && rows.isEmpty()))
        return new Page(page, pageSize, 0, List.of());
      if (!rows.isArray()
          || rows.size() > pageSize
          || rows.isEmpty() && (long) (page - 1) * pageSize < total)
        throw new SourceFailure("SOURCE_SCHEMA_CHANGED", false, null);
      long expected = Math.max(0, Math.min(pageSize, total - (long) (page - 1) * pageSize));
      if (rows.size() != expected) throw new SourceFailure("SOURCE_PAGE_INCOMPLETE", true, null);
      var items = new ArrayList<SourceRestaurant>();
      for (var row : rows) {
        if (!row.isObject()) throw new SourceFailure("SOURCE_SCHEMA_CHANGED", false, null);
        var fields = new LinkedHashMap<String, String>();
        for (String name : FIELDS) {
          var value = row.path(name);
          if (!value.isMissingNode() && !value.isNull() && !value.isString())
            throw new SourceFailure("SOURCE_SCHEMA_CHANGED", false, null);
          fields.put(name, value.isString() ? value.asString() : "");
        }
        items.add(new SourceRestaurant(fields));
      }
      return new Page(page, pageSize, total, List.copyOf(items));
    } catch (SourceFailure exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new SourceFailure("SOURCE_RESPONSE_INVALID", false, null);
    }
  }

  private String safeCode(String code) {
    String safe = code.replaceAll("[^A-Za-z0-9_-]", "");
    return safe.substring(0, Math.min(20, safe.length()));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private Duration retryAfter(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      long seconds =
          value.matches("[0-9]+")
              ? Long.parseLong(value)
              : Duration.between(
                      clock.instant(),
                      ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                  .toSeconds();
      return Duration.ofSeconds(Math.max(0, seconds));
    } catch (RuntimeException exception) {
      return null;
    }
  }
}
