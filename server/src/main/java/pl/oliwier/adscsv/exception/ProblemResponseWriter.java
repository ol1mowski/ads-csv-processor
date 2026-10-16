package pl.oliwier.adscsv.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProblemResponseWriter {

  private final JacksonJsonHttpMessageConverter converter;

  public ProblemResponseWriter(JsonMapper jsonMapper) {
    this.converter = new JacksonJsonHttpMessageConverter(jsonMapper.rebuild());
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    write(request, response, ProblemMapper.problem(status, code, detail));
  }

  public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
      throws IOException {
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(problem.getStatus());
    converter.write(
        problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
  }
}
