package pl.oliwier.adscsv.service.transformation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CtrTransformationTest {

  private static final Locale DEFAULT_LOCALE = Locale.getDefault();

  private final CtrTransformation transformation = new CtrTransformation();

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(DEFAULT_LOCALE);
  }

  @ParameterizedTest(name = "{0} / {1} = {2}")
  @CsvSource({
    "22, 591, 0.037225",
    "109, 2289, 0.047619",
    "35, 1227, 0.028525",
    "2, 451, 0.004435",
    "551, 28418, 0.019389",
    "0, 591, 0.000000",
    "1, 128, 0.007813",
    "5, 5, 1.000000",
    "7, 5, 1.400000",
    "1, 9999999999, 0.000000"
  })
  @DisplayName("CTR is clicks / impressions with scale 6 and HALF_UP rounding")
  void shouldComputeCtrWhenImpressionsArePositive(long clicks, long impressions, String expected) {
    assertThat(transformation.apply(row(impressions, clicks))).isEqualTo(expected);
  }

  @Test
  @DisplayName("CTR is an empty cell when impressions are zero")
  void shouldReturnEmptyCellWhenImpressionsAreZero() {
    assertThat(transformation.apply(row(0, 0))).isEmpty();
    assertThat(Ctr.of(0, 0)).isEmpty();
  }

  @Test
  @DisplayName("CTR uses a dot separator regardless of the default locale")
  void shouldIgnoreDefaultLocaleWhenFormatting() {
    Locale.setDefault(Locale.forLanguageTag("pl-PL"));

    assertThat(transformation.apply(row(591, 22))).isEqualTo("0.037225");
  }

  @Test
  @DisplayName("Totals of the sample give the ratio of sums")
  void shouldComputeRatioOfSumsForSampleTotals() {
    assertThat(Ctr.of(176_755, 6_733_389)).contains("0.026251");
  }

  @Test
  @DisplayName("The derived column is named ctr")
  void shouldNameColumnCtr() {
    assertThat(transformation.columnName()).isEqualTo("ctr");
  }

  private static AdRow row(long impressions, long clicks) {
    return new AdRow() {
      @Override
      public long impressions() {
        return impressions;
      }

      @Override
      public long clicks() {
        return clicks;
      }

      @Override
      public BigDecimal revenue() {
        return BigDecimal.ZERO;
      }

      @Override
      public BigDecimal advertiserMargin() {
        return BigDecimal.ZERO;
      }
    };
  }
}
