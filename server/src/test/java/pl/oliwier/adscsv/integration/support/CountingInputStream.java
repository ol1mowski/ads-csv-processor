package pl.oliwier.adscsv.integration.support;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public final class CountingInputStream extends FilterInputStream {

  private final AtomicLong bytesRead;

  public CountingInputStream(InputStream in, AtomicLong bytesRead) {
    super(in);
    this.bytesRead = bytesRead;
  }

  @Override
  public int read() throws IOException {
    var b = super.read();
    if (b != -1) {
      bytesRead.incrementAndGet();
    }
    return b;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    var n = super.read(target, offset, length);
    if (n > 0) {
      bytesRead.addAndGet(n);
    }
    return n;
  }
}
