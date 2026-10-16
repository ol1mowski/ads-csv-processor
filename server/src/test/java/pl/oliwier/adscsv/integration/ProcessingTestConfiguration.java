package pl.oliwier.adscsv.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.oliwier.adscsv.integration.support.LatchingColumnTransformation;
import pl.oliwier.adscsv.integration.support.RequestThreadRecorder;
import pl.oliwier.adscsv.service.transformation.CtrTransformation;

@TestConfiguration(proxyBeanMethods = false)
class ProcessingTestConfiguration {

  @Bean
  @Primary
  LatchingColumnTransformation latchingColumnTransformation(CtrTransformation ctr) {
    return new LatchingColumnTransformation(ctr);
  }

  @Bean
  RequestThreadRecorder requestThreadRecorder() {
    return new RequestThreadRecorder();
  }
}
