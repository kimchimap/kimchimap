package kr.kimchimap.search.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.kimchimap.search.dto.SearchRequest;
import kr.kimchimap.search.dto.SearchResponse.MatchedScope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MapSearchRepository {
  public record Row(
      UUID id,
      String name,
      String address,
      double latitude,
      double longitude,
      String businessStatus,
      double distanceMeters) {}

  public record MatchRow(UUID restaurantId, int groupIndex, UUID id, String name, String usage) {
    public MatchedScope toDto() {
      return new MatchedScope(groupIndex, id, name, usage);
    }
  }

  public record PageBoundary(double distanceMeters, UUID id) {}

  private final JdbcClient jdbc;

  public MapSearchRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  record SqlQuery(String sql, java.util.Map<String, Object> parameters) {}

  public List<Row> search(SearchRequest request, Instant asOf, PageBoundary boundary) {
    var query = searchQuery(request, asOf, boundary);
    return jdbc.sql(query.sql()).params(query.parameters()).query(Row.class).list();
  }

  SqlQuery searchQuery(SearchRequest request, Instant asOf, PageBoundary boundary) {
    var sql = new SearchSql(request, asOf);
    var common = sql.common("");
    String predicate = sql.groupsPredicate();
    if (boundary != null) {
      predicate += " AND (r.distance_meters, r.id) > (:lastDistance, :lastId)";
      sql.parameters().put("lastDistance", boundary.distanceMeters());
      sql.parameters().put("lastId", boundary.id());
    }
    sql.parameters().put("limit", request.limit() + 1);
    return new SqlQuery(
        common
            + "SELECT r.* FROM spatial r WHERE true "
            + predicate
            + " ORDER BY r.distance_meters, r.id LIMIT :limit",
        java.util.Map.copyOf(sql.parameters()));
  }

  public List<MatchRow> matchedScopes(SearchRequest request, Instant asOf, List<UUID> ids) {
    if (request.groups().isEmpty() || ids.isEmpty()) return List.of();
    var sql = new SearchSql(request, asOf);
    sql.parameters().put("ids", ids);
    var common = sql.common("AND r.id IN (:ids)");
    var queries = new java.util.ArrayList<String>();
    for (int group = 0; group < request.groups().size(); group++) {
      queries.add(
          "SELECT ss.restaurant_id, "
              + group
              + " AS group_index, ss.id, ss.name, ss.usage FROM app.serving_scope ss"
              + " WHERE ss.restaurant_id IN (:ids) AND "
              + sql.scopePredicate(group));
    }
    return jdbc.sql(
            common
                + String.join(" UNION ALL ", queries)
                + " ORDER BY restaurant_id, group_index, id LIMIT 2001")
        .params(sql.parameters())
        .query(MatchRow.class)
        .list();
  }
}
