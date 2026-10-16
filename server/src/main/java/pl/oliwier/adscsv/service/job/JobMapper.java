package pl.oliwier.adscsv.service.job;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.service.state.RejectedRowsJson;
import pl.oliwier.adscsv.service.transformation.Ctr;

@Component
@RequiredArgsConstructor
public class JobMapper {

  private static final int MAX_PERCENT_BEFORE_COMPLETION = 99;

  private final RejectedRowsJson rejectedRows;

  public JobResponse toResponse(ProcessingJob job) {
    var completed = job.getStatus() == JobStatus.COMPLETED;
    return new JobResponse(
        job.getId(),
        job.getOriginalFileName(),
        job.getStatus(),
        job.getSizeBytes(),
        job.getBytesProcessed(),
        progressPercent(job),
        job.getRowsProcessed(),
        job.getRowsRejected(),
        rejectedRows.read(job.getRejectedSamples()),
        totals(job),
        job.getFailureReason(),
        job.getAttempts(),
        toMillis(job.getCreatedAt()),
        toMillis(job.getStartedAt()),
        toMillis(job.getFinishedAt()),
        completed ? ApiRoutes.result(job.getId()) : null);
  }

  private static int progressPercent(ProcessingJob job) {
    if (job.getStatus() == JobStatus.COMPLETED) {
      return 100;
    }
    var percent = job.getBytesProcessed() * 100 / job.getSizeBytes();
    return (int) Math.min(percent, MAX_PERCENT_BEFORE_COMPLETION);
  }

  private static Totals totals(ProcessingJob job) {
    return new Totals(
        Long.toString(job.getTotalImpressions()),
        Long.toString(job.getTotalClicks()),
        Ctr.of(job.getTotalClicks(), job.getTotalImpressions()).orElse(null));
  }

  private static Instant toMillis(Instant instant) {
    return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS);
  }
}
