package kr.kimchimap.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.kimchimap.search.service.SearchRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class SearchRateLimiterTest {
  @Test
  void redisOutageKeepsPublicSearchAvailableWithinBoundedLocalLimits() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.redis.core.script.RedisScript<Long>>any(),
            anyList()))
        .thenThrow(new RedisConnectionFailureException("테스트 장애"));
    var limiter =
        new SearchRateLimiter(
            redis, Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
    for (int request = 0; request < 60; request++) assertThat(limiter.allow("192.0.2.1")).isTrue();
    assertThat(limiter.allow("192.0.2.1")).isFalse();
    assertThat(limiter.allow("192.0.2.2")).isTrue();
  }
}
