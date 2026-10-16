package pl.oliwier.adscsv.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

public record LoginRequest(
    @NotNull @Size(min = 1, max = 64) String username,
    @NotNull @Size(min = 1, max = 128) String password) {

  @Override
  public @NonNull String toString() {
    return "LoginRequest[username=" + username + "]";
  }
}
