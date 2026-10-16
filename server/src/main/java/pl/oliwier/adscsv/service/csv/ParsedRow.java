package pl.oliwier.adscsv.service.csv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import pl.oliwier.adscsv.service.transformation.AdRow;

final class ParsedRow implements AdRow {

  private final byte[] bytes;
  private final int[] fieldStarts;
  private final long impressions;
  private final long clicks;

  ParsedRow(byte[] bytes, int[] fieldStarts) {
    this.bytes = bytes;
    this.fieldStarts = fieldStarts;
    this.impressions = parseCount(Column.IMPRESSIONS);
    this.clicks = parseCount(Column.CLICKS);
  }

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
    return parseDecimal(Column.REVENUE);
  }

  @Override
  public BigDecimal advertiserMargin() {
    return parseDecimal(Column.ADVERTISER_MARGIN);
  }

  static int from(int[] fieldStarts, Column column) {
    return fieldStarts[column.ordinal()];
  }

  static int to(int[] fieldStarts, Column column) {
    return fieldStarts[column.ordinal() + 1] - 1;
  }

  private long parseCount(Column column) {
    var value = 0L;
    for (var i = from(fieldStarts, column); i < to(fieldStarts, column); i++) {
      value = value * 10 + (bytes[i] - '0');
    }
    return value;
  }

  private BigDecimal parseDecimal(Column column) {
    var from = from(fieldStarts, column);
    return new BigDecimal(
        new String(bytes, from, to(fieldStarts, column) - from, StandardCharsets.US_ASCII));
  }
}
