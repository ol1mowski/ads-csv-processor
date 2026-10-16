package pl.oliwier.adscsv.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;
import pl.oliwier.adscsv.config.properties.AuthProperties;

@Component
public class AuthCookies {

  public static final String NAME = "access_token";

  private static final String SAME_SITE_LAX = "Lax";

  private final Duration maxAge;
  private final boolean secure;

  public AuthCookies(AuthProperties auth) {
    this.maxAge = auth.tokenTtl();
    this.secure = auth.secureCookies();
  }

  public Optional<String> read(HttpServletRequest request) {
    return Optional.ofNullable(WebUtils.getCookie(request, NAME))
        .map(Cookie::getValue)
        .filter(StringUtils::hasText);
  }

  public void issue(HttpServletResponse response, String token) {
    write(response, token, maxAge);
  }

  public void expire(HttpServletResponse response) {
    write(response, "", Duration.ZERO);
  }

  private void write(HttpServletResponse response, String value, Duration lifetime) {
    var cookie =
        ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(SAME_SITE_LAX)
            .path("/")
            .maxAge(lifetime)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
