package kr.kimchimap.global.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiProblemWriter {
  private final ObjectMapper mapper;

  public ApiProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse response, int status, String code, String title)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setProperty("code", code);
    problem.setProperty("traceId", MDC.get("traceId"));
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
