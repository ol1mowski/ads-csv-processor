package pl.oliwier.adscsv.integration.queued;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.oliwier.adscsv.support.ProgressAssertions.assertNoProgress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.oliwier.adscsv.integration.ProcessingDisabledTestBase;
import pl.oliwier.adscsv.model.FailureReason;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.processing.StartupRecovery;

class StartupRecoveryIntegrationTest extends ProcessingDisabledTestBase {

  private static final byte[] INPUT =
      "advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin\n"
          .getBytes(StandardCharsets.US_ASCII);

  @Autowired private StartupRecovery recovery;

  @Test
  @DisplayName("A job interrupted by a restart is requeued with its progress wiped")
  void shouldRequeueInterruptedJobKeepingItsInput() throws IOException {
    var id = rows.insertProcessing(1, INPUT.length);
    rows.recordPartialProgress(id, INPUT.length / 2);
    storage.writeInput(id, INPUT);

    recovery.recover();

    assertThat(rows.status(id)).isEqualTo(JobStatus.QUEUED);
    assertThat(rows.attempts(id)).isEqualTo(1);
    assertNoProgress(api.get(id));
    assertThat(storage.input(id)).exists();
  }

  @Test
  @DisplayName("A job interrupted on its last attempt fails as INTERRUPTED and loses its files")
  void shouldFailJobOutOfAttemptsAndDeleteItsFiles() throws IOException {
    var id = rows.insertProcessing(3, INPUT.length);
    storage.writeInput(id, INPUT);

    recovery.recover();

    assertThat(rows.status(id)).isEqualTo(JobStatus.FAILED);
    assertThat(rows.failureReason(id)).isEqualTo(FailureReason.INTERRUPTED.name());
    assertThat(rows.hasFinishedAt(id)).isTrue();
    assertThat(storage.jobDirectory(id)).doesNotExist();
  }

  @Test
  @DisplayName("Unfinished uploads are removed while queued jobs stay untouched")
  void shouldClearUnfinishedUploadsAndKeepQueuedJobs() throws IOException {
    var part = Files.write(storage.incoming().resolve(UUID.randomUUID() + ".part"), INPUT);
    var queued = rows.insertQueued(INPUT.length);
    storage.writeInput(queued, INPUT);

    recovery.recover();

    assertThat(part).doesNotExist();
    assertThat(rows.status(queued)).isEqualTo(JobStatus.QUEUED);
    assertThat(storage.input(queued)).exists();
  }

  @Test
  @DisplayName(
      "Files a crash left behind for cancelled, failed or unknown jobs are removed; queued and"
          + " completed jobs keep theirs")
  void shouldRemoveOrphanedFilesAndKeepFilesOfLiveJobs() throws IOException {
    var cancelled = rows.insertFinished(JobStatus.CANCELLED, INPUT.length);
    storage.writeInput(cancelled, INPUT);
    var failed = rows.insertFinished(JobStatus.FAILED, INPUT.length);
    storage.writeInput(failed, INPUT);
    var unknown = UUID.randomUUID();
    storage.writeInput(unknown, INPUT);
    var queued = rows.insertQueued(INPUT.length);
    storage.writeInput(queued, INPUT);
    var completed = rows.insertFinished(JobStatus.COMPLETED, INPUT.length);
    storage.writeOutput(completed, INPUT);
    var foreign = Files.createDirectories(storage.jobs().resolve("not-a-job"));

    recovery.recover();

    assertThat(storage.jobDirectory(cancelled)).doesNotExist();
    assertThat(storage.jobDirectory(failed)).doesNotExist();
    assertThat(storage.jobDirectory(unknown)).doesNotExist();
    assertThat(storage.input(queued)).exists();
    assertThat(storage.output(completed)).exists();
    assertThat(foreign).as("only job directories are swept").exists();
  }
}
