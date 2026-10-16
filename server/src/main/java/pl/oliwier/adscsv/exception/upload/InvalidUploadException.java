package pl.oliwier.adscsv.exception.upload;

import pl.oliwier.adscsv.exception.ApplicationException;

public class InvalidUploadException extends ApplicationException {

  private final UploadProblem problem;

  public InvalidUploadException(UploadProblem problem) {
    super(problem.code(), problem.message());
    this.problem = problem;
  }

  public UploadProblem problem() {
    return problem;
  }
}
