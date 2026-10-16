package pl.oliwier.adscsv.service.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoundedLineReaderTest {

  private static final int MIB = 1 << 20;

  @Test
  @DisplayName("LF, CRLF and a final line without terminator are all read as lines")
  void shouldReadLinesWhenTerminatorsAreMixed() throws IOException {
    var lines = readAll("a;1\nb;2\r\nc;3", 64, 16);

    assertThat(lines).containsExactly("1:a;1", "2:b;2", "3:c;3");
  }

  @Test
  @DisplayName("A BOM is skipped on the first line only but still counted as consumed bytes")
  void shouldSkipBomOnFirstLineOnly() throws IOException {
    var input = "﻿head\n﻿row\n";
    var bytes = input.getBytes(StandardCharsets.UTF_8);
    try (var reader = reader(bytes, 64, 16)) {
      reader.next();
      assertThat(text(reader.current())).isEqualTo("head");
      reader.next();
      assertThat(text(reader.current())).isEqualTo("﻿row");
      assertThat(reader.next()).isFalse();
      assertThat(reader.bytesConsumed()).isEqualTo(bytes.length);
    }
  }

  @Test
  @DisplayName("Empty lines and a lone CR are returned as empty, non-record lines")
  void shouldReturnEmptyLinesAsNonRecords() throws IOException {
    try (var reader = reader("a\n\n\r\nb\n".getBytes(StandardCharsets.US_ASCII), 64, 16)) {
      var records = new ArrayList<String>();
      while (reader.next()) {
        if (reader.current().isRecord()) {
          records.add(reader.lineNumber() + ":" + text(reader.current()));
        }
      }
      assertThat(records).containsExactly("1:a", "4:b");
    }
  }

  @Test
  @DisplayName("An empty stream yields no lines")
  void shouldReturnNoLinesWhenStreamIsEmpty() throws IOException {
    try (var reader = reader(new byte[0], 64, 16)) {
      assertThat(reader.next()).isFalse();
      assertThat(reader.bytesConsumed()).isZero();
      assertThat(reader.lineNumber()).isZero();
    }
  }

  @Test
  @DisplayName("Lines crossing the 1 MiB buffer boundary are reassembled exactly")
  void shouldReassembleLinesWhenTheyCrossBufferBoundary() throws IOException {
    var out = new ByteArrayOutputStream();
    var expected = new ArrayList<String>();
    for (var i = 0; out.size() < 3 * MIB; i++) {
      var line = "row-" + i + "-" + "x".repeat(i % 97);
      expected.add(line);
      out.writeBytes((line + "\n").getBytes(StandardCharsets.US_ASCII));
    }
    var bytes = out.toByteArray();

    try (var reader = reader(bytes, MIB, 1024)) {
      var actual = new ArrayList<String>();
      while (reader.next()) {
        actual.add(text(reader.current()));
      }
      assertThat(actual).isEqualTo(expected);
      assertThat(reader.bytesConsumed()).isEqualTo(bytes.length);
      assertThat(reader.lineNumber()).isEqualTo(expected.size());
    }
  }

  @Test
  @DisplayName("A 10 MB line without newline is flagged too long and skipped in bounded memory")
  void shouldFlagAndSkipLineWhenItExceedsLimit() throws IOException {
    var longLength = 10_000_000L;
    var tail = "\nnext\n".getBytes(StandardCharsets.US_ASCII);
    InputStream in =
        new SequenceInputStream(
            new RepeatingInputStream(longLength), new ByteArrayInputStream(tail));

    try (var reader = new BoundedLineReader(in, MIB, 1024)) {
      assertThat(reader.next()).isTrue();
      assertThat(reader.current().isTooLong()).isTrue();
      assertThat(reader.current().isRecord()).isTrue();
      assertThat(reader.lineNumber()).isEqualTo(1);

      var continuations = 0;
      while (reader.next() && reader.lineNumber() == 1) {
        continuations++;
        assertThat(reader.current().isRecord()).isFalse();
      }

      assertThat(continuations).isGreaterThanOrEqualTo(9);
      assertThat(text(reader.current())).isEqualTo("next");
      assertThat(reader.lineNumber()).isEqualTo(2);
      assertThat(reader.next()).isFalse();
      assertThat(reader.bytesConsumed()).isEqualTo(longLength + tail.length);
    }
  }

  @Test
  @DisplayName("A line of exactly the maximum length is accepted, one byte more is too long")
  void shouldApplyLimitToContentBeforeNewline() throws IOException {
    var lines = readAll("12345678\n123456789\nok\n", 32, 8);

    assertThat(lines).containsExactly("1:12345678", "2:<too long>", "3:ok");
  }

  @Test
  @DisplayName("A too long last line without terminator ends the stream cleanly")
  void shouldEndCleanlyWhenTooLongLineHasNoTerminator() throws IOException {
    var lines = readAll("ok\n" + "x".repeat(100), 16, 8);

    assertThat(lines).containsExactly("1:ok", "2:<too long>");
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 7, 42, 1234, 99_999})
  @DisplayName("Random input read through tiny buffers matches a reference splitter")
  void shouldMatchReferenceSplitterWhenInputIsRandom(long seed) throws IOException {
    var random = new Random(seed);
    var maxLine = 1 + random.nextInt(12);
    var bufferSize = maxLine + 2 + random.nextInt(20);
    var bytes = randomInput(random, 2000);

    assertThat(readAll(new TrickleInputStream(bytes, random), bufferSize, maxLine))
        .isEqualTo(reference(bytes, maxLine));
    assertThat(readAll(new ByteArrayInputStream(bytes), bufferSize, maxLine))
        .isEqualTo(reference(bytes, maxLine));
  }

  @Test
  @DisplayName("A buffer smaller than the longest line plus terminator is rejected")
  void shouldRejectBufferThatCannotHoldALine() {
    var in = new ByteArrayInputStream(new byte[0]);

    assertThatThrownBy(() -> new BoundedLineReader(in, 9, 8))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static BoundedLineReader reader(byte[] bytes, int bufferSize, int maxLine) {
    return new BoundedLineReader(new ByteArrayInputStream(bytes), bufferSize, maxLine);
  }

  private static List<String> readAll(String input, int bufferSize, int maxLine)
      throws IOException {
    return readAll(
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), bufferSize, maxLine);
  }

  private static String text(Line line) {
    return new String(
        line.buffer(), line.start(), line.end() - line.start(), StandardCharsets.UTF_8);
  }

  private static List<String> readAll(InputStream in, int bufferSize, int maxLine)
      throws IOException {
    var lines = new ArrayList<String>();
    try (var reader = new BoundedLineReader(in, bufferSize, maxLine)) {
      var previousLineNumber = 0L;
      while (reader.next()) {
        if (reader.lineNumber() == previousLineNumber) {
          continue;
        }
        previousLineNumber = reader.lineNumber();
        var line = reader.current();
        lines.add(reader.lineNumber() + ":" + (line.isTooLong() ? "<too long>" : text(line)));
      }
    }
    return lines;
  }

  private static List<String> reference(byte[] bytes, int maxLine) {
    var lines = new ArrayList<String>();
    var text = new String(bytes, StandardCharsets.ISO_8859_1);
    var parts = new ArrayList<>(List.of(text.split("\n", -1)));
    if (parts.getLast().isEmpty()) {
      parts.removeLast();
    }
    for (var i = 0; i < parts.size(); i++) {
      var content = parts.get(i);
      if (content.length() > maxLine) {
        lines.add((i + 1) + ":<too long>");
        continue;
      }
      var stripped = content.endsWith("\r") ? content.substring(0, content.length() - 1) : content;
      lines.add((i + 1) + ":" + stripped);
    }
    return lines;
  }

  private static byte[] randomInput(Random random, int length) {
    var alphabet = "ab;\r\n\n".getBytes(StandardCharsets.US_ASCII);
    var bytes = new byte[length];
    for (var i = 0; i < length; i++) {
      bytes[i] = random.nextInt(10) == 0 ? (byte) 'x' : alphabet[random.nextInt(alphabet.length)];
    }
    var runStart = random.nextInt(length / 2);
    for (var i = runStart; i < runStart + 40; i++) {
      bytes[i] = 'z';
    }
    return bytes;
  }

  private static final class RepeatingInputStream extends InputStream {

    private long remaining;

    RepeatingInputStream(long length) {
      this.remaining = length;
    }

    @Override
    public int read() {
      if (remaining == 0) {
        return -1;
      }
      remaining--;
      return 'x';
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (remaining == 0) {
        return -1;
      }
      var n = (int) Math.min(length, remaining);
      Arrays.fill(buffer, offset, offset + n, (byte) 'x');
      remaining -= n;
      return n;
    }
  }

  private static final class TrickleInputStream extends InputStream {

    private final byte[] bytes;
    private final Random random;
    private int position;

    TrickleInputStream(byte[] bytes, Random random) {
      this.bytes = bytes;
      this.random = random;
    }

    @Override
    public int read() {
      return position < bytes.length ? bytes[position++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (position == bytes.length) {
        return -1;
      }
      var n = Math.min(Math.min(length, 1 + random.nextInt(5)), bytes.length - position);
      System.arraycopy(bytes, position, buffer, offset, n);
      position += n;
      return n;
    }
  }
}
