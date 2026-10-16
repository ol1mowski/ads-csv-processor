package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.exception.job.ResultGoneException;
import pl.oliwier.adscsv.exception.job.ResultNotAvailableException;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.job.ResultFile;
import pl.oliwier.adscsv.support.SampleData;

class JobControllerResultTest extends JobControllerSliceTest {

  private static final byte[] REPORT =
      (SampleData.HEADER + ";ctr\n" + SampleData.FIRST_ROW + ";0.037225\n")
          .getBytes(StandardCharsets.US_ASCII);

  @Test
  @DisplayName("GET /jobs/{id}/result streams the CSV as an attachment with its length")
  void shouldStreamResultWhenCompleted() throws IOException {
    given(jobService.result(JOB_ID, OWNER))
        .willReturn(
            new ResultFile(new ByteArrayResource(REPORT), "ads-processed.csv", REPORT.length));

    var result = assertThat(mvc.get().uri(JOB_URI + "/result").with(owner()));

    result
        .hasStatusOk()
        .hasContentType("text/csv")
        .hasHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(REPORT.length))
        .hasHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"ads-processed.csv\"; filename*=UTF-8''ads-processed.csv");
    result.body().isEqualTo(REPORT);
  }

  @Test
  @DisplayName("GET /jobs/{id}/result encodes a non-ASCII download name per RFC 5987")
  void shouldEncodeUtf8FileNameInContentDisposition() throws IOException {
    given(jobService.result(JOB_ID, OWNER))
        .willReturn(
            new ResultFile(
                new ByteArrayResource(REPORT), "raport_łódź-processed.csv", REPORT.length));

    assertThat(mvc.get().uri(JOB_URI + "/result").with(owner()))
        .hasStatusOk()
        .headers()
        .hasHeaderSatisfying(
            HttpHeaders.CONTENT_DISPOSITION,
            values ->
                assertThat(values)
                    .singleElement()
                    .asString()
                    .startsWith("attachment;")
                    .contains("filename*=UTF-8''raport_%C5%82%C3%B3d%C5%BA-processed.csv"));
  }

  @Test
  @DisplayName("GET /jobs/{id}/result returns 409 RESULT_NOT_AVAILABLE before completion")
  void shouldReturn409WhenResultNotReady() throws IOException {
    given(jobService.result(JOB_ID, OWNER))
        .willThrow(new ResultNotAvailableException(JobStatus.PROCESSING));

    assertThat(mvc.get().uri(JOB_URI + "/result").with(owner()))
        .hasStatus(HttpStatus.CONFLICT)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 409, "code": "RESULT_NOT_AVAILABLE", "currentStatus": "PROCESSING"}
            """);
  }

  @Test
  @DisplayName("GET /jobs/{id}/result returns 410 RESULT_GONE when the file vanished")
  void shouldReturn410WhenResultFileMissing() throws IOException {
    given(jobService.result(JOB_ID, OWNER)).willThrow(new ResultGoneException(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI + "/result").with(owner()))
        .hasStatus(HttpStatus.GONE)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 410, "code": "RESULT_GONE"}
            """);
  }

  @Test
  @DisplayName("GET /jobs/{id}/result returns 404 as problem+json for an unknown job")
  void shouldReturnProblemJsonWhenResultJobUnknown() throws IOException {
    given(jobService.result(JOB_ID, OWNER)).willThrow(new JobNotFoundException(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI + "/result").with(owner()))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 404, "code": "JOB_NOT_FOUND"}
            """);
  }

  @Test
  @DisplayName("GET /jobs/{id}/result returns 400 for a malformed id")
  void shouldReturn400WhenResultIdMalformed() {
    assertThat(mvc.get().uri("/api/v1/jobs/not-a-uuid/result").with(owner()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID"}
            """);
    verifyNoInteractions(jobService);
  }
}
