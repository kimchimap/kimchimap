package kr.kimchimap.global.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ProblemDetail dependencyFailure(org.springframework.dao.DataAccessException exception) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DEPENDENCY_UNAVAILABLE",
        "필요한 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
  }

  @ExceptionHandler(ApiException.class)
  ProblemDetail businessFailure(ApiException exception) {
    return problem(exception.status(), exception.code(), exception.getMessage());
  }

  @ExceptionHandler({
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class
  })
  ProblemDetail invalidRequest(Exception exception) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력 형식과 필수 항목을 확인해 주세요.");
  }

  private ProblemDetail problem(HttpStatus status, String code, String message) {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setTitle("요청 처리 실패");
    problem.setProperty("code", code);
    problem.setProperty("traceId", MDC.get("traceId"));
    return problem;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpectedFailure(Exception exception) {
    org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
        .error(
            "요청 처리 실패: type={}, traceId={}",
            exception.getClass().getSimpleName(),
            MDC.get("traceId"));
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
  }
}
