package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.dto.PageResponse;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.JobResponses;

class JobControllerQueryTest extends JobControllerSliceTest {

  @Test
  @DisplayName("GET /jobs/{id} returns the full status document with millisecond UTC timestamps")
  void shouldReturnJobWhenExists() {
    given(jobService.get(JOB_ID, OWNER)).willReturn(JobResponses.completed(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI).with(owner()))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%1$s",
              "fileName": "sample_ads_data.csv",
              "status": "COMPLETED",
              "sizeBytes": 77752,
              "bytesProcessed": 77752,
              "progressPercent": 100,
              "rowsProcessed": 997,
              "rowsRejected": 3,
              "rejectedSamples": [{"line": 5, "reason": "FIELD_COUNT"}],
              "totals": {"impressions": "6733389", "clicks": "176755", "ctr": "0.026251"},
              "failureReason": null,
              "attempts": 1,
              "createdAt": "2026-10-14T10:15:30.123Z",
              "startedAt": "2026-10-14T10:15:30.410Z",
              "finishedAt": "2026-10-14T10:15:30.502Z",
              "resultUrl": "/api/v1/jobs/%1$s/result"
            }
            """
                .formatted(JOB_ID));
  }

  @Test
  @DisplayName("GET /jobs/{id} of an unfinished job has no result URL and no total CTR")
  void shouldOmitResultUrlWhenNotCompleted() {
    given(jobService.get(JOB_ID, OWNER)).willReturn(JobResponses.queued(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI).with(owner()))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": "QUEUED", "resultUrl": null, "startedAt": null,
             "totals": {"impressions": "0", "clicks": "0", "ctr": null}}
            """);
  }

  @Test
  @DisplayName("GET /jobs/{id} returns 404 JOB_NOT_FOUND as problem+json")
  void shouldReturn404WhenJobUnknown() {
    given(jobService.get(JOB_ID, OWNER)).willThrow(new JobNotFoundException(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI).with(owner()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 404, "code": "JOB_NOT_FOUND", "instance": "%s"}
            """
                .formatted(JOB_URI));
  }

  @Test
  @DisplayName("GET /jobs/{id} returns 400 for a malformed id")
  void shouldReturn400WhenIdMalformed() {
    assertThat(mvc.get().uri("/api/v1/jobs/not-a-uuid").with(owner()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID"}
            """);
    verifyNoInteractions(jobService);
  }

  @Test
  @DisplayName("GET /jobs uses page 0, size 20 and no filter by default")
  void shouldUseDefaultPagingWhenNoParams() {
    given(jobService.history(OWNER, 0, 20, null))
        .willReturn(new PageResponse<>(List.of(JobResponses.queued(JOB_ID)), 0, 20, 1, 1));

    assertThat(mvc.get().uri("/api/v1/jobs").with(owner()))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"items": [{"id": "%s", "status": "QUEUED"}],
             "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}
            """
                .formatted(JOB_ID));
  }

  @Test
  @DisplayName("GET /jobs passes a comma separated status filter and paging to the service")
  void shouldPassStatusFilterWhenProvided() {
    var finished = Set.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED);
    given(jobService.history(OWNER, 2, 5, finished))
        .willReturn(new PageResponse<>(List.of(), 2, 5, 7, 2));

    assertThat(
            mvc.get()
                .uri("/api/v1/jobs")
                .param("page", "2")
                .param("size", "5")
                .param("status", "COMPLETED,FAILED,CANCELLED")
                .with(owner()))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"items": [], "page": 2, "size": 5, "totalElements": 7, "totalPages": 2}
            """);
  }

  @Test
  @DisplayName("GET /jobs accepts the status filter as repeated parameters")
  void shouldAcceptRepeatedStatusParameters() {
    var filter = Set.of(JobStatus.QUEUED, JobStatus.PROCESSING);
    given(jobService.history(OWNER, 0, 20, filter))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

    assertThat(
            mvc.get()
                .uri("/api/v1/jobs")
                .param("status", "QUEUED")
                .param("status", "PROCESSING")
                .with(owner()))
        .hasStatusOk();
    verify(jobService).history(OWNER, 0, 20, filter);
  }

  @Test
  @DisplayName("GET /jobs treats an empty status parameter as no filter")
  void shouldTreatEmptyStatusAsNoFilter() {
    given(
            jobService.history(
                eq(OWNER), eq(0), eq(20), argThat(status -> status == null || status.isEmpty())))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

    assertThat(mvc.get().uri("/api/v1/jobs").param("status", "").with(owner())).hasStatusOk();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "page=-1",
        "page=1000001",
        "page=2147483647",
        "size=0",
        "size=-5",
        "status=FOO",
        "status=completed",
        "page=abc"
      })
  @DisplayName("GET /jobs returns 400 REQUEST_INVALID for invalid paging or status")
  void shouldReturn400WhenPagingInvalid(String query) {
    assertThat(mvc.get().uri("/api/v1/jobs?" + query).with(owner()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID"}
            """);
    verifyNoInteractions(jobService);
  }

  @Test
  @DisplayName("GET /jobs returns an empty page when there is no history")
  void shouldReturnEmptyPageWhenNoJobs() {
    given(jobService.history(eq(OWNER), anyInt(), anyInt(), isNull()))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

    assertThat(mvc.get().uri("/api/v1/jobs").with(owner()))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
            """);
  }
}
