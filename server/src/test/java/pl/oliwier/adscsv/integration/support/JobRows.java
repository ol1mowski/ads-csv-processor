package pl.oliwier.adscsv.integration.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.support.TestUsers;

public final class JobRows {

  private final JdbcTemplate jdbc;

  public JobRows(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long count() {
    return jdbc.queryForObject("SELECT count(*) FROM processing_job", Long.class);
  }

  public long activeCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM processing_job WHERE status IN ('QUEUED', 'PROCESSING')", Long.class);
  }

  public void truncate() {
    jdbc.execute("TRUNCATE processing_job");
  }

  public JobStatus status(UUID id) {
    return JobStatus.valueOf(
        jdbc.queryForObject("SELECT status FROM processing_job WHERE id = ?", String.class, id));
  }

  public int attempts(UUID id) {
    return jdbc.queryForObject(
        "SELECT attempts FROM processing_job WHERE id = ?", Integer.class, id);
  }

  public String failureReason(UUID id) {
    return jdbc.queryForObject(
        "SELECT failure_reason FROM processing_job WHERE id = ?", String.class, id);
  }

  public String owner(UUID id) {
    return jdbc.queryForObject("SELECT owner FROM processing_job WHERE id = ?", String.class, id);
  }

  public boolean hasFinishedAt(UUID id) {
    return jdbc.queryForObject(
        "SELECT finished_at IS NOT NULL FROM processing_job WHERE id = ?", Boolean.class, id);
  }

  public UUID insertQueued(long sizeBytes) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO processing_job (id, owner, original_file_name, status, size_bytes, created_at)
        VALUES (?, ?, 'queued.csv', 'QUEUED', ?, now())
        """,
        id,
        TestUsers.ALICE,
        sizeBytes);
    return id;
  }

  public UUID insertProcessing(int attempts, long sizeBytes) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO processing_job (id, owner, original_file_name, status, size_bytes, attempts,
                                    created_at, started_at)
        VALUES (?, ?, 'interrupted.csv', 'PROCESSING', ?, ?, now(), now())
        """,
        id,
        TestUsers.ALICE,
        sizeBytes,
        attempts);
    return id;
  }

  public UUID insertFinished(JobStatus status, long sizeBytes) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO processing_job (id, owner, original_file_name, status, size_bytes,
                                    failure_reason, created_at, finished_at)
        VALUES (?, ?, 'finished.csv', ?, ?, ?, now(), now())
        """,
        id,
        TestUsers.ALICE,
        status.name(),
        sizeBytes,
        status == JobStatus.FAILED ? FailureReason.PROCESSING_ERROR.name() : null);
    return id;
  }

  public void recordPartialProgress(UUID id, long bytes) {
    jdbc.update(
        """
        UPDATE processing_job
        SET bytes_processed = ?, rows_processed = 7, rows_rejected = 1, total_impressions = 700,
            total_clicks = 21, rejected_samples = '[{"line":3,"reason":"FIELD_COUNT"}]'
        WHERE id = ?
        """,
        bytes,
        id);
  }
}
