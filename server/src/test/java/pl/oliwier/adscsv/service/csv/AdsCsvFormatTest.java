package pl.oliwier.adscsv.service.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.oliwier.adscsv.support.SampleData;

class AdsCsvFormatTest {

  private static final String BOM = "﻿";

  @Test
  @DisplayName("The expected header lists the eight sample columns in order")
  void shouldExposeSampleHeader() {
    assertThat(AdsCsvFormat.HEADER).isEqualTo(SampleData.HEADER);
    assertThat(AdsCsvFormat.FIELD_COUNT).isEqualTo(8);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", ""})
  @DisplayName("The raw first line matches with LF, CRLF or no terminator")
  void shouldAcceptHeaderWhenTerminatorVaries(String terminator) {
    assertThat(isHeaderLine(SampleData.HEADER + terminator)).isTrue();
  }

  @Test
  @DisplayName("A UTF-8 BOM before the header is accepted")
  void shouldAcceptHeaderWhenPrefixedWithBom() {
    assertThat(isHeaderLine(BOM + SampleData.HEADER + "\r\n")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "campaignId;advertiserId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin",
        "advertiserid;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin",
        "advertiserId,campaignId,creativeId,adBoxUUID,impressions,clicks,revenue,advertiserMargin",
        "advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin;x",
        "advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue",
        " advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin",
        ""
      })
  @DisplayName("Any other first line is not the header")
  void shouldRejectHeaderWhenColumnsDiffer(String line) {
    assertThat(isHeaderLine(line + "\n")).isFalse();
  }

  @Test
  @DisplayName("A double BOM is not stripped twice")
  void shouldRejectHeaderWhenBomIsRepeated() {
    assertThat(isHeaderLine(BOM + BOM + SampleData.HEADER + "\n")).isFalse();
  }

  @Test
  @DisplayName("The reader's first line is recognised as the header after BOM and CR removal")
  void shouldRecogniseHeaderLineFromReader() throws IOException {
    var bytes = (BOM + SampleData.HEADER + "\r\n").getBytes(StandardCharsets.UTF_8);
    try (var reader = new BoundedLineReader(new ByteArrayInputStream(bytes), 4096, 1024)) {
      assertThat(reader.next()).isTrue();
      assertThat(AdsCsvFormat.isHeader(reader.current())).isTrue();
    }
  }

  private static boolean isHeaderLine(String text) {
    var bytes = text.getBytes(StandardCharsets.UTF_8);
    return AdsCsvFormat.isHeaderLine(bytes);
  }
}
