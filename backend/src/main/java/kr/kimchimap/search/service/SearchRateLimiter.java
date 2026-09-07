package kr.kimchimap.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SearchRateLimiter {
  private static final int LIMIT = 60;
  private static final int MAX_FALLBACK_CLIENTS = 10000;
  private static final DefaultRedisScript<Long> COUNT =
      new DefaultRedisScript<>(
          """
      local count = redis.call('INCR', KEYS[1])
      if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
      return count
      """,
          Long.class);

  private record Window(long expiresAt, int count) {}

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final AtomicLong nextRedisAttempt = new AtomicLong();
  private final Map<String, Window> fallback = new HashMap<>();

  public SearchRateLimiter(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  public boolean allow(String remoteAddress) {
    String key = fingerprint(remoteAddress);
    long now = clock.millis();
    if (now >= nextRedisAttempt.get()) {
      try {
        Long count = redis.execute(COUNT, List.of("rate:public-search:" + key));
        if (count != null) return count <= LIMIT;
      } catch (org.springframework.data.redis.RedisConnectionFailureException
          | org.springframework.dao.QueryTimeoutException exception) {
        nextRedisAttempt.set(now + 5000);
      }
    }
    return allowLocally(key, now);
  }

  private synchronized boolean allowLocally(String key, long now) {
    fallback.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    var current = fallback.get(key);
    if (current == null) {
      if (fallback.size() >= MAX_FALLBACK_CLIENTS) return false;
      fallback.put(key, new Window(now + 60000, 1));
      return true;
    }
    if (current.count() >= LIMIT) return false;
    fallback.put(key, new Window(current.expiresAt(), current.count() + 1));
    return true;
  }

  private String fingerprint(String address) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(address.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
