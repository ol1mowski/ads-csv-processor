package pl.oliwier.adscsv.support;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.regex.Pattern;

public final class AdsCsvGenerator {

  public static final String HEADER_LINE =
      "advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin\n";

  private static final byte[] HEADER = HEADER_LINE.getBytes(StandardCharsets.US_ASCII);
  private static final Pattern SIZE = Pattern.compile("(\\d+)(B|KiB|MiB|GiB)?");
  private static final String USAGE =
      "Usage: java AdsCsvGenerator.java --size <n>[B|KiB|MiB|GiB] --out <file>"
          + " [--seed <long>] [--invalid-every <n>]";
  private static final long DEFAULT_SEED = 42;
  private static final int OUTPUT_BUFFER_BYTES = 1 << 20;

  private final long seed;
  private final int invalidEvery;
  private final Report report;

  private AdsCsvGenerator(long targetBytes, long seed, int invalidEvery) {
    this.seed = seed;
    this.invalidEvery = invalidEvery;
    this.report = measure(targetBytes, seed, invalidEvery);
  }

  public static AdsCsvGenerator generate(long targetBytes, long seed) {
    return generate(targetBytes, seed, 0);
  }

  public static AdsCsvGenerator generate(long targetBytes, long seed, int invalidEvery) {
    if (targetBytes < HEADER.length) {
      throw new IllegalArgumentException("Target size must fit at least the header line");
    }
    if (invalidEvery < 0) {
      throw new IllegalArgumentException("invalidEvery must be zero or positive");
    }
    return new AdsCsvGenerator(targetBytes, seed, invalidEvery);
  }

  public Report report() {
    return report;
  }

  public long byteLength() {
    return report.byteLength();
  }

  public InputStream openStream() {
    return new RowStream(new RowSource(seed, invalidEvery), report.rows());
  }

  public void writeTo(Path file) throws IOException {
    try (var in = openStream();
        var out = new BufferedOutputStream(Files.newOutputStream(file), OUTPUT_BUFFER_BYTES)) {
      in.transferTo(out);
    }
  }

  public static void main(String[] args) throws IOException {
    var options = parseOptions(args);
    if (options == null || !options.containsKey("--size") || !options.containsKey("--out")) {
      System.err.println(USAGE);
      System.exit(2);
      return;
    }
    var generator =
        generate(
            parseSize(options.get("--size")),
            Long.parseLong(options.getOrDefault("--seed", Long.toString(DEFAULT_SEED))),
            Integer.parseInt(options.getOrDefault("--invalid-every", "0")));
    var out = Path.of(options.get("--out"));
    generator.writeTo(out);
    System.out.println("file=" + out.toAbsolutePath());
    System.out.println(generator.report().describe());
  }

  static long parseSize(String text) {
    var matcher = SIZE.matcher(text.strip());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unsupported size: " + text);
    }
    var unit = matcher.group(2) == null ? "B" : matcher.group(2);
    var shift =
        switch (unit) {
          case "KiB" -> 10;
          case "MiB" -> 20;
          case "GiB" -> 30;
          default -> 0;
        };
    return Math.multiplyExact(Long.parseLong(matcher.group(1)), 1L << shift);
  }

  private static Map<String, String> parseOptions(String[] args) {
    if (args.length % 2 != 0) {
      return null;
    }
    var options = new HashMap<String, String>();
    for (var i = 0; i < args.length; i += 2) {
      options.put(args[i], args[i + 1]);
    }
    return options;
  }

  private static Report measure(long targetBytes, long seed, int invalidEvery) {
    var source = new RowSource(seed, invalidEvery);
    long bytes = HEADER.length;
    long rows = 0;
    long invalidRows = 0;
    long impressions = 0;
    long clicks = 0;
    while (true) {
      source.next();
      if (bytes + source.length() > targetBytes) {
        return new Report(bytes, rows, invalidRows, impressions, clicks);
      }
      bytes += source.length();
      rows++;
      if (source.valid()) {
        impressions += source.impressions();
        clicks += source.clicks();
      } else {
        invalidRows++;
      }
    }
  }

  public record Report(
      long byteLength, long rows, long invalidRows, long totalImpressions, long totalClicks) {

    public long validRows() {
      return rows - invalidRows;
    }

    public Optional<String> totalCtr() {
      if (totalImpressions == 0) {
        return Optional.empty();
      }
      return Optional.of(
          BigDecimal.valueOf(totalClicks)
              .divide(BigDecimal.valueOf(totalImpressions), 6, RoundingMode.HALF_UP)
              .toPlainString());
    }

    public String describe() {
      return "bytes="
          + byteLength
          + " rows="
          + rows
          + " validRows="
          + validRows()
          + " invalidRows="
          + invalidRows
          + " totalImpressions="
          + totalImpressions
          + " totalClicks="
          + totalClicks
          + " totalCtr="
          + totalCtr().orElse("");
    }
  }

  private static final class RowSource {

    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INVALID_COUNT = "n/a".getBytes(StandardCharsets.US_ASCII);
    private static final int ZERO_IMPRESSIONS_PER_MILLE = 10;

    private final SplittableRandom random;
    private final int invalidEvery;
    private final byte[] buffer = new byte[160];
    private final byte[] digits = new byte[20];
    private long index;
    private int length;
    private boolean valid;
    private long impressions;
    private long clicks;

    RowSource(long seed, int invalidEvery) {
      this.random = new SplittableRandom(seed);
      this.invalidEvery = invalidEvery;
    }

    void next() {
      index++;
      length = 0;
      valid = invalidEvery == 0 || index % invalidEvery != 0;
      appendNumber(1000 + random.nextInt(9000));
      appendSeparator();
      appendNumber(10_000 + random.nextInt(2000));
      appendSeparator();
      appendNumber(500_000 + random.nextInt(30_000));
      appendSeparator();
      appendUuid(random.nextLong(), random.nextLong());
      appendSeparator();
      impressions =
          random.nextInt(1000) < ZERO_IMPRESSIONS_PER_MILLE ? 0 : 1 + random.nextInt(5000);
      clicks = impressions == 0 ? 0 : random.nextInt((int) (impressions / 20) + 1);
      if (valid) {
        appendNumber(impressions);
      } else {
        appendBytes(INVALID_COUNT);
      }
      appendSeparator();
      appendNumber(clicks);
      appendSeparator();
      appendDecimal(random.nextInt(100), random.nextInt(10_000));
      appendSeparator();
      appendDecimal(0, random.nextInt(10_000));
      buffer[length++] = '\n';
    }

    byte[] buffer() {
      return buffer;
    }

    int length() {
      return length;
    }

    boolean valid() {
      return valid;
    }

    long impressions() {
      return impressions;
    }

    long clicks() {
      return clicks;
    }

    private void appendSeparator() {
      buffer[length++] = ';';
    }

    private void appendBytes(byte[] bytes) {
      System.arraycopy(bytes, 0, buffer, length, bytes.length);
      length += bytes.length;
    }

    private void appendNumber(long value) {
      var count = 0;
      var remaining = value;
      do {
        digits[count++] = (byte) ('0' + remaining % 10);
        remaining /= 10;
      } while (remaining > 0);
      while (count > 0) {
        buffer[length++] = digits[--count];
      }
    }

    private void appendDecimal(int integerPart, int fourDecimals) {
      appendNumber(integerPart);
      buffer[length++] = '.';
      for (var divisor = 1000; divisor > 0; divisor /= 10) {
        buffer[length++] = (byte) ('0' + fourDecimals / divisor % 10);
      }
    }

    private void appendUuid(long high, long low) {
      for (var nibble = 0; nibble < 32; nibble++) {
        if (nibble == 8 || nibble == 12 || nibble == 16 || nibble == 20) {
          buffer[length++] = '-';
        }
        var word = nibble < 16 ? high : low;
        var shift = 60 - 4 * (nibble % 16);
        buffer[length++] = HEX[(int) (word >>> shift) & 0xF];
      }
    }
  }

  private static final class RowStream extends InputStream {

    private final RowSource source;
    private final long rowCount;
    private long rowsEmitted;
    private byte[] current = HEADER;
    private int currentLength = HEADER.length;
    private int position;

    RowStream(RowSource source, long rowCount) {
      this.source = source;
      this.rowCount = rowCount;
    }

    @Override
    public int read() {
      if (!ensureAvailable()) {
        return -1;
      }
      return current[position++] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) {
      Objects.checkFromIndexSize(offset, length, target.length);
      if (length == 0) {
        return 0;
      }
      var copied = 0;
      while (copied < length && ensureAvailable()) {
        var chunk = Math.min(length - copied, currentLength - position);
        System.arraycopy(current, position, target, offset + copied, chunk);
        position += chunk;
        copied += chunk;
      }
      return copied == 0 ? -1 : copied;
    }

    private boolean ensureAvailable() {
      if (position < currentLength) {
        return true;
      }
      if (rowsEmitted == rowCount) {
        return false;
      }
      source.next();
      current = source.buffer();
      currentLength = source.length();
      position = 0;
      rowsEmitted++;
      return true;
    }
  }
}
