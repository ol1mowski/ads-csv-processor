package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.integration.support.StreamingHttp;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.AdsCsvGenerator;

class LargeUploadIntegrationTest extends ProcessingEnabledTestBase {

  private static final long ONE_GIB = 1L << 30;
  private static final long MAX_TEST_HEAP_BYTES = 384L << 20;
  private static final long SEED = 42;
  private static final int READ_BUFFER_BYTES = 64 * 1024;
  private static final Duration LARGE_JOB_TIMEOUT = Duration.ofMinutes(5);

  @AfterEach
  void removeLargeFiles() throws IOException {
    storage.clear();
  }

  @Test
  @DisplayName("A 1 GiB upload streams through a 384 MB heap, completes and streams back")
  void shouldStreamAndProcessOneGibibyteWithinBoundedHeap() throws Exception {
    assertThat(Runtime.getRuntime().maxMemory())
        .as("failsafe must run this test with -Xmx384m")
        .isLessThanOrEqualTo(MAX_TEST_HEAP_BYTES);
    var generator = AdsCsvGenerator.generate(ONE_GIB, SEED);
    var expected = generator.report();
    var http = streaming();

    var response = http.upload("large.csv", generator::openStream, generator.byteLength());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
    var id = StreamingHttp.jobId(response);
    var job = awaitFinished(id, LARGE_JOB_TIMEOUT);
    assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
    assertThat(job.sizeBytes()).isEqualTo(expected.byteLength());
    assertThat(job.bytesProcessed()).isEqualTo(expected.byteLength());
    assertThat(job.rowsProcessed()).isEqualTo(expected.validRows());
    assertThat(job.rowsRejected()).isZero();
    assertThat(job.totals())
        .isEqualTo(
            new Totals(
                Long.toString(expected.totalImpressions()),
                Long.toString(expected.totalClicks()),
                expected.totalCtr().orElseThrow()));

    var download = http.download(ApiRoutes.result(id));
    assertThat(download.statusCode()).isEqualTo(200);
    var declaredLength = download.headers().firstValueAsLong("Content-Length").orElseThrow();
    var received = count(download.body());
    assertThat(received.bytes())
        .isEqualTo(declaredLength)
        .isEqualTo(Files.size(storage.output(id)));
    assertThat(received.lines()).isEqualTo(expected.validRows() + 1);
  }

  private static Received count(InputStream body) throws IOException {
    var buffer = new byte[READ_BUFFER_BYTES];
    long bytes = 0;
    long lines = 0;
    try (body) {
      for (var n = body.read(buffer); n != -1; n = body.read(buffer)) {
        bytes += n;
        for (var i = 0; i < n; i++) {
          if (buffer[i] == '\n') {
            lines++;
          }
        }
      }
    }
    return new Received(bytes, lines);
  }

  private record Received(long bytes, long lines) {}
}
