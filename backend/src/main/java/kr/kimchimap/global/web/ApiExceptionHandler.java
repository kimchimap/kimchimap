package kr.kimchimap.global.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(Exception.class)
  ProblemDetail unexpectedFailure(Exception exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    problem.setTitle("요청 처리 실패");
    problem.setProperty("code", "INTERNAL_ERROR");
    problem.setProperty("traceId", MDC.get("traceId"));
    return problem;
  }
}
