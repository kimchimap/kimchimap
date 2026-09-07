package kr.kimchimap.restaurant.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CoordinateRepository {
  public record Point(double latitude, double longitude) {}

  private final JdbcClient jdbc;

  public CoordinateRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Point transform(double x, double y, int sourceSrid) {
    return jdbc.sql(
            """
        SELECT public.ST_Y(point) AS latitude, public.ST_X(point) AS longitude FROM
          (SELECT public.ST_Transform(public.ST_SetSRID(public.ST_MakePoint(:x, :y), :srid), 4326) AS point) converted
        """)
        .param("x", x)
        .param("y", y)
        .param("srid", sourceSrid)
        .query(Point.class)
        .single();
  }
}
