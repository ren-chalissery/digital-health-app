package io.simplicity.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.simplicity.training.service.RateLimiter.OnOutage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * What a rate limiter does when the thing it counts with is unavailable.
 *
 * <p>It used to allow everything, on the reasoning that losing the cache should not lock
 * administrators out. That is right for some features and wrong for others: the invitation limit
 * exists to stop the product being used as a mail relay, and a limiter that stops limiting under
 * load is no limit at all. Each caller now says which it wants.
 */
class RateLimiterTest {

  @Test
  void allowsUpToTheLimitAndRefusesTheNext() {
    RateLimiter limiter = new RateLimiter(workingRedis());

    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("scope", "key", 3, Duration.ofHours(1), OnOutage.REFUSE))
          .as("request %d of 3 should be allowed", i + 1)
          .isTrue();
    }

    assertThat(limiter.tryAcquire("scope", "key", 3, Duration.ofHours(1), OnOutage.REFUSE))
        .isFalse();
  }

  @Test
  void countsEachKeySeparately() {
    RateLimiter limiter = new RateLimiter(workingRedis());

    assertThat(limiter.tryAcquire("scope", "one", 1, Duration.ofHours(1), OnOutage.REFUSE)).isTrue();

    assertThat(limiter.tryAcquire("scope", "two", 1, Duration.ofHours(1), OnOutage.REFUSE))
        .as("a second user must not inherit the first one's count")
        .isTrue();
  }

  @Test
  void allowsTheRequestWhenRedisIsDownAndTheFeatureMattersMore() {
    RateLimiter limiter = new RateLimiter(brokenRedis());

    assertThat(limiter.tryAcquire("assistant", "user", 1, Duration.ofHours(1), OnOutage.ALLOW))
        .isTrue();
  }

  @Test
  void refusesTheRequestWhenRedisIsDownAndTheLimitMattersMore() {
    RateLimiter limiter = new RateLimiter(brokenRedis());

    assertThat(limiter.tryAcquire("invite", "org", 1, Duration.ofHours(1), OnOutage.REFUSE))
        .as("an unenforceable invitation limit is what turns the product into a mail relay")
        .isFalse();
  }

  private StringRedisTemplate workingRedis() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);

    java.util.Map<String, Long> counts = new java.util.HashMap<>();
    when(values.increment(anyString()))
        .thenAnswer(call -> counts.merge(call.getArgument(0), 1L, Long::sum));
    return redis;
  }

  private StringRedisTemplate brokenRedis() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(anyString())).thenThrow(new QueryTimeoutException("Redis is unreachable"));
    return redis;
  }
}
