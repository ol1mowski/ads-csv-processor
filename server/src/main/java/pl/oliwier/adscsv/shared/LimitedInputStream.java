package pl.oliwier.adscsv.shared;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.NonNull;

public final class LimitedInputStream extends FilterInputStream {

  private final long maxBytes;
  private long count;

  public LimitedInputStream(InputStream in, long maxBytes) {
    super(in);
    this.maxBytes = maxBytes;
  }

  public long count() {
    return count;
  }

  @Override
  public int read() throws IOException {
    var b = super.read();
    if (b != -1) {
      consumed(1);
    }
    return b;
  }

  @Override
  public int read(byte @NonNull [] buffer, int offset, int length) throws IOException {
    var n = super.read(buffer, offset, length);
    if (n > 0) {
      consumed(n);
    }
    return n;
  }

  @Override
  public long skip(long n) throws IOException {
    var skipped = super.skip(n);
    consumed(skipped);
    return skipped;
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  private void consumed(long bytes) throws SizeLimitExceededException {
    count += bytes;
    if (count > maxBytes) {
      throw new SizeLimitExceededException(maxBytes);
    }
  }
}
