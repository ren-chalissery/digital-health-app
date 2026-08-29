package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.support.AbstractIntegrationTest;
import io.simplicity.training.support.TestJwtConfiguration.FakeCognitoUserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Guards the boundary between a Cognito access token and an application user.
 *
 * <p>These exist because the obvious implementation is wrong in a way no other test would catch: an
 * access token from a pool that identifies people by email carries neither an {@code email} claim
 * nor an address in {@code username}, so anything reading the address from the token stores the
 * subject UUID instead and quietly breaks invitation acceptance.
 */
class CognitoProvisioningTest extends AbstractIntegrationTest {

  private static final String SUB = "8f1c0a6e-0f4a-4a2e-9a2f-2b6f0e5d7c31";

  @Autowired private FakeCognitoUserDirectory cognito;

  @Test
  void storesTheAddressCognitoReportsRatherThanAnythingOnTheToken() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(SUB, "Ada@Example.Org")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ada@example.org"));

    assertThat(users.findByCognitoSub(SUB).orElseThrow().getEmail())
        .as("the username claim holds the subject UUID, and must never reach the email column")
        .isEqualTo("ada@example.org");
  }

  @Test
  void asksCognitoOnceAndThenNeverAgain() throws Exception {
    String bearer = tokens.bearerFor(SUB, "ada@example.org");

    for (int i = 0; i < 4; i++) {
      mockMvc
          .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
          .andExpect(status().isOk());
    }

    assertThat(cognito.lookups())
        .as("resolving the address is a network call, so it belongs on the provisioning path only")
        .isEqualTo(1);
  }

  @Test
  void refusesToProvisionSomebodyCognitoWillNotVouchFor() {
    // Cognito returns nothing for an address it has not verified, which is what forgetting models.
    String bearer = tokens.bearerFor(SUB, "unverified@example.org");
    cognito.forget(SUB);

    assertThatThrownBy(
            () -> mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no verified address");

    assertThat(users.findByCognitoSub(SUB))
        .as("no address means no account, rather than an account keyed on something unusable")
        .isEmpty();
  }
}
