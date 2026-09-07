package kr.kimchimap.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationTest {
  @Test
  void missingSecretsPreventProductionStartup() {
    var config = new ProductionConfiguration();
    var check = config.requireProductionSettings(new MockEnvironment());
    assertThatThrownBy(check::afterSingletonsInstantiated)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("운영 필수 설정 누락");
  }
}
