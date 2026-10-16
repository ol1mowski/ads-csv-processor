package pl.oliwier.adscsv.model;

public enum FailureReason {
  INVALID_HEADER,
  NO_VALID_ROWS,
  TOO_MANY_INVALID_ROWS,
  INPUT_MISSING,
  INTERRUPTED,
  PROCESSING_ERROR
}
