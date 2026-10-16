package pl.oliwier.adscsv.service.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.oliwier.adscsv.config.properties.UploadProperties;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.exception.upload.InvalidUploadException;
import pl.oliwier.adscsv.exception.upload.UploadProblem;
import pl.oliwier.adscsv.service.csv.AdsCsvFormat;
import pl.oliwier.adscsv.service.job.JobMapper;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.storage.JobFileStorage;
import pl.oliwier.adscsv.shared.LimitedInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobUploadService {

  private static final double REQUIRED_SPACE_FACTOR = 2.2;
  private static final long OUT_OF_SPACE_BYTES = 64 * 1024;

  private final JobFileStorage storage;
  private final JobStateService jobs;
  private final JobMapper mapper;
  private final UploadProperties props;

  public JobResponse accept(String owner, String fileName, long contentLength, InputStream body)
      throws IOException {
    var maxBytes = props.maxSize().toBytes();
    rejectByDeclaredLength(contentLength, maxBytes);
    requireSpaceFor(isDeclared(contentLength) ? contentLength : maxBytes);
    return store(owner, fileName, contentLength, new LimitedInputStream(body, maxBytes));
  }

  private void requireSpaceFor(long uploadBytes) throws IOException {
    var inputAndOutputBytes = (long) Math.ceil(uploadBytes * REQUIRED_SPACE_FACTOR);
    if (storage.usableSpace() < inputAndOutputBytes) {
      throw new InvalidUploadException(UploadProblem.INSUFFICIENT_STORAGE);
    }
  }

  private boolean isOutOfSpace() {
    try {
      return storage.usableSpace() < OUT_OF_SPACE_BYTES;
    } catch (IOException e) {
      return false;
    }
  }

  private JobResponse store(
      String owner, String fileName, long contentLength, LimitedInputStream in) throws IOException {
    var id = UUID.randomUUID();
    var queued = false;
    try {
      var size = receive(id, contentLength, in);
      storage.commitInput(id);
      var job = jobs.createQueued(id, owner, fileName, size);
      queued = true;
      log.info("Job {} queued with {} bytes", id, size);
      return mapper.toResponse(job);
    } catch (InvalidUploadException e) {
      log.info("Upload of job {} rejected with {} after {} bytes", id, e.problem(), in.count());
      throw e;
    } catch (IOException e) {
      if (isOutOfSpace()) {
        log.warn("Upload of job {} ran out of disk space after {} bytes", id, in.count(), e);
        throw new InvalidUploadException(UploadProblem.INSUFFICIENT_STORAGE);
      }
      throw e;
    } finally {
      if (!queued) {
        discard(id);
      }
    }
  }

  private long receive(UUID id, long contentLength, LimitedInputStream in) throws IOException {
    try (var out = storage.createIncoming(id)) {
      var header = UploadBody.readHeader(in);
      if (header.length == 0) {
        throw new InvalidUploadException(UploadProblem.EMPTY);
      }
      if (!AdsCsvFormat.isHeaderLine(header)) {
        throw new InvalidUploadException(UploadProblem.INVALID_HEADER);
      }
      out.write(header);
      UploadBody.copy(in, out);
    }
    if (isDeclared(contentLength) && contentLength != in.count()) {
      throw new InvalidUploadException(UploadProblem.INTERRUPTED);
    }
    return in.count();
  }

  private void discard(UUID id) {
    storage.deleteIncoming(id);
    storage.deleteJob(id);
  }

  private static void rejectByDeclaredLength(long contentLength, long maxBytes) {
    if (contentLength == 0) {
      throw new InvalidUploadException(UploadProblem.EMPTY);
    }
    if (contentLength > maxBytes) {
      throw new InvalidUploadException(UploadProblem.TOO_LARGE);
    }
  }

  private static boolean isDeclared(long contentLength) {
    return contentLength >= 0;
  }
}
