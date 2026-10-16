package pl.oliwier.adscsv.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ProblemResponseWriterTest {

  private final ProblemResponseWriter problems =
      new ProblemResponseWriter(JsonMapper.builder().build());
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("POST", "/api/v1/jobs/x/cancel");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  @DisplayName("A problem is written as RFC 9457 JSON with its code and the request path")
  void shouldWriteProblemJsonWithCodeAndInstance() throws IOException {
    problems.write(request, response, HttpStatus.CONFLICT, "SOME_CODE", "Some detail");

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"detail\":\"Some detail\",\"instance\":\"/api/v1/jobs/x/cancel\",\"status\":409,"
                + "\"title\":\"Conflict\",\"code\":\"SOME_CODE\"}");
  }
}
