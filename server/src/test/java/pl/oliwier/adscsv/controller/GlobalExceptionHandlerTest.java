package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class GlobalExceptionHandlerTest extends JobControllerSliceTest {

  @Test
  @DisplayName("An unsupported method returns 405 METHOD_NOT_ALLOWED as problem+json")
  void shouldReturn405WhenMethodNotAllowed() {
    assertThat(mvc.delete().uri(JOB_URI).with(owner()).with(xsrf()))
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 405, "code": "METHOD_NOT_ALLOWED"}
            """);
  }

  @Test
  @DisplayName("An unknown path returns 404 NOT_FOUND as problem+json")
  void shouldReturn404WhenPathUnknown() {
    assertThat(mvc.get().uri("/api/v1/unknown").with(owner()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 404, "code": "NOT_FOUND"}
            """);
  }

  @Test
  @DisplayName("A client that disconnects mid-response gets no problem body written")
  void shouldNotWriteProblemWhenClientAborts() {
    given(jobService.get(JOB_ID, OWNER))
        .willAnswer(
            _ -> {
              throw new ClientAbortException("Broken pipe");
            });

    var result = assertThat(mvc.get().uri(JOB_URI).with(owner()));

    result.failure().isInstanceOf(ClientAbortException.class);
    result.doesNotContainHeader(HttpHeaders.CONTENT_TYPE);
    result.body().isEmpty();
  }

  @Test
  @DisplayName("An unexpected error on a read endpoint returns a safe 500")
  void shouldReturnSafe500WhenReadFails() {
    given(jobService.get(JOB_ID, OWNER))
        .willThrow(new IllegalStateException("db password=hunter2"));

    var result = assertThat(mvc.get().uri(JOB_URI).with(owner()));

    result
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 500, "code": "INTERNAL_ERROR"}
            """);
    result.bodyText().doesNotContain("hunter2");
  }
}
