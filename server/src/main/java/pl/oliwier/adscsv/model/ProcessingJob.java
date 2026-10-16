package pl.oliwier.adscsv.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processing_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob {

  @Id private UUID id;

  private String owner;
  private String originalFileName;

  @Enumerated(EnumType.STRING)
  private JobStatus status;

  private long sizeBytes;
  private long bytesProcessed;
  private long rowsProcessed;
  private long rowsRejected;
  private long totalImpressions;
  private long totalClicks;
  private String rejectedSamples;

  @Enumerated(EnumType.STRING)
  private FailureReason failureReason;

  private int attempts;
  private Instant createdAt;
  private Instant startedAt;
  private Instant finishedAt;

  public static ProcessingJob queued(
      UUID id, String owner, String fileName, long sizeBytes, Instant now) {
    var job = new ProcessingJob();
    job.id = id;
    job.owner = owner;
    job.originalFileName = fileName;
    job.status = JobStatus.QUEUED;
    job.sizeBytes = sizeBytes;
    job.createdAt = now;
    return job;
  }
}
