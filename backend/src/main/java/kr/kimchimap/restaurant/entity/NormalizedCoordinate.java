package kr.kimchimap.restaurant.entity;

public record NormalizedCoordinate(
    Double latitude, Double longitude, Status status, String reason) {
  public enum Status {
    VERIFIED,
    MISSING,
    INVALID,
    REVIEW_REQUIRED
  }
}
