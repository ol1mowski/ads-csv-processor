package pl.oliwier.adscsv.service.processing;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.service.csv.AdsCsvFormat;
import pl.oliwier.adscsv.service.csv.BoundedLineReader;
import pl.oliwier.adscsv.service.csv.Line;
import pl.oliwier.adscsv.service.csv.RowResult;
import pl.oliwier.adscsv.service.csv.RowTransformer;
import pl.oliwier.adscsv.service.processing.ProcessingOutcome.Cancelled;
import pl.oliwier.adscsv.service.processing.ProcessingOutcome.Completed;
import pl.oliwier.adscsv.service.processing.ProcessingOutcome.Failed;
import pl.oliwier.adscsv.service.processing.ProcessingOutcome.Released;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.storage.JobFileStorage;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvJobProcessor {

  private static final int BUFFER_BYTES = 1 << 20;

  private final JobStateService jobs;
  private final JobFileStorage storage;
  private final RowTransformer rowTransformer;
  private final ProcessingProperties props;

  public void process(UUID jobId, BooleanSupplier stopRequested) {
    var stats =
        new TransformStats(
            props.rejectedSamples(), props.minRejectedToAbort(), props.maxRejectedRatio());
    var outcome = transform(jobId, stats, stopRequested);
    log.info("Job {} finished transformation: {}", jobId, outcome);
    try {
      settle(jobId, outcome, stats);
    } catch (DataAccessException | TransactionException e) {
      log.warn("Job {} could not be settled, startup recovery will requeue it", jobId, e);
    }
  }

  private ProcessingOutcome transform(
      UUID jobId, TransformStats stats, BooleanSupplier stopRequested) {
    try {
      return transformFile(jobId, stats, stopRequested);
    } catch (NoSuchFileException e) {
      log.warn("Job {} has no input file", jobId);
      return new Failed(FailureReason.INPUT_MISSING);
    } catch (DataAccessException | TransactionException e) {
      log.warn("Job {} lost the database while reporting progress", jobId, e);
      return new Released();
    } catch (IOException | RuntimeException e) {
      log.error("Job {} failed", jobId, e);
      return new Failed(FailureReason.PROCESSING_ERROR);
    }
  }

  private ProcessingOutcome transformFile(
      UUID jobId, TransformStats stats, BooleanSupplier stopRequested) throws IOException {
    ProcessingOutcome outcome;
    try (var lines =
            new BoundedLineReader(
                storage.openInput(jobId), BUFFER_BYTES, AdsCsvFormat.MAX_LINE_BYTES);
        var out = new BufferedOutputStream(storage.createOutput(jobId), BUFFER_BYTES)) {
      outcome = transformLines(jobId, lines, out, stats, stopRequested);
    }
    if (outcome instanceof Completed) {
      storage.commitOutput(jobId);
    }
    return outcome;
  }

  private ProcessingOutcome transformLines(
      UUID jobId,
      BoundedLineReader lines,
      OutputStream out,
      TransformStats stats,
      BooleanSupplier stopRequested)
      throws IOException {
    if (!lines.next() || !AdsCsvFormat.isHeader(lines.current())) {
      return new Failed(FailureReason.INVALID_HEADER);
    }
    out.write(rowTransformer.outputHeader());
    var progressInterval = props.progressInterval().toNanos();
    var nextProgressAt = System.nanoTime();
    var checkEveryBytes = props.checkEvery().toBytes();
    var nextCheckAt = checkEveryBytes;
    while (lines.next()) {
      var line = lines.current();
      if (line.isRecord() && !transformRecord(line, lines.lineNumber(), out, stats)) {
        return new Failed(FailureReason.TOO_MANY_INVALID_ROWS);
      }
      var consumed = lines.bytesConsumed();
      if (consumed < nextCheckAt) {
        continue;
      }
      nextCheckAt = consumed + checkEveryBytes;
      stats.consumed(consumed);
      if (stopRequested.getAsBoolean()) {
        return new Released();
      }
      var now = System.nanoTime();
      if (now - nextProgressAt >= 0) {
        nextProgressAt = now + progressInterval;
        if (!jobs.reportProgress(jobId, stats.snapshot())) {
          return new Cancelled();
        }
      }
    }
    stats.consumed(lines.bytesConsumed());
    if (stats.hasOnlyRejected()) {
      return new Failed(FailureReason.NO_VALID_ROWS);
    }
    return stats.rejectedAboveRatio()
        ? new Failed(FailureReason.TOO_MANY_INVALID_ROWS)
        : new Completed();
  }

  private boolean transformRecord(
      Line line, long lineNumber, OutputStream out, TransformStats stats) throws IOException {
    switch (rowTransformer.transform(line)) {
      case RowResult.Transformed row -> {
        line.writeTo(out);
        out.write(AdsCsvFormat.SEPARATOR);
        out.write(row.derivedValue().getBytes(StandardCharsets.US_ASCII));
        out.write('\n');
        stats.accept(row.impressions(), row.clicks());
        return true;
      }
      case RowResult.Rejected rejected -> {
        stats.reject(lineNumber, rejected.reason());
        return !stats.rejectedTooMany();
      }
    }
  }

  private void settle(UUID jobId, ProcessingOutcome outcome, TransformStats stats) {
    var discarded =
        switch (outcome) {
          case Completed _ -> !jobs.complete(jobId, stats.snapshot(), stats.samples());
          case Failed failed -> {
            jobs.fail(jobId, failed.reason(), stats.snapshot(), stats.samples());
            yield true;
          }
          case Cancelled _ -> true;
          case Released _ -> !jobs.release(jobId);
        };
    if (discarded) {
      storage.deleteJob(jobId);
    } else if (outcome instanceof Completed) {
      storage.deleteInput(jobId);
    }
  }
}
