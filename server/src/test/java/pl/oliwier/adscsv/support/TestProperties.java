package pl.oliwier.adscsv.support;

import java.time.Duration;
import org.springframework.util.unit.DataSize;
import pl.oliwier.adscsv.config.properties.AuthProperties;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.config.properties.UploadProperties;

public final class TestProperties {

  public static final String JWT_SECRET = "unit-test-signing-secret-32-chars!";

  private TestProperties() {}

  public static AuthProperties auth() {
    return auth(JWT_SECRET, Duration.ofMinutes(30), false);
  }

  public static AuthProperties auth(String jwtSecret, Duration tokenTtl, boolean secureCookies) {
    return new AuthProperties(jwtSecret, tokenTtl, secureCookies, false);
  }

  public static ProcessingProperties processing() {
    return processing(2, Duration.ZERO, DataSize.ofKilobytes(4), 1000, 0.01);
  }

  public static ProcessingProperties processing(
      int workers,
      Duration progressInterval,
      DataSize checkEvery,
      long minRejectedToAbort,
      double maxRejectedRatio) {
    return new ProcessingProperties(
        workers,
        Duration.ofSeconds(1),
        progressInterval,
        checkEvery,
        3,
        minRejectedToAbort,
        maxRejectedRatio,
        10,
        Duration.ofMillis(200));
  }

  public static ProcessingProperties workers(int workers, Duration shutdownWait) {
    var defaults = processing();
    return new ProcessingProperties(
        workers,
        defaults.pollInterval(),
        defaults.progressInterval(),
        defaults.checkEvery(),
        defaults.maxAttempts(),
        defaults.minRejectedToAbort(),
        defaults.maxRejectedRatio(),
        defaults.rejectedSamples(),
        shutdownWait);
  }

  public static UploadProperties upload(long maxBytes) {
    return new UploadProperties(DataSize.ofBytes(maxBytes));
  }
}
