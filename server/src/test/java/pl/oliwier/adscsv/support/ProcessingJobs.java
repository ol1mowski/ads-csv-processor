package pl.oliwier.adscsv.support;

import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;

public final class ProcessingJobs {

  public static final Instant CREATED_AT = Instant.parse("2026-10-14T10:15:30.123456Z");
  public static final String OWNER = TestUsers.ALICE;

  private ProcessingJobs() {}

  public static ProcessingJob queued(UUID id) {
    return ProcessingJob.queued(id, OWNER, "ads.csv", 1000, CREATED_AT);
  }

  public static ProcessingJob withStatus(UUID id, JobStatus status) {
    var job = queued(id);
    ReflectionTestUtils.setField(job, "status", status);
    return job;
  }

  public static ProcessingJob completed(UUID id, String fileName, int attempts) {
    var job = ProcessingJob.queued(id, OWNER, fileName, 1000, CREATED_AT);
    ReflectionTestUtils.setField(job, "status", JobStatus.COMPLETED);
    ReflectionTestUtils.setField(job, "attempts", attempts);
    ReflectionTestUtils.setField(job, "bytesProcessed", 1000L);
    return job;
  }

  public static ProcessingJob failed(UUID id, FailureReason reason) {
    var job = withStatus(id, JobStatus.FAILED);
    ReflectionTestUtils.setField(job, "failureReason", reason);
    return job;
  }

  public static ProcessingJob set(ProcessingJob job, String field, Object value) {
    ReflectionTestUtils.setField(job, field, value);
    return job;
  }
}
