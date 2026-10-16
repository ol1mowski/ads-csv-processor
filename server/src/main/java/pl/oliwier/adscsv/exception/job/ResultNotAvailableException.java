package pl.oliwier.adscsv.exception.job;

import pl.oliwier.adscsv.model.JobStatus;

public class ResultNotAvailableException extends JobStateConflictException {

  public ResultNotAvailableException(JobStatus currentStatus) {
    super("RESULT_NOT_AVAILABLE", "Result is available only for completed jobs", currentStatus);
  }
}
