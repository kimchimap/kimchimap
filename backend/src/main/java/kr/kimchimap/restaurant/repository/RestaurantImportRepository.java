package kr.kimchimap.restaurant.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.kimchimap.restaurant.dto.ImportedRestaurant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantImportRepository {
  public record Existing(UUID id, String contentHash, Instant sourceUpdatedAt) {}

  private final JdbcClient jdbc;

  public RestaurantImportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Existing find(UUID source, String externalId) {
    return jdbc.sql(
            """
        SELECT x.restaurant_id AS id, r.content_hash, r.source_updated_at
        FROM app.restaurant_external_id x LEFT JOIN app.restaurant_source_record r USING(source_id, external_id)
        WHERE x.source_id = :source AND x.external_id = :external FOR UPDATE OF x
        """)
        .param("source", source)
        .param("external", externalId)
        .query(Existing.class)
        .optional()
        .orElse(null);
  }

  public List<UUID> possibleMatches(ImportedRestaurant row) {
    return jdbc.sql(
            """
        SELECT id FROM app.restaurant WHERE name = :name AND address = :address ORDER BY id LIMIT 6
        """)
        .param("name", row.name())
        .param("address", row.address())
        .query(UUID.class)
        .list();
  }

  public void create(UUID id, UUID source, ImportedRestaurant row) {
    var params = parameters(id, source, row);
    jdbc.sql(
            """
        INSERT INTO app.restaurant(id, name, address, original_address, business_status, coordinate_status, location,
            published, phone_display, phone_number, phone_source_id)
        VALUES (:id, :name, :address, :address, :state, :coordinateStatus,
            CASE WHEN CAST(:latitude AS double precision) IS NOT NULL THEN public.ST_SetSRID(public.ST_MakePoint(:longitude, :latitude),4326) ELSE NULL END,
            true, :phoneDisplay, :phoneNumber, :phoneSource)
        """)
        .params(params)
        .update();
    jdbc.sql(
            "INSERT INTO app.restaurant_external_id(source_id, external_id, restaurant_id, original_status) VALUES (:source, :external, :id, :originalStatus)")
        .params(params)
        .update();
  }

  public void update(UUID id, UUID source, ImportedRestaurant row) {
    var params = parameters(id, source, row);
    jdbc.sql(
            """
        WITH overrides AS (SELECT field_name FROM app.restaurant_manual_override WHERE restaurant_id = :id AND active)
        UPDATE app.restaurant SET
          name = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='name') THEN name ELSE :name END,
          address = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='address') THEN address ELSE :address END,
          business_status = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='business_status') THEN business_status ELSE :state END,
          location = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='location') THEN location
            WHEN CAST(:latitude AS double precision) IS NOT NULL THEN public.ST_SetSRID(public.ST_MakePoint(:longitude, :latitude),4326) ELSE NULL END,
          coordinate_status = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='location') THEN coordinate_status ELSE :coordinateStatus END,
          phone_number = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='phone') THEN phone_number ELSE :phoneNumber END,
          phone_display = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='phone') THEN phone_display ELSE :phoneDisplay END,
          phone_source_id = CASE WHEN EXISTS (SELECT 1 FROM overrides WHERE field_name='phone') THEN phone_source_id ELSE :phoneSource END,
          version = version + 1 WHERE id = :id
        """)
        .params(params)
        .update();
    jdbc.sql(
            "UPDATE app.restaurant_external_id SET original_status = :originalStatus WHERE source_id = :source AND external_id = :external")
        .params(params)
        .update();
  }

  public boolean hasOverride(UUID id) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM app.restaurant_manual_override WHERE restaurant_id = :id AND active)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  public void recordObservation(
      UUID source, ImportedRestaurant row, UUID job, Instant fetchedAt, boolean retainPrevious) {
    var params = parameters(null, source, row);
    params.put("job", job);
    params.put("fetched", fetchedAt.atOffset(ZoneOffset.UTC));
    params.put("retain", retainPrevious);
    params.put("hash", row.contentHash());
    params.put("sourceUpdated", timestamp(row.sourceUpdatedAt()));
    params.put("sourceModified", timestamp(row.sourceModifiedAt()));
    params.put("x", row.originalX());
    params.put("y", row.originalY());
    params.put("originalPhone", row.originalPhone());
    jdbc.sql(
            """
        INSERT INTO app.restaurant_source_record(source_id, external_id, content_hash, source_updated_at, source_modified_at,
          original_x, original_y, original_srid, original_phone, first_collected_at, last_fetch_succeeded_at, last_seen_job_id)
        VALUES (:source, :external, :hash, :sourceUpdated, :sourceModified, :x, :y, 5174, :originalPhone, :fetched, :fetched, :job)
        ON CONFLICT (source_id, external_id) DO UPDATE SET
          content_hash = CASE WHEN :retain THEN restaurant_source_record.content_hash ELSE EXCLUDED.content_hash END,
          source_updated_at = CASE WHEN :retain THEN restaurant_source_record.source_updated_at ELSE EXCLUDED.source_updated_at END,
          source_modified_at = CASE WHEN :retain THEN restaurant_source_record.source_modified_at ELSE EXCLUDED.source_modified_at END,
          original_x = CASE WHEN :retain THEN restaurant_source_record.original_x ELSE EXCLUDED.original_x END,
          original_y = CASE WHEN :retain THEN restaurant_source_record.original_y ELSE EXCLUDED.original_y END,
          original_phone = CASE WHEN :retain THEN restaurant_source_record.original_phone ELSE EXCLUDED.original_phone END,
          last_fetch_succeeded_at = EXCLUDED.last_fetch_succeeded_at, last_seen_job_id = EXCLUDED.last_seen_job_id,
          missing_since_job_id = NULL
        """)
        .params(params)
        .update();
  }

  private Map<String, Object> parameters(UUID id, UUID source, ImportedRestaurant row) {
    var p = new java.util.HashMap<String, Object>();
    p.put("id", id);
    p.put("source", source);
    p.put("external", row.externalId());
    p.put("name", row.name());
    p.put("address", row.address());
    p.put("state", row.businessStatus());
    p.put("originalStatus", row.originalStatus());
    p.put("coordinateStatus", row.coordinate().status().name());
    p.put("latitude", row.coordinate().latitude());
    p.put("longitude", row.coordinate().longitude());
    p.put("phoneDisplay", row.phone() == null ? null : row.phone().display());
    p.put("phoneNumber", row.phone() == null ? null : row.phone().number());
    p.put("phoneSource", row.phone() == null ? null : source);
    return p;
  }

  private Object timestamp(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
