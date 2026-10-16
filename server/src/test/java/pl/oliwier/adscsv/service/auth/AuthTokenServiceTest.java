package pl.oliwier.adscsv.service.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.oliwier.adscsv.config.properties.AuthProperties;
import pl.oliwier.adscsv.support.MutableClock;
import pl.oliwier.adscsv.support.TestProperties;

class AuthTokenServiceTest {

  private static final Duration TTL = Duration.ofMinutes(30);
  private static final String OTHER_SECRET = "another-signing-secret-32-chars!!";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
  private final AuthTokenService service =
      new AuthTokenService(TestProperties.auth(TestProperties.JWT_SECRET, TTL, false), clock);

  @Test
  @DisplayName("A fresh token verifies as its user")
  void shouldVerifyTokenWhenItWasJustIssued() {
    assertThat(service.verify(service.issue("alice"))).contains("alice");
  }

  @Test
  @DisplayName("A token stops verifying the moment its lifetime ends")
  void shouldRejectTokenWhenItExpired() {
    var token = service.issue("alice");

    clock.advance(TTL.minusSeconds(1));
    assertThat(service.verify(token)).isPresent();

    clock.advance(Duration.ofSeconds(1));
    assertThat(service.verify(token)).isEmpty();
  }

  @Test
  @DisplayName("A token signed with another key is rejected")
  void shouldRejectTokenWhenSignedWithAnotherKey() {
    var foreign =
        new AuthTokenService(TestProperties.auth(OTHER_SECRET, TTL, false), clock).issue("alice");

    assertThat(service.verify(foreign)).isEmpty();
  }

  @Test
  @DisplayName("Tokens made by a service with a generated key do not outlive that service")
  void shouldGenerateRandomKeyWhenNoSecretIsConfigured() {
    var generated = new AuthProperties(null, TTL, false, true);
    var first = new AuthTokenService(generated, clock);
    var second = new AuthTokenService(generated, clock);

    var token = first.issue("alice");

    assertThat(first.verify(token)).isPresent();
    assertThat(second.verify(token)).isEmpty();
  }

  @Test
  @DisplayName("Without a secret the service refuses to start unless a generated key is allowed")
  void shouldRefuseToStartWhenSecretIsMissingAndGeneratedKeyIsNotAllowed() {
    assertThatThrownBy(() -> new AuthTokenService(TestProperties.auth(null, TTL, false), clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  @DisplayName("A token whose payload was edited after signing is rejected")
  void shouldRejectTokenWhenPayloadWasTampered() {
    var parts = service.issue("alice").split("\\.");
    var forgedClaims =
        "{\"iss\":\"ads-csv-processor\",\"sub\":\"bob\",\"exp\":%d}"
            .formatted(clock.instant().plus(TTL).getEpochSecond());
    var forgedPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(forgedClaims.getBytes(UTF_8));

    assertThat(service.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
  }

  @Test
  @DisplayName("An unsigned alg=none token is rejected")
  void shouldRejectTokenWhenItIsUnsigned() {
    var unsigned =
        new PlainJWT(claims("ads-csv-processor", "alice", clock.instant().plus(TTL)).build())
            .serialize();

    assertThat(service.verify(unsigned)).isEmpty();
  }

  @Test
  @DisplayName("A token signed here with every required claim is accepted")
  void shouldAcceptTokenWhenItCarriesEveryRequiredClaim() throws Exception {
    var token = sign(claims("ads-csv-processor", "alice", clock.instant().plus(TTL)));

    assertThat(service.verify(token)).contains("alice");
  }

  @Test
  @DisplayName("A correctly signed token for another issuer is rejected")
  void shouldRejectTokenWhenIssuerIsForeign() throws Exception {
    var token = sign(claims("someone-else", "alice", clock.instant().plus(TTL)));

    assertThat(service.verify(token)).isEmpty();
  }

  @Test
  @DisplayName("A correctly signed token without an expiry is rejected")
  void shouldRejectTokenWhenItHasNoExpiry() throws Exception {
    var token =
        sign(new JWTClaimsSet.Builder().issuer("ads-csv-processor").subject("alice").build());

    assertThat(service.verify(token)).isEmpty();
  }

  @Test
  @DisplayName("A correctly signed token without a subject is rejected")
  void shouldRejectTokenWhenItHasNoSubject() throws Exception {
    var token =
        sign(
            new JWTClaimsSet.Builder()
                .issuer("ads-csv-processor")
                .expirationTime(Date.from(clock.instant().plus(TTL)))
                .build());

    assertThat(service.verify(token)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "garbage", "a.b.c", "a.b", "eyJhbGciOiJIUzI1NiJ9.e30.", "....."})
  @DisplayName("Anything that is not a token is rejected without an exception")
  void shouldRejectValueWhenItIsNotAToken(String value) {
    assertThat(service.verify(value)).isEmpty();
  }

  private JWTClaimsSet.Builder claims(String issuer, String subject, Instant expiresAt) {
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject(subject)
        .expirationTime(Date.from(expiresAt));
  }

  private static String sign(JWTClaimsSet.Builder claims) throws Exception {
    return sign(claims.build());
  }

  private static String sign(JWTClaimsSet claims) throws Exception {
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(TestProperties.JWT_SECRET.getBytes(UTF_8)));
    return jwt.serialize();
  }
}
