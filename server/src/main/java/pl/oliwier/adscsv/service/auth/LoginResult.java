package pl.oliwier.adscsv.service.auth;

import org.jspecify.annotations.NonNull;

public record LoginResult(String username, String token) {

  @Override
  public @NonNull String toString() {
    return "LoginResult[username=" + username + "]";
  }
}
