package kr.kimchimap.restaurant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RestaurantDetail(
    UUID id,
    String name,
    String address,
    String businessStatus,
    Double latitude,
    Double longitude,
    String coordinateStatus,
    Contact contact,
    List<Scope> scopes,
    List<Designation> designations,
    Instant asOf,
    String informationNotice) {
  public record Contact(String display, String number, String sourceName) {}

  public record Scope(UUID id, String name, String usage, String precision, List<Origin> origins) {}

  public record Origin(
      UUID ingredientId,
      String ingredientName,
      String status,
      String reason,
      UUID selectedRecordId,
      List<Claim> claims) {}

  public record Claim(
      UUID id,
      String classification,
      List<Component> components,
      String originalExpression,
      String evidenceKind,
      String sourceName,
      String publicReference,
      String publicSummary,
      Instant observedAt,
      String observedPrecision,
      Instant sourceUpdatedAt,
      String sourceUpdatedPrecision,
      Instant collectedAt,
      Instant lastFetchSucceededAt,
      Instant reviewedAt,
      Instant validFrom,
      Instant validUntil,
      String freshness) {}

  public record Component(String kind, String countryCode, String countryName, BigDecimal ratio) {}

  public record Designation(
      UUID id,
      String schemeName,
      String applicableItems,
      String criteriaOriginal,
      String sourceName,
      LocalDate designatedOn,
      LocalDate expiresOn,
      LocalDate cancelledOn) {}
}
