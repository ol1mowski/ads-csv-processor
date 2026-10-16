package pl.oliwier.adscsv.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import pl.oliwier.adscsv.service.auth.AuthTokenService;
import pl.oliwier.adscsv.support.MutableClock;
import pl.oliwier.adscsv.support.TestProperties;

class JwtCookieAuthenticationFilterTest {

  private static final Duration TTL = Duration.ofMinutes(30);
  private static final String ME = "/api/v1/auth/me";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
  private final AuthTokenService tokens =
      new AuthTokenService(TestProperties.auth(TestProperties.JWT_SECRET, TTL, false), clock);
  private final AuthCookies cookies =
      new AuthCookies(TestProperties.auth(TestProperties.JWT_SECRET, TTL, false));
  private final InMemoryUserDetailsManager users =
      new InMemoryUserDetailsManager(
          User.withUsername("alice").password("{noop}x").roles("USER").build());
  private final JwtCookieAuthenticationFilter filter =
      new JwtCookieAuthenticationFilter(cookies, tokens, users);

  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicBoolean chainReached = new AtomicBoolean();
  private final AtomicReference<Authentication> seenByChain = new AtomicReference<>();
  private final FilterChain chain =
      (request, response) -> {
        chainReached.set(true);
        seenByChain.set(SecurityContextHolder.getContext().getAuthentication());
      };

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("A valid token signs the request in as its user with the user's roles")
  void shouldAuthenticateWhenTokenIsValid() throws Exception {
    filter.doFilter(request("GET", ME, tokens.issue("alice")), response, chain);

    var authentication = seenByChain.get();
    assertThat(authentication).isNotNull();
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getName()).isEqualTo("alice");
    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  @DisplayName("A request without the cookie passes through anonymous and untouched")
  void shouldStayAnonymousWhenThereIsNoCookie() throws Exception {
    filter.doFilter(new MockHttpServletRequest("GET", ME), response, chain);

    assertThat(chainReached).isTrue();
    assertThat(seenByChain.get()).isNull();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-jwt", "a.b.c"})
  @DisplayName("A cookie that is not a valid token is cleared and the request stays anonymous")
  void shouldClearCookieWhenTokenIsInvalid(String value) throws Exception {
    filter.doFilter(request("GET", ME, value), response, chain);

    assertThat(chainReached).isTrue();
    assertThat(seenByChain.get()).isNull();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .singleElement()
        .asString()
        .startsWith("access_token=;")
        .contains("Max-Age=0");
  }

  @Test
  @DisplayName("An expired token is cleared and the request stays anonymous")
  void shouldClearCookieWhenTokenExpired() throws Exception {
    var token = tokens.issue("alice");
    clock.advance(TTL);

    filter.doFilter(request("GET", ME, token), response, chain);

    assertThat(seenByChain.get()).isNull();
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  @DisplayName("A valid token of a user who no longer exists is cleared and not trusted")
  void shouldClearCookieWhenUserIsGone() throws Exception {
    var token = tokens.issue("mallory");

    filter.doFilter(request("GET", ME, token), response, chain);

    assertThat(chainReached).isTrue();
    assertThat(seenByChain.get()).isNull();
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  @DisplayName("A valid token is never reissued, so its expiry stays fixed")
  void shouldNotReissueTokenWhenItIsValid() throws Exception {
    var token = tokens.issue("alice");
    clock.advance(TTL.minusSeconds(1));

    filter.doFilter(request("GET", ME, token), response, chain);

    assertThat(seenByChain.get().getName()).isEqualTo("alice");
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  @DisplayName("A valid token also signs in requests outside the API, so denied paths answer 403")
  void shouldAuthenticateWhenRequestIsNotAnApiCall() throws Exception {
    filter.doFilter(request("GET", "/internal", tokens.issue("alice")), response, chain);

    assertThat(seenByChain.get().getName()).isEqualTo("alice");
  }

  private static MockHttpServletRequest request(String method, String path, String token) {
    var request = new MockHttpServletRequest(method, path);
    request.setCookies(new Cookie(AuthCookies.NAME, token));
    return request;
  }
}
