package pl.oliwier.adscsv.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.oliwier.adscsv.service.csv.AdsCsvFormat;
import pl.oliwier.adscsv.service.csv.BoundedLineReader;
import pl.oliwier.adscsv.service.csv.RowResult;
import pl.oliwier.adscsv.service.csv.RowTransformer;
import pl.oliwier.adscsv.service.transformation.CtrTransformation;

class AdsCsvGeneratorTest {

  private static final long ONE_MIB = 1L << 20;

  @Test
  @DisplayName(
      "The stream yields exactly the reported number of bytes without exceeding the target")
  void shouldStreamExactlyTheReportedByteLength() throws IOException {
    var generator = AdsCsvGenerator.generate(ONE_MIB, 42);

    var bytes = readAll(generator.openStream());

    assertThat((long) bytes.length).isEqualTo(generator.byteLength());
    assertThat(generator.byteLength()).isLessThanOrEqualTo(ONE_MIB).isGreaterThan(ONE_MIB - 200);
    assertThat(
            new String(bytes, 0, AdsCsvGenerator.HEADER_LINE.length(), StandardCharsets.US_ASCII))
        .isEqualTo(AdsCsvGenerator.HEADER_LINE);
  }

  @Test
  @DisplayName("The same seed reproduces identical bytes and a different seed does not")
  void shouldBeDeterministicForTheSameSeed() throws IOException {
    var first = readAll(AdsCsvGenerator.generate(256 * 1024, 7).openStream());
    var again = readAll(AdsCsvGenerator.generate(256 * 1024, 7).openStream());
    var other = readAll(AdsCsvGenerator.generate(256 * 1024, 8).openStream());

    assertThat(again).isEqualTo(first);
    assertThat(other).isNotEqualTo(first);
  }

  @Test
  @DisplayName("Single-byte reads return the same bytes as bulk reads")
  void shouldReturnSameBytesForSingleByteReads() throws IOException {
    var generator = AdsCsvGenerator.generate(8 * 1024, 3);
    var bulk = readAll(generator.openStream());

    try (var in = generator.openStream()) {
      for (var expected : bulk) {
        assertThat(in.read()).isEqualTo(expected & 0xFF);
      }
      assertThat(in.read()).isEqualTo(-1);
    }
  }

  @Test
  @DisplayName("The production parser agrees with the report on rows, rejections and totals")
  void shouldReportWhatTheProductionParserReads() throws IOException {
    var generator = AdsCsvGenerator.generate(2 * ONE_MIB, 11, 97);
    var report = generator.report();
    var transformer = new RowTransformer(new CtrTransformation());
    long valid = 0;
    long invalid = 0;
    long impressions = 0;
    long clicks = 0;

    try (var lines =
        new BoundedLineReader(generator.openStream(), 64 * 1024, AdsCsvFormat.MAX_LINE_BYTES)) {
      assertThat(lines.next()).isTrue();
      assertThat(AdsCsvFormat.isHeader(lines.current())).isTrue();
      while (lines.next()) {
        switch (transformer.transform(lines.current())) {
          case RowResult.Transformed row -> {
            valid++;
            impressions += row.impressions();
            clicks += row.clicks();
          }
          case RowResult.Rejected _ -> invalid++;
        }
      }
      assertThat(lines.bytesConsumed()).isEqualTo(report.byteLength());
    }

    assertThat(report.invalidRows()).isPositive();
    assertThat(valid).isEqualTo(report.validRows());
    assertThat(invalid).isEqualTo(report.invalidRows());
    assertThat(impressions).isEqualTo(report.totalImpressions());
    assertThat(clicks).isEqualTo(report.totalClicks());
  }

  @Test
  @DisplayName("The command line writes the same bytes the stream produces")
  void shouldWriteFileFromCommandLine(@TempDir Path directory) throws IOException {
    var out = directory.resolve("ads.csv");

    AdsCsvGenerator.main(new String[] {"--size", "64KiB", "--seed", "5", "--out", out.toString()});

    assertThat(Files.readAllBytes(out))
        .isEqualTo(readAll(AdsCsvGenerator.generate(64 * 1024, 5).openStream()));
  }

  @ParameterizedTest
  @CsvSource({"123, 123", "2B, 2", "3KiB, 3072", "5MiB, 5242880", "5GiB, 5368709120"})
  @DisplayName("Sizes accept bytes and binary units")
  void shouldParseBinarySizes(String text, long expected) {
    assertThat(AdsCsvGenerator.parseSize(text)).isEqualTo(expected);
  }

  @Test
  @DisplayName("A target smaller than the header line is rejected")
  void shouldRejectTargetSmallerThanHeader() {
    assertThatThrownBy(() -> AdsCsvGenerator.generate(10, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] readAll(InputStream in) throws IOException {
    try (in) {
      return in.readAllBytes();
    }
  }
}
