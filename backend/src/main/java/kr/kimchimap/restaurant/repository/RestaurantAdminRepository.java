package kr.kimchimap.restaurant.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kr.kimchimap.restaurant.dto.RestaurantAdministration.Snapshot;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantAdminRepository {
  private static final UUID SOURCE = UUID.fromString("e9c56d7a-d847-4ef4-a925-c3c95da2a3e3");

  public record Row(
      UUID id,
      long version,
      String name,
      String address,
      String businessStatus,
      Double latitude,
      Double longitude,
      String phone,
      boolean published) {
    public Snapshot snapshot() {
      return new Snapshot(name, address, businessStatus, latitude, longitude, phone, published);
    }
  }

  public record AuditRow(
      UUID id,
      UUID actorId,
      long previousVersion,
      String beforeSnapshot,
      String afterSnapshot,
      String reason,
      Instant createdAt) {}

  private final JdbcClient jdbc;

  public RestaurantAdminRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Row> find(UUID id, boolean lock) {
    return jdbc.sql(
            "SELECT id,version,name,address,business_status,public.ST_Y(location) latitude,public.ST_X(location) longitude,phone_display phone,published FROM app.restaurant WHERE id=:id"
                + (lock ? " FOR UPDATE" : ""))
        .param("id", id)
        .query(Row.class)
        .optional();
  }

  public List<AuditRow> audits(UUID id) {
    return jdbc.sql(
            "SELECT id,actor_id,previous_version,before_snapshot::text,after_snapshot::text,reason,created_at FROM app.restaurant_correction WHERE restaurant_id=:id ORDER BY created_at DESC,id LIMIT 100")
        .param("id", id)
        .query(AuditRow.class)
        .list();
  }

  public void update(
      UUID id,
      Snapshot snapshot,
      BusinessPhone phone,
      boolean phoneChanged,
      boolean locationChanged) {
    jdbc.sql(
            """
        UPDATE app.restaurant SET name=:name,address=:address,business_status=:status,
            location=CASE WHEN :lat::double precision IS NULL THEN NULL ELSE public.ST_SetSRID(public.ST_MakePoint(:lng,:lat),4326) END,
            coordinate_status=CASE WHEN NOT :locationChanged THEN coordinate_status WHEN :lat::double precision IS NULL THEN 'MISSING' ELSE 'VERIFIED' END,
            phone_display=CASE WHEN :phoneChanged THEN :phone ELSE phone_display END,
            phone_number=CASE WHEN :phoneChanged THEN :number ELSE phone_number END,
            phone_source_id=CASE WHEN :phoneChanged THEN :source::uuid ELSE phone_source_id END,
            published=:published,version=version+1 WHERE id=:id
        """)
        .param("id", id)
        .param("name", snapshot.name())
        .param("address", snapshot.address())
        .param("status", snapshot.businessStatus())
        .param("lat", snapshot.latitude())
        .param("lng", snapshot.longitude())
        .param("phone", phone == null ? null : phone.display())
        .param("number", phone == null ? null : phone.number())
        .param("source", phone == null ? null : SOURCE)
        .param("phoneChanged", phoneChanged)
        .param("locationChanged", locationChanged)
        .param("published", snapshot.published())
        .update();
  }

  public void protect(UUID id, Set<String> fields, UUID actor, String reason) {
    for (String field : fields)
      jdbc.sql(
              """
          INSERT INTO app.restaurant_manual_override(restaurant_id,field_name,reason,actor_reference)
          VALUES (:id,:field,:reason,:actor) ON CONFLICT(restaurant_id,field_name)
          DO UPDATE SET reason=EXCLUDED.reason,actor_reference=EXCLUDED.actor_reference,created_at=CURRENT_TIMESTAMP,active=true
          """)
          .param("id", id)
          .param("field", field)
          .param("reason", reason)
          .param("actor", actor.toString())
          .update();
  }

  public void audit(UUID id, UUID actor, long version, String before, String after, String reason) {
    jdbc.sql(
            "INSERT INTO app.restaurant_correction(id,restaurant_id,actor_id,previous_version,before_snapshot,after_snapshot,reason) VALUES (:audit,:id,:actor,:version,:before::jsonb,:after::jsonb,:reason)")
        .param("audit", UUID.randomUUID())
        .param("id", id)
        .param("actor", actor)
        .param("version", version)
        .param("before", before)
        .param("after", after)
        .param("reason", reason)
        .update();
  }
}
