package pl.oliwier.adscsv.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOperationsTest {

  @TempDir Path dir;

  @Test
  @DisplayName("Atomic move relocates the file without changing its bytes")
  void shouldMoveFileWhenTargetIsOnSameFileSystem() throws IOException {
    var source = Files.writeString(dir.resolve("a.part"), "data");
    var target = dir.resolve("b.csv");

    FileOperations.fsync(source);
    FileOperations.atomicMove(source, target);

    assertThat(source).doesNotExist();
    assertThat(target).hasContent("data");
  }

  @Test
  @DisplayName("Deleting a missing file is a quiet no-op")
  void shouldReturnFalseWhenDeletingMissingFile() {
    assertThat(FileOperations.deleteQuietly(dir.resolve("missing"))).isFalse();
  }

  @Test
  @DisplayName("Deleting a non-empty directory quietly reports failure instead of throwing")
  void shouldReturnFalseWhenDirectoryIsNotEmpty() throws IOException {
    var nested = Files.createDirectories(dir.resolve("nested"));
    Files.writeString(nested.resolve("file"), "x");

    assertThat(FileOperations.deleteQuietly(nested)).isFalse();
    assertThat(nested).exists();
  }

  @Test
  @DisplayName("Recursive delete removes the whole tree")
  void shouldDeleteTreeWhenDeletingRecursively() throws IOException {
    var root = Files.createDirectories(dir.resolve("job/sub"));
    Files.writeString(root.resolve("file"), "x");
    Files.writeString(dir.resolve("job/input.csv"), "y");

    FileOperations.deleteRecursivelyQuietly(dir.resolve("job"));

    assertThat(dir.resolve("job")).doesNotExist();
  }

  @Test
  @DisplayName("Recursive delete of a missing directory is a quiet no-op")
  void shouldIgnoreMissingDirectoryWhenDeletingRecursively() {
    FileOperations.deleteRecursivelyQuietly(dir.resolve("missing"));

    assertThat(dir).isEmptyDirectory();
  }
}
