package pl.oliwier.adscsv.integration.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestThreadRecorder extends OncePerRequestFilter {

  private final ConcurrentLinkedQueue<ServedRequest> served = new ConcurrentLinkedQueue<>();

  public List<ServedRequest> served() {
    return List.copyOf(served);
  }

  public void clear() {
    served.clear();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var thread = Thread.currentThread();
    served.add(
        new ServedRequest(
            request.getMethod(), request.getRequestURI(), thread.isVirtual(), thread.getName()));
    chain.doFilter(request, response);
  }

  public record ServedRequest(String method, String path, boolean virtual, String threadName) {}
}
