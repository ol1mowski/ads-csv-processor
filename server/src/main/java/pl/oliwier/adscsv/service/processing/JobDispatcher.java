package pl.oliwier.adscsv.service.processing;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.config.WorkerExecutorConfig;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.service.state.JobStateService;

@Slf4j
@Component
public class JobDispatcher implements SmartLifecycle {

  private final JobStateService jobs;
  private final CsvJobProcessor processor;
  private final TaskExecutor executor;
  private final ProcessingProperties props;
  private final Semaphore slots;
  private volatile boolean stopping;
  private volatile boolean running;

  public JobDispatcher(
      JobStateService jobs,
      CsvJobProcessor processor,
      @Qualifier(WorkerExecutorConfig.CSV_WORKER_EXECUTOR) TaskExecutor executor,
      ProcessingProperties props) {
    this.jobs = jobs;
    this.processor = processor;
    this.executor = executor;
    this.props = props;
    this.slots = new Semaphore(props.workers());
  }

  public void dispatch() {
    while (!stopping && slots.tryAcquire()) {
      if (!claimAndSubmit()) {
        slots.release();
        return;
      }
    }
  }

  @Override
  public void start() {
    stopping = false;
    running = true;
  }

  @Override
  public void stop() {
    stopping = true;
    try {
      var drained =
          slots.tryAcquire(props.workers(), props.shutdownWait().toMillis(), TimeUnit.MILLISECONDS);
      if (drained) {
        slots.release(props.workers());
      } else {
        log.warn("Workers still busy after {}", props.shutdownWait());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private boolean claimAndSubmit() {
    Optional<UUID> claimed;
    try {
      claimed = jobs.claimNext();
    } catch (RuntimeException e) {
      log.warn("Claiming the next job failed, retrying on the next tick", e);
      return false;
    }
    return claimed.isPresent() && submit(claimed.get());
  }

  private boolean submit(UUID jobId) {
    try {
      executor.execute(() -> run(jobId));
      return true;
    } catch (TaskRejectedException e) {
      log.warn("Worker pool rejected job {}, returning it to the queue", jobId);
      returnToQueue(jobId);
      return false;
    }
  }

  private void returnToQueue(UUID jobId) {
    try {
      jobs.release(jobId);
    } catch (RuntimeException e) {
      log.warn(
          "Job {} could not be returned to the queue, startup recovery will requeue it", jobId, e);
    }
  }

  private void run(UUID jobId) {
    try {
      processor.process(jobId, () -> stopping);
    } catch (RuntimeException e) {
      log.error("Worker crashed on job {}", jobId, e);
    } finally {
      slots.release();
    }
  }
}
