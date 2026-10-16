package pl.oliwier.adscsv.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record SecurityUsersProperties(
    @NotEmpty(message = NO_ACCOUNTS) List<@Valid @NotNull User> users) {

  public static final String NO_ACCOUNTS =
      "no accounts configured: run with the demo profile (SPRING_PROFILES_ACTIVE=demo) or set"
          + " APP_SECURITY_USERS_0_USERNAME and APP_SECURITY_USERS_0_PASSWORD_HASH";
  public static final String USERNAME = "^[A-Za-z0-9._-]{1,64}$";
  public static final String BCRYPT_COST_10_HASH = "^\\{bcrypt}\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$";

  public record User(
      @NotNull @Pattern(regexp = USERNAME) String username,
      @NotNull @Pattern(regexp = BCRYPT_COST_10_HASH) String passwordHash) {

    @Override
    public @NonNull String toString() {
      return "User[username=" + username + "]";
    }
  }
}
