package pl.oliwier.adscsv.service.csv;

import java.io.IOException;
import java.io.OutputStream;

public final class Line {

  private byte[] buffer = new byte[0];
  private int start;
  private int length;
  private boolean tooLong;

  void set(byte[] buffer, int start, int length, boolean tooLong) {
    this.buffer = buffer;
    this.start = start;
    this.length = length;
    this.tooLong = tooLong;
  }

  byte[] buffer() {
    return buffer;
  }

  int start() {
    return start;
  }

  int end() {
    return start + length;
  }

  boolean isTooLong() {
    return tooLong;
  }

  public boolean isRecord() {
    return tooLong || length > 0;
  }

  public void writeTo(OutputStream out) throws IOException {
    out.write(buffer, start, length);
  }
}
