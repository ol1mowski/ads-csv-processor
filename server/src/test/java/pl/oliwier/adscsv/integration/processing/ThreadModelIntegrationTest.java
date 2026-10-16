package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.integration.support.RequestThreadRecorder.ServedRequest;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class ThreadModelIntegrationTest extends ProcessingEnabledTestBase {

  @Test
  @DisplayName("HTTP requests, including the streaming upload, are served on virtual threads")
  void shouldServeHttpRequestsOnVirtualThreads() {
    var id = api.accepted("threads.csv", SampleData.csv(SampleData.FIRST_ROW)).id();
    api.get(id);
    api.page("");

    var served = requestThreads.served();

    assertThat(served).extracting(ServedRequest::method).contains("POST", "GET");
    assertThat(served)
        .allSatisfy(
            request ->
                assertThat(request.virtual())
                    .as("%s %s on %s", request.method(), request.path(), request.threadName())
                    .isTrue());
  }

  @Test
  @DisplayName("CSV rows are transformed on the bounded pool of platform worker threads")
  void shouldTransformRowsOnPlatformWorkerThreads() throws InterruptedException {
    var reached = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    transformation.arm(1, reached, release);
    try {
      var id = api.accepted("threads.csv", SampleData.csv(SampleData.FIRST_ROW)).id();
      assertThat(reached.await(10, TimeUnit.SECONDS)).isTrue();

      var worker = transformation.latchedThread().orElseThrow();
      assertThat(worker.isVirtual()).isFalse();
      assertThat(worker.getName()).startsWith("csv-worker-");

      release.countDown();
      awaitStatus(id, JobStatus.COMPLETED);
    } finally {
      release.countDown();
    }
  }
}
