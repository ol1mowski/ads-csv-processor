package pl.oliwier.adscsv.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import pl.oliwier.adscsv.config.properties.HistoryProperties;
import pl.oliwier.adscsv.exception.job.JobNotCancellableException;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.exception.job.ResultGoneException;
import pl.oliwier.adscsv.exception.job.ResultNotAvailableException;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.state.RejectedRowsJson;
import pl.oliwier.adscsv.service.storage.JobFileStorage;
import pl.oliwier.adscsv.service.storage.StoredFile;
import pl.oliwier.adscsv.support.ProcessingJobs;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");
  private static final String OWNER = ProcessingJobs.OWNER;

  @Mock JobStateService jobs;
  @Mock JobFileStorage storage;

  private JobService service;

  @BeforeEach
  void setUp() {
    var mapper = new JobMapper(new RejectedRowsJson(JsonMapper.builder().build()));
    service = new JobService(jobs, storage, mapper, new HistoryProperties(100));
  }

  @Test
  @DisplayName("An existing job is mapped to its response")
  void shouldReturnJobWhenItExists() {
    given(jobs.findById(JOB_ID, OWNER)).willReturn(Optional.of(ProcessingJobs.queued(JOB_ID)));

    assertThat(service.get(JOB_ID, OWNER).id()).isEqualTo(JOB_ID);
  }

  @Test
  @DisplayName("An unknown job id raises JobNotFoundException")
  void shouldThrowNotFoundWhenJobIsUnknown() {
    given(jobs.findById(JOB_ID, OWNER)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(JOB_ID, OWNER)).isInstanceOf(JobNotFoundException.class);
  }

  @Test
  @DisplayName("History pages are sorted newest first and capped at the maximum page size")
  void shouldClampPageSizeWhenAboveMax() {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    given(jobs.findPage(eq(OWNER), eq(Set.of()), pageable.capture()))
        .willReturn(page(ProcessingJobs.queued(JOB_ID)));

    var history = service.history(OWNER, 3, 1000, null);

    assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    assertThat(history.items()).hasSize(1);
  }

  @Test
  @DisplayName("A missing status filter is passed on as an empty set, meaning no filter")
  void shouldPassEmptyFilterWhenStatusIsMissing() {
    given(jobs.findPage(eq(OWNER), eq(Set.of()), any(Pageable.class))).willReturn(page());

    assertThat(service.history(OWNER, 0, 20, null).items()).isEmpty();
  }

  @Test
  @DisplayName("A status filter is passed to the job lookup")
  void shouldFilterByStatusWhenProvided() {
    var statuses = Set.of(JobStatus.COMPLETED, JobStatus.FAILED);
    given(jobs.findPage(eq(OWNER), eq(statuses), any(Pageable.class))).willReturn(page());

    var history = service.history(OWNER, 0, 20, statuses);

    assertThat(history.items()).isEmpty();
    assertThat(history.totalElements()).isZero();
  }

  @Test
  @DisplayName("Cancelling a queued job removes its files right away")
  void shouldDeleteFilesWhenQueuedJobIsCancelled() {
    givenCancelSees(JobStatus.QUEUED, JobStatus.CANCELLED);

    assertThat(service.cancel(JOB_ID, OWNER).status()).isEqualTo(JobStatus.CANCELLED);
    verify(storage).deleteJob(JOB_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"PROCESSING", "CANCELLED"})
  @DisplayName("Cancelling a running or already cancelled job leaves cleanup to the worker")
  void shouldNotDeleteFilesWhenJobIsRunningOrCancelled(JobStatus previous) {
    givenCancelSees(previous, JobStatus.CANCELLED);

    assertThat(service.cancel(JOB_ID, OWNER).status()).isEqualTo(JobStatus.CANCELLED);
    verify(storage, never()).deleteJob(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"COMPLETED", "FAILED"})
  @DisplayName("Cancelling a finished job is a conflict carrying its status")
  void shouldThrowConflictWhenJobIsFinished(JobStatus previous) {
    given(jobs.cancel(JOB_ID, OWNER)).willReturn(Optional.of(previous));

    assertThatThrownBy(() -> service.cancel(JOB_ID, OWNER))
        .isInstanceOfSatisfying(
            JobNotCancellableException.class,
            e -> assertThat(e.currentStatus()).isEqualTo(previous));
  }

  @Test
  @DisplayName("Cancelling an unknown job raises JobNotFoundException")
  void shouldThrowNotFoundWhenCancellingUnknownJob() {
    given(jobs.cancel(JOB_ID, OWNER)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel(JOB_ID, OWNER))
        .isInstanceOf(JobNotFoundException.class);
  }

  @Test
  @DisplayName("The result of a completed job is the output of its last attempt")
  void shouldReturnResultFileWhenJobCompleted() throws IOException {
    var content = new ByteArrayResource(new byte[] {1, 2, 3});
    given(jobs.findById(JOB_ID, OWNER))
        .willReturn(Optional.of(ProcessingJobs.completed(JOB_ID, "raport_łódź.CSV", 2)));
    given(storage.findOutput(JOB_ID)).willReturn(Optional.of(new StoredFile(content, 3)));

    var result = service.result(JOB_ID, OWNER);

    assertThat(result).isEqualTo(new ResultFile(content, "raport_łódź-processed.csv", 3));
  }

  @ParameterizedTest
  @EnumSource(value = JobStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Asking for the result of an unfinished job is a conflict")
  void shouldThrowConflictWhenResultIsNotReady(JobStatus status) {
    given(jobs.findById(JOB_ID, OWNER))
        .willReturn(Optional.of(ProcessingJobs.withStatus(JOB_ID, status)));

    assertThatThrownBy(() -> service.result(JOB_ID, OWNER))
        .isInstanceOfSatisfying(
            ResultNotAvailableException.class,
            e -> assertThat(e.currentStatus()).isEqualTo(status));
  }

  @Test
  @DisplayName("A completed job whose file vanished reports the result as gone")
  void shouldThrowGoneWhenResultFileIsMissing() throws IOException {
    given(jobs.findById(JOB_ID, OWNER))
        .willReturn(Optional.of(ProcessingJobs.completed(JOB_ID, "ads.csv", 1)));
    given(storage.findOutput(JOB_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.result(JOB_ID, OWNER)).isInstanceOf(ResultGoneException.class);
  }

  @Test
  @DisplayName("The result of an unknown job raises JobNotFoundException")
  void shouldThrowNotFoundWhenResultJobIsUnknown() {
    given(jobs.findById(JOB_ID, OWNER)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.result(JOB_ID, OWNER))
        .isInstanceOf(JobNotFoundException.class);
  }

  @ParameterizedTest
  @CsvSource({"ads.csv, ads-processed.csv", "ADS.CSV, ADS-processed.csv", "x, x-processed.csv"})
  @DisplayName("The download name replaces the extension with -processed.csv")
  void shouldBuildDownloadName(String fileName, String expected) {
    assertThat(JobService.downloadName(fileName)).isEqualTo(expected);
  }

  @Test
  @DisplayName("The service opens no transaction of its own: each state role call runs in its own")
  void shouldLeaveTransactionsToStateRoles() {
    assertThat(JobService.class.getAnnotation(Transactional.class)).isNull();
    assertThat(JobService.class.getDeclaredMethods())
        .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class)).isNull());
  }

  private void givenCancelSees(JobStatus previous, JobStatus after) {
    given(jobs.cancel(JOB_ID, OWNER)).willReturn(Optional.of(previous));
    given(jobs.findById(JOB_ID, OWNER))
        .willReturn(Optional.of(ProcessingJobs.withStatus(JOB_ID, after)));
  }

  private static Page<ProcessingJob> page(ProcessingJob... jobs) {
    return new PageImpl<>(List.of(jobs));
  }
}
