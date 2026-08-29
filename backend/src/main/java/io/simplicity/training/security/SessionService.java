package io.simplicity.training.security;

import io.simplicity.training.repository.AppUserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single place that knows what has to happen to a live session when a user's access changes.
 *
 * <p>Two mechanisms are needed and it is easy to remember only one, so callers get a facade rather
 * than the cache and the denylist separately.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

  private final CachingPrincipalService principalCache;
  private final TokenRevocationService revocations;
  private final AppUserRepository users;

  /**
   * Their roles or memberships changed but they are still welcome. Dropping the cached principal
   * is enough; their token stays valid.
   */
  public void rolesChanged(UUID userId) {
    principalCache.evictUser(userId);
  }

  /**
   * Their access has been withdrawn. As well as dropping the cache, the subject is denylisted so
   * the access token they are already holding stops working immediately rather than at expiry.
   */
  public void accessRevoked(UUID userId) {
    users
        .findById(userId)
        .ifPresent(
            user -> {
              principalCache.evict(user.getCognitoSub());
              revocations.revoke(user.getCognitoSub());
            });
  }

  /** Undoes {@link #accessRevoked}, for example when a suspended member is reinstated. */
  public void accessRestored(UUID userId) {
    users
        .findById(userId)
        .ifPresent(
            user -> {
              revocations.restore(user.getCognitoSub());
              principalCache.evict(user.getCognitoSub());
            });
  }
}
