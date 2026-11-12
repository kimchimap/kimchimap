package kr.kimchimap.restaurant.service;

import java.util.UUID;
import kr.kimchimap.global.web.ApiException;
import kr.kimchimap.restaurant.dto.ProposedRestaurant;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import kr.kimchimap.restaurant.repository.RestaurantRegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantRegistrationService {
  private final RestaurantRegistrationRepository restaurants;

  public RestaurantRegistrationService(RestaurantRegistrationRepository restaurants) {
    this.restaurants = restaurants;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID registerReviewed(ProposedRestaurant input) {
    var phone = BusinessPhone.parse(input.phone());
    if (input.phone() != null && !input.phone().isBlank() && phone == null)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_PHONE", "가게 전화번호 형식을 확인해 주세요.");
    if (restaurants.duplicate(input))
      throw new ApiException(
          HttpStatus.CONFLICT,
          "RESTAURANT_MATCH_REQUIRED",
          "같은 이름·주소의 업소가 있습니다. 기존 업소 여부를 확인해 주세요.");
    return restaurants.create(input, phone);
  }
}
