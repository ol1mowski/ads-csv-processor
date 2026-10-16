package pl.oliwier.adscsv.exception.job;

import java.util.UUID;
import pl.oliwier.adscsv.exception.ApplicationException;

public class ResultGoneException extends ApplicationException {

  public ResultGoneException(UUID id) {
    super("RESULT_GONE", "Result file of job " + id + " is no longer available");
  }
}
