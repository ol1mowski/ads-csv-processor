package pl.oliwier.adscsv.exception;

public abstract class ApplicationException extends RuntimeException {

  private final String code;

  protected ApplicationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
