package pl.oliwier.adscsv.service.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;
import pl.oliwier.adscsv.service.state.RejectedRowsJson;
import pl.oliwier.adscsv.support.ProcessingJobs;
import tools.jackson.databind.json.JsonMapper;

class JobMapperTest {

  private static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");

  private final JobMapper mapper =
      new JobMapper(new RejectedRowsJson(JsonMapper.builder().build()));

  @ParameterizedTest(name = "{0} of 1000 bytes -> {1}%")
  @CsvSource({"0, 0", "4, 0", "425, 42", "999, 99", "1000, 99"})
  @DisplayName("Progress is floored and capped at 99 until the job completes")
  void shouldCapProgressBelowHundredWhenNotCompleted(long bytes, int expected) {
    var job = ProcessingJobs.withStatus(JOB_ID, JobStatus.PROCESSING);
    ProcessingJobs.set(job, "bytesProcessed", bytes);

    assertThat(mapper.toResponse(job).progressPercent()).isEqualTo(expected);
  }

  @Test
  @DisplayName("A completed job reports 100 percent and a relative result URL")
  void shouldReportFullProgressAndResultUrlWhenCompleted() {
    var response = mapper.toResponse(ProcessingJobs.completed(JOB_ID, "ads.csv", 1));

    assertThat(response.progressPercent()).isEqualTo(100);
    assertThat(response.resultUrl()).isEqualTo("/api/v1/jobs/" + JOB_ID + "/result");
  }

  @ParameterizedTest
  @EnumSource(value = JobStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Only completed jobs expose a result URL")
  void shouldOmitResultUrlWhenNotCompleted(JobStatus status) {
    assertThat(mapper.toResponse(ProcessingJobs.withStatus(JOB_ID, status)).resultUrl()).isNull();
  }

  @Test
  @DisplayName("Timestamps are truncated to milliseconds and missing ones stay null")
  void shouldTruncateTimestampsToMillis() {
    var job = ProcessingJobs.queued(JOB_ID);
    ProcessingJobs.set(job, "startedAt", Instant.parse("2026-10-14T10:15:31.999999Z"));

    var response = mapper.toResponse(job);

    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-14T10:15:30.123Z"));
    assertThat(response.startedAt()).isEqualTo(Instant.parse("2026-10-14T10:15:31.999Z"));
    assertThat(response.finishedAt()).isNull();
  }

  @Test
  @DisplayName("Totals are decimal strings with the ratio-of-sums CTR")
  void shouldExposeTotalsAsStrings() {
    var job = ProcessingJobs.completed(JOB_ID, "ads.csv", 1);
    ProcessingJobs.set(job, "totalImpressions", 6_733_389L);
    ProcessingJobs.set(job, "totalClicks", 176_755L);

    assertThat(mapper.toResponse(job).totals())
        .isEqualTo(new Totals("6733389", "176755", "0.026251"));
  }

  @Test
  @DisplayName("Total CTR is null when there were no impressions")
  void shouldReturnNullCtrWhenNoImpressions() {
    assertThat(mapper.toResponse(ProcessingJobs.queued(JOB_ID)).totals())
        .isEqualTo(new Totals("0", "0", null));
  }

  @Test
  @DisplayName("Rejected samples are decoded from their stored JSON")
  void shouldDecodeRejectedSamples() {
    var job = ProcessingJobs.completed(JOB_ID, "ads.csv", 1);
    ProcessingJobs.set(job, "rejectedSamples", "[{\"line\":7,\"reason\":\"INVALID_CLICKS\"}]");

    assertThat(mapper.toResponse(job).rejectedSamples())
        .containsExactly(new RejectedRow(7, RejectReason.INVALID_CLICKS));
  }

  @Test
  @DisplayName("All identifying fields are copied from the entity")
  void shouldCopyEntityFields() {
    var response = mapper.toResponse(ProcessingJobs.queued(JOB_ID));

    assertThat(response.id()).isEqualTo(JOB_ID);
    assertThat(response.fileName()).isEqualTo("ads.csv");
    assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
    assertThat(response.sizeBytes()).isEqualTo(1000);
    assertThat(response.attempts()).isZero();
    assertThat(response.failureReason()).isNull();
    assertThat(response.rejectedSamples()).isEmpty();
  }
}
