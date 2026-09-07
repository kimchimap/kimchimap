package kr.kimchimap.restaurant;

import static org.assertj.core.api.Assertions.assertThat;

import kr.kimchimap.restaurant.entity.BusinessPhone;
import org.junit.jupiter.api.Test;

class BusinessPhoneTest {
  @Test
  void businessContactPreservesDisplayAndProducesSafeDialNumber() {
    var phone = BusinessPhone.parse(" (02) 1234-5678 ");
    assertThat(phone.display()).isEqualTo("(02) 1234-5678");
    assertThat(phone.number()).isEqualTo("0212345678");
    assertThat(BusinessPhone.parse("+82-2-1234-5678").number()).isEqualTo("+82212345678");
    assertThat(BusinessPhone.parse("1588-1234").number()).isEqualTo("15881234");
  }

  @Test
  void absentInvalidAndExecutableValuesProduceNoCallLink() {
    for (String raw :
        java.util.List.of(
            "", "123", "javascript:alert(1)", "02-1234-5678;ext=123", "+82+0212345678"))
      assertThat(BusinessPhone.parse(raw)).isNull();
    assertThat(BusinessPhone.parse(null)).isNull();
  }
}
