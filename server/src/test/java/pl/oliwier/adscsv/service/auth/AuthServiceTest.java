package pl.oliwier.adscsv.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.oliwier.adscsv.dto.LoginRequest;
import pl.oliwier.adscsv.exception.auth.InvalidCredentialsException;
import pl.oliwier.adscsv.support.MutableClock;
import pl.oliwier.adscsv.support.TestProperties;

@ExtendWith(OutputCaptureExtension.class)
class AuthServiceTest {

  private static final String CLIENT_ADDRESS = "203.0.113.7";

  private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
  private final AuthTokenService tokens =
      new AuthTokenService(
          TestProperties.auth(), new MutableClock(Instant.parse("2026-01-01T10:00:00Z")));
  private final AuthService service = new AuthService(authenticationManager, tokens);

  private final LoginRequest login = new LoginRequest("alice", "secret");

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Valid credentials yield a token for the user and log the client address")
  void shouldIssueTokenWhenCredentialsValid(CapturedOutput output) {
    var authenticated =
        UsernamePasswordAuthenticationToken.authenticated(
            "alice", null, AuthorityUtils.NO_AUTHORITIES);
    when(authenticationManager.authenticate(any())).thenReturn(authenticated);

    var result = service.login(login, CLIENT_ADDRESS);

    assertThat(result.username()).isEqualTo("alice");
    assertThat(result.toString()).doesNotContain(result.token());
    assertThat(tokens.verify(result.token())).contains("alice");
    assertThat(output)
        .contains("User alice logged in from " + CLIENT_ADDRESS)
        .doesNotContain("secret");
  }

  @Test
  @DisplayName("Logging in is stateless: no security context is left behind")
  void shouldLeaveNoServerStateWhenLoggingIn() {
    var authenticated =
        UsernamePasswordAuthenticationToken.authenticated(
            "alice", null, AuthorityUtils.NO_AUTHORITIES);
    when(authenticationManager.authenticate(any())).thenReturn(authenticated);

    service.login(login, CLIENT_ADDRESS);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("Bad credentials become InvalidCredentialsException logged with the address only")
  void shouldThrowInvalidCredentialsWhenAuthenticationFails(CapturedOutput output) {
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
    var wrong = new LoginRequest("mallory", "wrong-password-123");

    assertThatThrownBy(() -> service.login(wrong, CLIENT_ADDRESS))
        .isInstanceOf(InvalidCredentialsException.class);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(output)
        .contains("Failed login attempt from " + CLIENT_ADDRESS)
        .doesNotContain("mallory")
        .doesNotContain("wrong-password-123");
  }
}
