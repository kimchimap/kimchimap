package kr.kimchimap.restaurant.service;

import kr.kimchimap.restaurant.entity.NormalizedCoordinate;
import kr.kimchimap.restaurant.entity.NormalizedCoordinate.Status;
import kr.kimchimap.restaurant.repository.CoordinateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinateNormalizationService {
  private final CoordinateRepository repository;

  public CoordinateNormalizationService(CoordinateRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public NormalizedCoordinate normalize(Double x, Double y, int sourceSrid) {
    if (x == null && y == null) return unavailable(Status.MISSING, "원천 좌표가 없습니다.");
    if (x == null || y == null || !Double.isFinite(x) || !Double.isFinite(y))
      return unavailable(Status.INVALID, "원천 좌표 형식이 올바르지 않습니다.");
    if (sourceSrid != 4326 && sourceSrid != 5174)
      return unavailable(Status.REVIEW_REQUIRED, "원천 좌표계 확인이 필요합니다.");
    if (sourceSrid == 4326 && (x < -180 || x > 180 || y < -90 || y > 90)
        || sourceSrid == 5174 && (x < -100000 || x > 1000000 || y < -100000 || y > 1000000)) {
      return unavailable(Status.INVALID, "원천 좌표 범위를 벗어났습니다.");
    }
    var point = repository.transform(x, y, sourceSrid);
    if (!Double.isFinite(point.latitude())
        || !Double.isFinite(point.longitude())
        || point.latitude() < 32
        || point.latitude() > 39
        || point.longitude() < 124
        || point.longitude() > 132) {
      return unavailable(Status.REVIEW_REQUIRED, "국내 서비스 영역 밖의 좌표입니다. 원천 정보를 확인해 주세요.");
    }
    return new NormalizedCoordinate(
        point.latitude(), point.longitude(), Status.VERIFIED, "원천 좌표를 검증하고 변환했습니다.");
  }

  private NormalizedCoordinate unavailable(Status status, String reason) {
    return new NormalizedCoordinate(null, null, status, reason);
  }
}
