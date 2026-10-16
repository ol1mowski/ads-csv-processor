package pl.oliwier.adscsv.service.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.support.TestProperties;

@ExtendWith(MockitoExtension.class)
class JobDispatcherTest {

  @Mock JobStateService jobs;
  @Mock CsvJobProcessor processor;

  private final ManualExecutor executor = new ManualExecutor();

  @Test
  @DisplayName("No more jobs than workers are claimed at once")
  void shouldClaimAtMostWorkersJobsWhenQueueIsFull() {
    given(jobs.claimNext()).willAnswer(_ -> Optional.of(job()));
    var dispatcher = dispatcher(2);

    dispatcher.dispatch();
    dispatcher.dispatch();

    assertThat(executor.pending()).isEqualTo(2);
    verify(jobs, times(2)).claimNext();
  }

  @Test
  @DisplayName("A finished worker frees its slot for the next claim")
  void shouldClaimAgainWhenWorkerFinishes() {
    given(jobs.claimNext()).willAnswer(_ -> Optional.of(job()));
    var dispatcher = dispatcher(2);
    dispatcher.dispatch();

    executor.runNext();
    dispatcher.dispatch();

    assertThat(executor.pending()).isEqualTo(2);
    verify(jobs, times(3)).claimNext();
  }

  @Test
  @DisplayName("An empty jobs returns the slot")
  void shouldReleaseSlotWhenQueueIsEmpty() {
    given(jobs.claimNext()).willReturn(Optional.empty(), Optional.of(job()));
    var dispatcher = dispatcher(1);

    dispatcher.dispatch();
    dispatcher.dispatch();

    assertThat(executor.pending()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("claimFailures")
  @DisplayName("A database or transaction error while claiming returns the slot")
  void shouldReleaseSlotWhenClaimFails(RuntimeException failure) {
    given(jobs.claimNext()).willThrow(failure).willReturn(Optional.of(job()));
    var dispatcher = dispatcher(1);

    dispatcher.dispatch();
    dispatcher.dispatch();

    assertThat(executor.pending()).isEqualTo(1);
  }

  @Test
  @DisplayName("A failure to return a rejected job keeps the slot available")
  void shouldReleaseSlotWhenReturningRejectedJobFails() {
    given(jobs.claimNext()).willReturn(Optional.of(job()), Optional.empty());
    given(jobs.release(any())).willThrow(new QueryTimeoutException("database unavailable"));
    TaskExecutor rejecting =
        _ -> {
          throw new TaskRejectedException("full");
        };
    var dispatcher =
        new JobDispatcher(
            jobs, processor, rejecting, TestProperties.workers(1, Duration.ofMillis(100)));

    dispatcher.dispatch();
    dispatcher.dispatch();

    verify(jobs, times(2)).claimNext();
  }

  @Test
  @DisplayName("A crashing worker still returns its slot")
  void shouldReleaseSlotWhenProcessorThrows() {
    given(jobs.claimNext()).willAnswer(_ -> Optional.of(job()));
    willThrow(new IllegalStateException("boom")).given(processor).process(any(), any());
    var dispatcher = dispatcher(1);
    dispatcher.dispatch();

    executor.runNext();
    dispatcher.dispatch();

    assertThat(executor.pending()).isEqualTo(1);
  }

  @Test
  @DisplayName("A rejected submission releases the job back to the jobs")
  void shouldReleaseJobWhenExecutorRejectsTask() {
    var claimed = job();
    given(jobs.claimNext()).willReturn(Optional.of(claimed), Optional.empty());
    TaskExecutor rejecting =
        _ -> {
          throw new TaskRejectedException("full");
        };
    var dispatcher =
        new JobDispatcher(
            jobs, processor, rejecting, TestProperties.workers(1, Duration.ofMillis(100)));

    dispatcher.dispatch();
    dispatcher.dispatch();

    verify(jobs).release(claimed);
    verify(jobs, times(2)).claimNext();
  }

  @Test
  @DisplayName("After stop no new job is claimed")
  void shouldNotClaimWhenStopped() {
    var dispatcher = dispatcher(2);
    dispatcher.start();

    dispatcher.stop();
    dispatcher.dispatch();

    verify(jobs, never()).claimNext();
    assertThat(dispatcher.isRunning()).isFalse();
  }

  @Test
  @DisplayName("Stop waits until busy workers return their slots")
  void shouldWaitForBusyWorkersWhenStopping() {
    given(jobs.claimNext()).willReturn(Optional.of(job()));
    var finished = new AtomicBoolean();
    willAnswer(
            _ -> {
              Thread.sleep(50);
              finished.set(true);
              return null;
            })
        .given(processor)
        .process(any(), any());
    var dispatcher =
        new JobDispatcher(
            jobs, processor, executor, TestProperties.workers(1, Duration.ofSeconds(10)));
    dispatcher.start();
    dispatcher.dispatch();
    Thread.ofVirtual().start(executor::runNext);

    dispatcher.stop();

    assertThat(finished).isTrue();
  }

  @Test
  @DisplayName("Running workers see the stop request through their supplier")
  void shouldSignalStopToRunningWorkers() {
    given(jobs.claimNext()).willReturn(Optional.of(job()));
    var stopRequested = new AtomicReference<BooleanSupplier>();
    willAnswer(
            call -> {
              stopRequested.set(call.getArgument(1));
              return null;
            })
        .given(processor)
        .process(any(), any());
    var dispatcher = dispatcher(1);
    dispatcher.start();
    dispatcher.dispatch();
    executor.runNext();
    assertThat(stopRequested.get().getAsBoolean()).isFalse();

    dispatcher.stop();

    assertThat(stopRequested.get().getAsBoolean()).isTrue();
  }

  @Test
  @DisplayName("The lifecycle reports running between start and stop")
  void shouldReportRunningBetweenStartAndStop() {
    var dispatcher = dispatcher(1);
    assertThat(dispatcher.isRunning()).isFalse();

    dispatcher.start();
    assertThat(dispatcher.isRunning()).isTrue();

    dispatcher.stop();
    assertThat(dispatcher.isRunning()).isFalse();
  }

  private JobDispatcher dispatcher(int workers) {
    return new JobDispatcher(
        jobs, processor, executor, TestProperties.workers(workers, Duration.ofMillis(100)));
  }

  static Stream<RuntimeException> claimFailures() {
    return Stream.of(
        new QueryTimeoutException("database unavailable"),
        new CannotCreateTransactionException("no connection"));
  }

  private static UUID job() {
    return UUID.randomUUID();
  }

  private static final class ManualExecutor implements TaskExecutor {

    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public synchronized void execute(Runnable task) {
      tasks.add(task);
    }

    synchronized int pending() {
      return tasks.size();
    }

    void runNext() {
      Runnable task;
      synchronized (this) {
        task = tasks.poll();
      }
      task.run();
    }
  }
}
