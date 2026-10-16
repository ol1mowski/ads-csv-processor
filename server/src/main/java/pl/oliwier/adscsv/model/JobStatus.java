package pl.oliwier.adscsv.model;

public enum JobStatus {
  QUEUED,
  PROCESSING,
  COMPLETED,
  FAILED,
  CANCELLED;

  public boolean isActive() {
    return this == QUEUED || this == PROCESSING;
  }
}
