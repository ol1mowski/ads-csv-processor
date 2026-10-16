package pl.oliwier.adscsv.service.transformation;

import java.math.BigDecimal;

public interface AdRow {

  long impressions();

  long clicks();

  BigDecimal revenue();

  BigDecimal advertiserMargin();
}
