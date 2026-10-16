package pl.oliwier.adscsv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;

@Configuration(proxyBeanMethods = false)
public class WorkerExecutorConfig {

  public static final String CSV_WORKER_EXECUTOR = "csvWorkerExecutor";

  @Bean(CSV_WORKER_EXECUTOR)
  ThreadPoolTaskExecutor csvWorkerExecutor(ProcessingProperties props) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(props.workers());
    executor.setMaxPoolSize(props.workers());
    executor.setQueueCapacity(props.workers());
    executor.setThreadFactory(Thread.ofPlatform().name("csv-worker-", 1).factory());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationMillis(props.shutdownWait().toMillis());
    return executor;
  }
}
