package pl.oliwier.adscsv.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import pl.oliwier.adscsv.config.properties.StorageProperties;
import pl.oliwier.adscsv.shared.FileOperations;

@Component
public class JobFileStorage {

  private static final String INPUT_FILE = "input.csv";
  private static final String OUTPUT_FILE = "output.csv";
  private static final String PART_SUFFIX = ".part";

  private final Path root;
  private final Path incoming;
  private final Path jobs;
  private final FileAttribute<?>[] fileAttributes;
  private final FileAttribute<?>[] directoryAttributes;

  public JobFileStorage(StorageProperties props) throws IOException {
    this.root = props.root();
    this.incoming = root.resolve("incoming");
    this.jobs = root.resolve("jobs");
    var posix = root.getFileSystem().supportedFileAttributeViews().contains("posix");
    this.fileAttributes = posix ? ownerOnly("rw-------") : new FileAttribute<?>[0];
    this.directoryAttributes = posix ? ownerOnly("rwx------") : new FileAttribute<?>[0];
    writableDirectory(incoming);
    writableDirectory(jobs);
  }

  public OutputStream createIncoming(UUID jobId) throws IOException {
    return newOutputStream(incomingPart(jobId), StandardOpenOption.CREATE_NEW);
  }

  public void commitInput(UUID jobId) throws IOException {
    var part = incomingPart(jobId);
    FileOperations.fsync(part);
    Files.createDirectories(jobDirectory(jobId), directoryAttributes);
    FileOperations.atomicMove(part, input(jobId));
  }

  public void deleteIncoming(UUID jobId) {
    FileOperations.deleteQuietly(incomingPart(jobId));
  }

  public void clearIncoming() throws IOException {
    try (var files = Files.list(incoming)) {
      files.forEach(FileOperations::deleteQuietly);
    }
  }

  public InputStream openInput(UUID jobId) throws IOException {
    return Files.newInputStream(input(jobId));
  }

  public OutputStream createOutput(UUID jobId) throws IOException {
    return newOutputStream(
        output(jobId), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  public void commitOutput(UUID jobId) throws IOException {
    FileOperations.fsync(output(jobId));
  }

  public Optional<StoredFile> findOutput(UUID jobId) throws IOException {
    var path = output(jobId);
    try {
      var attributes = Files.readAttributes(path, BasicFileAttributes.class);
      return attributes.isRegularFile()
          ? Optional.of(new StoredFile(new FileSystemResource(path), attributes.size()))
          : Optional.empty();
    } catch (NoSuchFileException e) {
      return Optional.empty();
    }
  }

  public void deleteInput(UUID jobId) {
    FileOperations.deleteQuietly(input(jobId));
  }

  public void deleteJob(UUID jobId) {
    FileOperations.deleteRecursivelyQuietly(jobDirectory(jobId));
  }

  public List<UUID> jobIds() throws IOException {
    try (var directories = Files.list(jobs)) {
      return directories
          .map(directory -> parseJobId(directory.getFileName().toString()))
          .flatMap(Optional::stream)
          .toList();
    }
  }

  public long usableSpace() throws IOException {
    return Files.getFileStore(root).getUsableSpace();
  }

  private Path incomingPart(UUID jobId) {
    return incoming.resolve(jobId + PART_SUFFIX);
  }

  private Path jobDirectory(UUID jobId) {
    return jobs.resolve(jobId.toString());
  }

  private Path input(UUID jobId) {
    return jobDirectory(jobId).resolve(INPUT_FILE);
  }

  private Path output(UUID jobId) {
    return jobDirectory(jobId).resolve(OUTPUT_FILE);
  }

  private OutputStream newOutputStream(Path file, StandardOpenOption... options)
      throws IOException {
    var openOptions = EnumSet.of(StandardOpenOption.WRITE, options);
    return Channels.newOutputStream(Files.newByteChannel(file, openOptions, fileAttributes));
  }

  private void writableDirectory(Path directory) throws IOException {
    Files.createDirectories(directory, directoryAttributes);
    if (!Files.isWritable(directory)) {
      throw new IOException("Storage directory is not writable: " + directory);
    }
  }

  private static Optional<UUID> parseJobId(String name) {
    try {
      var id = UUID.fromString(name);
      return id.toString().equals(name) ? Optional.of(id) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static FileAttribute<?>[] ownerOnly(String permissions) {
    return new FileAttribute<?>[] {
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions))
    };
  }
}
