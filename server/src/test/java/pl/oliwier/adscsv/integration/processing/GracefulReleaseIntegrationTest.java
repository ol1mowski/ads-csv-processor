package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pl.oliwier.adscsv.support.ProgressAssertions.assertNoProgress;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.processing.JobDispatcher;
import pl.oliwier.adscsv.support.SampleData;

class GracefulReleaseIntegrationTest extends ProcessingEnabledTestBase {

  private static final int BLOCKING_ROW = 500;
  private static final Duration STOP_TIMEOUT = Duration.ofSeconds(15);

  @Autowired private JobDispatcher dispatcher;

  @Test
  @DisplayName(
      "Stopping the dispatcher mid-file returns the job to the queue, wipes its progress and the"
          + " next claim runs it as attempt 2")
  void shouldReleaseJobToQueueWhenDispatcherStopsMidFile() throws Exception {
    var reached = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    transformation.arm(BLOCKING_ROW, reached, release);
    var stopper = Thread.ofPlatform().name("dispatcher-stopper").unstarted(dispatcher::stop);
    try {
      var id = api.accepted("sample.csv", SampleData.bytes()).id();
      assertThat(reached.await(10, TimeUnit.SECONDS)).isTrue();

      stopper.start();
      await("stop() waits for the busy worker")
          .until(() -> stopper.getState() == Thread.State.TIMED_WAITING);
      release.countDown();
      stopper.join(STOP_TIMEOUT);

      assertThat(stopper.isAlive()).as("stop() returned").isFalse();
      assertThat(dispatcher.isRunning()).isFalse();
      assertThat(rows.status(id)).isEqualTo(JobStatus.QUEUED);
      assertThat(rows.attempts(id)).isEqualTo(1);
      assertNoProgress(api.get(id));
      assertThat(storage.input(id)).exists();

      transformation.disarm();
      dispatcher.start();

      var completed = awaitStatus(id, JobStatus.COMPLETED);
      assertThat(completed.attempts()).isEqualTo(2);
      assertThat(completed.rowsProcessed()).isEqualTo(SampleData.ROWS);
    } finally {
      release.countDown();
      stopper.join(STOP_TIMEOUT.toMillis());
      dispatcher.start();
    }
  }
}
