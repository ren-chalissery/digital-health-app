package io.simplicity.training.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Invitation tokens are bearer credentials: whoever holds one can join an organisation. They are
 * therefore generated from a cryptographic source and stored only as a SHA-256 digest, so a
 * database leak cannot be replayed into access.
 *
 * <p>Plain SHA-256 rather than a password hash is the right choice here. These are 256 bits of
 * randomness, not a memorised secret, so they are not brute-forceable and a slow hash would only
 * cost latency on a request that happens while somebody waits.
 */
public final class InvitationTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private InvitationTokens() {}

  public static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required but unavailable", e);
    }
  }
}
