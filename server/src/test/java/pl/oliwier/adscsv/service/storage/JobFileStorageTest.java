package pl.oliwier.adscsv.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.oliwier.adscsv.config.properties.StorageProperties;

class JobFileStorageTest {

  private static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");

  @TempDir Path root;

  private JobFileStorage storage;

  @BeforeEach
  void setUp() throws IOException {
    storage = new JobFileStorage(new StorageProperties(root));
  }

  @Test
  @DisplayName("Construction creates the incoming and jobs directories")
  void shouldCreateDirectoriesWhenConstructed() {
    assertThat(root.resolve("incoming")).isDirectory();
    assertThat(root.resolve("jobs")).isDirectory();
  }

  @Test
  @DisplayName("Construction fails fast when the root cannot hold directories")
  void shouldFailWhenRootIsAFile() throws IOException {
    var file = Files.writeString(root.resolve("file"), "x");

    assertThatThrownBy(() -> new JobFileStorage(new StorageProperties(file)))
        .isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Committing an upload moves the part file to the job input")
  void shouldMovePartToJobInputWhenCommitted() throws IOException {
    writeIncoming("payload");

    storage.commitInput(JOB_ID);

    assertThat(root.resolve("incoming/" + JOB_ID + ".part")).doesNotExist();
    assertThat(jobDirectory(JOB_ID).resolve("input.csv")).hasContent("payload");
    try (var in = storage.openInput(JOB_ID)) {
      assertThat(in.readAllBytes()).asString().isEqualTo("payload");
    }
  }

  @Test
  @DisplayName("Uploaded data, results and job directories are accessible only to the service user")
  void shouldRestrictPermissionsToOwnerWhenJobFilesAreCreated() throws IOException {
    assumeTrue(root.getFileSystem().supportedFileAttributeViews().contains("posix"));
    commitInput();
    storage.createOutput(JOB_ID).close();

    var ownerOnlyFile = PosixFilePermissions.fromString("rw-------");
    assertThat(Files.getPosixFilePermissions(jobDirectory(JOB_ID)))
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    assertThat(Files.getPosixFilePermissions(jobDirectory(JOB_ID).resolve("input.csv")))
        .isEqualTo(ownerOnlyFile);
    assertThat(Files.getPosixFilePermissions(jobDirectory(JOB_ID).resolve("output.csv")))
        .isEqualTo(ownerOnlyFile);
  }

  @Test
  @DisplayName("An incoming part is never silently overwritten")
  void shouldRejectSecondIncomingPartForSameJob() throws IOException {
    writeIncoming("first");

    assertThatThrownBy(() -> storage.createIncoming(JOB_ID))
        .isInstanceOf(FileAlreadyExistsException.class);
  }

  @Test
  @DisplayName("Opening a missing input reports NoSuchFileException")
  void shouldThrowNoSuchFileWhenInputMissing() {
    assertThatThrownBy(() -> storage.openInput(JOB_ID)).isInstanceOf(NoSuchFileException.class);
  }

  @Test
  @DisplayName("A committed output is found with its size")
  void shouldFindOutputWhenItExists() throws IOException {
    commitInput();
    assertThat(storage.findOutput(JOB_ID)).isEmpty();
    try (var out = storage.createOutput(JOB_ID)) {
      out.write("result".getBytes());
    }
    storage.commitOutput(JOB_ID);

    var found = storage.findOutput(JOB_ID);

    assertThat(found).hasValueSatisfying(file -> assertThat(file.sizeBytes()).isEqualTo(6));
    assertThat(found.orElseThrow().content().getContentAsByteArray())
        .asString()
        .isEqualTo("result");
  }

  @Test
  @DisplayName("Deleting the input of a finished job keeps its result")
  void shouldKeepOutputWhenInputIsDeleted() throws IOException {
    commitInput();
    storage.createOutput(JOB_ID).close();

    storage.deleteInput(JOB_ID);

    try (var files = Files.list(jobDirectory(JOB_ID))) {
      assertThat(files.map(file -> file.getFileName().toString())).containsExactly("output.csv");
    }
  }

  @Test
  @DisplayName("Deletes of missing files and directories never throw")
  void shouldIgnoreMissingFilesWhenDeleting() {
    storage.deleteIncoming(JOB_ID);
    storage.deleteInput(JOB_ID);
    storage.deleteJob(JOB_ID);

    assertThat(jobDirectory(JOB_ID)).doesNotExist();
  }

  @Test
  @DisplayName("Deleting a job removes its whole directory")
  void shouldDeleteJobDirectory() throws IOException {
    commitInput();
    storage.createOutput(JOB_ID).close();

    storage.deleteJob(JOB_ID);

    assertThat(jobDirectory(JOB_ID)).doesNotExist();
  }

  @Test
  @DisplayName("Clearing incoming removes every part file")
  void shouldRemoveAllPartsWhenClearingIncoming() throws IOException {
    writeIncoming("one");
    storage.createIncoming(UUID.randomUUID()).close();

    storage.clearIncoming();

    assertThat(root.resolve("incoming")).isEmptyDirectory();
  }

  @Test
  @DisplayName("Job ids come from canonical UUID directory names only")
  void shouldListJobIdsOfJobDirectoriesOnly() throws IOException {
    commitInput();
    Files.createDirectories(root.resolve("jobs").resolve("not-a-job"));
    Files.createDirectories(root.resolve("jobs").resolve("1-1-1-1-1"));

    assertThat(storage.jobIds()).containsExactly(JOB_ID);
  }

  @Test
  @DisplayName("Usable space of the volume is reported")
  void shouldReportUsableSpace() throws IOException {
    assertThat(storage.usableSpace()).isPositive();
  }

  private void writeIncoming(String content) throws IOException {
    try (var out = storage.createIncoming(JOB_ID)) {
      out.write(content.getBytes());
    }
  }

  private void commitInput() throws IOException {
    writeIncoming("input");
    storage.commitInput(JOB_ID);
  }

  private Path jobDirectory(UUID id) {
    return root.resolve("jobs").resolve(id.toString());
  }
}
