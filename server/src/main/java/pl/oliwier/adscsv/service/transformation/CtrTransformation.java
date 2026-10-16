package pl.oliwier.adscsv.service.transformation;

import org.springframework.stereotype.Component;

@Component
public class CtrTransformation implements ColumnTransformation {

  private static final String UNDEFINED = "";

  @Override
  public String columnName() {
    return "ctr";
  }

  @Override
  public String apply(AdRow row) {
    return Ctr.of(row.clicks(), row.impressions()).orElse(UNDEFINED);
  }
}
