package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Withdrawing somebody's access has to end it now, not whenever their token happens to expire.
 *
 * <p>Everything needed already existed — a Redis denylist, a filter that consults it, and {@code
 * SessionService.accessRevoked} — and nothing in production ever called it. A removed clinician
 * kept working access for up to fifteen minutes.
 */
class AccessWithdrawalTest extends AbstractIntegrationTest {

  private static final String ADMIN_SUB = "sub-withdrawal-admin";
  private static final String MEMBER_SUB = "sub-withdrawal-member";

  @Autowired private TokenRevocationService revocations;

  private Organisation organisation;
  private AppUser member;

  @BeforeEach
  void seedAnOrganisation() {
    organisation =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("withdrawal-clinic")
                .slug("withdrawal-clinic")
                .organisationType(OrganisationType.CLINIC)
                .build());

    AppUser admin =
        users.saveAndFlush(
            AppUser.builder().email("withdrawal-admin@example.org").cognitoSub(ADMIN_SUB).build());
    member =
        users.saveAndFlush(
            AppUser.builder()
                .email("withdrawal-member@example.org")
                .cognitoSub(MEMBER_SUB)
                .build());

    orgMemberships.saveAndFlush(
        OrgMembership.of(admin.getId(), organisation.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(
        OrgMembership.of(member.getId(), organisation.getId(), OrgRole.ORG_MEMBER));
  }

  @Test
  void refusesTheExistingTokenOfAMemberWhoWasRemoved() throws Exception {
    String bearer = tokens.bearerFor(MEMBER_SUB);
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());

    removeTheMember();

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnauthorized());
  }

  /**
   * The other half, and the reason revocation records an instant rather than banning a subject: a
   * clinician removed from one of the organisations they belong to must still be able to sign in
   * and work in the others.
   */
  @Test
  void acceptsAFreshTokenFromSomebodyWhoWasRemoved() throws Exception {
    removeTheMember();

    assertThat(revocations.isRevoked(MEMBER_SUB, Instant.now().plusSeconds(5))).isFalse();
  }

  @Test
  void refusesTheExistingTokenOfSomebodyWhoLeft() throws Exception {
    String bearer = tokens.bearerFor(MEMBER_SUB);

    mockMvc
        .perform(
            delete("/api/v1/orgs/" + organisation.getId() + "/members/me")
                .header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isUnauthorized());
  }

  /**
   * A role change is not a withdrawal. Demoting somebody should take effect promptly, which cache
   * eviction already achieves, but must not sign them out mid-sentence.
   */
  @Test
  void doesNotRevokeWhenOnlyARoleChanged() throws Exception {
    String bearer = tokens.bearerFor(MEMBER_SUB);

    mockMvc
        .perform(
            patch("/api/v1/orgs/" + organisation.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRole\":\"ORG_ADMIN\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
  }

  /**
   * The audit table has always had an {@code ip_address} column and never filled it, so it could
   * say who did what but never from where.
   */
  @Test
  void recordsWhereTheChangeCameFrom() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + organisation.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB))
                // As the load balancer presents it: the caller's claim first, the address the
                // proxy actually saw appended last.
                .header("X-Forwarded-For", "9.9.9.9, 203.0.113.5"))
        .andExpect(status().isNoContent());

    assertThat(auditEvents.findByOrgIdOrderByCreatedAtDesc(organisation.getId()))
        .isNotEmpty()
        .allSatisfy(event -> assertThat(event.getIpAddress()).isEqualTo("203.0.113.5"));
  }

  private void removeTheMember() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + organisation.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor(ADMIN_SUB)))
        .andExpect(status().isNoContent());
  }
}
