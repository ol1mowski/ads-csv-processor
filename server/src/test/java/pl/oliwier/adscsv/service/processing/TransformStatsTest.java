package pl.oliwier.adscsv.service.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;

class TransformStatsTest {

  @Test
  @DisplayName("Only the configured number of rejected samples is kept")
  void shouldKeepAtMostConfiguredSamples() {
    var stats = new TransformStats(2, 1000, 0.01);

    stats.reject(2, RejectReason.FIELD_COUNT);
    stats.reject(3, RejectReason.INVALID_CLICKS);
    stats.reject(4, RejectReason.LINE_TOO_LONG);

    assertThat(stats.samples())
        .containsExactly(
            new RejectedRow(2, RejectReason.FIELD_COUNT),
            new RejectedRow(3, RejectReason.INVALID_CLICKS));
    assertThat(stats.snapshot().rowsRejected()).isEqualTo(3);
  }

  @Test
  @DisplayName("Totals overflow fails loudly instead of wrapping around")
  void shouldThrowWhenTotalsOverflow() {
    var stats = new TransformStats(10, 1000, 0.01);
    stats.accept(Long.MAX_VALUE, 0);

    assertThatThrownBy(() -> stats.accept(1, 0)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  @DisplayName("Snapshot carries counters, totals and the last consumed byte offset")
  void shouldSnapshotCountersAndTotals() {
    var stats = new TransformStats(10, 1000, 0.01);
    stats.accept(591, 22);
    stats.accept(2289, 109);
    stats.reject(4, RejectReason.FIELD_COUNT);
    stats.consumed(300);

    assertThat(stats.snapshot()).isEqualTo(new ProgressSnapshot(300, 2, 1, 2880, 131));
  }

  @ParameterizedTest(name = "{0} accepted, {1} rejected -> {2}")
  @CsvSource({
    "0, 1000, false",
    "0, 1001, true",
    "99000, 1001, true",
    "99100, 1001, false",
    "200000, 1001, false"
  })
  @DisplayName(
      "Mid-file, rejections abort the job only above both the absolute and the ratio limit")
  void shouldFailOnlyAboveBothThresholds(long accepted, long rejected, boolean tooMany) {
    var stats = new TransformStats(10, 1000, 0.01);
    for (var i = 0; i < accepted; i++) {
      stats.accept(1, 0);
    }
    for (var i = 0; i < rejected; i++) {
      stats.reject(i, RejectReason.FIELD_COUNT);
    }

    assertThat(stats.rejectedTooMany()).isEqualTo(tooMany);
  }

  @ParameterizedTest(name = "{0} accepted, {1} rejected -> {2}")
  @CsvSource({"0, 0, false", "99, 1, false", "98, 2, true", "1, 999, true", "99100, 1001, false"})
  @DisplayName("At the end of the file the ratio alone decides, whatever the number of rejections")
  void shouldJudgeRatioAloneAtEndOfFile(long accepted, long rejected, boolean aboveRatio) {
    var stats = new TransformStats(10, 1000, 0.01);
    for (var i = 0; i < accepted; i++) {
      stats.accept(1, 0);
    }
    for (var i = 0; i < rejected; i++) {
      stats.reject(i, RejectReason.FIELD_COUNT);
    }

    assertThat(stats.rejectedAboveRatio()).isEqualTo(aboveRatio);
  }

  @Test
  @DisplayName("Only-rejected input is detected, empty input is not")
  void shouldDetectOnlyRejectedRows() {
    var stats = new TransformStats(10, 1000, 0.01);
    assertThat(stats.hasOnlyRejected()).isFalse();

    stats.reject(2, RejectReason.FIELD_COUNT);
    assertThat(stats.hasOnlyRejected()).isTrue();

    stats.accept(1, 1);
    assertThat(stats.hasOnlyRejected()).isFalse();
  }
}
