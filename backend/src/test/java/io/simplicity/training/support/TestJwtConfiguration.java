package io.simplicity.training.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.simplicity.training.security.CognitoUserDirectory;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces the Cognito-backed decoder with one that trusts a locally generated key pair, so the
 * suite runs offline and fast.
 *
 * <p>The claim set deliberately mirrors what a real pool issues rather than what would be
 * convenient. In particular there is no {@code email} claim, and {@code username} holds the subject
 * UUID, because that is what Cognito emits for a pool with {@code UsernameAttributes: [email]}. A
 * token here that carried the address would let the suite pass while production failed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {

  public static final String ISSUER = "https://cognito-idp.test.amazonaws.com/test-pool";

  private static final RSAKey RSA_KEY = generateKey();

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-key").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not generate a test signing key", e);
    }
  }

  @Bean
  @Primary
  JwtDecoder testJwtDecoder() throws JOSEException {
    return NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
  }

  /**
   * Takes precedence over the real directory rather than replacing it conditionally, so the
   * production wiring is still exercised on every context load. A conditional here once let a
   * missing bean reach a container image, because the fake quietly filled the gap in tests.
   */
  @Bean
  @Primary
  FakeCognitoUserDirectory fakeCognitoUserDirectory() {
    return new FakeCognitoUserDirectory();
  }

  @Bean
  TestTokenFactory testTokenFactory(FakeCognitoUserDirectory directory) {
    return new TestTokenFactory(directory);
  }

  /**
   * Stands in for the {@code GetUser} call. Addresses are registered against a subject rather than
   * a token string, because a test may mint several tokens for the same person.
   */
  public static class FakeCognitoUserDirectory implements CognitoUserDirectory {

    private final Map<String, String> emailsBySub = new ConcurrentHashMap<>();
    private final AtomicInteger lookups = new AtomicInteger();

    public void register(String cognitoSub, String email) {
      emailsBySub.put(cognitoSub, email);
    }

    /** How often Cognito has been asked, so tests can prove the hot path does not ask at all. */
    public int lookups() {
      return lookups.get();
    }

    public void forget(String cognitoSub) {
      emailsBySub.remove(cognitoSub);
    }

    public void reset() {
      emailsBySub.clear();
      lookups.set(0);
    }

    @Override
    public Optional<String> verifiedEmail(String accessToken) {
      lookups.incrementAndGet();
      return Optional.ofNullable(emailsBySub.get(subjectOf(accessToken)));
    }

    private String subjectOf(String accessToken) {
      try {
        return SignedJWT.parse(accessToken).getJWTClaimsSet().getSubject();
      } catch (ParseException e) {
        throw new IllegalArgumentException("Not a readable access token", e);
      }
    }
  }

  /** Mints access tokens shaped like Cognito's for use in MockMvc requests. */
  public static class TestTokenFactory {

    private final FakeCognitoUserDirectory directory;

    TestTokenFactory(FakeCognitoUserDirectory directory) {
      this.directory = directory;
    }

    public String accessTokenFor(String cognitoSub) {
      return accessTokenFor(cognitoSub, Map.of());
    }

    public String accessTokenFor(String cognitoSub, Map<String, Object> extraClaims) {
      Instant now = Instant.now();
      JWTClaimsSet.Builder claims =
          new JWTClaimsSet.Builder()
              .subject(cognitoSub)
              .issuer(ISSUER)
              .jwtID(UUID.randomUUID().toString())
              .claim("token_use", "access")
              .claim("client_id", "test-client")
              // Cognito sets this to the immutable username, which for an email-identified pool is
              // the subject UUID rather than the address.
              .claim("username", cognitoSub)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(15, ChronoUnit.MINUTES)));
      extraClaims.forEach(claims::claim);

      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
              claims.build());
      try {
        jwt.sign(new RSASSASigner(RSA_KEY));
      } catch (JOSEException e) {
        throw new IllegalStateException("Could not sign the test token", e);
      }
      return jwt.serialize();
    }

    public String bearerFor(String cognitoSub) {
      return "Bearer " + accessTokenFor(cognitoSub);
    }

    /**
     * For a caller whose address the application will have to look up, which is every caller being
     * provisioned for the first time.
     */
    public String bearerFor(String cognitoSub, String email) {
      directory.register(cognitoSub, email);
      return "Bearer " + accessTokenFor(cognitoSub);
    }
  }
}
