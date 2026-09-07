package kr.kimchimap.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.kimchimap.auth.dto.ActiveSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {
  private static final String SESSION = "km:{auth}:session:";
  private static final String TOKEN = "km:{auth}:token:";
  private static final String MEMBER = "km:{auth}:member:";
  private static final DefaultRedisScript<String> CREATE =
      script("session-create.lua", String.class);
  private static final DefaultRedisScript<String> ROTATE =
      script("session-rotate.lua", String.class);
  private static final DefaultRedisScript<Long> REVOKE_ALL =
      script("session-revoke-all.lua", Long.class);
  private final StringRedisTemplate redis;

  public SessionRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public String create(UUID id, UUID member, long version, String hash, Duration lifetime) {
    return redis.execute(
        CREATE,
        List.of(SESSION + id, MEMBER + member, TOKEN + hash),
        id.toString(),
        member.toString(),
        Long.toString(version),
        hash,
        Long.toString(lifetime.toMillis()),
        SESSION);
  }

  public Optional<UUID> lookup(String hash) {
    String id = redis.opsForValue().get(TOKEN + hash);
    return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
  }

  public String rotate(UUID id, String oldHash, String nextHash) {
    return redis.execute(
        ROTATE,
        List.of(SESSION + id, TOKEN + oldHash, TOKEN + nextHash),
        id.toString(),
        oldHash,
        nextHash);
  }

  public Optional<ActiveSession> active(UUID id) {
    var fields =
        redis.opsForHash().multiGet(SESSION + id, List.of("status", "member", "version", "expiry"));
    if (!"ACTIVE".equals(fields.get(0))) return Optional.empty();
    return Optional.of(
        new ActiveSession(
            id,
            UUID.fromString((String) fields.get(1)),
            Long.parseLong((String) fields.get(2)),
            Instant.ofEpochMilli(Long.parseLong((String) fields.get(3)))));
  }

  public void revoke(UUID id) {
    redis.execute(
        new DefaultRedisScript<>(
            "if redis.call('EXISTS',KEYS[1])==1 then redis.call('HSET',KEYS[1],'status','REVOKED') end return 1",
            Long.class),
        List.of(SESSION + id));
  }

  public void revokeAll(UUID member) {
    redis.execute(REVOKE_ALL, List.of(MEMBER + member), SESSION);
  }

  public boolean allowRefresh(String clientHash) {
    Long count =
        redis.execute(
            new DefaultRedisScript<>(
                "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end return n",
                Long.class),
            List.of("km:{auth}:refresh-rate:" + clientHash));
    return count != null && count <= 20;
  }

  private static <T> DefaultRedisScript<T> script(String name, Class<T> result) {
    var script = new DefaultRedisScript<T>();
    script.setLocation(new ClassPathResource("redis/" + name));
    script.setResultType(result);
    return script;
  }
}
