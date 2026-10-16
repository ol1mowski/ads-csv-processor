package pl.oliwier.adscsv.service.csv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class AdsCsvFormat {

  public static final char SEPARATOR = ';';
  public static final int MAX_LINE_BYTES = 1024;
  static final int FIELD_COUNT = Column.values().length;
  static final String HEADER =
      Arrays.stream(Column.values())
          .map(Column::headerName)
          .collect(Collectors.joining(String.valueOf(SEPARATOR)));

  private static final byte[] HEADER_BYTES = HEADER.getBytes(StandardCharsets.US_ASCII);

  private AdsCsvFormat() {}

  public static boolean isHeader(Line line) {
    return Arrays.equals(
        line.buffer(), line.start(), line.end(), HEADER_BYTES, 0, HEADER_BYTES.length);
  }

  public static boolean isHeaderLine(byte[] raw) {
    try (var lines =
        new BoundedLineReader(new ByteArrayInputStream(raw), MAX_LINE_BYTES + 2, MAX_LINE_BYTES)) {
      return lines.next() && isHeader(lines.current());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
