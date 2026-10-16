package pl.oliwier.adscsv.exception.job;

import java.util.UUID;
import pl.oliwier.adscsv.exception.ApplicationException;

public class JobNotFoundException extends ApplicationException {

  public JobNotFoundException(UUID id) {
    super("JOB_NOT_FOUND", "Job " + id + " does not exist");
  }
}
