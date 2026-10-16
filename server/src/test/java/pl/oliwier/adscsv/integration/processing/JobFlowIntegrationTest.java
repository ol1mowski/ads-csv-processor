package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class JobFlowIntegrationTest extends ProcessingEnabledTestBase {

  private static final String SAMPLE_NAME = "sample_ads_data.csv";
  private static final Pattern DERIVED_CTR = Pattern.compile("(\\d+\\.\\d{6})?");
  private static final Totals SAMPLE_TOTALS = new Totals("6733389", "176755", "0.026251");

  @Test
  @DisplayName("The sample is accepted with 202, processed to COMPLETED and its input is deleted")
  void shouldProcessSampleFileEndToEnd() {
    var exchange =
        api.upload(SAMPLE_NAME, SampleData.bytes())
            .expectStatus()
            .isAccepted()
            .expectBody(JobResponse.class)
            .returnResult();
    var accepted = exchange.getResponseBody();
    var id = accepted.id();

    assertThat(exchange.getResponseHeaders().getLocation()).hasToString(ApiRoutes.job(id));
    assertThat(accepted.status()).isEqualTo(JobStatus.QUEUED);
    assertThat(accepted.sizeBytes()).isEqualTo(SampleData.SIZE_BYTES);

    var completed = awaitStatus(id, JobStatus.COMPLETED);

    assertThat(completed.rowsProcessed()).isEqualTo(SampleData.ROWS);
    assertThat(completed.rowsRejected()).isZero();
    assertThat(completed.rejectedSamples()).isEmpty();
    assertThat(completed.bytesProcessed()).isEqualTo(SampleData.SIZE_BYTES);
    assertThat(completed.progressPercent()).isEqualTo(100);
    assertThat(completed.totals()).isEqualTo(SAMPLE_TOTALS);
    assertThat(completed.attempts()).isEqualTo(1);
    assertThat(completed.failureReason()).isNull();
    assertThat(completed.startedAt()).isNotNull();
    assertThat(completed.finishedAt()).isAfterOrEqualTo(completed.startedAt());
    assertThat(completed.resultUrl()).isEqualTo(ApiRoutes.result(id));
    await("input of a completed job is deleted")
        .untilAsserted(() -> assertThat(storage.input(id)).doesNotExist());
    assertThat(storage.output(id)).exists();
  }

  @Test
  @DisplayName("The sample result keeps every input line and appends the exact ctr column")
  void shouldServeSampleResultWithCtrColumn() {
    var sample = SampleData.bytes();
    var id = api.accepted(SAMPLE_NAME, sample).id();
    awaitStatus(id, JobStatus.COMPLETED);

    var result = api.result(id).expectStatus().isOk().expectBody().returnResult();
    var body = result.getResponseBody();
    var headers = result.getResponseHeaders();
    var lines = new String(body, StandardCharsets.US_ASCII).lines().toList();
    var inputLines = new String(sample, StandardCharsets.US_ASCII).lines().toList();

    assertThat(headers.getContentType()).isNotNull();
    assertThat(headers.getContentType().isCompatibleWith(JobsApi.TEXT_CSV)).isTrue();
    assertThat(headers.getContentLength()).isEqualTo(body.length);
    assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment")
        .contains("filename*=UTF-8''sample_ads_data-processed.csv");
    assertThat(body[body.length - 1]).isEqualTo((byte) '\n');
    assertThat(lines).hasSize(1001);
    assertThat(lines.getFirst()).isEqualTo(SampleData.HEADER + ";ctr");
    assertThat(lines.get(1)).endsWith(";0.037225");
    assertThat(lines.get(2)).endsWith(";0.047619");
    assertThat(lines.get(3)).endsWith(";0.028525");
    assertThat(lines.get(217)).endsWith(";");
    assertThat(lines.stream().skip(1).filter(line -> line.endsWith(";"))).hasSize(11);
    for (var i = 1; i < lines.size(); i++) {
      var input = inputLines.get(i);
      assertThat(lines.get(i)).as("line %d", i + 1).startsWith(input + ";");
      assertThat(lines.get(i).substring(input.length() + 1))
          .as("line %d", i + 1)
          .matches(DERIVED_CTR);
    }
  }

  @Test
  @DisplayName("Every job endpoint answers an unknown id with 404 problem+json")
  void shouldReturnProblemJson404ForUnknownJob() {
    var unknown = UUID.randomUUID();

    api.job(unknown)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_FOUND");
    api.cancel(unknown)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_FOUND");
    api.result(unknown)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("JOB_NOT_FOUND");
  }

  @Test
  @DisplayName("The health endpoint reports UP with the database connected")
  void shouldReportHealthUp() {
    api.health().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
  }
}
