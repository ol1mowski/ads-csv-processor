package pl.oliwier.adscsv.service.transformation;

public interface ColumnTransformation {

  String columnName();

  String apply(AdRow row);
}
