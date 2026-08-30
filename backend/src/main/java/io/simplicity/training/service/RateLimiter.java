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
 * <p>When Redis is unreachable the answer depends on the feature, so each caller states its own
 * posture rather than inheriting one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

  private final StringRedisTemplate redis;

  /** What to do when the counter cannot be reached. There is no default: see {@link #tryAcquire}. */
  public enum OnOutage {
    /** The feature is worth more than the limit. */
    ALLOW,
    /** The limit is worth more than the feature. */
    REFUSE
  }

  /**
   * @param onOutage what to do if Redis is unreachable. Stated by every caller rather than
   *     defaulted, because the right answer differs per feature and a default makes the wrong one
   *     invisible: allowing every question through is a kindness, allowing every invitation
   *     through is an open mail relay.
   */
  public boolean tryAcquire(
      String scope, String key, int limit, Duration window, OnOutage onOutage) {
    String redisKey = "ratelimit:" + scope + ":" + key;
    try {
      Long count = redis.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redis.expire(redisKey, window);
      }
      return count == null || count <= limit;
    } catch (DataAccessException e) {
      boolean allowed = onOutage == OnOutage.ALLOW;
      log.warn(
          "Rate limiting unavailable for {}, {} the request",
          redisKey,
          allowed ? "allowing" : "refusing",
          e);
      return allowed;
    }
  }
}
