package io.simplicity.training.security;

import java.util.Optional;

/**
 * Reads the caller's verified email address from Cognito.
 *
 * <p>The address cannot come from the access token. Cognito omits {@code email} from access tokens
 * unless the pool has a version two pre-token-generation trigger, and because the pool identifies
 * users by email its {@code username} claim holds the subject UUID rather than the address. The
 * only claim that reliably identifies the caller is {@code sub}, so the address has to be fetched.
 */
public interface CognitoUserDirectory {

  /**
   * @param accessToken the caller's own token, which Cognito accepts as the credential for reading
   *     that caller's attributes
   * @return the address, or empty when Cognito has not verified one
   */
  Optional<String> verifiedEmail(String accessToken);
}
