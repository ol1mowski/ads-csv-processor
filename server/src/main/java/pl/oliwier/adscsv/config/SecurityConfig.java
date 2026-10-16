package pl.oliwier.adscsv.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CompositeHeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.security.web.savedrequest.NullRequestCache;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.config.properties.AuthProperties;
import pl.oliwier.adscsv.config.properties.SecurityUsersProperties;
import pl.oliwier.adscsv.exception.ProblemResponseWriter;
import pl.oliwier.adscsv.exception.auth.NotAuthenticatedException;
import pl.oliwier.adscsv.security.AuthCookies;
import pl.oliwier.adscsv.security.JwtCookieAuthenticationFilter;
import pl.oliwier.adscsv.service.auth.AuthTokenService;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  public static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
          + " font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self';"
          + " form-action 'self'; frame-ancestors 'none'";

  private static final String[] PUBLIC_RESOURCES = {
    "/", "/index.html", "/assets/**", "/favicon.svg", "/actuator/health"
  };
  private static final String SAME_SITE_LAX = "Lax";
  private static final String USER_ROLE = "USER";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final HeaderWriter SECURITY_HEADERS =
      new CompositeHeaderWriter(
          List.of(
              new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY),
              new XContentTypeOptionsHeaderWriter(),
              new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY),
              new ReferrerPolicyHeaderWriter(ReferrerPolicy.SAME_ORIGIN)));

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemResponseWriter problems,
      CsrfTokenRepository csrfTokens,
      AuthCookies authCookies,
      AuthTokenService authTokens,
      UserDetailsService users) {
    http.authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_RESOURCES)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, ApiRoutes.ME)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, ApiRoutes.LOGIN)
                    .permitAll()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .csrf(
            csrf ->
                csrf.spa()
                    .csrfTokenRepository(csrfTokens)
                    .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(
            logout ->
                logout
                    .logoutUrl(ApiRoutes.LOGOUT)
                    .addLogoutHandler((_, response, _) -> authCookies.expire(response))
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint(problems))
                    .accessDeniedHandler(accessDeniedHandler(problems)))
        .headers(
            headers ->
                headers
                    .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                    .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                    .addHeaderWriter(SECURITY_HEADERS)
                    .addHeaderWriter(
                        new DelegatingRequestMatcherHeaderWriter(
                            pathPattern("/api/**"),
                            new StaticHeadersWriter(HttpHeaders.CACHE_CONTROL, "no-store"))))
        .addFilterAfter(
            new JwtCookieAuthenticationFilter(authCookies, authTokens, users),
            SecurityContextHolderFilter.class);
    return http.build();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository(AuthProperties auth) {
    var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setHeaderName(CSRF_HEADER);
    repository.setCookiePath("/");
    repository.setCookieCustomizer(
        cookie -> cookie.sameSite(SAME_SITE_LAX).secure(auth.secureCookies()));
    return repository;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(SecurityUsersProperties props) {
    return new InMemoryUserDetailsManager(
        props.users().stream()
            .map(
                user ->
                    User.withUsername(user.username())
                        .password(user.passwordHash())
                        .roles(USER_ROLE)
                        .build())
            .toList());
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService users, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  static AuthenticationEntryPoint authenticationEntryPoint(ProblemResponseWriter problems) {
    return (request, response, _) ->
        problems.write(
            request,
            response,
            HttpStatus.UNAUTHORIZED,
            NotAuthenticatedException.CODE,
            NotAuthenticatedException.MESSAGE);
  }

  static AccessDeniedHandler accessDeniedHandler(ProblemResponseWriter problems) {
    return (request, response, e) -> {
      if (e instanceof CsrfException) {
        problems.write(
            request,
            response,
            HttpStatus.FORBIDDEN,
            "CSRF_INVALID",
            "Missing or invalid CSRF token");
        return;
      }
      problems.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied");
    };
  }
}
