package pl.oliwier.adscsv.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.Totals;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.RejectReason;
import pl.oliwier.adscsv.model.RejectedRow;

public final class JobResponses {

  public static final Instant CREATED_AT = Instant.parse("2026-10-14T10:15:30.123Z");
  public static final Instant STARTED_AT = Instant.parse("2026-10-14T10:15:30.410Z");
  public static final Instant FINISHED_AT = Instant.parse("2026-10-14T10:15:30.502Z");

  private JobResponses() {}

  public static JobResponse queued(UUID id) {
    return new JobResponse(
        id,
        "ads.csv",
        JobStatus.QUEUED,
        77_752,
        0,
        0,
        0,
        0,
        List.of(),
        new Totals("0", "0", null),
        null,
        0,
        CREATED_AT,
        null,
        null,
        null);
  }

  public static JobResponse completed(UUID id) {
    return new JobResponse(
        id,
        "sample_ads_data.csv",
        JobStatus.COMPLETED,
        77_752,
        77_752,
        100,
        997,
        3,
        List.of(new RejectedRow(5, RejectReason.FIELD_COUNT)),
        new Totals("6733389", "176755", "0.026251"),
        null,
        1,
        CREATED_AT,
        STARTED_AT,
        FINISHED_AT,
        ApiRoutes.result(id));
  }

  public static JobResponse withStatus(UUID id, JobStatus status) {
    var queued = queued(id);
    return new JobResponse(
        id,
        queued.fileName(),
        status,
        queued.sizeBytes(),
        queued.bytesProcessed(),
        queued.progressPercent(),
        queued.rowsProcessed(),
        queued.rowsRejected(),
        queued.rejectedSamples(),
        queued.totals(),
        status == JobStatus.FAILED ? FailureReason.NO_VALID_ROWS : null,
        queued.attempts(),
        queued.createdAt(),
        queued.startedAt(),
        status.isActive() ? null : FINISHED_AT,
        null);
  }
}
