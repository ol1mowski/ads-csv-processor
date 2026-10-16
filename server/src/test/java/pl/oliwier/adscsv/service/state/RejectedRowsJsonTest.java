package pl.oliwier.adscsv.service.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;
import tools.jackson.databind.json.JsonMapper;

class RejectedRowsJsonTest {

  private final RejectedRowsJson json = new RejectedRowsJson(JsonMapper.builder().build());

  @Test
  @DisplayName("Samples survive a JSON round trip")
  void shouldReadWhatWasWritten() {
    var rows =
        List.of(
            new RejectedRow(5, RejectReason.FIELD_COUNT),
            new RejectedRow(123_456_789, RejectReason.INVALID_ADVERTISER_MARGIN));

    var written = json.write(rows);

    assertThat(written)
        .isEqualTo(
            "[{\"line\":5,\"reason\":\"FIELD_COUNT\"},"
                + "{\"line\":123456789,\"reason\":\"INVALID_ADVERTISER_MARGIN\"}]");
    assertThat(json.read(written)).isEqualTo(rows);
  }

  @Test
  @DisplayName("Fifteen worst-case samples fit the 1024-character column")
  void shouldFitColumnWhenSamplesAreAtConfiguredMaximum() {
    var rows =
        Collections.nCopies(
            15, new RejectedRow(999_999_999L, RejectReason.INVALID_ADVERTISER_MARGIN));

    assertThat(json.write(rows)).hasSizeLessThanOrEqualTo(1024);
  }

  @Test
  @DisplayName("Missing samples read as an empty list")
  void shouldReturnEmptyListWhenColumnIsNull() {
    assertThat(json.read(null)).isEmpty();
    assertThat(json.read(" ")).isEmpty();
    assertThat(json.read(json.write(List.of()))).isEmpty();
  }
}
