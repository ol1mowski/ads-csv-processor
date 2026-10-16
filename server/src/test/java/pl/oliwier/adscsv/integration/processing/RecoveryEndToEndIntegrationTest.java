package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.processing.StartupRecovery;
import pl.oliwier.adscsv.support.SampleData;

class RecoveryEndToEndIntegrationTest extends ProcessingEnabledTestBase {

  @Autowired private StartupRecovery recovery;

  @Test
  @DisplayName("A job interrupted by a crash is requeued at startup and completed by attempt 2")
  void shouldRequeueInterruptedJobAndCompleteItOnNextAttempt() throws IOException {
    var sample = SampleData.bytes();
    var id = rows.insertProcessing(1, sample.length);
    storage.writeInput(id, sample);
    storage.writeOutput(
        id, "partial output of the crashed attempt".getBytes(StandardCharsets.US_ASCII));

    recovery.recover();

    var completed = awaitStatus(id, JobStatus.COMPLETED);
    assertThat(completed.attempts()).isEqualTo(2);
    assertThat(completed.rowsProcessed()).isEqualTo(SampleData.ROWS);
    assertThat(completed.totals()).isEqualTo(new Totals("6733389", "176755", "0.026251"));
    await("the input is removed").untilAsserted(() -> assertThat(storage.input(id)).doesNotExist());
    assertThat(api.resultText(id).lines()).hasSize(1001);
  }

  @Test
  @DisplayName(
      "A queued job whose input vanished, e.g. after an OS crash, fails loudly as INPUT_MISSING")
  void shouldFailQueuedJobWhoseInputIsMissing() {
    var id = rows.insertQueued(SampleData.SIZE_BYTES);

    var failed = awaitStatus(id, JobStatus.FAILED);

    assertThat(failed.failureReason()).isEqualTo(FailureReason.INPUT_MISSING);
    assertThat(failed.attempts()).isEqualTo(1);
    assertThat(failed.rowsProcessed()).isZero();
    assertThat(storage.jobDirectory(id)).doesNotExist();
  }
}
