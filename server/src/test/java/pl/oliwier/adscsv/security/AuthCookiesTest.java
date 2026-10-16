package pl.oliwier.adscsv.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.oliwier.adscsv.support.TestProperties;

class AuthCookiesTest {

  private static final Duration TTL = Duration.ofMinutes(30);

  private final AuthCookies cookies =
      new AuthCookies(TestProperties.auth(TestProperties.JWT_SECRET, TTL, false));
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  @DisplayName(
      "The token cookie is HttpOnly, SameSite=Lax, site-wide and lives as long as the token")
  void shouldIssueHardenedCookieWhenTokenIsGiven() {
    cookies.issue(response, "the-token");

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .singleElement()
        .asString()
        .startsWith("access_token=the-token;")
        .contains("Max-Age=1800")
        .contains("Path=/")
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .doesNotContain("Secure");
  }

  @Test
  @DisplayName("The cookie gets the Secure attribute only when configured")
  void shouldMarkCookieSecureWhenConfigured() {
    new AuthCookies(TestProperties.auth(TestProperties.JWT_SECRET, TTL, true))
        .issue(response, "the-token");

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("; Secure");
  }

  @Test
  @DisplayName("Expiring the cookie empties it and ends it immediately with the same attributes")
  void shouldExpireCookieWhenAskedTo() {
    cookies.expire(response);

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .singleElement()
        .asString()
        .startsWith("access_token=;")
        .contains("Max-Age=0")
        .contains("Path=/")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  @DisplayName("The token is read from the cookie")
  void shouldReadTokenWhenCookieIsPresent() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("other", "x"), new Cookie("access_token", "the-token"));

    assertThat(cookies.read(request)).contains("the-token");
  }

  @Test
  @DisplayName("A missing or empty cookie reads as no token")
  void shouldReadNothingWhenCookieIsMissingOrEmpty() {
    var request = new MockHttpServletRequest();
    assertThat(cookies.read(request)).isEmpty();

    request.setCookies(new Cookie("access_token", ""));
    assertThat(cookies.read(request)).isEmpty();
  }
}
