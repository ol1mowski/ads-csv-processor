package pl.oliwier.adscsv.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.ApiSession;
import pl.oliwier.adscsv.integration.support.CountingInputStream;
import pl.oliwier.adscsv.integration.support.JobsApi;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestUsers;

class CsrfIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] CSV = SampleData.csv(SampleData.FIRST_ROW);

  @Test
  @DisplayName("An upload without the CSRF header is 403 before a single body byte is sent")
  void shouldReturn403BeforeBodyIsSentWhenUploadLacksCsrfHeader() throws Exception {
    var bytesSent = new AtomicLong();

    var response =
        streaming()
            .uploadExpectingContinueWithoutCsrfHeader(
                "no-token.csv",
                () -> new CountingInputStream(new ByteArrayInputStream(CSV), bytesSent),
                CSV.length);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("Content-Type"))
        .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.body()).contains("\"code\":\"CSRF_INVALID\"");
    assertThat(bytesSent).as("body bytes the client had to send").hasValue(0);
    assertNothingStored();
  }

  @Test
  @DisplayName("An upload whose header does not match the cookie token is 403")
  void shouldReturn403WhenUploadHeaderDoesNotMatchCookie() throws IOException {
    var forged = ApiSession.of(session.token().orElseThrow(), "forged-token");
    var mismatched =
        restTestClient
            .mutate()
            .defaultHeader("Cookie", session.cookieHeader())
            .defaultHeader(ApiSession.XSRF_HEADER, forged.csrfToken())
            .build();

    new JobsApi(mismatched)
        .upload("forged.csv", CSV)
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CSRF_INVALID");
    assertNothingStored();
  }

  @Test
  @DisplayName("A cancel without the CSRF header is 403 and the job stays queued")
  void shouldReturn403WhenCancelLacksCsrfHeader() {
    var id = api.accepted("queued.csv", CSV).id();

    cookiesOnly()
        .post()
        .uri(ApiRoutes.job(id) + "/cancel")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.FORBIDDEN)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CSRF_INVALID");

    assertThat(rows.status(id)).isEqualTo(JobStatus.QUEUED);
    assertThat(storage.input(id)).exists();
  }

  @Test
  @DisplayName("A logout without the CSRF header is 403 and the login stays valid")
  void shouldReturn403WhenLogoutLacksCsrfHeader() {
    cookiesOnly()
        .post()
        .uri(ApiRoutes.LOGOUT)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CSRF_INVALID");

    cookiesOnly()
        .get()
        .uri(ApiRoutes.ME)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.username")
        .isEqualTo(TestUsers.ALICE);
  }

  @Test
  @DisplayName("A login without the CSRF header is 403 and issues no token cookie")
  void shouldReturn403WhenLoginLacksCsrfHeader() {
    var result =
        restTestClient
            .post()
            .uri(ApiRoutes.LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"username\":\"alice\",\"password\":\"alice-test-pass\"}")
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CSRF_INVALID")
            .returnResult();

    assertThat(ApiSession.cookie(result, ApiSession.AUTH_COOKIE)).isEmpty();
  }

  @Test
  @DisplayName("Read endpoints need no CSRF header")
  void shouldServeReadsWithoutCsrfHeader() {
    var id = api.accepted("readable.csv", CSV).id();

    cookiesOnly().get().uri(ApiRoutes.job(id)).exchange().expectStatus().isOk();
    cookiesOnly().get().uri(ApiRoutes.JOBS).exchange().expectStatus().isOk();
    cookiesOnly().get().uri(ApiRoutes.result(id)).exchange().expectStatus().isEqualTo(409);
  }

  private RestTestClient cookiesOnly() {
    return session.bindCookiesOnly(restTestClient);
  }

  private void assertNothingStored() throws IOException {
    assertThat(rows.count()).isZero();
    assertThat(storage.incomingFiles()).isEmpty();
    assertThat(storage.jobDirectories()).isEmpty();
  }
}
