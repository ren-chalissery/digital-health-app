package io.simplicity.training.security;

import io.simplicity.training.config.AppProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TokenRevocationService {

  private static final String KEY_PREFIX = "denylist:";

  private final StringRedisTemplate redis;
  private final AppProperties properties;

  /** Epoch seconds, so the value is legible in {@code redis-cli} and needs no serialisation. */
  public void revoke(String cognitoSub) {
    if (cognitoSub == null) {
      return;
    }
    redis
        .opsForValue()
        .set(
            key(cognitoSub),
            Long.toString(Instant.now().getEpochSecond()),
            properties.auth().accessTokenTtl());
  }

  public void restore(String cognitoSub) {
    if (cognitoSub != null) {
      redis.delete(key(cognitoSub));
    }
  }

  /**
   * True when this token predates the revocation.
   *
   * <p>An instant rather than a flag, and the difference matters. A flag rejects every token from
   * the subject, including one issued <em>after</em> the revocation — so removing somebody from
   * one of the two organisations they belong to would lock them out of both, and out of signing
   * back in, until the entry expired. Comparing against {@code iat} means the old token dies and
   * the next one works.
   *
   * <p>A token issued in the <em>same second</em> as the revocation counts as revoked. Both are
   * whole seconds, so their order within that second is unknowable, and the safe reading is that
   * the token came first. The cost is one needlessly refused token per revocation at worst; the
   * alternative is a token that outlives the revocation that was meant to kill it.
   *
   * @param issuedAt the token's {@code iat}; a token that cannot be dated fails closed
   */
  public boolean isRevoked(String cognitoSub, Instant issuedAt) {
    Instant revokedAt = revokedAt(cognitoSub);
    if (revokedAt == null) {
      return false;
    }
    if (issuedAt == null) {
      return true;
    }
    // Both sides truncated, so the comparison does not depend on the caller's precision. A JWT
    // `iat` is whole seconds; an Instant handed in by a test or a future caller may not be.
    return !issuedAt.truncatedTo(ChronoUnit.SECONDS).isAfter(revokedAt);
  }

  private Instant revokedAt(String cognitoSub) {
    if (cognitoSub == null) {
      return null;
    }
    String stored = redis.opsForValue().get(key(cognitoSub));
    if (stored == null) {
      return null;
    }
    try {
      return Instant.ofEpochSecond(Long.parseLong(stored));
    } catch (NumberFormatException e) {
      // An entry written by an older build held "1". Treat anything unparseable as "revoked now",
      // which errs towards refusing a token rather than honouring one.
      log.warn("Unparseable revocation entry for {}, treating as revoked", cognitoSub);
      return Instant.now();
    }
  }

  private String key(String cognitoSub) {
    return KEY_PREFIX + cognitoSub;
  }
}
