package kr.kimchimap.origin.repository;

import java.util.List;
import kr.kimchimap.origin.dto.Catalogs.CountryItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CountryRepository {
  private final JdbcClient jdbc;

  public CountryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<CountryItem> findActive() {
    return jdbc.sql("SELECT code, name FROM app.country WHERE active ORDER BY code")
        .query(CountryItem.class)
        .list();
  }
}
