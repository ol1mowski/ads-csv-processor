package pl.oliwier.adscsv.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import pl.oliwier.adscsv.exception.ProblemResponseWriter;
import tools.jackson.databind.json.JsonMapper;

class SecurityConfigTest {

  private final ProblemResponseWriter problems =
      new ProblemResponseWriter(JsonMapper.builder().build());
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("POST", "/api/v1/jobs/x/cancel");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  @DisplayName("The entry point answers 401 UNAUTHENTICATED without a challenge or redirect")
  void shouldWriteUnauthenticatedProblemWhenEntryPointCommences() throws Exception {
    SecurityConfig.authenticationEntryPoint(problems)
        .commence(request, response, new BadCredentialsException("no session"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).isNull();
    assertThat(response.getRedirectedUrl()).isNull();
    assertThat(response.getContentAsString())
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains("\"detail\":\"Authentication is required\"");
  }

  @Test
  @DisplayName("A missing or invalid CSRF token is 403 CSRF_INVALID")
  void shouldWriteCsrfProblemWhenCsrfTokenIsRejected() throws Exception {
    var handler = SecurityConfig.accessDeniedHandler(problems);
    var invalid = new MockHttpServletResponse();

    handler.handle(request, response, new MissingCsrfTokenException(null));
    handler.handle(
        request,
        invalid,
        new InvalidCsrfTokenException(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "a"), "b"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"CSRF_INVALID\"");
    assertThat(invalid.getStatus()).isEqualTo(403);
    assertThat(invalid.getContentAsString()).contains("\"code\":\"CSRF_INVALID\"");
  }

  @Test
  @DisplayName("Any other authorization denial is 403 FORBIDDEN")
  void shouldWriteForbiddenProblemWhenAccessIsDenied() throws Exception {
    SecurityConfig.accessDeniedHandler(problems)
        .handle(request, response, new AuthorizationDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString())
        .contains("\"code\":\"FORBIDDEN\"")
        .contains("\"detail\":\"Access is denied\"");
  }
}
