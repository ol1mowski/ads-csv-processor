package pl.oliwier.adscsv.integration.support;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import pl.oliwier.adscsv.service.transformation.AdRow;
import pl.oliwier.adscsv.service.transformation.ColumnTransformation;

public class LatchingColumnTransformation implements ColumnTransformation {

  private static final long RELEASE_TIMEOUT_SECONDS = 10;

  private final ColumnTransformation delegate;
  private final AtomicInteger calls = new AtomicInteger();
  private volatile Latch latch;
  private volatile Thread latchedThread;

  public LatchingColumnTransformation(ColumnTransformation delegate) {
    this.delegate = delegate;
  }

  public void arm(int callNumber, CountDownLatch reached, CountDownLatch release) {
    calls.set(0);
    latchedThread = null;
    latch = new Latch(callNumber, reached, release);
  }

  public void disarm() {
    latch = null;
  }

  public Optional<Thread> latchedThread() {
    return Optional.ofNullable(latchedThread);
  }

  @Override
  public String columnName() {
    return delegate.columnName();
  }

  @Override
  public String apply(AdRow row) {
    var current = latch;
    if (current != null && calls.incrementAndGet() == current.callNumber()) {
      latchedThread = Thread.currentThread();
      current.reached().countDown();
      awaitQuietly(current.release());
    }
    return delegate.apply(row);
  }

  private static void awaitQuietly(CountDownLatch release) {
    try {
      release.await(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record Latch(int callNumber, CountDownLatch reached, CountDownLatch release) {}
}
