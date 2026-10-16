package pl.oliwier.adscsv.shared;

import java.io.IOException;

public class SizeLimitExceededException extends IOException {

  public SizeLimitExceededException(long limitBytes) {
    super("Stream exceeded the limit of " + limitBytes + " bytes");
  }
}
