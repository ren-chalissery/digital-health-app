package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.simplicity.training.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenRevocationTest extends AbstractIntegrationTest {

  private static final String SUBJECT = "revocation-subject";

  @Autowired private TokenRevocationService revocations;

  @Test
  void rejectsATokenIssuedBeforeTheRevocation() {
    revocations.revoke(SUBJECT);

    assertThat(revocations.isRevoked(SUBJECT, Instant.now().minusSeconds(60))).isTrue();
  }

  /**
   * The reason revocation stores an instant rather than a flag.
   *
   * <p>A flag rejects every token from the subject, including one issued after the revocation. Wired
   * to member removal, that would lock a clinician who works across two clinics out of both — and
   * out of signing back in — until the entry expired.
   */
  @Test
  void acceptsATokenIssuedAfterTheRevocation() {
    revocations.revoke(SUBJECT);

    assertThat(revocations.isRevoked(SUBJECT, Instant.now().plusSeconds(5))).isFalse();
  }

  /**
   * Found by an existing test going green when it should have stayed red.
   *
   * <p>Both values are whole seconds, so a token minted in the same second as the revocation cannot
   * be ordered against it. Reading that as "issued after" left the token alive.
   */
  @Test
  void aTokenIssuedInTheSameSecondAsTheRevocationIsRefused() {
    Instant now = Instant.now();
    revocations.revoke(SUBJECT);

    assertThat(revocations.isRevoked(SUBJECT, now)).isTrue();
  }

  @Test
  void aSubjectThatWasNeverRevokedIsNotRevoked() {
    assertThat(revocations.isRevoked("never-revoked", Instant.now())).isFalse();
  }

  @Test
  void restoreClearsTheRevocation() {
    revocations.revoke(SUBJECT);

    revocations.restore(SUBJECT);

    assertThat(revocations.isRevoked(SUBJECT, Instant.now().minusSeconds(60))).isFalse();
  }

  /** Fail closed: a token we cannot date does not get the benefit of the doubt. */
  @Test
  void aTokenWithNoIssuedAtIsTreatedAsOlderThanTheRevocation() {
    revocations.revoke(SUBJECT);

    assertThat(revocations.isRevoked(SUBJECT, null)).isTrue();
  }

  @Test
  void aNullSubjectIsNeverRevoked() {
    assertThat(revocations.isRevoked(null, Instant.now().minusSeconds(60))).isFalse();
  }
}
