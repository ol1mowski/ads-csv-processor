package pl.oliwier.adscsv.integration.support;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.PageResponse;

public final class JobsApi {

  public static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

  private static final ParameterizedTypeReference<PageResponse<JobResponse>> PAGE =
      new ParameterizedTypeReference<>() {};

  private final RestTestClient client;

  public JobsApi(RestTestClient client) {
    this.client = client;
  }

  public ResponseSpec upload(String fileName, byte[] body) {
    return client
        .post()
        .uri(ApiRoutes.JOBS + "?fileName={fileName}", fileName)
        .contentType(TEXT_CSV)
        .body(body)
        .exchange();
  }

  public JobResponse accepted(String fileName, byte[] body) {
    return upload(fileName, body)
        .expectStatus()
        .isAccepted()
        .expectBody(JobResponse.class)
        .returnResult()
        .getResponseBody();
  }

  public ResponseSpec job(UUID id) {
    return client.get().uri(ApiRoutes.job(id)).exchange();
  }

  public JobResponse get(UUID id) {
    return job(id)
        .expectStatus()
        .isOk()
        .expectBody(JobResponse.class)
        .returnResult()
        .getResponseBody();
  }

  public ResponseSpec cancel(UUID id) {
    return client.post().uri(ApiRoutes.job(id) + "/cancel").exchange();
  }

  public JobResponse cancelled(UUID id) {
    return cancel(id)
        .expectStatus()
        .isOk()
        .expectBody(JobResponse.class)
        .returnResult()
        .getResponseBody();
  }

  public ResponseSpec result(UUID id) {
    return client.get().uri(ApiRoutes.result(id)).exchange();
  }

  public String resultText(UUID id) {
    var body = result(id).expectStatus().isOk().expectBody().returnResult().getResponseBody();
    return new String(body, StandardCharsets.US_ASCII);
  }

  public ResponseSpec history(String query) {
    return client.get().uri(ApiRoutes.JOBS + query).exchange();
  }

  public PageResponse<JobResponse> page(String query) {
    return history(query).expectStatus().isOk().expectBody(PAGE).returnResult().getResponseBody();
  }

  public ResponseSpec health() {
    return client.get().uri("/actuator/health").exchange();
  }
}
