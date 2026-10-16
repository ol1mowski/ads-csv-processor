package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class ProcessingCancellationIntegrationTest extends ProcessingEnabledTestBase {

  private static final int BLOCKING_ROW = 500;

  @Test
  @DisplayName("Cancel during processing stops the worker and removes all job files")
  void shouldStopWorkerAndCleanUpWhenCancelledMidFile() throws InterruptedException {
    var reached = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    transformation.arm(BLOCKING_ROW, reached, release);
    try {
      var id = api.accepted("sample.csv", SampleData.bytes()).id();
      assertThat(reached.await(10, TimeUnit.SECONDS)).as("worker reached row 500").isTrue();

      var processing = api.get(id);
      assertThat(processing.status()).isEqualTo(JobStatus.PROCESSING);
      assertThat(processing.attempts()).isEqualTo(1);
      assertThat(processing.startedAt()).isNotNull();
      assertThat(processing.bytesProcessed()).isPositive().isLessThan(SampleData.SIZE_BYTES);
      assertThat(processing.rowsProcessed()).isPositive().isLessThan(BLOCKING_ROW);
      assertThat(processing.progressPercent()).isBetween(1, 99);
      assertThat(processing.resultUrl()).isNull();

      assertThat(api.cancelled(id).status()).isEqualTo(JobStatus.CANCELLED);
      assertThat(api.cancelled(id).status()).isEqualTo(JobStatus.CANCELLED);
      assertResultNotAvailable(id);
      release.countDown();

      await("worker removes the cancelled job's directory")
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(storage.jobDirectory(id)).doesNotExist());
      assertThat(api.get(id).status()).isEqualTo(JobStatus.CANCELLED);
      assertResultNotAvailable(id);
    } finally {
      release.countDown();
    }

    transformation.disarm();
    var next = api.accepted("next.csv", SampleData.bytes()).id();
    assertThat(awaitStatus(next, JobStatus.COMPLETED).rowsProcessed()).isEqualTo(SampleData.ROWS);
  }

  private void assertResultNotAvailable(UUID id) {
    api.result(id)
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RESULT_NOT_AVAILABLE")
        .jsonPath("$.currentStatus")
        .isEqualTo("CANCELLED");
  }
}
