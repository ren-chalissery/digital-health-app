package io.simplicity.training.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves a Cognito subject to an application principal, letting the caching decorator sit in
 * front of the database-backed implementation without either knowing about the other. Implemented
 * by {@code PrincipalService} and decorated by {@code CachingPrincipalService}.
 */
public interface PrincipalLookup {

  /**
   * @param verifiedEmail consulted only when the subject has no user row yet. Resolving it costs a
   *     round trip to Cognito, so it must stay unevaluated on the path every other request takes.
   */
  AppPrincipal resolve(String cognitoSub, Supplier<Optional<String>> verifiedEmail);
}
