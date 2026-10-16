package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import pl.oliwier.adscsv.exception.upload.InvalidUploadException;
import pl.oliwier.adscsv.exception.upload.UploadProblem;
import pl.oliwier.adscsv.support.JobResponses;
import pl.oliwier.adscsv.support.SampleData;

class JobControllerUploadTest extends JobControllerSliceTest {

  private static final byte[] BODY = SampleData.csv(SampleData.FIRST_ROW);

  @Test
  @DisplayName("POST /jobs returns 202 with a relative Location for an accepted CSV")
  void shouldReturn202WithLocationWhenCsvAccepted() throws IOException {
    given(uploadService.accept(eq(OWNER), eq("ads.csv"), anyLong(), any()))
        .willReturn(JobResponses.queued(JOB_ID));

    assertThat(upload("ads.csv"))
        .hasStatus(HttpStatus.ACCEPTED)
        .hasHeader(HttpHeaders.LOCATION, JOB_URI)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": "%s", "status": "QUEUED", "progressPercent": 0, "resultUrl": null}
            """
                .formatted(JOB_ID));
  }

  @Test
  @DisplayName("POST /jobs hands the raw body stream and its declared length to the service")
  void shouldPassBodyAndLengthToServiceWhenUploading() throws IOException {
    var received = new AtomicReference<byte[]>();
    given(uploadService.accept(eq(OWNER), eq("ads.csv"), eq((long) BODY.length), any()))
        .willAnswer(
            call -> {
              received.set(call.<InputStream>getArgument(3).readAllBytes());
              return JobResponses.queued(JOB_ID);
            });

    assertThat(upload("ads.csv")).hasStatus(HttpStatus.ACCEPTED);
    assertThat(received.get()).isEqualTo(BODY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Raport Q3 łódź.CSV", "ads.csv"})
  @DisplayName("POST /jobs accepts any printable name ending with .csv")
  void shouldAcceptFileNameWhenItIsPrintableCsvName(String fileName) throws IOException {
    given(uploadService.accept(eq(OWNER), eq(fileName), anyLong(), any()))
        .willReturn(JobResponses.queued(JOB_ID));

    assertThat(upload(fileName)).hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  @DisplayName("POST /jobs accepts a file name of exactly 255 characters")
  void shouldAcceptLongestAllowedFileName() throws IOException {
    var fileName = "a".repeat(251) + ".csv";
    given(uploadService.accept(eq(OWNER), eq(fileName), anyLong(), any()))
        .willReturn(JobResponses.queued(JOB_ID));

    assertThat(upload(fileName)).hasStatus(HttpStatus.ACCEPTED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @MethodSource("invalidFileNames")
  @DisplayName("POST /jobs returns 400 REQUEST_INVALID for a missing or unsafe file name")
  void shouldReturn400WhenFileNameInvalid(String fileName) {
    assertThat(upload(fileName))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID", "instance": "/api/v1/jobs"}
            """);
    verifyNoInteractions(uploadService);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"application/json", "multipart/form-data; boundary=x", "text/plain"})
  @DisplayName("POST /jobs returns 415 UNSUPPORTED_MEDIA_TYPE for anything but text/csv")
  void shouldReturn415WhenContentTypeIsNotCsv(String contentType) {
    var request =
        mvc.post()
            .uri("/api/v1/jobs")
            .param("fileName", "ads.csv")
            .content(BODY)
            .with(owner())
            .with(xsrf());
    if (contentType != null) {
      request.contentType(contentType);
    }

    assertThat(request)
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 415, "code": "UNSUPPORTED_MEDIA_TYPE"}
            """);
    verifyNoInteractions(uploadService);
  }

  @Test
  @DisplayName("POST /jobs passes an unknown length when the request has no Content-Length")
  void shouldPassUnknownLengthWhenContentLengthIsMissing() throws IOException {
    given(uploadService.accept(eq(OWNER), eq("ads.csv"), eq(-1L), any()))
        .willReturn(JobResponses.queued(JOB_ID));

    assertThat(
            mvc.post()
                .uri("/api/v1/jobs")
                .param("fileName", "ads.csv")
                .contentType("text/csv")
                .with(owner())
                .with(xsrf()))
        .hasStatus(HttpStatus.ACCEPTED);
  }

  @Test
  @DisplayName("POST /jobs accepts text/csv with a charset parameter")
  void shouldAcceptCsvWithCharsetParameter() throws IOException {
    given(uploadService.accept(eq(OWNER), eq("ads.csv"), anyLong(), any()))
        .willReturn(JobResponses.queued(JOB_ID));

    assertThat(
            mvc.post()
                .uri("/api/v1/jobs")
                .param("fileName", "ads.csv")
                .contentType("text/csv;charset=UTF-8")
                .content(BODY)
                .with(owner())
                .with(xsrf()))
        .hasStatus(HttpStatus.ACCEPTED);
  }

  @ParameterizedTest
  @EnumSource(UploadProblem.class)
  @DisplayName("POST /jobs maps every upload problem to its status and stable code")
  void shouldMapEveryUploadProblem(UploadProblem problem) throws IOException {
    given(uploadService.accept(any(), any(), anyLong(), any()))
        .willThrow(new InvalidUploadException(problem));
    var expected =
        switch (problem) {
          case EMPTY -> new Expected(400, "EMPTY_FILE", "Bad Request");
          case INTERRUPTED -> new Expected(400, "UPLOAD_INTERRUPTED", "Bad Request");
          case TOO_LARGE -> new Expected(413, "FILE_TOO_LARGE", "Content Too Large");
          case INVALID_HEADER -> new Expected(422, "INVALID_CSV_HEADER", "Unprocessable Content");
          case INSUFFICIENT_STORAGE ->
              new Expected(507, "INSUFFICIENT_STORAGE", "Insufficient Storage");
        };

    var result = assertThat(upload("ads.csv"));

    result
        .hasStatus(expected.status())
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": %d, "code": "%s", "title": "%s", "instance": "/api/v1/jobs"}
            """
                .formatted(expected.status(), expected.code(), expected.title()));
    result.doesNotContainHeader(HttpHeaders.RETRY_AFTER);
  }

  @Test
  @DisplayName("POST /jobs returns a safe 500 without exception details")
  void shouldReturnSafe500WhenUnexpectedError() throws IOException {
    given(uploadService.accept(any(), any(), anyLong(), any()))
        .willThrow(new IllegalStateException("secret detail from /internal/path"));

    var result = assertThat(upload("ads.csv"));

    result
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 500, "code": "INTERNAL_ERROR", "detail": "Unexpected server error"}
            """);
    result.bodyText().doesNotContain("secret").doesNotContain("Exception");
  }

  private MockMvcRequestBuilder upload(String fileName) {
    var request =
        mvc.post()
            .uri("/api/v1/jobs")
            .contentType("text/csv")
            .content(BODY)
            .with(owner())
            .with(xsrf());
    return fileName == null ? request : request.param("fileName", fileName);
  }

  static Stream<String> invalidFileNames() {
    return Stream.of(
        "   ",
        ".csv",
        "a".repeat(252) + ".csv",
        "ads.txt",
        "ads.csv.exe",
        "dir/ads.csv",
        "dir\\ads.csv",
        "ads\u0007.csv",
        "ads\n.csv",
        "evil‮vsc.csv");
  }

  private record Expected(int status, String code, String title) {}
}
