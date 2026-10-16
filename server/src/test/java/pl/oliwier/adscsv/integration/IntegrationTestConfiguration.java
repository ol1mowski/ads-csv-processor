package pl.oliwier.adscsv.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.oliwier.adscsv.config.properties.StorageProperties;
import pl.oliwier.adscsv.integration.support.HealthCheckCounter;
import pl.oliwier.adscsv.integration.support.StorageInspector;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(POSTGRES_IMAGE);
  }

  @Bean
  StorageInspector storageInspector(StorageProperties storage) {
    return new StorageInspector(storage.root());
  }

  @Bean
  HealthCheckCounter healthCheckCounter() {
    return new HealthCheckCounter();
  }
}
