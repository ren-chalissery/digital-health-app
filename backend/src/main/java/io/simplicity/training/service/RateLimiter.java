package io.simplicity.training.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * A fixed-window counter in Redis, used to stop a compromised administrator account from being
 * turned into a bulk mailer.
 *
 * <p>If Redis is unreachable the request is allowed. Losing the cache should degrade throttling,
 * not lock every administrator out of inviting colleagues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

  private final StringRedisTemplate redis;

  public boolean tryAcquire(String scope, String key, int limit, Duration window) {
    String redisKey = "ratelimit:" + scope + ":" + key;
    try {
      Long count = redis.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redis.expire(redisKey, window);
      }
      return count == null || count <= limit;
    } catch (DataAccessException e) {
      log.warn("Rate limiting unavailable for {}, allowing the request", redisKey, e);
      return true;
    }
  }
}
