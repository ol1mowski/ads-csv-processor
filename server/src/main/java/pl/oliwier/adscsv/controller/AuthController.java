package pl.oliwier.adscsv.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.LoginRequest;
import pl.oliwier.adscsv.dto.UserResponse;
import pl.oliwier.adscsv.exception.auth.NotAuthenticatedException;
import pl.oliwier.adscsv.security.AuthCookies;
import pl.oliwier.adscsv.service.auth.AuthService;

@RestController
@RequestMapping(ApiRoutes.AUTH)
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthCookies authCookies;

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(Principal principal) {
    if (principal == null) {
      throw new NotAuthenticatedException();
    }
    return ResponseEntity.ok(new UserResponse(principal.getName()));
  }

  @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserResponse> login(
      @Valid @RequestBody LoginRequest login,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result = authService.login(login, request.getRemoteAddr());
    authCookies.issue(response, result.token());
    return ResponseEntity.ok(new UserResponse(result.username()));
  }
}
