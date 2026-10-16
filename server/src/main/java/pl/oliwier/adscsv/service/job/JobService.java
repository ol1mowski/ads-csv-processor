package pl.oliwier.adscsv.service.job;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pl.oliwier.adscsv.config.properties.HistoryProperties;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.PageResponse;
import pl.oliwier.adscsv.exception.job.JobNotCancellableException;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.exception.job.ResultGoneException;
import pl.oliwier.adscsv.exception.job.ResultNotAvailableException;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.model.ProcessingJob;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.storage.JobFileStorage;

@Service
@RequiredArgsConstructor
public class JobService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
  private static final String PROCESSED_SUFFIX = "-processed.csv";
  private static final Pattern CSV_EXTENSION = Pattern.compile("(?i)\\.csv\\z");

  private final JobStateService jobs;
  private final JobFileStorage storage;
  private final JobMapper mapper;
  private final HistoryProperties historyProperties;

  public JobResponse get(UUID id, String owner) {
    return mapper.toResponse(owned(id, owner));
  }

  public PageResponse<JobResponse> history(
      String owner, int page, int size, Set<JobStatus> statuses) {
    var pageable =
        PageRequest.of(page, Math.min(size, historyProperties.maxPageSize()), NEWEST_FIRST);
    var filter = statuses == null ? Set.<JobStatus>of() : statuses;
    return PageResponse.from(jobs.findPage(owner, filter, pageable).map(mapper::toResponse));
  }

  public JobResponse cancel(UUID id, String owner) {
    var previous = jobs.cancel(id, owner).orElseThrow(() -> new JobNotFoundException(id));
    switch (previous) {
      case QUEUED -> storage.deleteJob(id);
      case PROCESSING, CANCELLED -> {}
      case COMPLETED, FAILED -> throw new JobNotCancellableException(previous);
    }
    return get(id, owner);
  }

  public ResultFile result(UUID id, String owner) throws IOException {
    var job = owned(id, owner);
    if (job.getStatus() != JobStatus.COMPLETED) {
      throw new ResultNotAvailableException(job.getStatus());
    }
    var stored = storage.findOutput(id).orElseThrow(() -> new ResultGoneException(id));
    return new ResultFile(
        stored.content(), downloadName(job.getOriginalFileName()), stored.sizeBytes());
  }

  private ProcessingJob owned(UUID id, String owner) {
    return jobs.findById(id, owner).orElseThrow(() -> new JobNotFoundException(id));
  }

  static String downloadName(String fileName) {
    return CSV_EXTENSION.matcher(fileName).replaceFirst("") + PROCESSED_SUFFIX;
  }
}
