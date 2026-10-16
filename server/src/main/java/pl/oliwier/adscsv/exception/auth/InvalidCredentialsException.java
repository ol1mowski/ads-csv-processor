package pl.oliwier.adscsv.exception.auth;

import pl.oliwier.adscsv.exception.ApplicationException;

public class InvalidCredentialsException extends ApplicationException {

  public InvalidCredentialsException() {
    super("BAD_CREDENTIALS", "Invalid username or password");
  }
}
