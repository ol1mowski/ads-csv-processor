package pl.oliwier.adscsv.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import pl.oliwier.adscsv.config.SecurityConfig;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.ApiSession;
import pl.oliwier.adscsv.integration.support.CountingInputStream;
import pl.oliwier.adscsv.integration.support.HealthCheckCounter;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.integration.support.RawHttpConnection;
import pl.oliwier.adscsv.integration.support.StreamingHttp;
import pl.oliwier.adscsv.support.SampleData;

class AnonymousAccessIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] CSV = SampleData.csv(SampleData.FIRST_ROW);
  private static final int HEALTH_FLOOD = 20;

  @Autowired private HealthCheckCounter healthChecks;

  @Test
  @DisplayName("Every job endpoint answers 401 problem+json without a token and set no auth cookie")
  void shouldReturn401ForEveryJobEndpointWithoutToken() throws IOException {
    var id = api.accepted("owned.csv", CSV).id();
    var anonymous = new JobsApi(anonymous().bind(restTestClient));

    assertUnauthenticated(anonymous.upload("anonymous.csv", CSV));
    assertUnauthenticated(anonymous.job(id));
    assertUnauthenticated(anonymous.history(""));
    assertUnauthenticated(anonymous.cancel(id));
    assertUnauthenticated(anonymous.result(id));
    assertUnauthenticated(anonymous.job(UUID.randomUUID()));
    assertThat(rows.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("An anonymous upload expecting 100-continue gets 401 before sending a body byte")
  void shouldReturn401BeforeBodyIsSentWhenUploadHasNoToken() throws Exception {
    var bytesSent = new AtomicLong();

    var response =
        new StreamingHttp(port, anonymous())
            .uploadExpectingContinue(
                "anonymous.csv",
                () -> new CountingInputStream(new ByteArrayInputStream(CSV), bytesSent),
                CSV.length);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE)).isEmpty();
    assertThat(bytesSent).as("body bytes the client had to send").hasValue(0);
    assertThat(rows.count()).isZero();
    assertThat(storage.incomingFiles()).isEmpty();
  }

  @Test
  @DisplayName("The SPA and the health check are public and carry the security headers")
  void shouldServeSpaAndHealthWithoutToken() {
    for (var path : new String[] {"/", "/index.html"}) {
      var result =
          restTestClient
              .get()
              .uri(path)
              .exchange()
              .expectStatus()
              .isOk()
              .expectHeader()
              .contentTypeCompatibleWith(MediaType.TEXT_HTML)
              .expectBody(String.class)
              .returnResult();
      assertThat(result.getResponseBody()).contains("<div id=\"root\"></div>");
      assertSecurityHeaders(result.getResponseHeaders());
      assertThat(result.getResponseHeaders().getCacheControl()).isNull();
    }
    api.health().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
    restTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("API responses, including errors, carry the security headers and no-store")
  void shouldSendSecurityHeadersOnApiResponses() {
    var ok = api.history("").expectStatus().isOk().expectBody().returnResult();
    var unauthorized =
        restTestClient
            .get()
            .uri("/api/v1/jobs")
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .returnResult();

    for (var headers :
        new HttpHeaders[] {ok.getResponseHeaders(), unauthorized.getResponseHeaders()}) {
      assertSecurityHeaders(headers);
      assertThat(headers.getCacheControl()).isEqualTo("no-store");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/env", "/actuator/info", "/error", "/login", "/internal"})
  @DisplayName("Everything that is neither public nor API is denied by default")
  void shouldDenyPathWhenItIsNeitherPublicNorApi(String path) {
    restTestClient
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    session
        .bind(restTestClient)
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("FORBIDDEN");
  }

  @ParameterizedTest
  @ValueSource(strings = {"//api/v1/jobs", "/api/v1/jobs;x=1", "/api/v1/%2e%2e/jobs"})
  @DisplayName("A request the firewall rejects gets 400")
  void shouldAnswer400WhenFirewallRejectsRequest(String target) throws IOException {
    String response;
    try (var connection = RawHttpConnection.open(port)) {
      connection.sendGet(target);
      response = connection.readResponse();
    }

    assertThat(response).startsWith("HTTP/1.1 400");
  }

  @Test
  @DisplayName("A flood of health checks runs the health indicators at most once per second")
  void shouldServeRepeatedHealthChecksFromCache() {
    var before = healthChecks.checks();

    for (var check = 0; check < HEALTH_FLOOD; check++) {
      restTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    assertThat(healthChecks.checks() - before).isLessThanOrEqualTo(3);
  }

  private static void assertUnauthenticated(ResponseSpec response) {
    var result =
        response
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader()
            .doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
            .expectHeader()
            .doesNotExist(HttpHeaders.LOCATION)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("UNAUTHENTICATED")
            .returnResult();
    assertThat(ApiSession.cookie(result, ApiSession.AUTH_COOKIE)).isEmpty();
  }

  private static void assertSecurityHeaders(HttpHeaders headers) {
    assertThat(headers.getFirst("Content-Security-Policy"))
        .isEqualTo(SecurityConfig.CONTENT_SECURITY_POLICY);
    assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("same-origin");
  }
}
