package kr.kimchimap.restaurant.dto;

import java.time.Instant;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import kr.kimchimap.restaurant.entity.NormalizedCoordinate;

public record ImportedRestaurant(
    String externalId,
    String name,
    String address,
    String businessStatus,
    String originalStatus,
    NormalizedCoordinate coordinate,
    String originalX,
    String originalY,
    BusinessPhone phone,
    String originalPhone,
    Instant sourceUpdatedAt,
    Instant sourceModifiedAt,
    String contentHash) {}
