package pl.oliwier.adscsv.service.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import pl.oliwier.adscsv.exception.upload.InvalidUploadException;
import pl.oliwier.adscsv.exception.upload.UploadProblem;
import pl.oliwier.adscsv.service.csv.AdsCsvFormat;
import pl.oliwier.adscsv.shared.SizeLimitExceededException;

final class UploadBody {

  private static final int BUFFER_BYTES = 64 * 1024;

  private UploadBody() {}

  static byte[] readHeader(InputStream in) {
    try {
      return readFirstLine(in, AdsCsvFormat.MAX_LINE_BYTES);
    } catch (IOException e) {
      throw readFailure(e);
    }
  }

  static byte[] readFirstLine(InputStream in, int maxBytes) throws IOException {
    var line = new byte[maxBytes];
    var length = 0;
    while (length < maxBytes) {
      var b = in.read();
      if (b == -1) {
        break;
      }
      line[length++] = (byte) b;
      if (b == '\n') {
        break;
      }
    }
    return Arrays.copyOf(line, length);
  }

  static void copy(InputStream in, OutputStream out) throws IOException {
    var buffer = new byte[BUFFER_BYTES];
    for (var n = read(in, buffer); n != -1; n = read(in, buffer)) {
      out.write(buffer, 0, n);
    }
  }

  private static int read(InputStream in, byte[] buffer) {
    try {
      return in.read(buffer);
    } catch (IOException e) {
      throw readFailure(e);
    }
  }

  private static InvalidUploadException readFailure(IOException e) {
    return new InvalidUploadException(
        e instanceof SizeLimitExceededException
            ? UploadProblem.TOO_LARGE
            : UploadProblem.INTERRUPTED);
  }
}
