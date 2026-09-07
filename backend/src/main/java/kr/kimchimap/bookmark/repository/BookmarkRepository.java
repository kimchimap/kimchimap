package kr.kimchimap.bookmark.repository;

import java.util.List;
import java.util.UUID;
import kr.kimchimap.bookmark.dto.BookmarkPage.Item;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookmarkRepository {
  private final JdbcClient jdbc;

  public BookmarkRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean published(UUID restaurant) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE id=:id AND published)")
        .param("id", restaurant)
        .query(Boolean.class)
        .single();
  }

  public void add(UUID member, UUID restaurant) {
    jdbc.sql(
            "INSERT INTO app.bookmark(member_id, restaurant_id) VALUES (:member, :restaurant) ON CONFLICT DO NOTHING")
        .param("member", member)
        .param("restaurant", restaurant)
        .update();
  }

  public void remove(UUID member, UUID restaurant) {
    jdbc.sql("DELETE FROM app.bookmark WHERE member_id=:member AND restaurant_id=:restaurant")
        .param("member", member)
        .param("restaurant", restaurant)
        .update();
  }

  public boolean cursorExists(UUID member, UUID cursor) {
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.bookmark WHERE member_id=:member AND restaurant_id=:cursor)")
        .param("member", member)
        .param("cursor", cursor)
        .query(Boolean.class)
        .single();
  }

  public List<Item> list(UUID member, UUID cursor, int limit) {
    return jdbc.sql(
            """
        SELECT r.id restaurant_id, r.name, r.address, r.business_status, b.created_at saved_at
        FROM app.bookmark b JOIN app.restaurant r ON r.id=b.restaurant_id
        WHERE b.member_id=:member AND r.published AND (:cursor::uuid IS NULL OR
          (b.created_at < (SELECT created_at FROM app.bookmark WHERE member_id=:member AND restaurant_id=:cursor)
           OR (b.created_at = (SELECT created_at FROM app.bookmark WHERE member_id=:member AND restaurant_id=:cursor)
               AND b.restaurant_id > :cursor)))
        ORDER BY b.created_at DESC, b.restaurant_id LIMIT :limit
        """)
        .param("member", member)
        .param("cursor", cursor)
        .param("limit", limit)
        .query(Item.class)
        .list();
  }
}
