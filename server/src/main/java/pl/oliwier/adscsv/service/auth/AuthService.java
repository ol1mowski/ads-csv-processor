package pl.oliwier.adscsv.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import pl.oliwier.adscsv.dto.LoginRequest;
import pl.oliwier.adscsv.exception.auth.InvalidCredentialsException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final AuthTokenService tokens;

  public LoginResult login(LoginRequest login, String clientAddress) {
    var authentication = authenticate(login, clientAddress);
    var token = tokens.issue(authentication.getName());
    log.info("User {} logged in from {}", authentication.getName(), clientAddress);
    return new LoginResult(authentication.getName(), token);
  }

  private Authentication authenticate(LoginRequest login, String clientAddress) {
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
    } catch (AuthenticationException e) {
      log.warn("Failed login attempt from {}", clientAddress);
      throw new InvalidCredentialsException();
    }
  }
}
