package pl.oliwier.adscsv.exception.job;

import pl.oliwier.adscsv.exception.ApplicationException;
import pl.oliwier.adscsv.model.JobStatus;

public abstract class JobStateConflictException extends ApplicationException {

  private final JobStatus currentStatus;

  protected JobStateConflictException(String code, String message, JobStatus currentStatus) {
    super(code, message);
    this.currentStatus = currentStatus;
  }

  public JobStatus currentStatus() {
    return currentStatus;
  }
}
