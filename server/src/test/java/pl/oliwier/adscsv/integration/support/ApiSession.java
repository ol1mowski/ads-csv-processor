package pl.oliwier.adscsv.integration.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.oliwier.adscsv.api.ApiRoutes;

public final class ApiSession {

  public static final String AUTH_COOKIE = "access_token";
  public static final String XSRF_COOKIE = "XSRF-TOKEN";
  public static final String XSRF_HEADER = "X-XSRF-TOKEN";

  private final String token;
  private final String csrfToken;

  private ApiSession(String token, String csrfToken) {
    this.token = token;
    this.csrfToken = csrfToken;
  }

  public static ApiSession anonymous(RestTestClient client) {
    var result =
        client
            .get()
            .uri(ApiRoutes.ME)
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .returnResult();
    var token = cookie(result, XSRF_COOKIE);
    assertThat(token).as("XSRF-TOKEN issued by GET %s", ApiRoutes.ME).isPresent();
    return new ApiSession(null, token.orElseThrow());
  }

  public static ApiSession login(RestTestClient client, String username, String password) {
    var anonymous = anonymous(client);
    var result =
        anonymous
            .bind(client)
            .post()
            .uri(ApiRoutes.LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("username", username, "password", password))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    var token = cookie(result, AUTH_COOKIE);
    assertThat(token).as("auth cookie issued by POST %s", ApiRoutes.LOGIN).isPresent();
    return new ApiSession(
        token.orElseThrow(), cookie(result, XSRF_COOKIE).orElse(anonymous.csrfToken));
  }

  public static ApiSession of(String token, String csrfToken) {
    return new ApiSession(token, csrfToken);
  }

  public static Optional<String> cookie(ExchangeResult result, String name) {
    var cookies = result.getResponseCookies().get(name);
    return cookies == null || cookies.isEmpty()
        ? Optional.empty()
        : Optional.of(cookies.getLast())
            .filter(cookie -> !cookie.getMaxAge().isZero())
            .map(ResponseCookie::getValue)
            .filter(value -> !value.isEmpty());
  }

  public RestTestClient bind(RestTestClient client) {
    return client
        .mutate()
        .defaultHeader(HttpHeaders.COOKIE, cookieHeader())
        .defaultHeader(XSRF_HEADER, csrfToken)
        .build();
  }

  public RestTestClient bindCookiesOnly(RestTestClient client) {
    return client.mutate().defaultHeader(HttpHeaders.COOKIE, cookieHeader()).build();
  }

  public String cookieHeader() {
    var xsrf = XSRF_COOKIE + "=" + csrfToken;
    return token == null ? xsrf : AUTH_COOKIE + "=" + token + "; " + xsrf;
  }

  public String csrfToken() {
    return csrfToken;
  }

  public Optional<String> token() {
    return Optional.ofNullable(token);
  }

  public ApiSession withoutSession() {
    return new ApiSession(null, csrfToken);
  }
}
