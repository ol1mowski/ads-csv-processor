package pl.oliwier.adscsv.service.transformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public final class Ctr {

  private static final int SCALE = 6;

  private Ctr() {}

  public static Optional<String> of(long clicks, long impressions) {
    if (impressions == 0) {
      return Optional.empty();
    }
    return Optional.of(
        BigDecimal.valueOf(clicks)
            .divide(BigDecimal.valueOf(impressions), SCALE, RoundingMode.HALF_UP)
            .toPlainString());
  }
}
