package pl.oliwier.adscsv.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static pl.oliwier.adscsv.support.Xsrf.xsrf;

import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.service.job.JobService;
import pl.oliwier.adscsv.service.upload.JobUploadService;
import pl.oliwier.adscsv.support.SecuredWebSlice;
import pl.oliwier.adscsv.support.TestUsers;

@WebMvcTest
@Import(SecuredWebSlice.class)
@ActiveProfiles("test")
class EndpointInventoryTest {

  @MockitoBean JobUploadService uploadService;

  @MockitoBean JobService jobService;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  @Autowired MockMvcTester mvc;

  @Test
  @DisplayName("The API exposes exactly the endpoints covered by the slice test matrix")
  void shouldExposeExactlyTheTestedEndpoints() {
    var endpoints =
        handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(
                info ->
                    info.getPatternValues().stream()
                        .filter(pattern -> pattern.startsWith("/api/"))
                        .flatMap(
                            pattern ->
                                info.getMethodsCondition().getMethods().stream()
                                    .map(method -> method + " " + pattern)))
            .collect(Collectors.toSet());

    assertThat(endpoints)
        .containsExactlyInAnyOrder(
            "POST /api/v1/jobs",
            "GET /api/v1/jobs",
            "GET /api/v1/jobs/{id}",
            "POST /api/v1/jobs/{id}/cancel",
            "GET /api/v1/jobs/{id}/result",
            "GET /api/v1/auth/me",
            "POST /api/v1/auth/login");
  }

  @Test
  @DisplayName("The third auth endpoint, POST /auth/logout, is served by the security filter chain")
  void shouldServeLogoutFromTheSecurityFilterChain() {
    var logoutExchange = mvc.post().uri(ApiRoutes.LOGOUT).with(xsrf()).exchange();
    var logout = assertThat(logoutExchange);

    logout.hasStatus(HttpStatus.NO_CONTENT);
    assertThat(logoutExchange.getMvcResult().getHandler()).isNull();
    assertThat(mvc.get().uri(ApiRoutes.LOGOUT).with(user(TestUsers.ALICE)))
        .hasStatus(HttpStatus.NOT_FOUND);
  }
}
