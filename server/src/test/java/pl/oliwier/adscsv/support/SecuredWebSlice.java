package pl.oliwier.adscsv.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import pl.oliwier.adscsv.config.ClockConfig;
import pl.oliwier.adscsv.config.SecurityConfig;
import pl.oliwier.adscsv.config.properties.AuthProperties;
import pl.oliwier.adscsv.config.properties.SecurityUsersProperties;
import pl.oliwier.adscsv.exception.ProblemResponseWriter;
import pl.oliwier.adscsv.security.AuthCookies;
import pl.oliwier.adscsv.service.auth.AuthService;
import pl.oliwier.adscsv.service.auth.AuthTokenService;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityUsersProperties.class, AuthProperties.class})
@Import({
  SecurityConfig.class,
  ProblemResponseWriter.class,
  ClockConfig.class,
  AuthService.class,
  AuthTokenService.class,
  AuthCookies.class
})
public class SecuredWebSlice {}
