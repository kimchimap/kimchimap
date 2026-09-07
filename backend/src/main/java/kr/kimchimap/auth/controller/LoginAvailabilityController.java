package kr.kimchimap.auth.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.kimchimap.global.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginAvailabilityController {
  @GetMapping("/api/v1/auth/login/kakao")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "카카오 로그인으로 이동"),
    @ApiResponse(responseCode = "429", description = "로그인 요청 제한"),
    @ApiResponse(responseCode = "503", description = "로그인 설정 또는 의존 서비스 확인 필요")
  })
  public void unavailable() {
    // 정상 설정에서는 Spring Security의 인가 요청 필터가 먼저 처리한다.
    throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "LOGIN_NOT_CONFIGURED",
        "로그인 연결이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.");
  }
}
