package pl.oliwier.adscsv.service.state;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.model.ProgressSnapshot;
import pl.oliwier.adscsv.model.RejectedRow;
import pl.oliwier.adscsv.repository.ProcessingJobRepository;

@Service
@RequiredArgsConstructor
public class JobStateService {

  private final ProcessingJobRepository repository;
  private final RejectedRowsJson rejectedRows;
  private final Clock clock;

  @Transactional
  public ProcessingJob createQueued(UUID id, String owner, String fileName, long sizeBytes) {
    return repository.save(ProcessingJob.queued(id, owner, fileName, sizeBytes, clock.instant()));
  }

  @Transactional(readOnly = true)
  public Optional<ProcessingJob> findById(UUID jobId, String owner) {
    return repository.findByIdAndOwner(jobId, owner);
  }

  @Transactional(readOnly = true)
  public Page<ProcessingJob> findPage(String owner, Set<JobStatus> statuses, Pageable pageable) {
    return statuses.isEmpty()
        ? repository.findByOwner(owner, pageable)
        : repository.findByOwnerAndStatusIn(owner, statuses, pageable);
  }

  @Transactional
  public Optional<JobStatus> cancel(UUID jobId, String owner) {
    var previous = repository.lockStatus(jobId, owner).map(JobStatus::valueOf);
    previous.filter(JobStatus::isActive).ifPresent(_ -> repository.cancelActive(jobId));
    return previous;
  }

  @Transactional
  public Optional<UUID> claimNext() {
    return repository.lockNextQueued().filter(id -> repository.claim(id) == 1);
  }

  @Transactional
  public boolean reportProgress(UUID jobId, ProgressSnapshot progress) {
    return repository.reportProgress(jobId, progress) == 1;
  }

  @Transactional
  public boolean complete(UUID jobId, ProgressSnapshot progress, List<RejectedRow> samples) {
    return finish(jobId, JobStatus.COMPLETED, null, progress, samples);
  }

  @Transactional
  public boolean fail(
      UUID jobId, FailureReason reason, ProgressSnapshot progress, List<RejectedRow> samples) {
    return finish(jobId, JobStatus.FAILED, reason.name(), progress, samples);
  }

  @Transactional
  public boolean release(UUID jobId) {
    return repository.release(jobId) == 1;
  }

  @Transactional
  public int requeueInterrupted(int maxAttempts) {
    return repository.requeueInterrupted(maxAttempts);
  }

  @Transactional
  public List<UUID> failExhausted(int maxAttempts) {
    var ids = repository.lockExhausted(maxAttempts);
    if (!ids.isEmpty()) {
      repository.failInterrupted(ids);
    }
    return ids;
  }

  @Transactional(readOnly = true)
  public Set<UUID> findIdsWithFiles(Collection<UUID> ids) {
    return ids.isEmpty() ? Set.of() : Set.copyOf(repository.findIdsWithFiles(ids));
  }

  private boolean finish(
      UUID jobId,
      JobStatus status,
      String reason,
      ProgressSnapshot progress,
      List<RejectedRow> samples) {
    var updated =
        repository.finish(jobId, status.name(), reason, progress, rejectedRows.write(samples));
    return updated == 1;
  }
}
