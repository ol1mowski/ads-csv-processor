package pl.oliwier.adscsv.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.http11.Http11NioProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebServerConfigTest {

  @Test
  @DisplayName("Tomcat answers Expect: 100-continue only once the upload body is actually read")
  void shouldSendContinueOnlyWhenTheBodyIsRead() {
    var protocol = new Http11NioProtocol();
    assertThat(protocol.getContinueResponseTimingInternal())
        .isEqualTo(ContinueResponseTiming.IMMEDIATELY);

    new WebServerConfig().continueOnlyWhenBodyIsRead().customize(protocol);

    assertThat(protocol.getContinueResponseTimingInternal())
        .isEqualTo(ContinueResponseTiming.ON_REQUEST_BODY_READ);
  }

  @Test
  @DisplayName("Tomcat never reads a request body to parse form parameters, not even for POST")
  void shouldNeverParseFormBodies() {
    var connector = new InspectableConnector();

    new WebServerConfig().neverParseFormBodies().customize(connector);

    assertThat(connector.getParseBodyMethods()).isEmpty();
    assertThat(connector.parsesBodyOf("POST")).isFalse();
    assertThat(connector.parsesBodyOf("PUT")).isFalse();
  }

  private static final class InspectableConnector extends Connector {

    boolean parsesBodyOf(String method) {
      return isParseBodyMethod(method);
    }
  }
}
