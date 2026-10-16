package pl.oliwier.adscsv.integration.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public final class HealthCheckCounter implements HealthIndicator {

  private final AtomicInteger checks = new AtomicInteger();

  @Override
  public Health health() {
    checks.incrementAndGet();
    return Health.up().build();
  }

  public int checks() {
    return checks.get();
  }
}
