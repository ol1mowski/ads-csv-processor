package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pl.oliwier.adscsv.support.ProgressAssertions.assertNoProgress;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.support.TestUsers;

class JobQueueIntegrationTest extends ProcessingDisabledTestBase {

  private static final int QUEUED_JOBS = 10;
  private static final long SIZE_BYTES = 1000;
  private static final ProgressSnapshot PROGRESS = new ProgressSnapshot(500, 5, 0, 50, 2);
  private static final String OWNER = TestUsers.ALICE;

  @Autowired private JobStateService jobs;
  @Autowired private Clock clock;

  @Test
  @DisplayName("Two dispatchers draining the queue claim disjoint jobs that together cover all")
  void shouldClaimDisjointJobsWhenTwoClaimersDrainTheQueue() throws Exception {
    var queued =
        IntStream.range(0, QUEUED_JOBS).mapToObj(_ -> createQueued()).collect(Collectors.toSet());
    var barrier = new CyclicBarrier(2);

    Set<UUID> first;
    Set<UUID> second;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(drain(barrier));
      var b = executor.submit(drain(barrier));
      first = a.get(10, TimeUnit.SECONDS);
      second = b.get(10, TimeUnit.SECONDS);
    }

    var union = new HashSet<>(first);
    union.addAll(second);
    assertThat(union).isEqualTo(queued);
    assertThat(first).doesNotContainAnyElementsOf(second);
    assertThat(union).allSatisfy(id -> assertThat(rows.attempts(id)).isEqualTo(1));
  }

  @Test
  @DisplayName("Once a job leaves PROCESSING every guarded worker write is refused")
  void shouldRefuseWorkerWritesWhenJobIsNoLongerProcessing() {
    var id = createQueued();
    assertThat(jobs.claimNext()).contains(id);
    assertThat(jobs.cancel(id, OWNER)).contains(JobStatus.PROCESSING);

    assertThat(jobs.reportProgress(id, PROGRESS)).isFalse();
    assertThat(jobs.complete(id, PROGRESS, List.of())).isFalse();
    assertThat(jobs.fail(id, FailureReason.PROCESSING_ERROR, PROGRESS, List.of())).isFalse();
    assertThat(jobs.release(id)).isFalse();
    assertThat(rows.status(id)).isEqualTo(JobStatus.CANCELLED);
  }

  @Test
  @DisplayName("A released job is claimed again as its next attempt")
  void shouldCountNewAttemptWhenReleasedJobIsClaimedAgain() {
    var id = createQueued();
    assertThat(jobs.claimNext()).contains(id);

    assertThat(jobs.release(id)).isTrue();
    assertThat(rows.status(id)).isEqualTo(JobStatus.QUEUED);
    assertThat(rows.attempts(id)).isEqualTo(1);

    assertThat(jobs.claimNext()).contains(id);
    assertThat(rows.attempts(id)).isEqualTo(2);
    assertThat(jobs.complete(id, PROGRESS, List.of())).isTrue();
    assertThat(rows.status(id)).isEqualTo(JobStatus.COMPLETED);
  }

  @Test
  @DisplayName("Release wipes the partial progress so a queued job never shows half a result")
  void shouldResetProgressWhenJobIsReleased() {
    var id = createQueued();
    assertThat(jobs.claimNext()).contains(id);
    assertThat(jobs.reportProgress(id, PROGRESS)).isTrue();
    assertThat(api.get(id).bytesProcessed()).isEqualTo(PROGRESS.bytesProcessed());

    assertThat(jobs.release(id)).isTrue();

    var released = api.get(id);
    assertThat(released.status()).isEqualTo(JobStatus.QUEUED);
    assertNoProgress(released);
  }

  @Test
  @DisplayName("Claims follow upload order and an empty queue yields nothing")
  void shouldClaimInCreationOrder() {
    var older = jobs.createQueued(UUID.randomUUID(), OWNER, "older.csv", SIZE_BYTES);
    await("clock to move past the older job")
        .until(() -> clock.instant().isAfter(older.getCreatedAt()));
    var newer = jobs.createQueued(UUID.randomUUID(), OWNER, "newer.csv", SIZE_BYTES);

    assertThat(jobs.claimNext()).contains(older.getId());
    assertThat(jobs.claimNext()).contains(newer.getId());
    assertThat(jobs.claimNext()).isEmpty();
  }

  private UUID createQueued() {
    var id = UUID.randomUUID();
    jobs.createQueued(id, OWNER, "queue.csv", SIZE_BYTES);
    return id;
  }

  private Callable<Set<UUID>> drain(CyclicBarrier barrier) {
    return () -> {
      barrier.await(10, TimeUnit.SECONDS);
      var claimed = new HashSet<UUID>();
      for (var id = jobs.claimNext(); id.isPresent(); id = jobs.claimNext()) {
        claimed.add(id.get());
      }
      return claimed;
    };
  }
}
