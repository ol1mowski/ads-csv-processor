package pl.oliwier.adscsv.config;

import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WebServerConfig {

  private static final String NO_METHODS = "";

  @Bean
  TomcatProtocolHandlerCustomizer<AbstractHttp11Protocol<?>> continueOnlyWhenBodyIsRead() {
    return protocol ->
        protocol.setContinueResponseTiming(ContinueResponseTiming.ON_REQUEST_BODY_READ.toString());
  }

  @Bean
  TomcatConnectorCustomizer neverParseFormBodies() {
    return connector -> connector.setParseBodyMethods(NO_METHODS);
  }
}
