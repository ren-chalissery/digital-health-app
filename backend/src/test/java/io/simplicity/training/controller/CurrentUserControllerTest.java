package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.simplicity.training.model.entity.AppUser;
import io.simplicity.training.model.entity.OrgMembership;
import io.simplicity.training.model.entity.Organisation;
import io.simplicity.training.model.entity.Team;
import io.simplicity.training.model.entity.TeamMember;
import io.simplicity.training.model.enums.OrgRole;
import io.simplicity.training.model.enums.OrganisationType;
import io.simplicity.training.model.enums.TeamRole;
import io.simplicity.training.model.enums.UserStatus;
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class CurrentUserControllerTest extends AbstractIntegrationTest {

  @Test
  void rejectsARequestWithNoToken() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void provisionsTheUserOnTheirVeryFirstRequest() throws Exception {
    String token =
        tokens.accessTokenFor("cognito-sub-1", Map.of("email", "New.Clinician@Example.Org"));

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new.clinician@example.org"))
        .andExpect(jsonPath("$.profileCompleted").value(false))
        .andExpect(jsonPath("$.organisations").isEmpty());

    assertThat(users.findByCognitoSub("cognito-sub-1")).isPresent();
  }

  @Test
  void provisionsOnlyOnceAcrossRepeatedRequests() throws Exception {
    String token = tokens.accessTokenFor("cognito-sub-2", Map.of("email", "repeat@example.org"));

    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk());
    }

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void adoptsTheRowAnAdministratorCreatedWhenInvitingThisAddress() throws Exception {
    Organisation org = anOrganisation("invited-org");
    AppUser invited =
        users.saveAndFlush(
            AppUser.builder().email("invited@example.org").status(UserStatus.INVITED).build());
    orgMemberships.saveAndFlush(
        OrgMembership.of(invited.getId(), org.getId(), OrgRole.ORG_MEMBER));

    String token =
        tokens.accessTokenFor("cognito-sub-3", Map.of("email", "invited@example.org"));

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invited.getId().toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.organisations.length()").value(1));

    assertThat(users.count())
        .as("linking to the invited row must not create a second account for the same person")
        .isEqualTo(1);
  }

  @Test
  void reportsOrganisationsAndTeamsTheUserBelongsTo() throws Exception {
    Organisation org = anOrganisation("reporting-org");
    AppUser user =
        users.saveAndFlush(
            AppUser.builder().email("member@example.org").cognitoSub("cognito-sub-4").build());
    orgMemberships.saveAndFlush(OrgMembership.of(user.getId(), org.getId(), OrgRole.ORG_ADMIN));
    Team team = teams.saveAndFlush(Team.builder().orgId(org.getId()).name("Ward 3").build());
    teamMembers.saveAndFlush(TeamMember.of(team.getId(), user.getId(), TeamRole.TEAM_ADMIN));

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor("cognito-sub-4")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations[0].orgRole").value("ORG_ADMIN"))
        .andExpect(jsonPath("$.organisations[0].name").value("reporting-org"))
        .andExpect(jsonPath("$.organisations[0].teams[0].name").value("Ward 3"))
        .andExpect(jsonPath("$.organisations[0].teams[0].teamRole").value("TEAM_ADMIN"));
  }

  @Test
  void doesNotReportTeamsFromAnotherOrganisation() throws Exception {
    Organisation a = anOrganisation("org-a");
    Organisation b = anOrganisation("org-b");
    AppUser locum =
        users.saveAndFlush(
            AppUser.builder().email("locum@example.org").cognitoSub("cognito-sub-5").build());
    orgMemberships.saveAndFlush(OrgMembership.of(locum.getId(), a.getId(), OrgRole.ORG_MEMBER));
    orgMemberships.saveAndFlush(OrgMembership.of(locum.getId(), b.getId(), OrgRole.ORG_MEMBER));
    Team teamA = teams.saveAndFlush(Team.builder().orgId(a.getId()).name("Team A").build());
    Team teamB = teams.saveAndFlush(Team.builder().orgId(b.getId()).name("Team B").build());
    teamMembers.saveAndFlush(TeamMember.of(teamA.getId(), locum.getId(), TeamRole.TEAM_MEMBER));
    teamMembers.saveAndFlush(TeamMember.of(teamB.getId(), locum.getId(), TeamRole.TEAM_MEMBER));

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor("cognito-sub-5")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organisations.length()").value(2))
        .andExpect(jsonPath("$.organisations[0].teams.length()").value(1))
        .andExpect(jsonPath("$.organisations[1].teams.length()").value(1));
  }

  @Test
  void refusesADeactivatedUser() throws Exception {
    users.saveAndFlush(
        AppUser.builder()
            .email("gone@example.org")
            .cognitoSub("cognito-sub-6")
            .status(UserStatus.DEACTIVATED)
            .build());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tokens.bearerFor("cognito-sub-6")))
        .andExpect(status().isForbidden());
  }

  private Organisation anOrganisation(String slug) {
    return organisations.saveAndFlush(
        Organisation.builder()
            .name(slug)
            .slug(slug)
            .organisationType(OrganisationType.HOSPITAL)
            .build());
  }
}
