package pl.oliwier.adscsv.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.oliwier.adscsv.service.auth.AuthTokenService;

@RequiredArgsConstructor
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

  private final AuthCookies cookies;
  private final AuthTokenService tokens;
  private final UserDetailsService users;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    cookies.read(request).ifPresent(token -> authenticate(token, response));
    chain.doFilter(request, response);
  }

  private void authenticate(String rawToken, HttpServletResponse response) {
    var user = tokens.verify(rawToken).flatMap(this::findUser);
    if (user.isEmpty()) {
      cookies.expire(response);
      return;
    }
    var holder = SecurityContextHolder.getContextHolderStrategy();
    var context = holder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user.get().getUsername(), null, user.get().getAuthorities()));
    holder.setContext(context);
  }

  private Optional<UserDetails> findUser(String username) {
    try {
      return Optional.of(users.loadUserByUsername(username));
    } catch (UsernameNotFoundException e) {
      return Optional.empty();
    }
  }
}
