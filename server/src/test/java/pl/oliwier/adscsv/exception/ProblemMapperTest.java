package pl.oliwier.adscsv.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import pl.oliwier.adscsv.exception.auth.NotAuthenticatedException;
import pl.oliwier.adscsv.exception.upload.UploadProblem;

class ProblemMapperTest {

  @Test
  @DisplayName("A problem carries the status, the detail and a stable code property")
  void shouldBuildProblemWhenStatusCodeAndDetailAreGiven() {
    var problem = ProblemMapper.problem(HttpStatus.CONFLICT, "SOME_CODE", "Some detail");

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("Some detail");
    assertThat(problem.getProperties()).containsEntry("code", "SOME_CODE");
  }

  @Test
  @DisplayName("A problem built from an application exception uses its code and message")
  void shouldBuildProblemWhenApplicationExceptionIsGiven() {
    var problem = ProblemMapper.problem(HttpStatus.UNAUTHORIZED, new NotAuthenticatedException());

    assertThat(problem.getStatus()).isEqualTo(401);
    assertThat(problem.getDetail()).isEqualTo(NotAuthenticatedException.MESSAGE);
    assertThat(problem.getProperties()).containsEntry("code", NotAuthenticatedException.CODE);
  }

  @ParameterizedTest
  @CsvSource({
    "404, NOT_FOUND",
    "405, METHOD_NOT_ALLOWED",
    "415, UNSUPPORTED_MEDIA_TYPE",
    "400, REQUEST_INVALID",
    "422, REQUEST_INVALID",
    "500, INTERNAL_ERROR",
    "503, INTERNAL_ERROR"
  })
  @DisplayName("A framework problem gets a code derived from its status")
  void shouldClassifyProblemWhenFrameworkBuiltIt(int status, String code) {
    var problem = ProblemDetail.forStatus(status);

    ProblemMapper.classify(problem, HttpStatus.valueOf(status));

    assertThat(problem.getProperties()).containsEntry("code", code);
  }

  @ParameterizedTest
  @EnumSource(UploadProblem.class)
  @DisplayName("Every upload problem maps to a 4xx or 5xx status")
  void shouldMapEveryUploadProblemToAnErrorStatus(UploadProblem problem) {
    assertThat(ProblemMapper.statusOf(problem).isError()).isTrue();
  }
}
