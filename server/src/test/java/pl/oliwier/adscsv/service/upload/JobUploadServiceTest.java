package pl.oliwier.adscsv.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static pl.oliwier.adscsv.support.UploadAssertions.uploadProblem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pl.oliwier.adscsv.config.properties.StorageProperties;
import pl.oliwier.adscsv.exception.upload.UploadProblem;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.service.job.JobMapper;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.state.RejectedRowsJson;
import pl.oliwier.adscsv.service.storage.JobFileStorage;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestProperties;
import pl.oliwier.adscsv.support.TestUsers;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JobUploadServiceTest {

  private static final long MAX_BYTES = 100_000;
  private static final long UNKNOWN_LENGTH = -1;
  private static final String OWNER = TestUsers.ALICE;
  private static final Instant NOW = Instant.parse("2026-10-14T10:15:30.123Z");
  private static final byte[] SAMPLE = SampleData.bytes();

  @TempDir Path root;
  @Mock JobStateService jobs;

  private JobFileStorage storage;

  @BeforeEach
  void setUp() throws IOException {
    storage = spy(new JobFileStorage(new StorageProperties(root)));
  }

  @Test
  @DisplayName("An accepted upload stores the exact bytes and queues the job")
  void shouldStoreBytesAndQueueJobWhenUploadIsValid() throws IOException {
    givenJobsAreCreated();

    var response = service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE));

    assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
    assertThat(response.sizeBytes()).isEqualTo(SAMPLE.length);
    assertThat(response.fileName()).isEqualTo("ads.csv");
    assertThat(Files.readAllBytes(input(response.id()))).isEqualTo(SAMPLE);
    assertThat(root.resolve("incoming")).isEmptyDirectory();
  }

  @Test
  @DisplayName("A body of unknown length is accepted while it stays within the limit")
  void shouldAcceptUploadWhenLengthIsUnknown() throws IOException {
    givenJobsAreCreated();

    var response = service().accept(OWNER, "ads.csv", UNKNOWN_LENGTH, stream(SAMPLE));

    assertThat(response.sizeBytes()).isEqualTo(SAMPLE.length);
  }

  @Test
  @DisplayName("A declared length above the limit is refused before a byte is read")
  void shouldRefuseDeclaredLengthAboveLimitWithoutReading() {
    var body = mock(InputStream.class);

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", MAX_BYTES + 1, body))
        .satisfies(uploadProblem(UploadProblem.TOO_LARGE));
    verifyNoInteractions(body, jobs);
  }

  @Test
  @DisplayName("A declared length of zero is refused as an empty file")
  void shouldRefuseZeroDeclaredLength() {
    var body = mock(InputStream.class);

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", 0, body))
        .satisfies(uploadProblem(UploadProblem.EMPTY));
    verifyNoInteractions(body);
  }

  @Test
  @DisplayName("An empty chunked body is refused as an empty file and leaves no files")
  void shouldRefuseEmptyBodyWhenLengthIsUnknown() {
    assertThatThrownBy(
            () -> service().accept(OWNER, "ads.csv", UNKNOWN_LENGTH, stream(new byte[0])))
        .satisfies(uploadProblem(UploadProblem.EMPTY));
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("A chunked body that grows past the limit is refused and its part file removed")
  void shouldRefuseStreamWhenItExceedsLimit() {
    var tooLarge = new byte[(int) MAX_BYTES + 1];
    var header = (SampleData.HEADER + "\n").getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(header, 0, tooLarge, 0, header.length);

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", UNKNOWN_LENGTH, stream(tooLarge)))
        .satisfies(uploadProblem(UploadProblem.TOO_LARGE));
    assertNoFilesLeft();
    verifyNoInteractions(jobs);
  }

  @Test
  @DisplayName("A wrong header is refused before the rest of the file is read")
  void shouldRefuseUploadWhenHeaderIsInvalid() {
    var body = "a,b,c\n1,2,3\n".getBytes(StandardCharsets.US_ASCII);

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(body), stream(body)))
        .satisfies(uploadProblem(UploadProblem.INVALID_HEADER));
    assertNoFilesLeft();
    verifyNoInteractions(jobs);
  }

  @Test
  @DisplayName("A body shorter than its declared length is an interrupted upload")
  void shouldRefuseUploadWhenBodyIsShorterThanDeclared() {
    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", SAMPLE.length + 10, stream(SAMPLE)))
        .satisfies(uploadProblem(UploadProblem.INTERRUPTED));
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("A read failure from the client is an interrupted upload")
  void shouldRefuseUploadWhenClientStreamFails() {
    var header = new ByteArrayInputStream((SampleData.HEADER + "\n").getBytes());
    var body = new SequenceInputStream(header, new FailingInputStream());

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", UNKNOWN_LENGTH, body))
        .satisfies(uploadProblem(UploadProblem.INTERRUPTED));
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("A failure while reading the header is an interrupted upload")
  void shouldRefuseUploadWhenHeaderCannotBeRead() {
    assertThatThrownBy(
            () -> service().accept(OWNER, "ads.csv", UNKNOWN_LENGTH, new FailingInputStream()))
        .satisfies(uploadProblem(UploadProblem.INTERRUPTED));
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("BOM and CRLF in the header are accepted and stored unchanged")
  void shouldStoreBytesUnchangedWhenHeaderHasBomAndCrlf() throws IOException {
    givenJobsAreCreated();
    var body =
        ("﻿" + SampleData.HEADER + "\r\n" + SampleData.FIRST_ROW + "\r\n")
            .getBytes(StandardCharsets.UTF_8);

    var response = service().accept(OWNER, "ads.csv", length(body), stream(body));

    assertThat(Files.readAllBytes(input(response.id()))).isEqualTo(body);
  }

  @Test
  @DisplayName("Bytes after the header survive a client that sends small chunks")
  void shouldKeepEveryByteWhenClientSendsSmallChunks() throws IOException {
    givenJobsAreCreated();
    var body = new ByteArrayOutputStream();
    body.writeBytes((SampleData.HEADER + "\n").getBytes(StandardCharsets.US_ASCII));
    while (body.size() < 20 * 1024) {
      body.writeBytes((SampleData.FIRST_ROW + "\n").getBytes(StandardCharsets.US_ASCII));
    }
    var bytes = body.toByteArray();

    var response =
        service().accept(OWNER, "ads.csv", length(bytes), new ChunkedInputStream(bytes, 1024));

    assertThat(Files.readAllBytes(input(response.id()))).isEqualTo(bytes);
  }

  @Test
  @DisplayName("The input is fsynced and moved before the job row is inserted")
  void shouldCommitInputBeforeCreatingJob() throws IOException {
    givenJobsAreCreated();

    var response = service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE));

    var order = inOrder(storage, jobs);
    order.verify(storage).commitInput(response.id());
    order.verify(jobs).createQueued(response.id(), OWNER, "ads.csv", SAMPLE.length);
  }

  @Test
  @DisplayName("A failed insert removes the already committed input")
  void shouldDeleteFilesWhenJobCreationFails() {
    given(jobs.createQueued(any(), anyString(), anyString(), anyLong()))
        .willThrow(new DataIntegrityViolationException("constraint"));

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("A failed commit removes both the part file and the job directory")
  void shouldDeleteFilesWhenCommitFails() throws IOException {
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IOException("fsync failed");
            })
        .when(storage)
        .commitInput(any());

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE)))
        .isInstanceOf(IOException.class)
        .hasMessage("fsync failed");
    assertNoFilesLeft();
    verify(jobs, never()).createQueued(any(), anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("A failure while closing the part file removes it")
  void shouldDeletePartWhenClosingItFails() throws IOException {
    doAnswer(call -> new FailingCloseOutputStream((OutputStream) call.callRealMethod()))
        .when(storage)
        .createIncoming(any());

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE)))
        .isInstanceOf(IOException.class)
        .hasMessage("no space left on device");
    assertNoFilesLeft();
  }

  @Test
  @DisplayName("A disk that fills up while the body is written is reported as insufficient storage")
  void shouldReportInsufficientStorageWhenDiskFillsDuringUpload() throws IOException {
    doAnswer(call -> new FailingWriteOutputStream((OutputStream) call.callRealMethod()))
        .when(storage)
        .createIncoming(any());
    doReturn(Long.MAX_VALUE / 4, 0L).when(storage).usableSpace();

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE)))
        .satisfies(uploadProblem(UploadProblem.INSUFFICIENT_STORAGE));
    assertNoFilesLeft();
    verifyNoInteractions(jobs);
  }

  @Test
  @DisplayName("Without enough free space for 2.2 times the maximum, chunked uploads are refused")
  void shouldReserveForMaximumSizeWhenLengthIsUnknown() throws IOException {
    doReturn((long) (MAX_BYTES * 2.2) - 1).when(storage).usableSpace();
    givenJobsAreCreated();
    var service = service();

    assertThatThrownBy(() -> service.accept(OWNER, "ads.csv", UNKNOWN_LENGTH, stream(SAMPLE)))
        .satisfies(uploadProblem(UploadProblem.INSUFFICIENT_STORAGE));
    assertThat(service.accept(OWNER, "ads.csv", length(SAMPLE), stream(SAMPLE)).status())
        .isEqualTo(JobStatus.QUEUED);
  }

  @Test
  @DisplayName("A declared length without room for 2.2 times its size is refused before reading")
  void shouldRefuseDeclaredLengthWithoutReadingWhenSpaceIsShort() throws IOException {
    doReturn((long) (SAMPLE.length * 2.2) - 1).when(storage).usableSpace();
    var body = mock(InputStream.class);

    assertThatThrownBy(() -> service().accept(OWNER, "ads.csv", length(SAMPLE), body))
        .satisfies(uploadProblem(UploadProblem.INSUFFICIENT_STORAGE));
    verifyNoInteractions(body, jobs);
    assertNoFilesLeft();
  }

  private JobUploadService service() {
    var mapper = new JobMapper(new RejectedRowsJson(JsonMapper.builder().build()));
    return new JobUploadService(storage, jobs, mapper, TestProperties.upload(MAX_BYTES));
  }

  private void givenJobsAreCreated() {
    given(jobs.createQueued(any(), eq(OWNER), eq("ads.csv"), anyLong()))
        .willAnswer(
            call ->
                ProcessingJob.queued(
                    call.getArgument(0),
                    call.getArgument(1),
                    call.getArgument(2),
                    call.getArgument(3),
                    NOW));
  }

  private Path input(UUID id) {
    return jobDirectory(id).resolve("input.csv");
  }

  private void assertNoFilesLeft() {
    assertThat(root.resolve("incoming")).isEmptyDirectory();
    assertThat(root.resolve("jobs")).isEmptyDirectory();
  }

  private static long length(byte[] body) {
    return body.length;
  }

  private static InputStream stream(byte[] body) {
    return new ByteArrayInputStream(body);
  }

  private static final class FailingInputStream extends InputStream {

    @Override
    public int read() throws IOException {
      throw new IOException("connection reset");
    }
  }

  private static final class ChunkedInputStream extends ByteArrayInputStream {

    private final int chunk;

    ChunkedInputStream(byte[] bytes, int chunk) {
      super(bytes);
      this.chunk = chunk;
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length) {
      return super.read(buffer, offset, Math.min(length, chunk));
    }
  }

  private static final class FailingWriteOutputStream extends FilterOutputStream {

    FailingWriteOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
      throw new IOException("No space left on device");
    }
  }

  private static final class FailingCloseOutputStream extends FilterOutputStream {

    FailingCloseOutputStream(OutputStream out) {
      super(out);
    }

    @Override
    public void close() throws IOException {
      super.close();
      throw new IOException("no space left on device");
    }
  }

  private Path jobDirectory(UUID id) {
    return root.resolve("jobs").resolve(id.toString());
  }
}
