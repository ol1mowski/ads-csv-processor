package pl.oliwier.adscsv.service.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class BoundedLineReader implements Closeable {

  private static final byte NEWLINE = '\n';
  private static final byte CARRIAGE_RETURN = '\r';
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private final InputStream in;
  private final byte[] buffer;
  private final int maxLineBytes;
  private final Line line = new Line();
  private int position;
  private int limit;
  private boolean endOfStream;
  private boolean skipping;
  private long bytesConsumed;
  private long lineNumber;

  public BoundedLineReader(InputStream in, int bufferBytes, int maxLineBytes) {
    if (maxLineBytes < 1 || bufferBytes < maxLineBytes + 2) {
      throw new IllegalArgumentException("Buffer must hold the longest line with its terminator");
    }
    this.in = in;
    this.buffer = new byte[bufferBytes];
    this.maxLineBytes = maxLineBytes;
  }

  public boolean next() throws IOException {
    return skipping ? skipFragment() : readLine();
  }

  public Line current() {
    return line;
  }

  public long lineNumber() {
    return lineNumber;
  }

  public long bytesConsumed() {
    return bytesConsumed;
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  private boolean readLine() throws IOException {
    var scanned = 0;
    while (true) {
      var windowEnd = Math.min(limit, position + maxLineBytes + 1);
      var newline = Bytes.indexOf(buffer, position + scanned, windowEnd, NEWLINE);
      if (newline >= 0) {
        return emitLine(newline - position, newline + 1 - position);
      }
      scanned = windowEnd - position;
      if (scanned > maxLineBytes) {
        return emitTooLong(scanned);
      }
      if (fill()) {
        return scanned > 0 && emitLine(scanned, scanned);
      }
    }
  }

  private boolean skipFragment() throws IOException {
    if (position == limit && fill()) {
      skipping = false;
      return false;
    }
    var newline = Bytes.indexOf(buffer, position, limit, NEWLINE);
    var consumed = (newline >= 0 ? newline + 1 : limit) - position;
    skipping = newline < 0;
    line.set(buffer, position, 0, false);
    advance(consumed);
    return true;
  }

  private boolean emitLine(int contentLength, int consumed) {
    var start = position;
    var length = contentLength;
    if (length > 0 && buffer[start + length - 1] == CARRIAGE_RETURN) {
      length--;
    }
    lineNumber++;
    if (lineNumber == 1) {
      var bom = bomLength(start, start + length);
      start += bom;
      length -= bom;
    }
    line.set(buffer, start, length, false);
    advance(consumed);
    return true;
  }

  private boolean emitTooLong(int consumed) {
    lineNumber++;
    line.set(buffer, position, 0, true);
    advance(consumed);
    skipping = true;
    return true;
  }

  private void advance(int consumed) {
    position += consumed;
    bytesConsumed += consumed;
  }

  private boolean fill() throws IOException {
    if (endOfStream) {
      return true;
    }
    compact();
    var read = in.read(buffer, limit, buffer.length - limit);
    if (read < 0) {
      endOfStream = true;
      return true;
    }
    limit += read;
    return false;
  }

  private void compact() {
    if (position == 0) {
      return;
    }
    var remaining = limit - position;
    System.arraycopy(buffer, position, buffer, 0, remaining);
    position = 0;
    limit = remaining;
  }

  private int bomLength(int from, int to) {
    var hasBom =
        to - from >= UTF8_BOM.length
            && Arrays.equals(buffer, from, from + UTF8_BOM.length, UTF8_BOM, 0, UTF8_BOM.length);
    return hasBom ? UTF8_BOM.length : 0;
  }
}
