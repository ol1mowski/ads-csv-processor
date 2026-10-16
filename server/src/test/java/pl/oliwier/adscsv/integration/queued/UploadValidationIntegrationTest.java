package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.CountingInputStream;
import pl.oliwier.adscsv.integration.support.RawHttpConnection;
import pl.oliwier.adscsv.support.SampleData;

class UploadValidationIntegrationTest extends ProcessingDisabledTestBase {

  @Test
  @DisplayName("A declared length above the limit gets 413 problem+json and nothing is stored")
  void shouldReturn413WithoutStoringWhenDeclaredLengthExceedsLimit() throws IOException {
    var sample = SampleData.bytes();
    assertThat(sample.length).isGreaterThan(MAX_UPLOAD_BYTES);

    api.upload("sample_ads_data.csv", sample)
        .expectStatus()
        .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("FILE_TOO_LARGE")
        .jsonPath("$.status")
        .isEqualTo(413);

    assertNothingStored();
  }

  @Test
  @DisplayName("A client expecting 100-continue gets 413 before it sends a single body byte")
  void shouldReturn413BeforeTheBodyIsSentWhenClientExpectsContinue() throws Exception {
    var sample = SampleData.bytes();
    var bytesSent = new AtomicLong();

    var response =
        streaming()
            .uploadExpectingContinue(
                "sample_ads_data.csv",
                () -> new CountingInputStream(new ByteArrayInputStream(sample), bytesSent),
                sample.length);

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("\"code\":\"FILE_TOO_LARGE\"");
    assertThat(bytesSent).as("body bytes the client had to send").hasValue(0);
    assertNothingStored();
  }

  @Test
  @DisplayName("A client expecting 100-continue uploads normally when the file is accepted")
  void shouldAcceptUploadWhenClientExpectsContinue() throws Exception {
    var body = SampleData.wholeLinesUpTo(MAX_UPLOAD_BYTES);
    var bytesSent = new AtomicLong();

    var response =
        streaming()
            .uploadExpectingContinue(
                "continued.csv",
                () -> new CountingInputStream(new ByteArrayInputStream(body), bytesSent),
                body.length);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
    assertThat(bytesSent).hasValue(body.length);
    assertThat(rows.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("A chunked body crossing the limit mid-stream gets 413 and leaves no part file")
  void shouldReturn413AndDeletePartFileWhenChunkedBodyExceedsLimit() throws Exception {
    var sample = SampleData.bytes();

    var response = streaming().uploadChunked("too-big.csv", () -> new ByteArrayInputStream(sample));

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("\"code\":\"FILE_TOO_LARGE\"");
    assertNothingStored();
  }

  @Test
  @DisplayName("A wrong header gets 422 and creates neither a job row nor files")
  void shouldReturn422AndCreateNoJobWhenHeaderInvalid() throws IOException {
    var body = "advertiserId,campaignId,creativeId\n1,2,3\n".getBytes(StandardCharsets.US_ASCII);

    api.upload("commas.csv", body)
        .expectStatus()
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_CSV_HEADER");

    assertNothingStored();
  }

  @Test
  @DisplayName("A request declaring Content-Length 0 gets 400 EMPTY_FILE")
  void shouldReturn400WhenDeclaredLengthIsZero() throws IOException {
    String response;
    try (var connection = RawHttpConnection.open(port)) {
      connection.sendCsvUploadHead("empty.csv", 0, session);
      response = connection.readResponse();
    }

    assertThat(response).startsWith("HTTP/1.1 400").contains("\"code\":\"EMPTY_FILE\"");
    assertNothingStored();
  }

  private void assertNothingStored() throws IOException {
    assertThat(rows.count()).isZero();
    assertThat(storage.incomingFiles()).isEmpty();
    assertThat(storage.jobDirectories()).isEmpty();
  }
}
