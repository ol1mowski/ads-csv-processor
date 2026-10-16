package pl.oliwier.adscsv.exception.auth;

import pl.oliwier.adscsv.exception.ApplicationException;

public class NotAuthenticatedException extends ApplicationException {

  public static final String CODE = "UNAUTHENTICATED";
  public static final String MESSAGE = "Authentication is required";

  public NotAuthenticatedException() {
    super(CODE, MESSAGE);
  }
}
