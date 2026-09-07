package kr.kimchimap.ingestion.service;

import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OriginCollectionPolicy {
  public boolean supportsDomesticQualification() {
    // 일반음식점 API에는 원산지 필드가 없어 수집 대상을 선별할 수 없다.
    return false;
  }

  public void requireQualifiedSource() {
    if (supportsDomesticQualification()) return;
    throw new ApiException(
        HttpStatus.CONFLICT,
        "ORIGIN_QUALIFIED_SOURCE_REQUIRED",
        "국내산 사용 항목을 확인할 수 없는 전체 음식점 수집은 중단되었습니다. 허가된 원산지 소스가 필요합니다.");
  }
}
