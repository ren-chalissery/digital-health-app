package io.simplicity.training.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces the Cognito-backed decoder with one that trusts a locally generated key pair, so the
 * suite runs offline and fast. The claim shape mirrors a real Cognito access token.
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

  @Bean
  TestTokenFactory testTokenFactory() {
    return new TestTokenFactory();
  }

  /** Mints access tokens shaped like Cognito's for use in MockMvc requests. */
  public static class TestTokenFactory {

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
  }
}
