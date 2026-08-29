package io.simplicity.training.security;

/**
 * Resolves a Cognito subject to an application principal. Implemented directly against the
 * database by {@link PrincipalService} and wrapped with a Redis cache by
 * {@code CachingPrincipalService}.
 */
public interface PrincipalLookup {

  AppPrincipal resolve(String cognitoSub, String email);
}
