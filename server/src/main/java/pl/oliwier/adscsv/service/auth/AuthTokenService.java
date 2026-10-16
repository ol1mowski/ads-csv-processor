package pl.oliwier.adscsv.service.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.oliwier.adscsv.config.properties.AuthProperties;

@Slf4j
@Service
public class AuthTokenService {

  private static final String ISSUER = "ads-csv-processor";
  private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
  private static final String KEY_ALGORITHM = "HmacSHA256";
  private static final int GENERATED_KEY_BYTES = 32;

  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Clock clock;
  private final Duration ttl;

  public AuthTokenService(AuthProperties auth, Clock clock) {
    var key = signingKey(auth);
    this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(ALGORITHM).build();
    this.decoder = decoder(key, clock);
    this.clock = clock;
    this.ttl = auth.tokenTtl();
  }

  public String issue(String username) {
    var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(username)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();
    var header = JwsHeader.with(ALGORITHM).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public Optional<String> verify(String token) {
    try {
      return Optional.ofNullable(decoder.decode(token).getSubject());
    } catch (JwtException e) {
      log.debug("Rejected auth token: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static SecretKey signingKey(AuthProperties auth) {
    if (auth.jwtSecret() != null) {
      return new SecretKeySpec(auth.jwtSecret().getBytes(UTF_8), KEY_ALGORITHM);
    }
    if (!auth.allowGeneratedSecret()) {
      throw new IllegalStateException(
          "app.security.auth.jwt-secret (JWT_SECRET) must be set; a generated signing key is"
              + " allowed only with app.security.auth.allow-generated-secret=true");
    }
    log.warn(
        "app.security.auth.jwt-secret is not set: using a random signing key, so logins end when"
            + " this process stops and are not valid on other instances");
    var bytes = new byte[GENERATED_KEY_BYTES];
    new SecureRandom().nextBytes(bytes);
    return new SecretKeySpec(bytes, KEY_ALGORITHM);
  }

  private static JwtDecoder decoder(SecretKey key, Clock clock) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(ISSUER),
            new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText),
            new JwtClaimValidator<Instant>(
                JwtClaimNames.EXP, expiry -> clock.instant().isBefore(expiry))));
    return decoder;
  }
}
