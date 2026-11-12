package kr.kimchimap.restaurant.repository;

import java.util.UUID;
import kr.kimchimap.restaurant.dto.ProposedRestaurant;
import kr.kimchimap.restaurant.entity.BusinessPhone;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantRegistrationRepository {
  private final JdbcClient jdbc;

  public RestaurantRegistrationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean duplicate(ProposedRestaurant input) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
        .param("key", "registration:" + input.name().strip() + ":" + input.address().strip())
        .query(Object.class)
        .single();
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app.restaurant WHERE name=:name AND address=:address)")
        .param("name", input.name().strip())
        .param("address", input.address().strip())
        .query(Boolean.class)
        .single();
  }

  public UUID create(ProposedRestaurant input, BusinessPhone phone) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
        INSERT INTO app.restaurant(id,name,address,business_status,coordinate_status,location,published,
          phone_display,phone_number,phone_source_id)
        VALUES (:id,:name,:address,'UNKNOWN','VERIFIED',
          public.ST_SetSRID(public.ST_MakePoint(:longitude,:latitude),4326),true,
          :display,:phone,:source)
        """)
        .param("id", id)
        .param("name", input.name().strip())
        .param("address", input.address().strip())
        .param("latitude", input.latitude())
        .param("longitude", input.longitude())
        .param("display", phone == null ? null : phone.display())
        .param("phone", phone == null ? null : phone.number())
        .param(
            "source",
            phone == null ? null : UUID.fromString("dea158a9-07a9-4db9-8531-6096193af1ac"))
        .update();
    return id;
  }
}
