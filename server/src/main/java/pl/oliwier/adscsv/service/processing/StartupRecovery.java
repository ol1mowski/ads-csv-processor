package pl.oliwier.adscsv.service.processing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.service.state.JobStateService;
import pl.oliwier.adscsv.service.storage.JobFileStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecovery implements SmartInitializingSingleton {

  private final JobStateService jobs;
  private final JobFileStorage storage;
  private final ProcessingProperties props;

  @Override
  public void afterSingletonsInstantiated() {
    recover();
  }

  public void recover() {
    try {
      storage.clearIncoming();
    } catch (IOException e) {
      throw new UncheckedIOException("Clearing unfinished uploads failed", e);
    }
    var failed = jobs.failExhausted(props.maxAttempts());
    failed.forEach(storage::deleteJob);
    var requeued = jobs.requeueInterrupted(props.maxAttempts());
    var orphaned = deleteOrphanedFiles();
    if (requeued > 0 || !failed.isEmpty() || !orphaned.isEmpty()) {
      log.warn(
          "Startup recovery requeued {} interrupted jobs, failed {} out of attempts and removed"
              + " the files of {} cancelled, failed or unknown jobs",
          requeued,
          failed.size(),
          orphaned.size());
    }
  }

  private List<UUID> deleteOrphanedFiles() {
    List<UUID> stored;
    try {
      stored = storage.jobIds();
    } catch (IOException e) {
      throw new UncheckedIOException("Listing job directories failed", e);
    }
    var withFiles = jobs.findIdsWithFiles(stored);
    var orphaned = stored.stream().filter(id -> !withFiles.contains(id)).toList();
    orphaned.forEach(storage::deleteJob);
    return orphaned;
  }
}
