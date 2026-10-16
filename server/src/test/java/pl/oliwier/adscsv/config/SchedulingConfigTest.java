package pl.oliwier.adscsv.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import pl.oliwier.adscsv.config.properties.ProcessingProperties;
import pl.oliwier.adscsv.service.processing.JobDispatcher;
import pl.oliwier.adscsv.support.TestProperties;

class SchedulingConfigTest {

  private final JobDispatcher dispatcher = mock(JobDispatcher.class);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
          .withUserConfiguration(SchedulingConfig.class)
          .withBean(JobDispatcher.class, () -> dispatcher)
          .withBean(ProcessingProperties.class, TestProperties::processing);

  @Test
  @DisplayName("The dispatcher tick is scheduled by default")
  void shouldScheduleDispatcherByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(SchedulingConfig.class);
          assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(1);
        });
  }

  @Test
  @DisplayName("Processing can be switched off so tests drive the queue by hand")
  void shouldNotScheduleWhenProcessingIsDisabled() {
    runner
        .withPropertyValues("app.processing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(SchedulingConfig.class));
  }

  @Test
  @DisplayName("Scheduled ticks run on virtual threads when virtual threads are enabled")
  void shouldRunTicksOnVirtualThreadsWhenEnabled() {
    var tick = new CompletableFuture<Thread>();
    willAnswer(_ -> tick.complete(Thread.currentThread())).given(dispatcher).dispatch();

    runner
        .withPropertyValues("spring.threads.virtual.enabled=true")
        .run(
            context ->
                assertThat(tick.get(5, TimeUnit.SECONDS).isVirtual())
                    .as("dispatcher tick thread is virtual")
                    .isTrue());
  }
}
