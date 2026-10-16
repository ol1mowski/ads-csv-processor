package pl.oliwier.adscsv.service.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.unit.DataSize;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.config.properties.StorageProperties;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;
import pl.oliwier.adscsv.service.csv.RowTransformer;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.storage.JobFileStorage;
import pl.oliwier.adscsv.service.transformation.CtrTransformation;
import pl.oliwier.adscsv.support.SampleData;
import pl.oliwier.adscsv.support.TestProperties;

@ExtendWith(MockitoExtension.class)
class CsvJobProcessorTest {

  private static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");
  private static final Pattern CTR_CELL = Pattern.compile("^(\\d+\\.\\d{6})?$");
  private static final ProcessingProperties PROPS = TestProperties.processing();
  private static final ProcessingProperties EVERY_MEBIBYTE =
      TestProperties.processing(2, Duration.ZERO, DataSize.ofMegabytes(1), 1000, 1.0);

  @TempDir Path root;
  @Mock JobStateService jobs;
  @Captor ArgumentCaptor<List<RejectedRow>> samples;

  private JobFileStorage storage;

  @BeforeEach
  void setUp() throws IOException {
    storage = new JobFileStorage(new StorageProperties(root));
  }

  @Test
  @DisplayName("The sample completes with exact totals, a ctr column and the input removed")
  void shouldCompleteSampleWhenAllRowsAreValid() throws IOException {
    givenInput(SampleData.bytes());
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    var snapshot = ArgumentCaptor.forClass(ProgressSnapshot.class);
    verify(jobs).complete(eq(JOB_ID), snapshot.capture(), eq(List.of()));
    assertThat(snapshot.getValue())
        .isEqualTo(
            new ProgressSnapshot(
                SampleData.SIZE_BYTES,
                SampleData.ROWS,
                0,
                SampleData.TOTAL_IMPRESSIONS,
                SampleData.TOTAL_CLICKS));
    assertThat(input()).doesNotExist();
    assertSampleOutput(output());
  }

  @Test
  @DisplayName("A rejected progress update stops the worker and removes the cancelled job")
  void shouldStopAndCleanUpWhenProgressUpdateIsRejected() throws IOException {
    givenInput(generatedCsv(50_000, 0));
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true, false);

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs, times(2)).reportProgress(eq(JOB_ID), any());
    verify(jobs, never()).complete(any(), any(), anyList());
    verify(jobs, never()).fail(any(), any(), any(), anyList());
    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName("When cancel wins against completion the job directory is removed")
  void shouldDeleteFilesWhenCompletionLosesToCancel() throws IOException {
    givenInput(SampleData.bytes());
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(false);

    processor(PROPS).process(JOB_ID, () -> false);

    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName("A file of only invalid rows fails early after 1001 rejections")
  void shouldFailEarlyWhenTooManyRowsAreInvalid() throws IOException {
    var bytes = generatedCsv(0, 5_000);
    givenInput(bytes);
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    var snapshot = ArgumentCaptor.forClass(ProgressSnapshot.class);
    verify(jobs)
        .fail(
            eq(JOB_ID),
            eq(FailureReason.TOO_MANY_INVALID_ROWS),
            snapshot.capture(),
            samples.capture());
    assertThat(snapshot.getValue().rowsRejected()).isEqualTo(1001);
    assertThat(snapshot.getValue().bytesProcessed()).isLessThan(bytes.length);
    assertThat(samples.getValue())
        .hasSize(10)
        .first()
        .isEqualTo(new RejectedRow(2, RejectReason.FIELD_COUNT));
    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName("Rare invalid rows under one percent do not fail the job")
  void shouldCompleteWhenInvalidRowsAreRare() throws IOException {
    var props = TestProperties.processing(2, Duration.ZERO, DataSize.ofKilobytes(4), 10, 0.01);
    givenInput(generatedCsv(10_000, 20));
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(props).process(JOB_ID, () -> false);

    var snapshot = ArgumentCaptor.forClass(ProgressSnapshot.class);
    verify(jobs).complete(eq(JOB_ID), snapshot.capture(), anyList());
    assertThat(snapshot.getValue().rowsProcessed()).isEqualTo(10_000);
    assertThat(snapshot.getValue().rowsRejected()).isEqualTo(20);
  }

  @Test
  @DisplayName("More than 1 % invalid rows fails the job at the end even below the abort minimum")
  void shouldFailAtEndOfFileWhenRejectedRatioIsExceeded() throws IOException {
    var bytes = generatedCsv(100, 5);
    givenInput(bytes);
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    var snapshot = ArgumentCaptor.forClass(ProgressSnapshot.class);
    verify(jobs)
        .fail(
            eq(JOB_ID),
            eq(FailureReason.TOO_MANY_INVALID_ROWS),
            snapshot.capture(),
            samples.capture());
    assertThat(snapshot.getValue().bytesProcessed()).isEqualTo(bytes.length);
    assertThat(snapshot.getValue().rowsProcessed()).isEqualTo(100);
    assertThat(snapshot.getValue().rowsRejected()).isEqualTo(5);
    assertThat(samples.getValue()).hasSize(5);
    verify(jobs, never()).complete(any(), any(), anyList());
    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName("Exactly 1 % invalid rows still completes the job")
  void shouldCompleteWhenRejectedRatioIsAtTheLimit() throws IOException {
    givenInput(generatedCsv(99, 1));
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs).complete(eq(JOB_ID), any(), samples.capture());
    assertThat(samples.getValue()).hasSize(1);
  }

  @Test
  @DisplayName("A few rows that are all invalid fail the job with NO_VALID_ROWS")
  void shouldFailWithNoValidRowsWhenEveryRowIsRejected() throws IOException {
    var bytes = SampleData.csv("bad", "1;2", "x;y;z");
    givenInput(bytes);

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs)
        .fail(
            eq(JOB_ID),
            eq(FailureReason.NO_VALID_ROWS),
            eq(new ProgressSnapshot(bytes.length, 0, 3, 0, 0)),
            eq(
                List.of(
                    new RejectedRow(2, RejectReason.FIELD_COUNT),
                    new RejectedRow(3, RejectReason.FIELD_COUNT),
                    new RejectedRow(4, RejectReason.FIELD_COUNT))));
  }

  @Test
  @DisplayName("A header-only file completes with zero rows and a header-only report")
  void shouldCompleteWhenFileHasOnlyHeader() throws IOException {
    var bytes = SampleData.csv();
    givenInput(bytes);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs).complete(JOB_ID, new ProgressSnapshot(bytes.length, 0, 0, 0, 0), List.of());
    assertThat(output()).hasContent(SampleData.HEADER + ";ctr");
  }

  @Test
  @DisplayName("A file whose first line is not the header fails with INVALID_HEADER")
  void shouldFailWhenHeaderIsInvalid() throws IOException {
    givenInput("a;b\n1;2\n".getBytes(StandardCharsets.US_ASCII));

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs).fail(eq(JOB_ID), eq(FailureReason.INVALID_HEADER), any(), anyList());
    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName(
      "An overlong line is checkpointed by bytes, so a skipped 16 MiB line is no blind spot")
  void shouldCheckpointWhileSkippingOverlongLine() throws IOException {
    var bytes = csvWithOverlongLine(16 << 20);
    givenInput(bytes);
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(EVERY_MEBIBYTE).process(JOB_ID, () -> false);

    verify(jobs, atLeast(8)).reportProgress(eq(JOB_ID), any());
    verify(jobs)
        .complete(
            JOB_ID,
            new ProgressSnapshot(bytes.length, 2, 1, 591 + 2289, 22 + 109),
            List.of(new RejectedRow(3, RejectReason.LINE_TOO_LONG)));
  }

  @Test
  @DisplayName("Cancellation is noticed in the middle of an overlong line")
  void shouldStopInsideOverlongLineWhenCancelled() throws IOException {
    givenInput(csvWithOverlongLine(16 << 20));
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true, true, true, false);

    processor(EVERY_MEBIBYTE).process(JOB_ID, () -> false);

    verify(jobs, times(4)).reportProgress(eq(JOB_ID), any());
    verify(jobs, never()).complete(any(), any(), anyList());
  }

  @Test
  @DisplayName("A missing input fails the job with INPUT_MISSING")
  void shouldFailWhenInputIsMissing() {
    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs).fail(eq(JOB_ID), eq(FailureReason.INPUT_MISSING), any(), anyList());
  }

  @Test
  @DisplayName("An I/O error fails the job with PROCESSING_ERROR and cleans up")
  void shouldFailAndCleanUpWhenOutputCannotBeWritten() throws IOException {
    givenInput(SampleData.bytes());
    var failing = spy(storage);
    doThrow(new IOException("disk full")).when(failing).createOutput(JOB_ID);

    processor(PROPS, failing).process(JOB_ID, () -> false);

    verify(jobs).fail(eq(JOB_ID), eq(FailureReason.PROCESSING_ERROR), any(), anyList());
    assertThat(jobDirectory()).doesNotExist();
  }

  @ParameterizedTest
  @MethodSource("stateStoreFailures")
  @DisplayName("A database failure while reporting progress returns the job to the queue")
  void shouldReleaseJobWhenProgressCannotBeStored(RuntimeException failure) throws IOException {
    givenInput(generatedCsv(5_000, 0));
    given(jobs.reportProgress(eq(JOB_ID), any())).willThrow(failure);
    given(jobs.release(JOB_ID)).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    verify(jobs, never()).fail(any(), any(), any(), anyList());
    verify(jobs, never()).complete(any(), any(), anyList());
    assertThat(input()).exists();
  }

  @Test
  @DisplayName("A job that cannot even be released stays for startup recovery with its input")
  void shouldKeepInputWhenDatabaseStaysDown() throws IOException {
    givenInput(generatedCsv(5_000, 0));
    var down = new CannotCreateTransactionException("connection pool exhausted");
    given(jobs.reportProgress(eq(JOB_ID), any())).willThrow(down);
    given(jobs.release(JOB_ID)).willThrow(down);

    processor(PROPS).process(JOB_ID, () -> false);

    assertThat(input()).exists();
  }

  @Test
  @DisplayName("A shutdown request releases the job and keeps its input for the next attempt")
  void shouldReleaseJobWhenStopIsRequested() throws IOException {
    givenInput(SampleData.bytes());
    given(jobs.release(JOB_ID)).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> true);

    verify(jobs).release(JOB_ID);
    verify(jobs, never()).reportProgress(any(), any());
    assertThat(input()).exists();
  }

  @Test
  @DisplayName("A job cancelled while it was being released loses its files")
  void shouldDeleteFilesWhenReleaseLosesToCancel() throws IOException {
    givenInput(SampleData.bytes());
    given(jobs.release(JOB_ID)).willReturn(false);

    processor(PROPS).process(JOB_ID, () -> true);

    assertThat(jobDirectory()).doesNotExist();
  }

  @Test
  @DisplayName("A rerun overwrites the partial output left by an interrupted attempt")
  void shouldOverwritePartialOutputWhenJobRunsAgain() throws IOException {
    givenInput(SampleData.bytes());
    Files.writeString(output(), "partial output of an interrupted attempt".repeat(10_000));
    given(jobs.reportProgress(eq(JOB_ID), any())).willReturn(true);
    given(jobs.complete(eq(JOB_ID), any(), anyList())).willReturn(true);

    processor(PROPS).process(JOB_ID, () -> false);

    assertSampleOutput(output());
  }

  static Stream<RuntimeException> stateStoreFailures() {
    return Stream.of(
        new QueryTimeoutException("statement timeout"),
        new CannotCreateTransactionException("connection pool exhausted"));
  }

  private CsvJobProcessor processor(ProcessingProperties props) {
    return processor(props, storage);
  }

  private CsvJobProcessor processor(ProcessingProperties props, JobFileStorage files) {
    return new CsvJobProcessor(jobs, files, new RowTransformer(new CtrTransformation()), props);
  }

  private void givenInput(byte[] bytes) throws IOException {
    try (var out = storage.createIncoming(JOB_ID)) {
      out.write(bytes);
    }
    storage.commitInput(JOB_ID);
  }

  private Path input() {
    return jobDirectory().resolve("input.csv");
  }

  private Path output() {
    return jobDirectory().resolve("output.csv");
  }

  private Path jobDirectory() {
    return root.resolve("jobs").resolve(JOB_ID.toString());
  }

  private static void assertSampleOutput(Path output) throws IOException {
    var inputLines = new String(SampleData.bytes(), StandardCharsets.US_ASCII).split("\n");
    var lines = Files.readAllLines(output, StandardCharsets.US_ASCII);
    assertThat(lines).hasSize(1001);
    assertThat(lines.getFirst()).isEqualTo(SampleData.HEADER + ";ctr");
    assertThat(lines.get(1)).endsWith(";0.037225");
    assertThat(lines.get(2)).endsWith(";0.047619");
    assertThat(lines.get(3)).endsWith(";0.028525");
    assertThat(lines.get(217)).endsWith(";");
    assertThat(lines.stream().filter(line -> line.endsWith(";"))).hasSize(11);
    for (var i = 1; i < lines.size(); i++) {
      assertThat(lines.get(i)).startsWith(inputLines[i] + ";");
      assertThat(lines.get(i).substring(inputLines[i].length() + 1)).matches(CTR_CELL);
    }
  }

  private static byte[] generatedCsv(int validRows, int invalidRows) {
    var out = new ByteArrayOutputStream();
    out.writeBytes((SampleData.HEADER + "\n").getBytes(StandardCharsets.US_ASCII));
    var total = validRows + invalidRows;
    var invalidEvery = invalidRows == 0 ? Integer.MAX_VALUE : Math.max(1, total / invalidRows);
    var invalidWritten = 0;
    for (var i = 1; i <= total; i++) {
      var invalid = invalidWritten < invalidRows && (validRows == 0 || i % invalidEvery == 0);
      var row = invalid ? "invalid;row" : SampleData.row(1000 + i % 500, i % 50);
      invalidWritten += invalid ? 1 : 0;
      out.writeBytes((row + "\n").getBytes(StandardCharsets.US_ASCII));
    }
    return out.toByteArray();
  }

  private static byte[] csvWithOverlongLine(int overlongBytes) {
    var out = new ByteArrayOutputStream();
    out.writeBytes((SampleData.HEADER + "\n" + SampleData.FIRST_ROW + "\n").getBytes());
    out.writeBytes("9".repeat(overlongBytes).getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(
        ("\n6310;10230;504616;f143262f-dc5c-4eed-8da0-365bf89897b9;2289;109;3.6122;0.3426\n")
            .getBytes(StandardCharsets.US_ASCII));
    return out.toByteArray();
  }
}
