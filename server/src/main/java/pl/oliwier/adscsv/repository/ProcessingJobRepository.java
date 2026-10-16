package pl.oliwier.adscsv.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.model.ProgressSnapshot;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

  @Query(
      value =
          """
          SELECT id FROM processing_job
          WHERE status = 'QUEUED'
          ORDER BY created_at, id
          LIMIT 1
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  Optional<UUID> lockNextQueued();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET status = 'PROCESSING', attempts = attempts + 1, started_at = now(),
              bytes_processed = 0, rows_processed = 0, rows_rejected = 0,
              total_impressions = 0, total_clicks = 0, rejected_samples = NULL
          WHERE id = :id AND status = 'QUEUED'
          """,
      nativeQuery = true)
  int claim(UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET bytes_processed = :#{#progress.bytesProcessed()},
              rows_processed = :#{#progress.rowsProcessed()},
              rows_rejected = :#{#progress.rowsRejected()},
              total_impressions = :#{#progress.impressions()},
              total_clicks = :#{#progress.clicks()}
          WHERE id = :id AND status = 'PROCESSING'
          """,
      nativeQuery = true)
  int reportProgress(UUID id, ProgressSnapshot progress);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET status = :status, failure_reason = :reason,
              bytes_processed = :#{#progress.bytesProcessed()},
              rows_processed = :#{#progress.rowsProcessed()},
              rows_rejected = :#{#progress.rowsRejected()},
              total_impressions = :#{#progress.impressions()},
              total_clicks = :#{#progress.clicks()}, rejected_samples = :samples,
              finished_at = now()
          WHERE id = :id AND status = 'PROCESSING'
          """,
      nativeQuery = true)
  int finish(UUID id, String status, String reason, ProgressSnapshot progress, String samples);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET status = 'QUEUED', started_at = NULL,
              bytes_processed = 0, rows_processed = 0, rows_rejected = 0,
              total_impressions = 0, total_clicks = 0, rejected_samples = NULL
          WHERE id = :id AND status = 'PROCESSING'
          """,
      nativeQuery = true)
  int release(UUID id);

  @Query(
      value = "SELECT status FROM processing_job WHERE id = :id AND owner = :owner FOR UPDATE",
      nativeQuery = true)
  Optional<String> lockStatus(UUID id, String owner);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job SET status = 'CANCELLED', finished_at = now()
          WHERE id = :id AND status IN ('QUEUED', 'PROCESSING')
          """,
      nativeQuery = true)
  int cancelActive(UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET status = 'QUEUED', started_at = NULL,
              bytes_processed = 0, rows_processed = 0, rows_rejected = 0,
              total_impressions = 0, total_clicks = 0, rejected_samples = NULL
          WHERE status = 'PROCESSING' AND attempts < :maxAttempts
          """,
      nativeQuery = true)
  int requeueInterrupted(int maxAttempts);

  @Query(
      value =
          """
          SELECT id FROM processing_job
          WHERE status = 'PROCESSING' AND attempts >= :maxAttempts
          FOR UPDATE
          """,
      nativeQuery = true)
  List<UUID> lockExhausted(int maxAttempts);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE processing_job
          SET status = 'FAILED', failure_reason = 'INTERRUPTED', finished_at = now()
          WHERE id IN (:ids) AND status = 'PROCESSING'
          """,
      nativeQuery = true)
  int failInterrupted(Collection<UUID> ids);

  @Query(
      value =
          """
          SELECT id FROM processing_job
          WHERE id IN (:ids) AND status IN ('QUEUED', 'PROCESSING', 'COMPLETED')
          """,
      nativeQuery = true)
  List<UUID> findIdsWithFiles(Collection<UUID> ids);

  Optional<ProcessingJob> findByIdAndOwner(UUID id, String owner);

  Page<ProcessingJob> findByOwner(String owner, Pageable pageable);

  Page<ProcessingJob> findByOwnerAndStatusIn(
      String owner, Collection<JobStatus> statuses, Pageable pageable);
}
