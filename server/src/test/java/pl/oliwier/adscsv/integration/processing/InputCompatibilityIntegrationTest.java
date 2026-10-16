package pl.oliwier.adscsv.integration.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import pl.oliwier.adscsv.integration.ProcessingEnabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;

class InputCompatibilityIntegrationTest extends ProcessingEnabledTestBase {

  @Test
  @DisplayName("A spreadsheet export with a BOM and CRLF endings is processed into clean LF output")
  void shouldProcessExportWithBomAndCrlfLineEndings() {
    var csv =
        ("\uFEFF"
                + SampleData.HEADER
                + "\r\n"
                + SampleData.FIRST_ROW
                + "\r\n"
                + SampleData.row(100, 5)
                + "\r\n")
            .getBytes(StandardCharsets.UTF_8);
    var id = api.accepted("excel.csv", csv).id();

    var completed = awaitStatus(id, JobStatus.COMPLETED);

    assertThat(completed.rowsProcessed()).isEqualTo(2);
    assertThat(completed.rowsRejected()).isZero();
    assertThat(api.resultText(id))
        .isEqualTo(
            SampleData.HEADER
                + ";ctr\n"
                + SampleData.FIRST_ROW
                + ";0.037225\n"
                + SampleData.row(100, 5)
                + ";0.050000\n");
  }

  @Test
  @DisplayName("A Polish file name survives the round trip and is encoded in Content-Disposition")
  void shouldKeepPolishFileNameAndEncodeItInContentDisposition() {
    var id = api.accepted("raport_łódź.csv", SampleData.csv(SampleData.FIRST_ROW)).id();
    awaitStatus(id, JobStatus.COMPLETED);

    var disposition =
        api.result(id)
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseHeaders()
            .getFirst(HttpHeaders.CONTENT_DISPOSITION);

    assertThat(api.get(id).fileName()).isEqualTo("raport_łódź.csv");
    assertThat(disposition).contains("filename*=UTF-8''raport_%C5%82%C3%B3d%C5%BA-processed.csv");
  }
}
