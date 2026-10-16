package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;
import pl.oliwier.adscsv.support.SampleData;

class JobOutcomeIntegrationTest extends ProcessingEnabledTestBase {

  private static final String SEVEN_FIELDS =
      "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798";
  private static final String BAD_IMPRESSIONS =
      "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;59x;22;2.9798;0.1927";
  private static final String BAD_UUID = "5342;11380;526779;not-a-uuid;591;22;2.9798;0.1927";
  private static final String MORE_CLICKS_THAN_IMPRESSIONS = SampleData.row(10, 11);
  private static final String[] MIXED_ROWS = {
    SampleData.FIRST_ROW,
    SEVEN_FIELDS,
    SampleData.row(100, 5),
    BAD_IMPRESSIONS,
    SampleData.ZERO_IMPRESSIONS_ROW,
    BAD_UUID,
    MORE_CLICKS_THAN_IMPRESSIONS
  };
  private static final List<RejectedRow> MIXED_REJECTIONS =
      List.of(
          new RejectedRow(3, RejectReason.FIELD_COUNT),
          new RejectedRow(5, RejectReason.INVALID_IMPRESSIONS),
          new RejectedRow(7, RejectReason.INVALID_AD_BOX_UUID),
          new RejectedRow(8, RejectReason.CLICKS_EXCEED_IMPRESSIONS));
  private static final int FILLER_ROWS = 400;

  @Test
  @DisplayName("Up to 1 % invalid rows are skipped and reported with their physical line numbers")
  void shouldCompleteAndReportRejectedRowsWithLineNumbers() {
    var rows = new ArrayList<>(List.of(MIXED_ROWS));
    rows.addAll(Collections.nCopies(FILLER_ROWS, SampleData.ZERO_IMPRESSIONS_ROW));
    var id = api.accepted("mixed.csv", SampleData.csv(rows.toArray(String[]::new))).id();

    var completed = awaitStatus(id, JobStatus.COMPLETED);

    assertThat(completed.rowsProcessed()).isEqualTo(3 + FILLER_ROWS);
    assertThat(completed.rowsRejected()).isEqualTo(4);
    assertThat(completed.rejectedSamples()).isEqualTo(MIXED_REJECTIONS);
    assertThat(completed.totals()).isEqualTo(new Totals("691", "27", "0.039074"));
    var output = api.resultText(id).lines().toList();
    assertThat(output).hasSize(1 + 3 + FILLER_ROWS);
    assertThat(output.subList(0, 4))
        .containsExactly(
            SampleData.HEADER + ";ctr",
            SampleData.FIRST_ROW + ";0.037225",
            SampleData.row(100, 5) + ";0.050000",
            SampleData.ZERO_IMPRESSIONS_ROW + ";");
  }

  @Test
  @DisplayName(
      "More than 1 % invalid rows fails the job at the end of the file and drops its files")
  void shouldFailWhenRejectedRatioIsExceededAtEndOfFile() {
    var id = api.accepted("mostly-broken.csv", SampleData.csv(MIXED_ROWS)).id();

    var failed = awaitStatus(id, JobStatus.FAILED);

    assertThat(failed.failureReason()).isEqualTo(FailureReason.TOO_MANY_INVALID_ROWS);
    assertThat(failed.rowsProcessed()).isEqualTo(3);
    assertThat(failed.rowsRejected()).isEqualTo(4);
    assertThat(failed.rejectedSamples()).isEqualTo(MIXED_REJECTIONS);
    assertThat(failed.resultUrl()).isNull();
    await("directory of a failed job is removed")
        .untilAsserted(() -> assertThat(storage.jobDirectory(id)).doesNotExist());
  }

  @Test
  @DisplayName(
      "A file without a single valid row fails with NO_VALID_ROWS and its files are removed")
  void shouldFailWithNoValidRowsAndDiscardFiles() {
    var id = api.accepted("broken.csv", SampleData.csv(SEVEN_FIELDS, BAD_UUID)).id();

    var failed = awaitStatus(id, JobStatus.FAILED);

    assertThat(failed.failureReason()).isEqualTo(FailureReason.NO_VALID_ROWS);
    assertThat(failed.rowsProcessed()).isZero();
    assertThat(failed.rowsRejected()).isEqualTo(2);
    assertThat(failed.resultUrl()).isNull();
    assertThat(failed.finishedAt()).isNotNull();
    await("directory of a failed job is removed")
        .untilAsserted(() -> assertThat(storage.jobDirectory(id)).doesNotExist());
    api.result(id)
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RESULT_NOT_AVAILABLE")
        .jsonPath("$.currentStatus")
        .isEqualTo("FAILED");
    api.cancel(id)
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_CANCELLABLE")
        .jsonPath("$.currentStatus")
        .isEqualTo("FAILED");
  }

  @Test
  @DisplayName("A file with only the header completes with zero rows and an undefined total ctr")
  void shouldCompleteWithZeroRowsWhenFileHasOnlyHeader() {
    var id = api.accepted("empty-report.csv", SampleData.csv()).id();

    var completed = awaitStatus(id, JobStatus.COMPLETED);

    assertThat(completed.rowsProcessed()).isZero();
    assertThat(completed.rowsRejected()).isZero();
    assertThat(completed.progressPercent()).isEqualTo(100);
    assertThat(completed.totals()).isEqualTo(new Totals("0", "0", null));
    assertThat(api.resultText(id)).isEqualTo(SampleData.HEADER + ";ctr\n");
  }

  @Test
  @DisplayName("Cancelling a completed job is a 409 conflict that leaves the result intact")
  void shouldRejectCancellationOfCompletedJob() {
    var id = api.accepted("done.csv", SampleData.csv(SampleData.FIRST_ROW)).id();
    awaitStatus(id, JobStatus.COMPLETED);

    api.cancel(id)
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_CANCELLABLE")
        .jsonPath("$.currentStatus")
        .isEqualTo("COMPLETED");

    assertThat(api.get(id).status()).isEqualTo(JobStatus.COMPLETED);
    assertThat(api.resultText(id))
        .isEqualTo(SampleData.HEADER + ";ctr\n" + SampleData.FIRST_ROW + ";0.037225\n");
  }

  @Test
  @DisplayName("A completed job whose result file vanished answers 410 instead of a server error")
  void shouldReturn410WhenResultFileDisappears() throws IOException {
    var id = api.accepted("vanishing.csv", SampleData.csv(SampleData.FIRST_ROW)).id();
    awaitStatus(id, JobStatus.COMPLETED);

    Files.delete(storage.output(id));

    api.result(id)
        .expectStatus()
        .isEqualTo(HttpStatus.GONE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RESULT_GONE");
  }

  @Test
  @DisplayName("Rejected row samples never contain raw row data")
  void shouldNotExposeRawRowDataInRejectedSamples() {
    var id = api.accepted("leaky.csv", SampleData.csv(SampleData.FIRST_ROW, BAD_UUID)).id();
    awaitStatus(id, JobStatus.FAILED);

    var body =
        new String(
            api.job(id).expectStatus().isOk().expectBody().returnResult().getResponseBody(),
            StandardCharsets.UTF_8);

    assertThat(body).doesNotContain("not-a-uuid");
    assertThat(api.get(id).rejectedSamples())
        .isEqualTo(List.of(new RejectedRow(3, RejectReason.INVALID_AD_BOX_UUID)));
  }
}
