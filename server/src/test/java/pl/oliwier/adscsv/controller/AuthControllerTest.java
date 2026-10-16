package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.oliwier.adscsv.support.Xsrf.mismatchedXsrf;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.AssertProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.security.AuthCookies;
import pl.oliwier.adscsv.service.auth.AuthTokenService;
import pl.oliwier.adscsv.support.SecuredWebSlice;
import pl.oliwier.adscsv.support.TestUsers;
import pl.oliwier.adscsv.support.Xsrf;

@WebMvcTest(AuthController.class)
@Import(SecuredWebSlice.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

  @Autowired MockMvcTester mvc;

  @Autowired AuthTokenService tokens;

  @Test
  @DisplayName(
      "GET /auth/me without a token is 401 UNAUTHENTICATED and still issues the CSRF token")
  void shouldReturn401AndIssueXsrfCookieWhenAnonymous() {
    var exchange = mvc.get().uri(ApiRoutes.ME).exchange();

    assertThat(exchange)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 401, "code": "UNAUTHENTICATED", "instance": "/api/v1/auth/me"}
            """);
    assertXsrfCookieIssued(exchange.getResponse());
    assertThat(exchange.getResponse().getCookie(AuthCookies.NAME)).isNull();
    assertThat(exchange.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName("GET /auth/me with a valid token cookie returns the username and creates no session")
  void shouldReturnUsernameWhenTokenCookieIsValid() {
    var exchange =
        mvc.get()
            .uri(ApiRoutes.ME)
            .cookie(new Cookie(AuthCookies.NAME, tokens.issue("alice")))
            .exchange();

    assertThat(exchange)
        .hasStatusOk()
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"username": "alice"}
            """);
    assertXsrfCookieIssued(exchange.getResponse());
    assertThat(exchange.getResponse().getCookie(AuthCookies.NAME)).isNull();
    assertThat(exchange.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName("GET /auth/me with a forged token cookie is 401 and clears that cookie")
  void shouldReturn401AndClearCookieWhenTokenCookieIsForged() {
    var exchange =
        mvc.get()
            .uri(ApiRoutes.ME)
            .cookie(new Cookie(AuthCookies.NAME, "forged.token.value"))
            .exchange();

    assertThat(exchange).hasStatus(HttpStatus.UNAUTHORIZED);
    var cleared = exchange.getResponse().getCookie(AuthCookies.NAME);
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isZero();
  }

  @Test
  @DisplayName("GET /auth/me with a valid token keeps the CSRF token instead of rotating it")
  void shouldKeepExistingXsrfCookieWhenAuthenticated() {
    var exchange =
        mvc.get()
            .uri(ApiRoutes.ME)
            .cookie(new Cookie(AuthCookies.NAME, tokens.issue("alice")))
            .cookie(new Cookie(Xsrf.COOKIE, "existing-token"))
            .exchange();

    assertThat(exchange).hasStatusOk();
    assertThat(exchange.getResponse().getCookie(Xsrf.COOKIE)).isNull();
  }

  @Test
  @DisplayName("GET /auth/me keeps an existing token instead of rotating it on every call")
  void shouldKeepExistingXsrfCookieWhenPresent() {
    var exchange =
        mvc.get().uri(ApiRoutes.ME).cookie(new Cookie(Xsrf.COOKIE, "existing-token")).exchange();

    assertThat(exchange).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getCookie(Xsrf.COOKIE)).isNull();
  }

  @Test
  @DisplayName("POST /auth/login sets an HttpOnly token cookie for the user and creates no session")
  void shouldLogInAndSetTokenCookie(CapturedOutput output) {
    var exchange = login(TestUsers.ALICE, TestUsers.ALICE_PASSWORD).with(xsrf()).exchange();

    assertThat(exchange)
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"username": "alice"}
            """);
    var token = cookies(exchange.getResponse(), AuthCookies.NAME).getLast();
    assertThat(token.isHttpOnly()).isTrue();
    assertThat(token.getSecure()).isFalse();
    assertThat(token.getPath()).isEqualTo("/");
    assertThat(token.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(token.getMaxAge()).isEqualTo(28800);
    assertThat(tokens.verify(token.getValue())).contains("alice");
    assertThat(exchange.getRequest().getSession(false)).isNull();
    assertThat(exchange.getResponse().getCookie(Xsrf.COOKIE))
        .as("login keeps the CSRF token the request carried")
        .isNull();
    assertThat(output)
        .contains("User alice logged in from")
        .doesNotContain(TestUsers.ALICE_PASSWORD)
        .doesNotContain(token.getValue());
  }

  @Test
  @DisplayName(
      "POST /auth/login while holding another user's token issues a token for the new user")
  void shouldIssueTokenForNewUserWhenAnotherTokenIsPresented() {
    var exchange =
        login(TestUsers.BOB, TestUsers.BOB_PASSWORD)
            .cookie(new Cookie(AuthCookies.NAME, tokens.issue("alice")))
            .with(xsrf())
            .exchange();

    assertThat(exchange).hasStatusOk();
    var token = cookies(exchange.getResponse(), AuthCookies.NAME).getLast();
    assertThat(tokens.verify(token.getValue())).contains("bob");
  }

  @Test
  @DisplayName("POST /auth/login with a stale token cookie still logs in; the new token comes last")
  void shouldLogInWhenStaleTokenCookieIsPresented() {
    var exchange =
        login(TestUsers.ALICE, TestUsers.ALICE_PASSWORD)
            .cookie(new Cookie(AuthCookies.NAME, "stale.token.value"))
            .with(xsrf())
            .exchange();

    assertThat(exchange).hasStatusOk();
    var token = cookies(exchange.getResponse(), AuthCookies.NAME).getLast();
    assertThat(token.getMaxAge()).isEqualTo(28800);
    assertThat(tokens.verify(token.getValue())).contains("alice");
  }

  @Test
  @DisplayName(
      "An unknown user and a wrong password get the identical 401 BAD_CREDENTIALS, no new tokens")
  void shouldReturnIdenticalProblemWhenUserUnknownOrPasswordWrong(CapturedOutput output) {
    var wrongPassword = login(TestUsers.ALICE, "wrong-password-123").with(xsrf()).exchange();
    var unknownUser = login("mallory", "wrong-password-123").with(xsrf()).exchange();

    assertThat(wrongPassword)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 401, "code": "BAD_CREDENTIALS", "detail": "Invalid username or password",
             "instance": "/api/v1/auth/login"}
            """);
    assertThat(unknownUser).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(unknownUser.getResponse().getContentAsByteArray())
        .isEqualTo(wrongPassword.getResponse().getContentAsByteArray());
    assertThat(wrongPassword.getResponse().getCookie(AuthCookies.NAME)).isNull();
    assertThat(unknownUser.getResponse().getCookie(AuthCookies.NAME)).isNull();
    assertThat(wrongPassword.getResponse().getCookie(Xsrf.COOKIE)).isNull();
    assertThat(unknownUser.getResponse().getCookie(Xsrf.COOKIE)).isNull();
    assertThat(wrongPassword.getRequest().getSession(false)).isNull();
    assertThat(output)
        .contains("Failed login attempt from")
        .doesNotContain("wrong-password-123")
        .doesNotContain("mallory");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"username\": \"alice\"}",
        "{\"password\": \"alice-test-pass\"}",
        "{\"username\": \"\", \"password\": \"alice-test-pass\"}",
        "{\"username\": \"alice\", \"password\": \"\"}",
        "{\"username\": null, \"password\": \"alice-test-pass\"}",
        "not json",
        "[]"
      })
  @DisplayName("POST /auth/login with a missing, empty or malformed field is 400 REQUEST_INVALID")
  void shouldReturn400WhenLoginBodyIsInvalid(String body) {
    assertThat(
            mvc.post()
                .uri(ApiRoutes.LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(xsrf()))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 400, "code": "REQUEST_INVALID"}
            """);
  }

  @Test
  @DisplayName("POST /auth/login enforces 64 characters for the username and 128 for the password")
  void shouldReturn400WhenCredentialsAreTooLong() {
    assertThat(login("a".repeat(65), "x").with(xsrf())).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(login(TestUsers.ALICE, "x".repeat(129)).with(xsrf()))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(login("a".repeat(64), "x".repeat(128)).with(xsrf()))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("POST /auth/login accepts only JSON")
  void shouldReturn415WhenLoginIsNotJson() {
    assertThat(
            mvc.post()
                .uri(ApiRoutes.LOGIN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", TestUsers.ALICE)
                .param("password", TestUsers.ALICE_PASSWORD)
                .with(xsrf()))
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 415, "code": "UNSUPPORTED_MEDIA_TYPE"}
            """);
  }

  @Test
  @DisplayName("POST /auth/login without the CSRF header is 403 CSRF_INVALID")
  void shouldReturn403WhenLoginLacksCsrfToken() {
    assertCsrfRejected(login(TestUsers.ALICE, TestUsers.ALICE_PASSWORD));
    assertCsrfRejected(login(TestUsers.ALICE, TestUsers.ALICE_PASSWORD).with(mismatchedXsrf()));
  }

  @Test
  @DisplayName("POST /auth/logout clears the token cookie and the CSRF token")
  void shouldClearTokenCookieWhenLoggingOut() {
    var exchange =
        mvc.post()
            .uri(ApiRoutes.LOGOUT)
            .cookie(new Cookie(AuthCookies.NAME, tokens.issue("alice")))
            .with(xsrf())
            .exchange();

    assertThat(exchange).hasStatus(HttpStatus.NO_CONTENT);
    var response = exchange.getResponse();
    var tokenCookies = cookies(response, AuthCookies.NAME);
    assertThat(tokenCookies)
        .singleElement()
        .satisfies(
            cookie -> {
              assertThat(cookie.getValue()).isEmpty();
              assertThat(cookie.getMaxAge()).isZero();
              assertThat(cookie.getPath()).isEqualTo("/");
              assertThat(cookie.isHttpOnly()).isTrue();
            });
    assertThat(cookies(response, Xsrf.COOKIE).getLast().getMaxAge())
        .as("the next login bootstraps a fresh CSRF token with GET /auth/me")
        .isZero();
    assertThat(exchange.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName("POST /auth/logout without the CSRF header is 403 and leaves the token cookie alone")
  void shouldReturn403WhenLogoutLacksCsrfToken() {
    var exchange =
        mvc.post()
            .uri(ApiRoutes.LOGOUT)
            .cookie(new Cookie(AuthCookies.NAME, tokens.issue("alice")))
            .exchange();

    assertCsrfRejected(exchange);
    assertThat(exchange.getResponse().getCookie(AuthCookies.NAME)).isNull();
  }

  private MockMvcRequestBuilder login(String username, String password) {
    return mvc.post()
        .uri(ApiRoutes.LOGIN)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username": "%s", "password": "%s"}
            """
                .formatted(username, password));
  }

  private void assertCsrfRejected(AssertProvider<MvcTestResultAssert> request) {
    assertThat(request)
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 403, "code": "CSRF_INVALID"}
            """);
  }

  private static void assertXsrfCookieIssued(MockHttpServletResponse response) {
    var cookie = cookies(response, Xsrf.COOKIE).getLast();
    assertThat(cookie.getValue()).isNotBlank();
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(cookie.getSecure()).isFalse();
  }

  private static List<Cookie> cookies(MockHttpServletResponse response, String name) {
    var matching =
        Arrays.stream(response.getCookies())
            .filter(cookie -> cookie.getName().equals(name))
            .toList();
    assertThat(matching).as("Set-Cookie %s", name).isNotEmpty();
    return matching;
  }
}
