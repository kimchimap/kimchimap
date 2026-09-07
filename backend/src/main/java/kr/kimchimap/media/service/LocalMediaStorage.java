package kr.kimchimap.media.service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalMediaStorage implements MediaStorage {
  private final Path root;

  public LocalMediaStorage(@Value("${app.media.root:.local/media}") String directory)
      throws IOException {
    Path configured = Path.of(directory).toAbsolutePath().normalize();
    if (Files.isSymbolicLink(configured)) throw new IOException("이미지 저장소는 심볼릭 링크일 수 없습니다.");
    Files.createDirectories(configured);
    root = configured.toRealPath();
    if (Files.getFileStore(root).supportsFileAttributeView("posix"))
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
  }

  @Override
  public void write(UUID key, byte[] original, byte[] sanitized) throws IOException {
    writeFile(path(key, true), original);
    try {
      writeFile(path(key, false), sanitized);
    } catch (IOException | RuntimeException exception) {
      Files.deleteIfExists(path(key, true));
      throw exception;
    }
  }

  private void writeFile(Path path, byte[] bytes) throws IOException {
    var output =
        Files.newOutputStream(
            path,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
    try (output) {
      if (Files.getFileStore(root).supportsFileAttributeView("posix"))
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
      output.write(bytes);
    } catch (IOException | RuntimeException exception) {
      Files.deleteIfExists(path);
      throw exception;
    }
  }

  @Override
  public byte[] read(UUID key, boolean original) throws IOException {
    Path path = path(key, original);
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new NoSuchFileException("이미지 파일을 찾을 수 없습니다.");
    try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
      byte[] bytes = input.readNBytes(20 * 1024 * 1024 + 1);
      if (bytes.length > 20 * 1024 * 1024) throw new IOException("이미지 저장 크기가 제한을 초과했습니다.");
      return bytes;
    }
  }

  @Override
  public void delete(UUID key) throws IOException {
    Files.deleteIfExists(path(key, true));
    Files.deleteIfExists(path(key, false));
  }

  @Override
  public List<UUID> olderThan(Instant cutoff, int limit) throws IOException {
    try (var files = Files.list(root)) {
      return files
          .filter(
              path ->
                  path.getFileName()
                      .toString()
                      .matches(
                          "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(original|clean)"))
          .filter(
              path -> {
                try {
                  return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                      && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
                          .toInstant()
                          .isBefore(cutoff);
                } catch (IOException exception) {
                  return false;
                }
              })
          .map(path -> UUID.fromString(path.getFileName().toString().substring(0, 36)))
          .distinct()
          .limit(limit)
          .toList();
    }
  }

  private Path path(UUID key, boolean original) {
    return root.resolve(key + (original ? ".original" : ".clean"));
  }
}
