package pl.oliwier.adscsv.shared;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileOperations {

  private static final Logger log = LoggerFactory.getLogger(FileOperations.class);

  private FileOperations() {}

  public static void fsync(Path file) throws IOException {
    try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  public static void atomicMove(Path source, Path target) throws IOException {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
  }

  public static boolean deleteQuietly(Path path) {
    try {
      return Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not delete {}", path, e);
      return false;
    }
  }

  public static void deleteRecursivelyQuietly(Path root) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(FileOperations::deleteQuietly);
    } catch (IOException | UncheckedIOException e) {
      log.warn("Could not delete directory {}", root, e);
    }
  }
}
