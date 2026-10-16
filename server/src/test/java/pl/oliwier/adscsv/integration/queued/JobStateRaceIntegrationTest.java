package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.exception.job.JobNotCancellableException;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.service.job.JobService;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.support.TestUsers;

class JobStateRaceIntegrationTest extends ProcessingDisabledTestBase {

  private static final int REPETITIONS = 50;
  private static final long SIZE_BYTES = 100;
  private static final ProgressSnapshot FULL_PROGRESS =
      new ProgressSnapshot(SIZE_BYTES, 1, 0, 10, 1);
  private static final String OWNER = TestUsers.ALICE;

  @Autowired private JobStateService jobs;
  @Autowired private JobService jobService;

  @RepeatedTest(REPETITIONS)
  @DisplayName("Cancel racing complete has exactly one winner and the final status matches it")
  void shouldHaveExactlyOneWinnerWhenCancelRacesComplete() throws Exception {
    var id = claimFreshJob();

    var race = race(() -> jobs.complete(id, FULL_PROGRESS, List.of()), () -> cancel(id));

    var completed = race.first();
    var cancelled = !race.second().conflict();
    assertThat(completed).as("exactly one transition wins").isNotEqualTo(cancelled);
    var winner = completed ? JobStatus.COMPLETED : JobStatus.CANCELLED;
    assertThat(race.second().status()).isEqualTo(winner);
    assertThat(rows.status(id)).isEqualTo(winner);
  }

  @RepeatedTest(REPETITIONS)
  @DisplayName(
      "Claim racing cancel on a queued job always ends CANCELLED and never leaves a live claim")
  void shouldEndCancelledWhenClaimRacesCancel() throws Exception {
    var id = createQueued();

    var race = race(() -> jobs.claimNext(), () -> jobs.cancel(id, OWNER));

    var claimed = race.first();
    var previous = race.second().orElseThrow();
    assertThat(previous).isIn(JobStatus.QUEUED, JobStatus.PROCESSING);
    assertThat(claimed.isPresent()).isEqualTo(previous == JobStatus.PROCESSING);
    claimed.ifPresent(
        lost -> assertThat(jobs.reportProgress(lost, FULL_PROGRESS)).as("worker stops").isFalse());
    assertThat(rows.status(id)).isEqualTo(JobStatus.CANCELLED);
  }

  @RepeatedTest(REPETITIONS)
  @DisplayName("Cancel racing a graceful release always succeeds instead of a false 409")
  void shouldCancelWhenCancelRacesRelease() throws Exception {
    var id = claimFreshJob();

    var race = race(() -> jobs.release(id), () -> cancel(id));

    assertThat(race.second().conflict()).as("cancel never answers 409").isFalse();
    assertThat(race.second().status()).isEqualTo(JobStatus.CANCELLED);
    assertThat(rows.status(id)).isEqualTo(JobStatus.CANCELLED);
    assertThat(rows.attempts(id)).isEqualTo(1);
  }

  private UUID createQueued() {
    var id = UUID.randomUUID();
    jobs.createQueued(id, OWNER, "race.csv", SIZE_BYTES);
    return id;
  }

  private UUID claimFreshJob() {
    var id = createQueued();
    assertThat(jobs.claimNext()).contains(id);
    return id;
  }

  private CancelOutcome cancel(UUID id) {
    try {
      return new CancelOutcome(jobService.cancel(id, OWNER).status(), false);
    } catch (JobNotCancellableException e) {
      return new CancelOutcome(e.currentStatus(), true);
    }
  }

  private static <A, B> Race<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var firstResult = executor.submit(gated(start, first));
      var secondResult = executor.submit(gated(start, second));
      start.countDown();
      return new Race<>(
          firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
    }
  }

  private static <T> Callable<T> gated(CountDownLatch start, Callable<T> task) {
    return () -> {
      start.await();
      return task.call();
    };
  }

  private record Race<A, B>(A first, B second) {}

  private record CancelOutcome(JobStatus status, boolean conflict) {}
}
