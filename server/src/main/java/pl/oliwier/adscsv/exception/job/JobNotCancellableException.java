package pl.oliwier.adscsv.exception.job;

import pl.oliwier.adscsv.model.JobStatus;

public class JobNotCancellableException extends JobStateConflictException {

  public JobNotCancellableException(JobStatus currentStatus) {
    super("JOB_NOT_CANCELLABLE", "Job is already finished", currentStatus);
  }
}
