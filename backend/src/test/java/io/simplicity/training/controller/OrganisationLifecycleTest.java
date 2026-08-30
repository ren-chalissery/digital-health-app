package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Switching between organisations, leaving one, and archiving one.
 *
 * <p>Weighted towards what happens after the pleasant path: an archived organisation has to become
 * unreachable for everybody who was in it, not merely disappear from a list, and it has to do so at
 * once rather than whenever a cached principal happens to expire.
 */
class OrganisationLifecycleTest extends AbstractIntegrationTest {

  private static final String ADMIN = "sub-admin";
  private static final String MEMBER = "sub-member";

  @Test
  void archivingHidesTheOrganisationFromItsAdministrator() throws Exception {
    Organisation org = anOrganisation("archive-me");
    admin(org);

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}", org.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, adminBearerAfterWithdrawal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations").isEmpty());
  }

  @Test
  void anArchivedOrganisationIsUnreachableByAnybodyWhoStillKnowsItsId() throws Exception {
    Organisation org = anOrganisation("gone");
    admin(org);
    AppUser member = member(org);

    // The other member is signed in and warm before the archive happens, so their principal is
    // cached with a role in this organisation.
    mockMvc
        .perform(get("/api/v1/orgs/{orgId}/members", org.getId()).header(HttpHeaders.AUTHORIZATION, memberBearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}", org.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    // A cached principal must not outlive the archive that invalidated it. Presented with a
    // refreshed token, so this proves the membership is gone rather than that the old token was.
    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/members", org.getId())
                .header(HttpHeaders.AUTHORIZATION, memberBearerAfterWithdrawal()))
        .andExpect(status().isForbidden());

    assertThat(member).isNotNull();
  }

  @Test
  void theLastAdministratorMayLeaveAndTakesTheOrganisationWithThem() throws Exception {
    Organisation org = anOrganisation("solo");
    admin(org);

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}/members/me", org.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    assertThat(organisations.findById(org.getId()).orElseThrow().isArchived())
        .as("leaving as the last administrator archives rather than orphaning")
        .isTrue();
  }

  @Test
  void anOrdinaryMemberMayLeaveWithoutDisturbingTheOrganisation() throws Exception {
    Organisation org = anOrganisation("stays");
    admin(org);
    member(org);

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}/members/me", org.getId()).header(HttpHeaders.AUTHORIZATION, memberBearer()))
        .andExpect(status().isNoContent());

    assertThat(organisations.findById(org.getId()).orElseThrow().isArchived()).isFalse();
    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, memberBearerAfterWithdrawal()))
        .andExpect(jsonPath("$.organisations").isEmpty());
  }

  @Test
  void removingTheLastAdministratorIsStillRefused() throws Exception {
    Organisation org = anOrganisation("protected");
    AppUser owner = admin(org);

    // Leaving is deliberate; being removed is usually a mistake, so the two answers differ.
    mockMvc
        .perform(
            delete("/api/v1/orgs/{orgId}/members/{userId}", org.getId(), owner.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void switchingChoosesAmongTheCallersOwnOrganisations() throws Exception {
    Organisation first = anOrganisation("first");
    Organisation second = anOrganisation("second");
    AppUser user = admin(first);
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), second.getId(), OrgRole.ORG_MEMBER));

    mockMvc
        .perform(
            put("/api/v1/me/active-organisation")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organisationId\":\"" + second.getId() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeOrganisationId").value(second.getId().toString()));
  }

  @Test
  void switchingToSomebodyElsesOrganisationIsRefused() throws Exception {
    Organisation mine = anOrganisation("mine");
    Organisation theirs = anOrganisation("theirs");
    admin(mine);

    mockMvc
        .perform(
            put("/api/v1/me/active-organisation")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organisationId\":\"" + theirs.getId() + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anActivePointerAtAnArchivedOrganisationFallsBackInsteadOfStranding() throws Exception {
    Organisation archived = anOrganisation("archived");
    Organisation live = anOrganisation("live");
    AppUser user = admin(archived);
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), live.getId(), OrgRole.ORG_MEMBER));

    mockMvc
        .perform(
            put("/api/v1/me/active-organisation")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organisationId\":\"" + archived.getId() + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}", archived.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, adminBearerAfterWithdrawal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeOrganisationId").value(live.getId().toString()));
  }

  @Test
  void archivingIsRecordedAgainstTheAdministratorWhoDidIt() throws Exception {
    Organisation org = anOrganisation("audited");
    AppUser owner = admin(org);

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}", org.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    assertThat(auditEvents.findAll())
        .anySatisfy(
            event -> {
              assertThat(event.getAction()).isEqualTo("ORGANISATION_ARCHIVED");
              assertThat(event.getActorUserId()).isEqualTo(owner.getId());
              assertThat(event.getOrgId()).isEqualTo(org.getId());
            });
    assertThat(organisations.findById(org.getId()).orElseThrow().getArchivedBy())
        .isEqualTo(owner.getId());
  }

  @Test
  void anOrdinaryMemberCannotArchive() throws Exception {
    Organisation org = anOrganisation("not-yours");
    admin(org);
    member(org);

    mockMvc
        .perform(delete("/api/v1/orgs/{orgId}", org.getId()).header(HttpHeaders.AUTHORIZATION, memberBearer()))
        .andExpect(status().isForbidden());
  }

  private String adminBearer() {
    return tokens.bearerFor(ADMIN, "admin@example.org");
  }

  private String memberBearer() {
    return tokens.bearerFor(MEMBER, "member@example.org");
  }

  /**
   * What a client holds once it has refreshed after having its access withdrawn.
   *
   * <p>Leaving, being removed and archiving now revoke the tokens issued before them, so a check
   * made afterwards has to present a newer one — exactly as the real clients do on a 401.
   */
  private String adminBearerAfterWithdrawal() {
    return tokens.bearerIssuedAfterNow(ADMIN);
  }

  private String memberBearerAfterWithdrawal() {
    return tokens.bearerIssuedAfterNow(MEMBER);
  }

  private AppUser admin(Organisation org) {
    AppUser user = userFor(ADMIN, "admin@example.org");
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_ADMIN));
    return user;
  }

  private AppUser member(Organisation org) {
    AppUser user = userFor(MEMBER, "member@example.org");
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_MEMBER));
    return user;
  }

  private AppUser userFor(String sub, String email) {
    return users
        .findByCognitoSub(sub)
        .orElseGet(
            () ->
                users.saveAndFlush(
                    AppUser.builder()
                        .email(email)
                        .cognitoSub(sub)
                        .fullName(email)
                        .profileCompleted(true)
                        .build()));
  }

  private Organisation anOrganisation(String slug) {
    return organisations.saveAndFlush(
        Organisation.builder()
            .name(slug)
            .slug(slug + "-" + UUID.randomUUID().toString().substring(0, 6))
            .organisationType(OrganisationType.HOSPITAL)
            .build());
  }
}
