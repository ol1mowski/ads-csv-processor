package pl.oliwier.adscsv.service.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldRuleTest {

  @ParameterizedTest(name = "{0} accepts [{1}] = {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "ID|5342|true",
        "ID|123456789012345678|true",
        "ID|1234567890123456789|false",
        "ID|+5|false",
        "ID|-1|false",
        "ID|' 5'|false",
        "ID|''|false",
        "ID|5.0|false",
        "COUNT|9999999999|true",
        "COUNT|0|true",
        "COUNT|12345678901|false",
        "COUNT|1e3|false",
        "UUID|287d06ca-6f4c-469a-8b22-d3081c8eaee9|true",
        "UUID|287D06CA-6F4C-469A-8B22-D3081C8EAEE9|true",
        "UUID|1-1-1-1-1|false",
        "UUID|287d06ca6f4c469a8b22d3081c8eaee9|false",
        "UUID|287d06ca-6f4c-469a-8b22-d3081c8eaeg9|false",
        "UUID|287d06ca-6f4c-469a-8b22-d3081c8eae9|false",
        "UUID|287d06ca-6f4c-469a-8b22-d3081c8eaee90|false",
        "UUID|287d06ca+6f4c-469a-8b22-d3081c8eaee9|false",
        "DECIMAL|3|true",
        "DECIMAL|2.9798|true",
        "DECIMAL|403.94700000|true",
        "DECIMAL|1E+3|false",
        "DECIMAL|+2.5|false",
        "DECIMAL|1,5000|false",
        "DECIMAL|-1.0000|false",
        "DECIMAL|1.|false",
        "DECIMAL|.5|false",
        "DECIMAL|1.123456789|false",
        "DECIMAL|1.2.3|false",
        "DECIMAL|1234567890123456|false"
      })
  @DisplayName("Field rules accept only their allow-listed shape")
  void shouldMatchAllowListWhenValidatingField(FieldRule rule, String value, boolean expected) {
    var bytes = value.getBytes(StandardCharsets.US_ASCII);

    assertThat(rule.accepts(bytes, 0, bytes.length)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Non-ASCII bytes are rejected by every rule")
  void shouldRejectNonAsciiBytes() {
    var bytes = "\u0661\u0662".getBytes(StandardCharsets.UTF_8);

    for (var rule : FieldRule.values()) {
      assertThat(rule.accepts(bytes, 0, bytes.length)).as(rule.name()).isFalse();
    }
  }

  @Test
  @DisplayName("Rules validate only the given slice of a shared buffer")
  void shouldValidateOnlyTheGivenRange() {
    var bytes = "x12y".getBytes(StandardCharsets.US_ASCII);

    assertThat(FieldRule.COUNT.accepts(bytes, 1, 3)).isTrue();
    assertThat(FieldRule.COUNT.accepts(bytes, 0, 3)).isFalse();
  }
}
