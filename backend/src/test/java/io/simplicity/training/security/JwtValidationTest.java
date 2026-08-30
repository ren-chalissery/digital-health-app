package io.simplicity.training.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * What a bearer token has to be before it authorises anything.
 *
 * <p>The decoder was {@code NimbusJwtDecoder.withJwkSetUri(...).build()}, which checks the
 * signature and the expiry and nothing else. Cognito signs ID tokens with the same keys as access
 * tokens, so an ID token authorised requests.
 */
class JwtValidationTest extends AbstractIntegrationTest {

  private static final String SUB = "sub-jwt-validation";

  @BeforeEach
  void seedAUser() {
    users.saveAndFlush(
        AppUser.builder().email("jwt-validation@example.org").cognitoSub(SUB).build());
  }

  /**
   * The test that stops a well-meaning fix taking the iOS app offline.
   *
   * <p>{@code app.cognito.client-id} was populated from the <em>web</em> client alone. Validating
   * against a single value would have rejected every request from iOS and Android the moment it
   * deployed.
   */
  @Test
  void acceptsAnAccessTokenFromEachKnownClient() throws Exception {
    for (String clientId : new String[] {"test-client", "test-client-ios", "test-client-android"}) {
      mockMvc
          .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearerWith("client_id", clientId)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void refusesAnIdToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearerWith("token_use", "id")))
        .andExpect(status().isUnauthorized());
  }

  /** A missing claim is a different code path from a wrong one, and both must refuse. */
  @Test
  void refusesATokenWithNoTokenUseClaim() throws Exception {
    Map<String, Object> withoutTokenUse = new HashMap<>();
    // Nimbus removes a claim when its value is null.
    withoutTokenUse.put("token_use", null);

    mockMvc
        .perform(
            get("/api/v1/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokens.accessTokenFor(SUB, withoutTokenUse)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refusesAnUnknownClient() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearerWith("client_id", "somebody-elses-app")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refusesATokenFromAnotherIssuer() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearerWith("iss", "https://cognito-idp.test.amazonaws.com/somebody-elses-pool")))
        .andExpect(status().isUnauthorized());
  }

  private String bearerWith(String claim, String value) {
    return "Bearer " + tokens.accessTokenFor(SUB, Map.of(claim, value));
  }
}
