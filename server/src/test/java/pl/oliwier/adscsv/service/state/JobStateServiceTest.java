package pl.oliwier.adscsv.service.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;
import pl.oliwier.adscsv.repository.ProcessingJobRepository;
import pl.oliwier.adscsv.support.ProcessingJobs;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobStateServiceTest {

  private static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");
  private static final Instant NOW = Instant.parse("2026-10-14T10:15:30.123Z");
  private static final ProgressSnapshot PROGRESS = new ProgressSnapshot(500, 7, 1, 70, 3);
  private static final String OWNER = ProcessingJobs.OWNER;

  @Mock ProcessingJobRepository repository;

  private JobStateService service;

  @BeforeEach
  void setUp() {
    service =
        new JobStateService(
            repository,
            new RejectedRowsJson(JsonMapper.builder().build()),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("A queued job is inserted with its owner and the clock's creation time")
  void shouldSaveQueuedJobWithClockTime() {
    given(repository.save(any(ProcessingJob.class))).willAnswer(call -> call.getArgument(0));

    var job = service.createQueued(JOB_ID, OWNER, "ads.csv", 1000);

    assertThat(job.getOwner()).isEqualTo(OWNER);
    assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    assertThat(job.getCreatedAt()).isEqualTo(NOW);
    assertThat(job.getSizeBytes()).isEqualTo(1000);
    assertThat(job.getAttempts()).isZero();
  }

  @Test
  @DisplayName("An owner looks a job up by its id and their name")
  void shouldFindJobByIdAndOwner() {
    var job = ProcessingJobs.queued(JOB_ID);
    given(repository.findByIdAndOwner(JOB_ID, OWNER)).willReturn(Optional.of(job));

    assertThat(service.findById(JOB_ID, OWNER)).contains(job);
    verify(repository, never()).findById(any());
  }

  @Test
  @DisplayName("Another user's job looks exactly like a missing one")
  void shouldReturnEmptyWhenJobBelongsToSomeoneElse() {
    lenient()
        .when(repository.findById(JOB_ID))
        .thenReturn(Optional.of(ProcessingJobs.queued(JOB_ID)));
    given(repository.findByIdAndOwner(JOB_ID, "bob")).willReturn(Optional.empty());

    assertThat(service.findById(JOB_ID, "bob")).isEmpty();
    verify(repository, never()).findById(any());
  }

  @Test
  @DisplayName("An empty status filter lists every job of the owner rather than an empty IN list")
  void shouldListAllJobsOfOwnerWhenStatusFilterIsEmpty() {
    var pageable = PageRequest.of(0, 20);
    given(repository.findByOwner(OWNER, pageable)).willReturn(page());

    assertThat(service.findPage(OWNER, Set.of(), pageable)).isEmpty();
    verify(repository, never()).findByOwnerAndStatusIn(any(), any(), any());
    verify(repository, never()).findAll(any(PageRequest.class));
  }

  @Test
  @DisplayName("A status filter is passed to the repository together with the owner")
  void shouldFilterByStatusWhenStatusesAreGiven() {
    var pageable = PageRequest.of(1, 5);
    var statuses = Set.of(JobStatus.COMPLETED, JobStatus.FAILED);
    var completed = ProcessingJobs.completed(JOB_ID, "ads.csv", 1);
    given(repository.findByOwnerAndStatusIn(OWNER, statuses, pageable)).willReturn(page(completed));

    assertThat(service.findPage(OWNER, statuses, pageable)).containsExactly(completed);
  }

  @Test
  @DisplayName("Claiming returns the id of the locked and updated job")
  void shouldReturnClaimedJobWhenClaimSucceeds() {
    given(repository.lockNextQueued()).willReturn(Optional.of(JOB_ID));
    given(repository.claim(JOB_ID)).willReturn(1);

    assertThat(service.claimNext()).contains(JOB_ID);
  }

  @Test
  @DisplayName("Nothing is claimed when the queue is empty")
  void shouldReturnEmptyWhenQueueIsEmpty() {
    given(repository.lockNextQueued()).willReturn(Optional.empty());

    assertThat(service.claimNext()).isEmpty();
    verify(repository, never()).claim(any());
  }

  @Test
  @DisplayName("Nothing is claimed when the guarded update changes no row")
  void shouldReturnEmptyWhenClaimUpdatesNoRow() {
    given(repository.lockNextQueued()).willReturn(Optional.of(JOB_ID));
    given(repository.claim(JOB_ID)).willReturn(0);

    assertThat(service.claimNext()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"1, true", "0, false"})
  @DisplayName("Guarded transitions report success only when exactly one row changed")
  void shouldReportSuccessOnlyWhenOneRowUpdated(int updated, boolean expected) {
    given(repository.release(JOB_ID)).willReturn(updated);
    given(repository.reportProgress(JOB_ID, PROGRESS)).willReturn(updated);
    given(repository.finish(JOB_ID, "COMPLETED", null, PROGRESS, "[]")).willReturn(updated);
    given(repository.finish(JOB_ID, "FAILED", "NO_VALID_ROWS", PROGRESS, "[]")).willReturn(updated);

    assertThat(service.release(JOB_ID)).isEqualTo(expected);
    assertThat(service.reportProgress(JOB_ID, PROGRESS)).isEqualTo(expected);
    assertThat(service.complete(JOB_ID, PROGRESS, List.of())).isEqualTo(expected);
    assertThat(service.fail(JOB_ID, FailureReason.NO_VALID_ROWS, PROGRESS, List.of()))
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("Completion stores rejected samples as JSON")
  void shouldStoreSamplesAsJsonWhenCompleting() {
    var samples = ArgumentCaptor.forClass(String.class);
    given(repository.finish(any(), eq("COMPLETED"), isNull(), eq(PROGRESS), samples.capture()))
        .willReturn(1);

    service.complete(JOB_ID, PROGRESS, List.of(new RejectedRow(4, RejectReason.FIELD_COUNT)));

    assertThat(samples.getValue()).isEqualTo("[{\"line\":4,\"reason\":\"FIELD_COUNT\"}]");
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"QUEUED", "PROCESSING"})
  @DisplayName("Cancel updates active jobs and returns the status seen under the row lock")
  void shouldCancelWhenJobIsActive(JobStatus status) {
    given(repository.lockStatus(JOB_ID, OWNER)).willReturn(Optional.of(status.name()));

    assertThat(service.cancel(JOB_ID, OWNER)).contains(status);
    verify(repository).cancelActive(JOB_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  @DisplayName("Cancel leaves finished jobs untouched")
  void shouldNotUpdateWhenJobIsFinished(JobStatus status) {
    given(repository.lockStatus(JOB_ID, OWNER)).willReturn(Optional.of(status.name()));

    assertThat(service.cancel(JOB_ID, OWNER)).contains(status);
    verify(repository, never()).cancelActive(any());
  }

  @Test
  @DisplayName("Cancel of an unknown job returns empty")
  void shouldReturnEmptyWhenCancellingUnknownJob() {
    given(repository.lockStatus(JOB_ID, OWNER)).willReturn(Optional.empty());

    assertThat(service.cancel(JOB_ID, OWNER)).isEmpty();
    verify(repository, never()).cancelActive(any());
  }

  @Test
  @DisplayName("Interrupted jobs below the attempt limit are requeued")
  void shouldRequeueInterruptedJobsBelowAttemptLimit() {
    given(repository.requeueInterrupted(3)).willReturn(2);

    assertThat(service.requeueInterrupted(3)).isEqualTo(2);
  }

  @Test
  @DisplayName("Interrupted jobs out of attempts are failed in the transaction that locked them")
  void shouldFailLockedExhaustedJobs() {
    given(repository.lockExhausted(3)).willReturn(List.of(JOB_ID));

    assertThat(service.failExhausted(3)).containsExactly(JOB_ID);
    verify(repository).failInterrupted(List.of(JOB_ID));
  }

  @Test
  @DisplayName("No update is issued when no interrupted job is out of attempts")
  void shouldSkipUpdateWhenNoExhaustedJobs() {
    given(repository.lockExhausted(anyInt())).willReturn(List.of());

    assertThat(service.failExhausted(3)).isEmpty();
    verify(repository, never()).failInterrupted(anyCollection());
  }

  @Test
  @DisplayName("Jobs whose files must stay are looked up in one query")
  void shouldFindIdsWithFiles() {
    given(repository.findIdsWithFiles(List.of(JOB_ID))).willReturn(List.of(JOB_ID));

    assertThat(service.findIdsWithFiles(List.of(JOB_ID))).containsExactly(JOB_ID);
  }

  @Test
  @DisplayName("Without job directories the database is not queried, since IN () is invalid SQL")
  void shouldSkipQueryWhenThereAreNoJobDirectories() {
    assertThat(service.findIdsWithFiles(List.of())).isEmpty();
    verify(repository, never()).findIdsWithFiles(anyCollection());
  }

  private static Page<ProcessingJob> page(ProcessingJob... jobs) {
    return new PageImpl<>(List.of(jobs));
  }
}
