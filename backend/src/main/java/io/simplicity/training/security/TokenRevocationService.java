package io.simplicity.training.security;

import io.simplicity.training.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Immediate session revocation.
 *
 * <p>Cognito access tokens are validated offline, so nothing this application does can shorten
 * their life. Denying a subject here closes the gap between removing somebody's access and their
 * current token expiring. Entries live exactly as long as an access token could, after which the
 * token is worthless anyway.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

  private static final String KEY_PREFIX = "denylist:";

  private final StringRedisTemplate redis;
  private final AppProperties properties;

  public void revoke(String cognitoSub) {
    if (cognitoSub == null) {
      return;
    }
    redis.opsForValue().set(key(cognitoSub), "1", properties.auth().accessTokenTtl());
  }

  public void restore(String cognitoSub) {
    if (cognitoSub != null) {
      redis.delete(key(cognitoSub));
    }
  }

  public boolean isRevoked(String cognitoSub) {
    return cognitoSub != null && Boolean.TRUE.equals(redis.hasKey(key(cognitoSub)));
  }

  private String key(String cognitoSub) {
    return KEY_PREFIX + cognitoSub;
  }
}
