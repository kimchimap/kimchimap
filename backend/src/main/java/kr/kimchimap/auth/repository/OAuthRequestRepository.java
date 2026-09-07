package kr.kimchimap.auth.repository;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthRequestRepository {
  private final StringRedisTemplate redis;

  public OAuthRequestRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void save(String key, String request) {
    redis.opsForValue().set("km:oauth:" + key, request, Duration.ofMinutes(10));
  }

  public String read(String key) {
    return redis.opsForValue().get("km:oauth:" + key);
  }

  public String consume(String key) {
    return redis.opsForValue().getAndDelete("km:oauth:" + key);
  }

  public boolean allowStart(String addressHash) {
    Long count =
        redis.execute(
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",
                Long.class),
            java.util.List.of("km:oauth-limit:" + addressHash));
    return count != null && count <= 20;
  }
}
