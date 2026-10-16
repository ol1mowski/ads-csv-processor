package pl.oliwier.adscsv.service.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.service.transformation.AdRow;
import pl.oliwier.adscsv.service.transformation.ColumnTransformation;
import pl.oliwier.adscsv.service.transformation.CtrTransformation;
import pl.oliwier.adscsv.support.SampleData;

class RowTransformerTest {

  private final RowTransformer transformer = new RowTransformer(new CtrTransformation());

  @Test
  @DisplayName("A sample row is transformed with its CTR and parsed counters")
  void shouldTransformRowWhenAllFieldsAreValid() {
    var result = transformer.transform(line(SampleData.FIRST_ROW));

    assertThat(result).isEqualTo(new RowResult.Transformed("0.037225", 591, 22));
  }

  @Test
  @DisplayName("A row with zero impressions gets an empty CTR cell")
  void shouldReturnEmptyCtrWhenImpressionsAreZero() {
    var result = transformer.transform(line(SampleData.ZERO_IMPRESSIONS_ROW));

    assertThat(result).isEqualTo(new RowResult.Transformed("", 0, 0));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927;1",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927;",
        "5342",
        ";;;;;;;;"
      })
  @DisplayName("A row without exactly eight fields is rejected with FIELD_COUNT")
  void shouldRejectRowWhenFieldCountDiffers(String row) {
    assertThat(transformer.transform(line(row)))
        .isEqualTo(new RowResult.Rejected(RejectReason.FIELD_COUNT));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "\"5342\";11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927|INVALID_ADVERTISER_ID",
        "5342;-1;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927|INVALID_CAMPAIGN_ID",
        "5342;11380;;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927|INVALID_CREATIVE_ID",
        "5342;11380;526779;1-1-1-1-1;591;22;2.9798;0.1927|INVALID_AD_BOX_UUID",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;+591;22;2.9798;0.1927|INVALID_IMPRESSIONS",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;2e1;2.9798;0.1927|INVALID_CLICKS",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;1E+3;0.1927|INVALID_REVENUE",
        "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;=1+1|INVALID_ADVERTISER_MARGIN"
      })
  @DisplayName("A field outside its allow-list rejects the row with the column's reason")
  void shouldRejectRowWhenFieldIsInvalid(String row, RejectReason expected) {
    assertThat(transformer.transform(line(row))).isEqualTo(new RowResult.Rejected(expected));
  }

  @ParameterizedTest
  @CsvSource({"10, 11", "0, 1"})
  @DisplayName("More clicks than impressions is rejected, since a CTR above 100 % is impossible")
  void shouldRejectRowWhenClicksExceedImpressions(long impressions, long clicks) {
    assertThat(transformer.transform(line(SampleData.row(impressions, clicks))))
        .isEqualTo(new RowResult.Rejected(RejectReason.CLICKS_EXCEED_IMPRESSIONS));
  }

  @Test
  @DisplayName("As many clicks as impressions is accepted with a CTR of exactly 1")
  void shouldAcceptRowWhenClicksEqualImpressions() {
    assertThat(transformer.transform(line(SampleData.row(10, 10))))
        .isEqualTo(new RowResult.Transformed("1.000000", 10, 10));
  }

  @Test
  @DisplayName("A line flagged as too long is rejected with LINE_TOO_LONG")
  void shouldRejectLineWhenTooLong() {
    var line = new Line();
    line.set(new byte[0], 0, 0, true);

    assertThat(transformer.transform(line))
        .isEqualTo(new RowResult.Rejected(RejectReason.LINE_TOO_LONG));
  }

  @Test
  @DisplayName("The output header appends the derived column name")
  void shouldAppendColumnNameToOutputHeader() {
    assertThat(new String(transformer.outputHeader(), StandardCharsets.US_ASCII))
        .isEqualTo(SampleData.HEADER + ";ctr\n");
  }

  @Test
  @DisplayName("A different transformation plugs in without changing the row pipeline")
  void shouldUseInjectedTransformationWhenItIsReplaced() {
    var margin = new RowTransformer(new MarginAmountTransformation());

    assertThat(margin.transform(line(SampleData.FIRST_ROW)))
        .isEqualTo(new RowResult.Transformed("0.5742", 591, 22));
    assertThat(new String(margin.outputHeader(), StandardCharsets.US_ASCII))
        .endsWith(";marginAmount\n");
  }

  @Test
  @DisplayName("Fields are read from the line's own slice of a shared buffer")
  void shouldReadFieldsFromLineSliceWhenBufferIsShared() {
    var bytes = ("junk\n" + SampleData.FIRST_ROW + "\nmore").getBytes(StandardCharsets.US_ASCII);
    var line = new Line();
    line.set(bytes, 5, SampleData.FIRST_ROW.length(), false);

    assertThat(transformer.transform(line))
        .isEqualTo(new RowResult.Transformed("0.037225", 591, 22));
  }

  private static Line line(String text) {
    var bytes = text.getBytes(StandardCharsets.UTF_8);
    var line = new Line();
    line.set(bytes, 0, bytes.length, false);
    return line;
  }

  private static final class MarginAmountTransformation implements ColumnTransformation {

    @Override
    public String columnName() {
      return "marginAmount";
    }

    @Override
    public String apply(AdRow row) {
      return row.revenue()
          .multiply(row.advertiserMargin())
          .setScale(4, RoundingMode.HALF_UP)
          .toPlainString();
    }
  }
}
