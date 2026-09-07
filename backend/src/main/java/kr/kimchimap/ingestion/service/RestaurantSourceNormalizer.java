package kr.kimchimap.ingestion.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HexFormat;
import java.util.TreeMap;
import kr.kimchimap.ingestion.dto.SourceRestaurant;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import kr.kimchimap.restaurant.service.CoordinateNormalizationService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RestaurantSourceNormalizer {
  private final CoordinateNormalizationService coordinates;
  private final ObjectMapper mapper;

  public RestaurantSourceNormalizer(
      CoordinateNormalizationService coordinates, ObjectMapper mapper) {
    this.coordinates = coordinates;
    this.mapper = mapper;
  }

  public ImportedRestaurant normalize(SourceRestaurant row) {
    String municipality = row.get("OPN_ATMY_GRP_CD");
    String management = row.get("MNG_NO");
    String name = row.get("BPLC_NM");
    String address =
        row.get("ROAD_NM_ADDR").isEmpty() ? row.get("LOTNO_ADDR") : row.get("ROAD_NM_ADDR");
    if (municipality.isEmpty()
        || management.isEmpty()
        || municipality.length() + management.length() > 198
        || name.isEmpty()
        || name.length() > 200
        || address.isEmpty()
        || address.length() > 2000) {
      throw new IllegalArgumentException("SOURCE_REQUIRED_FIELD_INVALID");
    }
    String state =
        switch (row.get("SALS_STTS_NM")) {
          case "영업/정상" -> "OPEN";
          case "폐업" -> "CLOSED";
          default -> "UNKNOWN";
        };
    var coordinate =
        coordinates.normalize(number(row.get("CRD_INFO_X")), number(row.get("CRD_INFO_Y")), 5174);
    String phone = row.get("TELNO");
    return new ImportedRestaurant(
        municipality + ":" + management,
        name,
        address,
        state,
        row.get("SALS_STTS_CD") + ":" + row.get("SALS_STTS_NM"),
        coordinate,
        row.get("CRD_INFO_X"),
        row.get("CRD_INFO_Y"),
        BusinessPhone.parse(phone),
        phone.length() <= 64 ? phone : null,
        date(row.get("DAT_UPDT_PNT")),
        date(row.get("LAST_MDFCN_PNT")),
        hash(row));
  }

  private Double number(String value) {
    if (value.isEmpty()) return null;
    try {
      return Double.valueOf(value);
    } catch (NumberFormatException exception) {
      return Double.NaN;
    }
  }

  private Instant date(String value) {
    if (value.isEmpty()) return null;
    for (String format : java.util.List.of("uuuuMMddHHmmss", "uuuu-MM-dd HH:mm:ss")) {
      try {
        return LocalDateTime.parse(
                value, DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT))
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant();
      } catch (java.time.format.DateTimeParseException exception) {
        /* 다음에 확인된 형식을 검사한다. */
      }
    }
    throw new IllegalArgumentException("SOURCE_DATE_INVALID");
  }

  private String hash(SourceRestaurant row) {
    var fields = new TreeMap<>(row.fields());
    fields.remove("DAT_UPDT_PNT");
    fields.remove("LAST_MDFCN_PNT");
    fields.replaceAll((name, value) -> value.strip());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
