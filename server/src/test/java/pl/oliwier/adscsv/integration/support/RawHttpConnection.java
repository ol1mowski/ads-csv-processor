package pl.oliwier.adscsv.integration.support;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RawHttpConnection implements AutoCloseable {

  private static final int READ_TIMEOUT_MILLIS = 10_000;

  private final Socket socket;

  private RawHttpConnection(Socket socket) {
    this.socket = socket;
  }

  public static RawHttpConnection open(int port) throws IOException {
    var socket = new Socket("localhost", port);
    socket.setSoTimeout(READ_TIMEOUT_MILLIS);
    return new RawHttpConnection(socket);
  }

  public void sendCsvUploadHead(String fileName, long contentLength, ApiSession session)
      throws IOException {
    var head =
        "POST /api/v1/jobs?fileName="
            + fileName
            + " HTTP/1.1\r\n"
            + "Host: localhost:"
            + socket.getPort()
            + "\r\n"
            + "Cookie: "
            + session.cookieHeader()
            + "\r\n"
            + ApiSession.XSRF_HEADER
            + ": "
            + session.csrfToken()
            + "\r\n"
            + "Content-Type: text/csv\r\n"
            + "Content-Length: "
            + contentLength
            + "\r\n"
            + "Connection: close\r\n\r\n";
    send(head.getBytes(StandardCharsets.US_ASCII));
  }

  public void sendGet(String target) throws IOException {
    var head =
        "GET "
            + target
            + " HTTP/1.1\r\n"
            + "Host: localhost:"
            + socket.getPort()
            + "\r\n"
            + "Accept: text/html\r\n"
            + "Connection: close\r\n\r\n";
    send(head.getBytes(StandardCharsets.US_ASCII));
  }

  public void send(byte[] bytes) throws IOException {
    var out = socket.getOutputStream();
    out.write(bytes);
    out.flush();
  }

  public String readResponse() throws IOException {
    return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  public static Map<String, String> headers(String head) {
    var headers = new HashMap<String, String>();
    for (var line : head.split("\r\n")) {
      var colon = line.indexOf(':');
      if (colon > 0) {
        headers.put(
            line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
            line.substring(colon + 1).trim());
      }
    }
    return headers;
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
