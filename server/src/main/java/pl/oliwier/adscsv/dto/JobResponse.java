package pl.oliwier.adscsv.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.RejectedRow;

public record JobResponse(
    UUID id,
    String fileName,
    JobStatus status,
    long sizeBytes,
    long bytesProcessed,
    int progressPercent,
    long rowsProcessed,
    long rowsRejected,
    List<RejectedRow> rejectedSamples,
    Totals totals,
    FailureReason failureReason,
    int attempts,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    String resultUrl) {}
