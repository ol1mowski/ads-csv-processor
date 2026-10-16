package pl.oliwier.adscsv.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.history")
public record HistoryProperties(@Min(1) @Max(MAX_PAGE_SIZE_LIMIT) int maxPageSize) {

  public static final int MAX_PAGE_SIZE_LIMIT = 1_000;
  public static final int MAX_PAGE = 1_000_000;
}
