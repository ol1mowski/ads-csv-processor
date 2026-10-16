package pl.oliwier.adscsv.integration.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import pl.oliwier.adscsv.api.ApiRoutes;

public final class StreamingHttp {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(CONNECT_TIMEOUT)
          .build();
  private final int port;
  private final ApiSession session;

  public StreamingHttp(int port, ApiSession session) {
    this.port = port;
    this.session = session;
  }

  public HttpResponse<String> uploadChunked(String fileName, Supplier<InputStream> body)
      throws IOException, InterruptedException {
    return client.send(
        request(fileName, BodyPublishers.ofInputStream(body)), BodyHandlers.ofString());
  }

  public HttpResponse<String> upload(String fileName, Supplier<InputStream> body, long length)
      throws IOException, InterruptedException {
    return client.send(request(fileName, sized(body, length)), BodyHandlers.ofString());
  }

  public HttpResponse<String> uploadExpectingContinue(
      String fileName, Supplier<InputStream> body, long length)
      throws IOException, InterruptedException {
    var request = builder(fileName).expectContinue(true).POST(sized(body, length)).build();
    return client.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> uploadExpectingContinueWithoutCsrfHeader(
      String fileName, Supplier<InputStream> body, long length)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(uploadUri(fileName))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "text/csv")
            .header("Cookie", session.cookieHeader())
            .expectContinue(true)
            .POST(sized(body, length))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> sendExpectingContinue(
      String method, String path, String contentType, Supplier<InputStream> body, long length)
      throws IOException, InterruptedException {
    var request =
        withCsrfHeader(requestTo(path, contentType))
            .expectContinue(true)
            .method(method, sized(body, length))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> sendExpectingContinueWithoutCsrfHeader(
      String method, String path, String contentType, Supplier<InputStream> body, long length)
      throws IOException, InterruptedException {
    var request =
        requestTo(path, contentType)
            .expectContinue(true)
            .method(method, sized(body, length))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> sendChunked(
      String method, String path, String contentType, Supplier<InputStream> body)
      throws IOException, InterruptedException {
    var request =
        withCsrfHeader(requestTo(path, contentType))
            .method(method, BodyPublishers.ofInputStream(body))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<InputStream> download(String path) throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(REQUEST_TIMEOUT)
            .header("Cookie", session.cookieHeader())
            .GET()
            .build();
    return client.send(request, BodyHandlers.ofInputStream());
  }

  public static UUID jobId(HttpResponse<?> response) {
    var location = response.headers().firstValue("Location").orElseThrow();
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  private HttpRequest request(String fileName, BodyPublisher body) {
    return builder(fileName).POST(body).build();
  }

  private HttpRequest.Builder builder(String fileName) {
    return HttpRequest.newBuilder(uploadUri(fileName))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "text/csv")
        .header("Cookie", session.cookieHeader())
        .header(ApiSession.XSRF_HEADER, session.csrfToken());
  }

  private HttpRequest.Builder requestTo(String path, String contentType) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", contentType)
        .header("Cookie", session.cookieHeader());
  }

  private HttpRequest.Builder withCsrfHeader(HttpRequest.Builder builder) {
    return builder.header(ApiSession.XSRF_HEADER, session.csrfToken());
  }

  private URI uploadUri(String fileName) {
    return URI.create(
        "http://localhost:"
            + port
            + ApiRoutes.JOBS
            + "?fileName="
            + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
  }

  private static BodyPublisher sized(Supplier<InputStream> body, long length) {
    return BodyPublishers.fromPublisher(BodyPublishers.ofInputStream(body), length);
  }
}
