package kr.kimchimap.certification.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.certification.dto.DesignationAdministration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DesignationRepository {
  public record Row(
      UUID id,
      UUID restaurantId,
      UUID sourceId,
      String externalId,
      String schemeName,
      String applicableItems,
      String criteriaOriginal,
      LocalDate designatedOn,
      LocalDate expiresOn,
      LocalDate cancelledOn,
      boolean publiclyVisible,
      long version,
      Instant reviewedAt) {
    public DesignationAdministration.Input input() {
      return new DesignationAdministration.Input(
          restaurantId,
          sourceId,
          externalId,
          schemeName,
          applicableItems,
          criteriaOriginal,
          designatedOn,
          expiresOn,
          cancelledOn,
          publiclyVisible);
    }
  }

  private final JdbcClient jdbc;

  public DesignationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<DesignationAdministration.Source> sources() {
    return jdbc.sql(
            "SELECT id,name,official_url,terms_reference FROM app.data_source WHERE designation_allowed AND collection_allowed AND republication_allowed ORDER BY name,id LIMIT 100")
        .query(DesignationAdministration.Source.class)
        .list();
  }

  public boolean lockAllowedSource(UUID id) {
    return jdbc.sql(
            "SELECT id FROM app.data_source WHERE id=:id AND designation_allowed AND collection_allowed AND republication_allowed FOR SHARE")
        .param("id", id)
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  public boolean restaurantExists(UUID id) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE id=:id AND published)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  public Optional<Row> find(UUID id, boolean lock) {
    return jdbc.sql("SELECT * FROM app.designation WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
        .param("id", id)
        .query(Row.class)
        .optional();
  }

  public List<DesignationAdministration.Item> recent(UUID restaurant) {
    return jdbc.sql(
            """
      SELECT d.id,d.restaurant_id,r.name restaurant_name,d.scheme_name,d.version,d.publicly_visible,
        (s.designation_allowed AND s.republication_allowed) source_allowed
      FROM app.designation d JOIN app.restaurant r ON r.id=d.restaurant_id JOIN app.data_source s ON s.id=d.source_id
      WHERE (:restaurant::uuid IS NULL OR d.restaurant_id=:restaurant) ORDER BY d.reviewed_at DESC NULLS LAST,d.id LIMIT 50
      """)
        .param("restaurant", restaurant)
        .query(DesignationAdministration.Item.class)
        .list();
  }

  public void create(UUID id, DesignationAdministration.Input input) {
    bind(
            jdbc.sql(
                """
        INSERT INTO app.designation(id,restaurant_id,source_id,external_id,scheme_name,applicable_items,criteria_original,designated_on,expires_on,cancelled_on,publicly_visible,reviewed_at)
        VALUES (:id,:restaurant,:source,:external,:scheme,:items,:criteria,:designated,:expires,:cancelled,:visible,CURRENT_TIMESTAMP)
        """),
            id,
            input)
        .update();
  }

  public void update(UUID id, DesignationAdministration.Input input) {
    bind(
            jdbc.sql(
                "UPDATE app.designation SET scheme_name=:scheme,applicable_items=:items,criteria_original=:criteria,designated_on=:designated,expires_on=:expires,cancelled_on=:cancelled,publicly_visible=:visible,version=version+1,reviewed_at=CURRENT_TIMESTAMP WHERE id=:id"),
            id,
            input)
        .update();
  }

  private JdbcClient.StatementSpec bind(
      JdbcClient.StatementSpec statement, UUID id, DesignationAdministration.Input input) {
    return statement
        .param("id", id)
        .param("restaurant", input.restaurantId())
        .param("source", input.sourceId())
        .param("external", input.externalId())
        .param("scheme", input.schemeName())
        .param("items", input.applicableItems())
        .param("criteria", input.criteriaOriginal())
        .param("designated", input.designatedOn())
        .param("expires", input.expiresOn())
        .param("cancelled", input.cancelledOn())
        .param("visible", input.publiclyVisible());
  }

  public void revision(UUID id, long version, String snapshot, UUID actor, String reason) {
    jdbc.sql(
            "INSERT INTO app.designation_revision(id,designation_id,version,snapshot,actor_id,reason) VALUES (:id,:designation,:version,:snapshot::jsonb,:actor,:reason)")
        .param("id", UUID.randomUUID())
        .param("designation", id)
        .param("version", version)
        .param("snapshot", snapshot)
        .param("actor", actor)
        .param("reason", reason)
        .update();
  }

  public List<DesignationAdministration.Revision> history(UUID id) {
    return jdbc.sql(
            "SELECT version,reason,created_at FROM app.designation_revision WHERE designation_id=:id ORDER BY version DESC LIMIT 100")
        .param("id", id)
        .query(DesignationAdministration.Revision.class)
        .list();
  }
}
