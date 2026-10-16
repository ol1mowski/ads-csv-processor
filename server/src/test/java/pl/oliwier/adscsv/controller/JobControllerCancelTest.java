package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.exception.job.JobNotCancellableException;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.JobResponses;

class JobControllerCancelTest extends JobControllerSliceTest {

  @Test
  @DisplayName("POST /jobs/{id}/cancel returns 200 with the cancelled job")
  void shouldReturn200WhenJobCancelled() {
    given(jobService.cancel(JOB_ID, OWNER))
        .willReturn(JobResponses.withStatus(JOB_ID, JobStatus.CANCELLED));

    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(owner()).with(xsrf()))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": "%s", "status": "CANCELLED", "finishedAt": "2026-10-14T10:15:30.502Z"}
            """
                .formatted(JOB_ID));
  }

  @Test
  @DisplayName("POST /jobs/{id}/cancel is idempotent for an already cancelled job")
  void shouldReturn200WhenAlreadyCancelled() {
    given(jobService.cancel(JOB_ID, OWNER))
        .willReturn(JobResponses.withStatus(JOB_ID, JobStatus.CANCELLED));

    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(owner()).with(xsrf())).hasStatusOk();
    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(owner()).with(xsrf()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("CANCELLED");
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"COMPLETED", "FAILED"})
  @DisplayName("POST /jobs/{id}/cancel returns 409 JOB_NOT_CANCELLABLE with the current status")
  void shouldReturn409WhenJobTerminal(JobStatus status) {
    given(jobService.cancel(JOB_ID, OWNER)).willThrow(new JobNotCancellableException(status));

    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(owner()).with(xsrf()))
        .hasStatus(HttpStatus.CONFLICT)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 409, "code": "JOB_NOT_CANCELLABLE", "currentStatus": "%s",
             "detail": "Job is already finished", "instance": "%s/cancel"}
            """
                .formatted(status, JOB_URI));
  }

  @Test
  @DisplayName("POST /jobs/{id}/cancel returns 404 for an unknown job")
  void shouldReturn404WhenCancellingUnknownJob() {
    given(jobService.cancel(JOB_ID, OWNER)).willThrow(new JobNotFoundException(JOB_ID));

    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(owner()).with(xsrf()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 404, "code": "JOB_NOT_FOUND"}
            """);
  }

  @Test
  @DisplayName("POST /jobs/{id}/cancel returns 400 for a malformed id")
  void shouldReturn400WhenCancelIdMalformed() {
    assertThat(mvc.post().uri("/api/v1/jobs/123/cancel").with(owner()).with(xsrf()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID"}
            """);
    verifyNoInteractions(jobService);
  }
}
