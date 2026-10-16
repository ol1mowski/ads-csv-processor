package pl.oliwier.adscsv.config.properties;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(@NotNull Path root) {

  public StorageProperties {
    root = root == null ? null : root.toAbsolutePath().normalize();
  }
}
