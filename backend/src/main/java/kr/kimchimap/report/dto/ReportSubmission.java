package kr.kimchimap.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportSubmission(
    @NotNull UUID restaurantId,
    UUID scopeId,
    @NotBlank @Size(max = 200) String scopeName,
    @NotBlank @Pattern(regexp = "SIDE_DISH|STEW|MAIN_DISH|OTHER|UNKNOWN") String usage,
    @NotNull LocalDate observedOn,
    @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Claim> claims,
    @NotNull @Size(min = 1, max = 5) List<@NotNull UUID> mediaIds,
    @AssertTrue boolean publicationConsent) {
  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportClaim")
  public record Claim(
      @NotNull UUID ingredientId,
      @NotBlank @Pattern(regexp = "DOMESTIC|IMPORTED_SPECIFIED|IMPORTED_UNSPECIFIED|MIXED|UNKNOWN")
          String classification,
      @NotBlank @Size(max = 1000) String originalExpression,
      @NotNull @Size(max = 20) List<@NotNull @Valid Component> components) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "ReportOriginComponent")
  public record Component(
      @NotBlank @Pattern(regexp = "DOMESTIC|IMPORTED_SPECIFIED|IMPORTED_UNSPECIFIED") String kind,
      @Pattern(regexp = "[A-Z]{2}") String countryCode,
      @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("1")
          @Digits(integer = 1, fraction = 6)
          BigDecimal ratio) {}
}
