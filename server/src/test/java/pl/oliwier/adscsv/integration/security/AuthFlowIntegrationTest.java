package pl.oliwier.adscsv.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.integration.support.ApiSession;
import pl.oliwier.adscsv.integration.support.BrowserClient;
import pl.oliwier.adscsv.service.auth.AuthTokenService;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestUsers;

class AuthFlowIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] CSV = SampleData.csv(SampleData.FIRST_ROW);
  private static final String SESSION_COOKIE = "JSESSIONID";

  @Autowired AuthTokenService tokens;

  @Test
  @DisplayName(
      "A browser bootstraps, logs in, uploads, logs out and logs in again with one cookie jar")
  void shouldLogInUseApiLogOutAndLogInAgainWithOneCookieJar() throws Exception {
    var browser = new BrowserClient(port);

    var bootstrap = browser.get(ApiRoutes.ME);
    assertThat(bootstrap.statusCode()).isEqualTo(401);
    assertThat(bootstrap.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    var anonymousXsrf = browser.cookie(ApiSession.XSRF_COOKIE);
    assertThat(anonymousXsrf).isPresent();
    assertThat(browser.cookie(ApiSession.AUTH_COOKIE)).isEmpty();

    var login =
        browser.postJson(ApiRoutes.LOGIN, credentials(TestUsers.ALICE, TestUsers.ALICE_PASSWORD));
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.body()).isEqualTo("{\"username\":\"alice\"}");
    assertThat(BrowserClient.setCookies(login, ApiSession.AUTH_COOKIE))
        .singleElement()
        .asString()
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .contains("Path=/")
        .contains("Max-Age=28800")
        .doesNotContain("Secure");
    assertThat(BrowserClient.setCookies(login, SESSION_COOKIE)).as("no server session").isEmpty();
    assertThat(BrowserClient.setCookies(login, ApiSession.XSRF_COOKIE))
        .as("login keeps the CSRF token")
        .isEmpty();
    assertThat(browser.cookie(ApiSession.XSRF_COOKIE)).isEqualTo(anonymousXsrf);

    assertThat(browser.get(ApiRoutes.ME).body()).isEqualTo("{\"username\":\"alice\"}");
    assertThat(browser.uploadCsv("browser.csv", CSV).statusCode()).isEqualTo(202);

    var logout = browser.post(ApiRoutes.LOGOUT);
    assertThat(logout.statusCode()).isEqualTo(204);
    assertThat(logout.body()).isEmpty();
    assertThat(BrowserClient.setCookies(logout, ApiSession.AUTH_COOKIE))
        .singleElement()
        .asString()
        .contains("Max-Age=0")
        .contains("HttpOnly");
    assertThat(browser.cookie(ApiSession.AUTH_COOKIE)).isEmpty();
    assertThat(browser.cookie(ApiSession.XSRF_COOKIE)).as("logout clears the CSRF token").isEmpty();

    assertThat(browser.get(ApiRoutes.ME).statusCode()).isEqualTo(401);
    assertThat(browser.cookie(ApiSession.XSRF_COOKIE))
        .as("GET /auth/me issues a new one")
        .isPresent();
    var again =
        browser.postJson(ApiRoutes.LOGIN, credentials(TestUsers.BOB, TestUsers.BOB_PASSWORD));
    assertThat(again.statusCode()).as(again.body()).isEqualTo(200);
    assertThat(browser.get(ApiRoutes.ME).body()).isEqualTo("{\"username\":\"bob\"}");
    assertThat(browser.get("/api/v1/jobs").body()).contains("\"totalElements\":0");
  }

  @Test
  @DisplayName("Logging in as another user replaces the identity carried by the cookie")
  void shouldIssueTokenForNewUserWhenLoggingInAgain() {
    var result =
        session
            .bind(restTestClient)
            .post()
            .uri(ApiRoutes.LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", TestUsers.BOB, "password", TestUsers.BOB_PASSWORD))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    var second = ApiSession.cookie(result, ApiSession.AUTH_COOKIE).orElseThrow();

    assertThat(second).isNotEqualTo(session.token().orElseThrow());
    me(ApiSession.of(second, session.csrfToken()))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.username")
        .isEqualTo(TestUsers.BOB);
  }

  @Test
  @DisplayName("A forged token is refused, the server clears its cookie and a login still works")
  void shouldClearForgedTokenAndStillAllowLogin() {
    var forged = ApiSession.of("forged.token.value", session.csrfToken());

    var rejected = me(forged).expectStatus().isUnauthorized().expectBody().returnResult();
    assertThat(setCookies(rejected)).anyMatch(cookie -> clears(cookie, ApiSession.AUTH_COOKIE));

    var login =
        forged
            .bind(restTestClient)
            .post()
            .uri(ApiRoutes.LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", TestUsers.ALICE, "password", TestUsers.ALICE_PASSWORD))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    assertThat(ApiSession.cookie(login, ApiSession.AUTH_COOKIE))
        .isPresent()
        .get()
        .isNotEqualTo("forged.token.value");
  }

  @Test
  @DisplayName("A validly signed token of a user who is not configured is refused and cleared")
  void shouldRefuseTokenWhenItsUserIsNotConfigured() {
    var orphan = ApiSession.of(tokens.issue("mallory"), session.csrfToken());

    var result = me(orphan).expectStatus().isUnauthorized().expectBody().returnResult();

    assertThat(setCookies(result)).anyMatch(cookie -> clears(cookie, ApiSession.AUTH_COOKIE));
    orphan
        .bind(restTestClient)
        .get()
        .uri("/api/v1/jobs")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("A valid token works for the whole API and never creates a server session")
  void shouldAuthenticateApiCallsWithoutServerSession() {
    var result =
        session
            .bind(restTestClient)
            .get()
            .uri("/api/v1/jobs")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();

    assertThat(setCookies(result)).noneMatch(cookie -> cookie.startsWith(SESSION_COOKIE + "="));
  }

  @Test
  @DisplayName("Wrong credentials get 401 BAD_CREDENTIALS, no token cookie and no new CSRF token")
  void shouldRejectWrongPasswordWithoutIssuingToken() {
    var anonymous = anonymous();

    var result =
        anonymous
            .bind(restTestClient)
            .post()
            .uri(ApiRoutes.LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", TestUsers.ALICE, "password", TestUsers.BOB_PASSWORD))
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("BAD_CREDENTIALS")
            .returnResult();

    assertThat(ApiSession.cookie(result, ApiSession.AUTH_COOKIE)).isEmpty();
    assertThat(ApiSession.cookie(result, ApiSession.XSRF_COOKIE)).isEmpty();
  }

  private RestTestClient.ResponseSpec me(ApiSession other) {
    return other.bind(restTestClient).get().uri(ApiRoutes.ME).exchange();
  }

  private static List<String> setCookies(ExchangeResult result) {
    var headers = result.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
    return headers == null ? List.of() : headers;
  }

  private static boolean clears(String setCookie, String name) {
    return setCookie.startsWith(name + "=;") && setCookie.contains("Max-Age=0");
  }

  private static String credentials(String username, String password) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
  }
}
