package io.simplicity.training.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.simplicity.training.config.AppProperties;
import io.simplicity.training.repository.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Caches the resolved principal so authorisation does not join across three tables on every
 * request.
 *
 * <p>The five-minute expiry is a backstop, not the correctness mechanism: every mutation that
 * changes a user's roles or memberships evicts their entry explicitly. Redis being unavailable
 * degrades performance but never correctness, because a failed read falls through to the database.
 */
@Service
@Primary
@Slf4j
public class CachingPrincipalService implements PrincipalLookup {

  private static final String KEY_PREFIX = "principal:";

  /**
   * Deliberately not the application's HTTP mapper. The cached representation is an internal
   * storage format, and tying it to the web layer's configuration would mean a change to, say, a
   * property naming strategy silently invalidating every entry in Redis.
   */
  private static final ObjectMapper CACHE_MAPPER =
      JsonMapper.builder()
          // Tolerate entries written by a previous version of the application. During a rolling
          // deploy both versions read the same Redis, and a removed field must not turn every
          // cached principal into a failed read.
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private final PrincipalService delegate;
  private final AppUserRepository users;
  private final StringRedisTemplate redis;
  private final AppProperties properties;

  public CachingPrincipalService(
      PrincipalService delegate,
      AppUserRepository users,
      StringRedisTemplate redis,
      AppProperties properties) {
    this.delegate = delegate;
    this.users = users;
    this.redis = redis;
    this.properties = properties;
  }

  @Override
  public AppPrincipal resolve(String cognitoSub, Supplier<Optional<String>> verifiedEmail) {
    AppPrincipal cached = read(cognitoSub);
    if (cached != null) {
      return cached;
    }
    AppPrincipal resolved = delegate.resolve(cognitoSub, verifiedEmail);
    write(cognitoSub, resolved);
    return resolved;
  }

  public void evict(String cognitoSub) {
    if (cognitoSub == null) {
      return;
    }
    try {
      redis.delete(key(cognitoSub));
    } catch (DataAccessException e) {
      log.warn("Could not evict the cached principal for {}", cognitoSub, e);
    }
  }

  /**
   * Mutations know the application user id, not the Cognito subject, so the subject is looked up
   * before evicting. A user who has been invited but never signed in has no subject and therefore
   * nothing cached.
   */
  public void evictUser(UUID userId) {
    users.findById(userId).map(user -> user.getCognitoSub()).ifPresent(this::evict);
  }

  private AppPrincipal read(String cognitoSub) {
    try {
      String json = redis.opsForValue().get(key(cognitoSub));
      return json == null ? null : CACHE_MAPPER.readValue(json, AppPrincipal.class);
    } catch (DataAccessException | JsonProcessingException e) {
      // A cache that cannot be read is a cache miss, never an error the caller sees.
      log.warn("Falling back to the database for principal {}", cognitoSub, e);
      return null;
    }
  }

  private void write(String cognitoSub, AppPrincipal principal) {
    try {
      redis
          .opsForValue()
          .set(
              key(cognitoSub),
              CACHE_MAPPER.writeValueAsString(principal),
              properties.auth().principalCacheTtl());
    } catch (DataAccessException | JsonProcessingException e) {
      log.warn("Could not cache the principal for {}", cognitoSub, e);
    }
  }

  private String key(String cognitoSub) {
    return KEY_PREFIX + cognitoSub;
  }
}
