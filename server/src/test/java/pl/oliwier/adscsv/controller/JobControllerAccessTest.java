package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static pl.oliwier.adscsv.support.Xsrf.mismatchedXsrf;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;
import static pl.oliwier.adscsv.support.Xsrf.xsrfCookieOnly;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;
import pl.oliwier.adscsv.config.SecurityConfig;
import pl.oliwier.adscsv.dto.PageResponse;
import pl.oliwier.adscsv.service.job.ResultFile;
import pl.oliwier.adscsv.support.JobResponses;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestUsers;

class JobControllerAccessTest extends JobControllerSliceTest {

  private static final byte[] BODY = SampleData.csv(SampleData.FIRST_ROW);

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyEndpoint")
  @DisplayName("Every job endpoint answers a request without a token with 401 UNAUTHENTICATED")
  void shouldReturn401WhenRequestHasNoToken(
      String endpoint, Function<MockMvcTester, MockMvcRequestBuilder> request) {
    var result = assertThat(request.apply(mvc).with(xsrf()));

    result
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE)
        .doesNotContainHeader(HttpHeaders.LOCATION)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 401, "code": "UNAUTHENTICATED", "title": "Unauthorized"}
            """);
    verifyNoInteractions(jobService, uploadService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateChangingEndpoints")
  @DisplayName("Every state-changing job endpoint answers a missing CSRF token with 403")
  void shouldReturn403WhenCsrfTokenIsMissing(
      String endpoint, Function<MockMvcTester, MockMvcRequestBuilder> request) {
    assertCsrfRejected(request.apply(mvc).with(owner()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateChangingEndpoints")
  @DisplayName("Every state-changing job endpoint rejects a CSRF cookie sent without the header")
  void shouldReturn403WhenOnlyTheCsrfCookieIsSent(
      String endpoint, Function<MockMvcTester, MockMvcRequestBuilder> request) {
    assertCsrfRejected(request.apply(mvc).with(owner()).with(xsrfCookieOnly()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateChangingEndpoints")
  @DisplayName("Every state-changing job endpoint rejects a header that does not match the cookie")
  void shouldReturn403WhenCsrfHeaderDoesNotMatchCookie(
      String endpoint, Function<MockMvcTester, MockMvcRequestBuilder> request) {
    assertCsrfRejected(request.apply(mvc).with(owner()).with(mismatchedXsrf()));
  }

  @Test
  @DisplayName("Read endpoints work without a CSRF token")
  void shouldServeReadsWithoutCsrfToken() {
    given(jobService.get(JOB_ID, OWNER)).willReturn(JobResponses.queued(JOB_ID));

    assertThat(mvc.get().uri(JOB_URI).with(owner())).hasStatusOk();
  }

  @Test
  @DisplayName("Every job endpoint passes the authenticated username to the service as the owner")
  void shouldPassAuthenticatedUsernameAsOwner() throws IOException {
    var bob = user(TestUsers.BOB);
    given(uploadService.accept(eq(TestUsers.BOB), eq("ads.csv"), anyLong(), any()))
        .willReturn(JobResponses.queued(JOB_ID));
    given(jobService.get(JOB_ID, TestUsers.BOB)).willReturn(JobResponses.queued(JOB_ID));
    given(jobService.history(TestUsers.BOB, 0, 20, null))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    given(jobService.cancel(JOB_ID, TestUsers.BOB)).willReturn(JobResponses.queued(JOB_ID));
    given(jobService.result(JOB_ID, TestUsers.BOB))
        .willReturn(new ResultFile(new ByteArrayResource(BODY), "ads-processed.csv", BODY.length));

    assertThat(upload(mvc).with(bob).with(xsrf())).hasStatus(HttpStatus.ACCEPTED);
    assertThat(mvc.get().uri(JOB_URI).with(bob)).hasStatusOk();
    assertThat(mvc.get().uri("/api/v1/jobs").with(bob)).hasStatusOk();
    assertThat(mvc.post().uri(JOB_URI + "/cancel").with(bob).with(xsrf())).hasStatusOk();
    assertThat(mvc.get().uri(JOB_URI + "/result").with(bob)).hasStatusOk();

    verify(uploadService).accept(eq(TestUsers.BOB), eq("ads.csv"), anyLong(), any());
    verify(jobService).get(JOB_ID, TestUsers.BOB);
    verify(jobService).history(eq(TestUsers.BOB), eq(0), eq(20), isNull());
    verify(jobService).cancel(JOB_ID, TestUsers.BOB);
    verify(jobService).result(JOB_ID, TestUsers.BOB);
  }

  @Test
  @DisplayName("API responses carry the security headers and are never stored by caches")
  void shouldSendSecurityHeadersAndNoStoreOnApiResponses() {
    given(jobService.get(JOB_ID, OWNER)).willReturn(JobResponses.queued(JOB_ID));

    assertSecurityHeaders(assertThat(mvc.get().uri(JOB_URI).with(owner())))
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    assertSecurityHeaders(assertThat(mvc.get().uri(JOB_URI)))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  @DisplayName("A request without a token creates no session and is not saved for replay")
  void shouldNotCreateSessionWhenRequestIsUnauthenticated() {
    var exchange = mvc.get().uri(JOB_URI).exchange();

    assertThat(exchange).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getMvcResult().getRequest().getSession(false)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/internal", "/actuator/env", "/error", "/login", "/index.htm"})
  @DisplayName("Any other path is denied: 403 FORBIDDEN when signed in, 401 without a token")
  void shouldDenyPathWhenItIsNeitherPublicNorApi(String path) {
    assertThat(mvc.get().uri(path).with(owner()))
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 403, "code": "FORBIDDEN", "instance": "%s"}
            """
                .formatted(path));
    assertThat(mvc.get().uri(path))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 401, "code": "UNAUTHENTICATED"}
            """);
  }

  @Test
  @DisplayName("The SPA index is public, carries the CSP and is not marked no-store")
  void shouldServeSpaIndexWithoutToken() {
    var result = assertThat(mvc.get().uri("/index.html"));

    assertSecurityHeaders(result.hasStatusOk()).doesNotContainHeader(HttpHeaders.CACHE_CONTROL);
  }

  static Stream<Arguments> everyEndpoint() {
    return Stream.concat(
        stateChangingEndpoints(),
        Stream.of(
            endpoint("GET /jobs/{id}", tester -> tester.get().uri(JOB_URI)),
            endpoint("GET /jobs", tester -> tester.get().uri("/api/v1/jobs")),
            endpoint("GET /jobs/{id}/result", tester -> tester.get().uri(JOB_URI + "/result"))));
  }

  static Stream<Arguments> stateChangingEndpoints() {
    return Stream.of(
        endpoint("POST /jobs", JobControllerAccessTest::upload),
        endpoint("POST /jobs/{id}/cancel", tester -> tester.post().uri(JOB_URI + "/cancel")));
  }

  private void assertCsrfRejected(MockMvcRequestBuilder request) {
    assertThat(request)
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"status": 403, "code": "CSRF_INVALID", "title": "Forbidden"}
            """);
    verifyNoInteractions(jobService, uploadService);
  }

  private static MockMvcRequestBuilder upload(MockMvcTester tester) {
    return tester
        .post()
        .uri("/api/v1/jobs")
        .param("fileName", "ads.csv")
        .contentType("text/csv")
        .content(BODY);
  }

  private static MvcTestResultAssert assertSecurityHeaders(MvcTestResultAssert result) {
    return result
        .hasHeader("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY)
        .hasHeader("X-Content-Type-Options", "nosniff")
        .hasHeader("X-Frame-Options", "DENY")
        .hasHeader("Referrer-Policy", "same-origin");
  }

  private static Arguments endpoint(
      String name, Function<MockMvcTester, MockMvcRequestBuilder> request) {
    return Arguments.of(name, request);
  }
}
