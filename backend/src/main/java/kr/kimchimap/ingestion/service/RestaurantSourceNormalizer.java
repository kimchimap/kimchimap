package kr.kimchimap.ingestion.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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
    String externalId = row.get("externalId");
    String name = row.get("name");
    String address = row.get("address");
    if (externalId.isEmpty()
        || externalId.length() > 200
        || name.isEmpty()
        || name.length() > 200
        || address.isEmpty()
        || address.length() > 2000) {
      throw new IllegalArgumentException("SOURCE_REQUIRED_FIELD_INVALID");
    }
    String state =
        switch (row.get("businessStatus")) {
          case "OPEN" -> "OPEN";
          case "CLOSED" -> "CLOSED";
          default -> "UNKNOWN";
        };
    int sourceSrid = srid(row.get("coordinateSrid"));
    var coordinate = coordinates.normalize(number(row.get("x")), number(row.get("y")), sourceSrid);
    String phone = row.get("phone");
    return new ImportedRestaurant(
        externalId,
        name,
        address,
        state,
        row.get("originalStatus"),
        coordinate,
        row.get("x"),
        row.get("y"),
        sourceSrid,
        BusinessPhone.parse(phone),
        phone.length() <= 64 ? phone : null,
        date(row.get("sourceUpdatedAt")),
        date(row.get("sourceModifiedAt")),
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
    try {
      return Instant.parse(value);
    } catch (java.time.format.DateTimeParseException exception) {
      throw new IllegalArgumentException("SOURCE_DATE_INVALID");
    }
  }

  private int srid(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("SOURCE_COORDINATE_SYSTEM_INVALID");
    }
  }

  private String hash(SourceRestaurant row) {
    var fields = new TreeMap<>(row.fields());
    fields.remove("sourceUpdatedAt");
    fields.remove("sourceModifiedAt");
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
