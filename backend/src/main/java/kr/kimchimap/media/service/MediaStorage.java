package kr.kimchimap.media.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaStorage {
  void write(UUID key, byte[] original, byte[] sanitized) throws IOException;

  byte[] read(UUID key, boolean original) throws IOException;

  void delete(UUID key) throws IOException;

  List<UUID> olderThan(Instant cutoff, int limit) throws IOException;
}
