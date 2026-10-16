package pl.oliwier.adscsv.integration.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import pl.oliwier.adscsv.shared.FileOperations;

public final class StorageInspector implements DisposableBean {

  private final Path root;

  public StorageInspector(Path root) {
    this.root = root;
  }

  public Path incoming() {
    return root.resolve("incoming");
  }

  public Path jobs() {
    return root.resolve("jobs");
  }

  public Path jobDirectory(UUID jobId) {
    return jobs().resolve(jobId.toString());
  }

  public Path input(UUID jobId) {
    return jobDirectory(jobId).resolve("input.csv");
  }

  public Path output(UUID jobId) {
    return jobDirectory(jobId).resolve("output.csv");
  }

  public List<Path> incomingFiles() throws IOException {
    return children(incoming());
  }

  public List<Path> jobDirectories() throws IOException {
    return children(jobs());
  }

  public Path writeInput(UUID jobId, byte[] content) throws IOException {
    Files.createDirectories(jobDirectory(jobId));
    return Files.write(input(jobId), content);
  }

  public Path writeOutput(UUID jobId, byte[] content) throws IOException {
    Files.createDirectories(jobDirectory(jobId));
    return Files.write(output(jobId), content);
  }

  public void clear() throws IOException {
    for (var child : incomingFiles()) {
      FileOperations.deleteRecursivelyQuietly(child);
    }
    for (var child : jobDirectories()) {
      FileOperations.deleteRecursivelyQuietly(child);
    }
  }

  @Override
  public void destroy() {
    FileOperations.deleteRecursivelyQuietly(root);
  }

  private static List<Path> children(Path directory) throws IOException {
    try (var paths = Files.list(directory)) {
      return paths.toList();
    }
  }
}
