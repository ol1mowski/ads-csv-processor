package pl.oliwier.adscsv.support;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class Xsrf {

  public static final String COOKIE = "XSRF-TOKEN";
  public static final String HEADER = "X-XSRF-TOKEN";

  private Xsrf() {}

  public static RequestPostProcessor xsrf() {
    var token = UUID.randomUUID().toString();
    return withCookieAndHeader(token, token);
  }

  public static RequestPostProcessor mismatchedXsrf() {
    return withCookieAndHeader(UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  public static RequestPostProcessor xsrfCookieOnly() {
    return request -> {
      addCookie(request, UUID.randomUUID().toString());
      return request;
    };
  }

  private static RequestPostProcessor withCookieAndHeader(String cookie, String header) {
    return request -> {
      addCookie(request, cookie);
      request.addHeader(HEADER, header);
      return request;
    };
  }

  private static void addCookie(MockHttpServletRequest request, String value) {
    var existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
    request.setCookies(
        Stream.concat(Arrays.stream(existing), Stream.of(new Cookie(COOKIE, value)))
            .toArray(Cookie[]::new));
  }
}
