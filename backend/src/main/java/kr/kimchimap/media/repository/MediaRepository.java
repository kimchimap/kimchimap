package kr.kimchimap.media.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {
  public record Stored(
      UUID id,
      UUID ownerId,
      UUID storageKey,
      String contentType,
      long sanitizedSize,
      int width,
      int height,
      String state,
      boolean publiclyVisible,
      Instant createdAt) {}

  private final JdbcClient jdbc;

  public MediaRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      UUID id,
      UUID owner,
      UUID key,
      String type,
      long originalSize,
      long size,
      int width,
      int height,
      String hash) {
    jdbc.sql(
            """
        INSERT INTO app.media(id,owner_id,storage_key,content_type,original_size,sanitized_size,width,height,content_hash)
        VALUES (:id,:owner,:key,:type,:original,:size,:width,:height,:hash)
        """)
        .param("id", id)
        .param("owner", owner)
        .param("key", key)
        .param("type", type)
        .param("original", originalSize)
        .param("size", size)
        .param("width", width)
        .param("height", height)
        .param("hash", hash)
        .update();
  }

  public Optional<Stored> find(UUID id) {
    return jdbc.sql(
            "SELECT id,owner_id,storage_key,content_type,sanitized_size,width,height,state,publicly_visible,created_at FROM app.media WHERE id=:id")
        .param("id", id)
        .query(Stored.class)
        .optional();
  }

  public boolean storageReferenced(UUID key) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app.media WHERE storage_key=:key)")
        .param("key", key)
        .query(Boolean.class)
        .single();
  }

  public List<Stored> expiredTemporary() {
    return jdbc.sql(
            """
        SELECT id,owner_id,storage_key,content_type,sanitized_size,width,height,state,publicly_visible,created_at
        FROM app.media WHERE (state='TEMPORARY' AND created_at < CURRENT_TIMESTAMP - interval '24 hours') OR state='DELETING'
        ORDER BY created_at LIMIT 100
        """)
        .query(Stored.class)
        .list();
  }

  public boolean markDeleting(UUID id) {
    return jdbc.sql(
                "UPDATE app.media SET state='DELETING' WHERE id=:id AND (state='DELETING' OR (state='TEMPORARY' AND created_at < CURRENT_TIMESTAMP - interval '24 hours'))")
            .param("id", id)
            .update()
        == 1;
  }

  public void deleteMarked(UUID id) {
    jdbc.sql("DELETE FROM app.media WHERE id=:id AND state='DELETING'").param("id", id).update();
  }
}
