package kr.kimchimap.report.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRateRepository {
  private final StringRedisTemplate redis;

  public ReportRateRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean allow(UUID member) {
    Long count =
        redis.execute(
            new DefaultRedisScript<>(
                "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],3600) end; return n",
                Long.class),
            List.of("km:report-limit:" + member));
    return count != null && count <= 60;
  }
}
