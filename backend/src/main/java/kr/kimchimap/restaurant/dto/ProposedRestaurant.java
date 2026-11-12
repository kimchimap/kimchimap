package kr.kimchimap.restaurant.dto;

import jakarta.validation.constraints.*;

public record ProposedRestaurant(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 500) String address,
    @Size(max = 64) String phone,
    @NotNull @DecimalMin("32") @DecimalMax("39.5") Double latitude,
    @NotNull @DecimalMin("124") @DecimalMax("132") Double longitude) {}
