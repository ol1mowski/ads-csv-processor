package pl.oliwier.adscsv.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.auth")
public record AuthProperties(
    @Size(min = 32) String jwtSecret,
    @NotNull @DurationMin(minutes = 1) Duration tokenTtl,
    boolean secureCookies,
    boolean allowGeneratedSecret) {

  public AuthProperties {
    jwtSecret = jwtSecret == null || jwtSecret.isBlank() ? null : jwtSecret;
  }

  @Override
  public @NonNull String toString() {
    return "AuthProperties[tokenTtl="
        + tokenTtl
        + ", secureCookies="
        + secureCookies
        + ", allowGeneratedSecret="
        + allowGeneratedSecret
        + "]";
  }
}
