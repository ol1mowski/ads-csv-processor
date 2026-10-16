package pl.oliwier.adscsv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdsCsvApplication {

  static void main(String[] args) {
    SpringApplication.run(AdsCsvApplication.class, args);
  }
}
