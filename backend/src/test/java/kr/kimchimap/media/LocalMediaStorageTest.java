package kr.kimchimap.media;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import kr.kimchimap.media.service.LocalMediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaStorageTest {
  @TempDir Path directory;

  @Test
  void writesPrivateFilesAndRejectsCollisionsWithoutDeletingPreviousData() throws Exception {
    var storage = new LocalMediaStorage(directory.resolve("media").toString());
    UUID key = UUID.randomUUID();
    storage.write(key, new byte[] {1, 2}, new byte[] {3, 4});
    assertThat(storage.read(key, true)).containsExactly(1, 2);
    assertThat(storage.read(key, false)).containsExactly(3, 4);
    assertThatThrownBy(() -> storage.write(key, new byte[] {5}, new byte[] {6}))
        .isInstanceOf(java.io.IOException.class);
    assertThat(storage.read(key, true)).containsExactly(1, 2);
    assertThat(storage.read(key, false)).containsExactly(3, 4);
    if (Files.getFileStore(directory).supportsFileAttributeView("posix"))
      assertThat(
              Files.getPosixFilePermissions(directory.resolve("media").resolve(key + ".original")))
          .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
  }

  @Test
  void rejectsSymlinkReadsAndStorageRoots() throws Exception {
    Path outside = directory.resolve("outside");
    Files.write(outside, new byte[] {9});
    Path root = directory.resolve("media");
    var storage = new LocalMediaStorage(root.toString());
    UUID key = UUID.randomUUID();
    Files.createSymbolicLink(root.resolve(key + ".clean"), outside);
    assertThatThrownBy(() -> storage.read(key, false)).isInstanceOf(java.io.IOException.class);
    Path link = directory.resolve("link");
    Files.createSymbolicLink(link, root);
    assertThatThrownBy(() -> new LocalMediaStorage(link.toString()))
        .isInstanceOf(java.io.IOException.class);
    assertThat(Files.readAllBytes(outside)).containsExactly(9);
  }

  @Test
  void listsOnlyOldRandomStorageKeys() throws Exception {
    Path root = directory.resolve("media");
    var storage = new LocalMediaStorage(root.toString());
    UUID old = UUID.randomUUID(), recent = UUID.randomUUID();
    storage.write(old, new byte[] {1}, new byte[] {2});
    storage.write(recent, new byte[] {1}, new byte[] {2});
    Files.setLastModifiedTime(root.resolve(old + ".original"), FileTime.from(Instant.EPOCH));
    Files.writeString(root.resolve("not-a-storage-key.original"), "test");
    assertThat(storage.olderThan(Instant.now().minusSeconds(60), 100)).containsExactly(old);
    storage.delete(old);
    assertThat(Files.exists(root.resolve(old + ".clean"))).isFalse();
  }
}
