package pl.oliwier.adscsv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.util.unit.DataSize;
import pl.oliwier.adscsv.config.properties.AuthProperties;
import pl.oliwier.adscsv.config.properties.HistoryProperties;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.config.properties.SecurityUsersProperties;
import pl.oliwier.adscsv.config.properties.StorageProperties;
import pl.oliwier.adscsv.config.properties.UploadProperties;

class ApplicationPropertiesTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private static final String BCRYPT_COST_10 =
      "$2a$10$p6AzXdmCk3GJmqbD9smD3uxFb9u8mBz2uCE4Aqo7fnpRUBxQCWPSu";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(AllProperties.class);

  private final ApplicationContextRunner usersRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(UserProperties.class);

  @Test
  @DisplayName("application.yml binds the documented limits")
  void shouldBindDocumentedDefaults() {
    runner.run(
        context -> {
          var upload = context.getBean(UploadProperties.class);
          var processing = context.getBean(ProcessingProperties.class);
          assertThat(upload.maxSize().toBytes()).isEqualTo(5_368_709_120L);
          assertThat(processing.workers()).isEqualTo(2);
          assertThat(processing.checkEvery()).isEqualTo(DataSize.ofMegabytes(1));
          assertThat(processing.maxAttempts()).isEqualTo(3);
          assertThat(processing.shutdownWait()).isEqualTo(Duration.ofSeconds(10));
          assertThat(processing.maxRejectedRatio()).isEqualTo(0.01);
          assertThat(context.getBean(HistoryProperties.class).maxPageSize()).isEqualTo(100);
          assertThat(context.getBean(StorageProperties.class).root()).isAbsolute();
          assertThat(context.getEnvironment().getProperty("spring.threads.virtual.enabled"))
              .isEqualTo("true");
          assertThat(
                  context
                      .getEnvironment()
                      .getProperty("spring.datasource.hikari.maximum-pool-size"))
              .isEqualTo("10");
        });
  }

  @Test
  @DisplayName("The auth token lives 8 hours, ships without a secret and is Secure on demand")
  void shouldBindAuthDefaults() {
    runner.run(
        context -> {
          var auth = context.getBean(AuthProperties.class);
          assertThat(auth.tokenTtl()).isEqualTo(Duration.ofHours(8));
          assertThat(auth.jwtSecret()).isNull();
          assertThat(auth.secureCookies()).isFalse();
          assertThat(context.getEnvironment().getProperty("server.servlet.session.timeout"))
              .isNull();
        });
    runner
        .withPropertyValues("COOKIE_SECURE=true", "JWT_SECRET=" + SECRET)
        .run(
            context -> {
              var auth = context.getBean(AuthProperties.class);
              assertThat(auth.secureCookies()).isTrue();
              assertThat(auth.jwtSecret()).isEqualTo(SECRET);
            });
  }

  @Test
  @DisplayName("Only the demo profile may sign tokens with a generated key")
  void shouldAllowGeneratedSecretOnlyInDemoProfile() {
    runner.run(
        context ->
            assertThat(context.getBean(AuthProperties.class).allowGeneratedSecret()).isFalse());
    runner
        .withPropertyValues("spring.profiles.active=demo")
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class).allowGeneratedSecret()).isTrue());
  }

  @Test
  @DisplayName("A signing secret shorter than 32 characters is rejected at startup")
  void shouldRejectJwtSecretWhenItIsTooShort() {
    runner
        .withPropertyValues("JWT_SECRET=" + SECRET.substring(1))
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0s", "59s"})
  @DisplayName("A token lifetime under a minute is rejected at startup")
  void shouldRejectTokenTtlWhenItIsTooShort(String ttl) {
    runner
        .withPropertyValues("app.security.auth.token-ttl=" + ttl)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("The auth properties never print the signing secret")
  void shouldHideJwtSecretWhenAuthPropertiesArePrinted() {
    var auth = new AuthProperties(SECRET, Duration.ofHours(8), false, false);

    assertThat(auth.toString()).contains("tokenTtl").doesNotContain(SECRET);
  }

  @Test
  @DisplayName(
      "Without a profile no accounts are configured, so startup fails and says how to fix it")
  void shouldFailStartupWhenNoUsersAreConfigured() {
    usersRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining(SecurityUsersProperties.NO_ACCOUNTS);
        });
  }

  @Test
  @DisplayName(
      "The demo profile defines alice and bob with bcrypt hashes of the documented passwords")
  void shouldBindDemoUsersWithBcryptHashes() {
    usersRunner
        .withPropertyValues("spring.profiles.active=demo")
        .run(
            context -> {
              var users = context.getBean(SecurityUsersProperties.class).users();
              var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
              assertThat(users)
                  .extracting(SecurityUsersProperties.User::username)
                  .containsExactly("alice", "bob");
              assertThat(users)
                  .allSatisfy(
                      user -> assertThat(user.passwordHash()).startsWith("{bcrypt}$2a$10$"));
              assertThat(encoder.matches("alice-demo-pass", users.get(0).passwordHash())).isTrue();
              assertThat(encoder.matches("bob-demo-pass", users.get(1).passwordHash())).isTrue();
              assertThat(encoder.matches("alice-demo-pass", users.get(1).passwordHash())).isFalse();
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "secret",
        "{noop}secret",
        "{foo}x",
        "{bycrypt}" + BCRYPT_COST_10,
        "{BCRYPT}" + BCRYPT_COST_10,
        BCRYPT_COST_10,
        "{bcrypt}$2a$12$p6AzXdmCk3GJmqbD9smD3uxFb9u8mBz2uCE4Aqo7fnpRUBxQCWPSu",
        "{bcrypt}$2a$04$p6AzXdmCk3GJmqbD9smD3uxFb9u8mBz2uCE4Aqo7fnpRUBxQCWPSu",
        "{bcrypt}$2a$10$tooShort",
        "{pbkdf2}5d923b44a6d129f3ddf3e3c8d29412723dcbde72445e8ef6bf3b508fbf17fa4ed4d6b99ca763d8dc"
      })
  @DisplayName("Startup accepts only {bcrypt} hashes of cost 10, the cost of the unknown-user hash")
  void shouldRejectUserWhenPasswordHashIsNotBcryptOfCostTen(String passwordHash) {
    usersRunner
        .withPropertyValues(
            "app.security.users[0].username=alice",
            "app.security.users[0].password-hash=" + passwordHash)
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"$2a$", "$2b$", "$2y$"})
  @DisplayName("Startup accepts a cost-10 bcrypt hash in every revision, including htpasswd's $2y$")
  void shouldAcceptUserWhenPasswordHashIsBcryptOfCostTen(String revision) {
    var hash = "{bcrypt}" + revision + BCRYPT_COST_10.substring(revision.length());

    usersRunner
        .withPropertyValues(
            "app.security.users[0].username=alice", "app.security.users[0].password-hash=" + hash)
        .run(
            context ->
                assertThat(context.getBean(SecurityUsersProperties.class).users())
                    .extracting(SecurityUsersProperties.User::passwordHash)
                    .containsExactly(hash));
  }

  @Test
  @DisplayName("A username outside the allow-list is rejected at startup")
  void shouldRejectUserWhenUsernameIsOutsideAllowList() {
    usersRunner
        .withPropertyValues(
            "app.security.users[0].username=al ice",
            "app.security.users[0].password-hash={bcrypt}" + BCRYPT_COST_10)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("The user list never prints password hashes")
  void shouldHidePasswordHashWhenUserIsPrinted() {
    var user = new SecurityUsersProperties.User("alice", "{bcrypt}" + BCRYPT_COST_10);

    assertThat(user.toString()).contains("alice").doesNotContain(BCRYPT_COST_10);
  }

  @Test
  @DisplayName("Zero workers is rejected at startup instead of breaking the executor")
  void shouldRejectZeroWorkers() {
    runner
        .withPropertyValues("app.processing.workers=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("A page size cap above the safe limit is rejected at startup")
  void shouldRejectMaxPageSizeWhenAboveSafeLimit() {
    runner
        .withPropertyValues("app.history.max-page-size=1001")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("The largest page at the largest page size keeps the offset within an int")
  void shouldKeepLargestOffsetWithinIntegerRange() {
    var largestOffset = (long) HistoryProperties.MAX_PAGE * HistoryProperties.MAX_PAGE_SIZE_LIMIT;

    assertThat(largestOffset).isLessThanOrEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @DisplayName("A missing storage root is rejected at startup")
  void shouldRejectMissingStorageRoot() {
    runner.withPropertyValues("app.storage.root=").run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({
    StorageProperties.class,
    UploadProperties.class,
    ProcessingProperties.class,
    HistoryProperties.class,
    AuthProperties.class
  })
  static class AllProperties {}

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SecurityUsersProperties.class)
  static class UserProperties {}
}
