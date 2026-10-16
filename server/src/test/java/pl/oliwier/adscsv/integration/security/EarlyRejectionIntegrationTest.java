package pl.oliwier.adscsv.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.filter.FormContentFilter;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.CountingInputStream;
import pl.oliwier.adscsv.integration.support.StreamingHttp;

class EarlyRejectionIntegrationTest extends ProcessingDisabledTestBase {

  private static final String FORM = MediaType.APPLICATION_FORM_URLENCODED_VALUE;
  private static final int SMALL_FORM_BODY_BYTES = 1024 * 1024;
  private static final int FORM_BODY_BYTES = 8 * 1024 * 1024;

  @Autowired private ApplicationContext context;

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
  @DisplayName("An anonymous form body sent with PUT, PATCH or DELETE is refused before it is read")
  void shouldRefuseFormBodyBeforeReadingItWhenMethodIsNotPost(String method) throws Exception {
    for (var path : new String[] {ApiRoutes.JOBS, "/"}) {
      var bytesSent = new AtomicLong();

      var response =
          new StreamingHttp(port, anonymous())
              .sendExpectingContinue(
                  method, path, FORM, () -> formBody(FORM_BODY_BYTES, bytesSent), FORM_BODY_BYTES);

      assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(401);
      assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
      assertThat(bytesSent).as("form bytes the client had to send").hasValue(0);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {SMALL_FORM_BODY_BYTES, FORM_BODY_BYTES})
  @DisplayName("A form POST without the CSRF header is refused before its body is read")
  void shouldRefuseFormPostWithoutCsrfHeaderBeforeReadingBody(int bodyBytes) throws Exception {
    for (var path : new String[] {"/", ApiRoutes.LOGIN, ApiRoutes.JOBS}) {
      var bytesSent = new AtomicLong();

      var response =
          new StreamingHttp(port, anonymous())
              .sendExpectingContinueWithoutCsrfHeader(
                  "POST", path, FORM, () -> formBody(bodyBytes, bytesSent), bodyBytes);

      assertThat(response.statusCode()).as("POST %s", path).isEqualTo(403);
      assertThat(response.body()).contains("\"code\":\"CSRF_INVALID\"");
      assertThat(bytesSent).as("form bytes the client had to send").hasValue(0);
    }
  }

  @Test
  @DisplayName("No filter parses form bodies of PUT, PATCH or DELETE ahead of the security chain")
  void shouldNotRegisterFormContentFilter() {
    assertThat(context.getBeanNamesForType(FormContentFilter.class)).isEmpty();
  }

  private static InputStream formBody(int length, AtomicLong bytesSent) {
    var body = new byte[length];
    Arrays.fill(body, (byte) 'a');
    body[0] = 'x';
    body[1] = '=';
    return new CountingInputStream(new ByteArrayInputStream(body), bytesSent);
  }
}
