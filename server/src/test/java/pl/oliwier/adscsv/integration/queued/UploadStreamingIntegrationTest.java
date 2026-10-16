package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.RawHttpConnection;
import pl.oliwier.adscsv.integration.support.StreamingHttp;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class UploadStreamingIntegrationTest extends ProcessingDisabledTestBase {

  private static final int WITHIN_LIMIT_BYTES = 60_000;
  private static final Duration DISCARD_TIMEOUT = Duration.ofSeconds(10);

  @Test
  @DisplayName("An accepted upload is stored byte for byte and queued with its Location")
  void shouldStoreAcceptedInputByteForByte() throws IOException {
    var body = SampleData.wholeLinesUpTo(WITHIN_LIMIT_BYTES);

    var exchange =
        api.upload("prefix.csv", body)
            .expectStatus()
            .isAccepted()
            .expectBody(JobResponse.class)
            .returnResult();
    var job = exchange.getResponseBody();

    assertThat(exchange.getResponseHeaders().getLocation()).hasToString(ApiRoutes.job(job.id()));
    assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
    assertThat(job.sizeBytes()).isEqualTo(body.length);
    assertThat(Files.readAllBytes(storage.input(job.id()))).isEqualTo(body);
    assertThat(storage.incomingFiles()).isEmpty();
    assertThat(rows.status(job.id())).isEqualTo(JobStatus.QUEUED);
  }

  @Test
  @DisplayName("A chunked upload within the limit is accepted and stored byte for byte")
  void shouldAcceptChunkedUploadWithinLimit() throws Exception {
    var body = SampleData.wholeLinesUpTo(WITHIN_LIMIT_BYTES);

    var response = streaming().uploadChunked("chunked.csv", () -> new ByteArrayInputStream(body));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
    var id = StreamingHttp.jobId(response);
    assertThat(api.get(id).sizeBytes()).isEqualTo(body.length);
    assertThat(Files.readAllBytes(storage.input(id))).isEqualTo(body);
    assertThat(storage.incomingFiles()).isEmpty();
  }

  @Test
  @DisplayName("A body cut short by the client leaves no row and no files")
  void shouldDiscardTruncatedUploadWhenClientDisconnects() throws IOException {
    var firstHalf = Arrays.copyOf(SampleData.bytes(), 500);

    try (var connection = RawHttpConnection.open(port)) {
      connection.sendCsvUploadHead("truncated.csv", 1000, session);
      connection.send(firstHalf);
      await("server starts receiving the body")
          .atMost(DISCARD_TIMEOUT)
          .until(() -> storage.incomingFiles().size() == 1);
    }

    await("server discards the part file")
        .atMost(DISCARD_TIMEOUT)
        .until(() -> storage.incomingFiles().isEmpty());
    assertThat(rows.count()).isZero();
    assertThat(storage.jobDirectories()).isEmpty();
  }
}
