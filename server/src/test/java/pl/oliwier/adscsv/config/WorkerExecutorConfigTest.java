package pl.oliwier.adscsv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.support.TestProperties;

class WorkerExecutorConfigTest {

  @Test
  @DisplayName("CSV workers are a bounded pool of platform threads even with virtual threads on")
  void shouldRunWorkersOnPlatformThreadsWhenVirtualThreadsAreEnabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
        .withUserConfiguration(WorkerExecutorConfig.class)
        .withBean(ProcessingProperties.class, TestProperties::processing)
        .withPropertyValues("spring.threads.virtual.enabled=true")
        .run(
            context -> {
              var executor =
                  context.getBean(
                      WorkerExecutorConfig.CSV_WORKER_EXECUTOR, ThreadPoolTaskExecutor.class);
              var worker = new CompletableFuture<Thread>();

              executor.execute(() -> worker.complete(Thread.currentThread()));

              var thread = worker.get(5, TimeUnit.SECONDS);
              assertThat(thread.isVirtual()).isFalse();
              assertThat(thread.getName()).startsWith("csv-worker-");
              assertThat(executor.getCorePoolSize()).isEqualTo(2);
              assertThat(executor.getMaxPoolSize()).isEqualTo(2);
              assertThat(executor.getQueueCapacity()).isEqualTo(2);
              assertThat(context).doesNotHaveBean("applicationTaskExecutor");
            });
  }
}
