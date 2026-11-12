package kr.kimchimap.restaurant.service;

import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.restaurant.dto.RestaurantAdministration.*;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import kr.kimchimap.restaurant.repository.RestaurantAdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RestaurantAdminService {
  private final RestaurantAdminRepository restaurants;
  private final ObjectMapper json;

  public RestaurantAdminService(RestaurantAdminRepository restaurants, ObjectMapper json) {
    this.restaurants = restaurants;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public View detail(UUID id) {
    var row = restaurants.find(id, false).orElseThrow(RestaurantAdminService::missing);
    var audits =
        restaurants.audits(id).stream()
            .map(
                a ->
                    new Audit(
                        a.id(),
                        a.actorId(),
                        a.previousVersion(),
                        json.readValue(a.beforeSnapshot(), Snapshot.class),
                        json.readValue(a.afterSnapshot(), Snapshot.class),
                        a.reason(),
                        a.createdAt()))
            .toList();
    return new View(id, row.version(), row.snapshot(), audits);
  }

  @Transactional
  public View correct(UUID actor, UUID id, Correction input) {
    var row = restaurants.find(id, true).orElseThrow(RestaurantAdminService::missing);
    if (row.version() != input.expectedVersion())
      throw new ApiException(
          HttpStatus.CONFLICT, "RESTAURANT_VERSION_CONFLICT", "업소 정보가 변경되었습니다. 최신 내용을 확인해 주세요.");
    if ((input.latitude() == null) != (input.longitude() == null)
        || input.latitude() != null
            && (!Double.isFinite(input.latitude()) || !Double.isFinite(input.longitude())))
      throw invalid("위도와 경도를 함께 확인해 주세요.");
    var phone = BusinessPhone.parse(input.phone());
    if (input.phone() != null && !input.phone().isBlank() && phone == null)
      throw invalid("공개할 업소 전화번호 형식을 확인해 주세요.");
    var before = row.snapshot();
    boolean published =
        input.businessStatus().equals("CLOSED")
            ? false
            : row.businessStatus().equals("CLOSED") && input.businessStatus().equals("OPEN")
                || row.published();
    var after =
        new Snapshot(
            input.name().strip(),
            input.address().strip(),
            input.businessStatus(),
            input.latitude(),
            input.longitude(),
            phone == null ? null : phone.display(),
            published);
    var fields = new HashSet<String>();
    if (!before.name().equals(after.name())) fields.add("name");
    if (!before.address().equals(after.address())) fields.add("address");
    if (!Objects.equals(before.latitude(), after.latitude())
        || !Objects.equals(before.longitude(), after.longitude())) fields.add("location");
    if (!before.businessStatus().equals(after.businessStatus())
        || before.published() != after.published()) fields.add("business_status");
    if (!Objects.equals(before.phone(), after.phone())) fields.add("phone");
    if (fields.isEmpty()) throw invalid("변경할 업소 정보가 없습니다.");
    if ((fields.contains("location") || fields.contains("address") || fields.contains("name"))
        && !Boolean.TRUE.equals(input.locationReviewed()))
      throw invalid("같은 가게의 이름·주소·위치인지 확인해 주세요.");
    if (fields.contains("phone")
        && phone != null
        && !Boolean.TRUE.equals(input.businessPhoneReviewed()))
      throw invalid("공개 가능한 업소 전화번호인지 확인해 주세요.");
    restaurants.update(id, after, phone, fields.contains("phone"), fields.contains("location"));
    restaurants.protect(id, fields, actor, input.reason());
    restaurants.audit(
        id,
        actor,
        row.version(),
        json.writeValueAsString(before),
        json.writeValueAsString(after),
        input.reason());
    return detail(id);
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESTAURANT_CORRECTION", message);
  }

  private static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "업소를 찾을 수 없습니다.");
  }
}
