package pl.oliwier.adscsv.integration.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class BrowserClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private final HttpClient client =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies).build();
  private final int port;

  public BrowserClient(int port) {
    this.port = port;
  }

  public HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return send(request(path).GET());
  }

  public HttpResponse<String> postJson(String path, String json)
      throws IOException, InterruptedException {
    return send(
        withCsrfHeader(request(path))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json)));
  }

  public HttpResponse<String> post(String path) throws IOException, InterruptedException {
    return send(withCsrfHeader(request(path)).POST(BodyPublishers.noBody()));
  }

  public HttpResponse<String> uploadCsv(String fileName, byte[] body)
      throws IOException, InterruptedException {
    return send(
        withCsrfHeader(request("/api/v1/jobs?fileName=" + fileName))
            .header("Content-Type", "text/csv")
            .POST(BodyPublishers.ofByteArray(body)));
  }

  public Optional<String> cookie(String name) {
    return cookies.getCookieStore().getCookies().stream()
        .filter(cookie -> cookie.getName().equals(name))
        .map(HttpCookie::getValue)
        .filter(value -> !value.isEmpty())
        .findFirst();
  }

  public static List<String> setCookies(HttpResponse<?> response, String name) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(header -> header.startsWith(name + "="))
        .toList();
  }

  private HttpRequest.Builder withCsrfHeader(HttpRequest.Builder request) {
    cookie(ApiSession.XSRF_COOKIE)
        .ifPresent(token -> request.header(ApiSession.XSRF_HEADER, token));
    return request;
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(TIMEOUT);
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return client.send(request.build(), BodyHandlers.ofString());
  }
}
