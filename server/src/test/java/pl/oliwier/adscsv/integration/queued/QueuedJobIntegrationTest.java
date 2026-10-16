package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.support.SampleData;

class QueuedJobIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] CSV = SampleData.csv(SampleData.FIRST_ROW);
  private static final String ISO_MILLIS_UTC =
      "\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z\"";

  @Autowired private JobStateService jobs;

  @Test
  @DisplayName("Cancelling a queued job deletes its files and takes it out of the queue")
  void shouldCancelQueuedJob() {
    var id = api.accepted("queued.csv", CSV).id();
    assertThat(storage.input(id)).exists();

    var cancelled = api.cancelled(id);

    assertThat(cancelled.status()).isEqualTo(JobStatus.CANCELLED);
    assertThat(cancelled.finishedAt()).isNotNull();
    assertThat(cancelled.resultUrl()).isNull();
    assertThat(storage.jobDirectory(id)).doesNotExist();
    assertThat(jobs.claimNext()).isEmpty();
    assertThat(rows.status(id)).isEqualTo(JobStatus.CANCELLED);
  }

  @Test
  @DisplayName("Cancelling an already cancelled job answers 200 again")
  void shouldAnswerRepeatedCancelIdempotently() {
    var id = api.accepted("twice.csv", CSV).id();
    api.cancelled(id);

    assertThat(api.cancelled(id).status()).isEqualTo(JobStatus.CANCELLED);
  }

  @Test
  @DisplayName("The result of a queued job is a 409 conflict naming the current status")
  void shouldReturn409WhenResultOfQueuedJobIsRequested() {
    var id = api.accepted("waiting.csv", CSV).id();

    api.result(id)
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RESULT_NOT_AVAILABLE")
        .jsonPath("$.currentStatus")
        .isEqualTo("QUEUED");
  }

  @Test
  @DisplayName("A queued job exposes its full shape with UTC millisecond timestamps")
  void shouldExposeQueuedJobSnapshot() {
    var id = api.accepted("snapshot.csv", CSV).id();

    var raw =
        new String(
            api.job(id).expectStatus().isOk().expectBody().returnResult().getResponseBody(),
            StandardCharsets.UTF_8);
    var job = api.get(id);

    assertThat(raw).containsPattern(ISO_MILLIS_UTC);
    assertThat(job.fileName()).isEqualTo("snapshot.csv");
    assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
    assertThat(job.sizeBytes()).isEqualTo(CSV.length);
    assertThat(job.progressPercent()).isZero();
    assertThat(job.attempts()).isZero();
    assertThat(job.startedAt()).isNull();
    assertThat(job.finishedAt()).isNull();
    assertThat(job.resultUrl()).isNull();
    assertThat(job.totals()).isEqualTo(new Totals("0", "0", null));
  }
}
