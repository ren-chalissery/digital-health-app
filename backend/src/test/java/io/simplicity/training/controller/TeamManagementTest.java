package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import io.simplicity.training.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class TeamManagementTest extends AbstractIntegrationTest {

  private static final String ADMIN_SUB = "sub-team-admin";
  private static final String MEMBER_SUB = "sub-team-member";

  private Organisation org;
  private AppUser admin;
  private AppUser member;

  @BeforeEach
  void seed() {
    org =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Team Hospital")
                .slug("team-hospital")
                .organisationType(OrganisationType.HOSPITAL)
                .build());
    admin = user("team-admin@example.org", ADMIN_SUB);
    member = user("team-member@example.org", MEMBER_SUB);
    orgMemberships.saveAndFlush(OrgMembership.of(admin.getId(), org.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(OrgMembership.of(member.getId(), org.getId(), OrgRole.ORG_MEMBER));
  }

  @Test
  void createsReadsRenamesAndDeletesATeam() throws Exception {
    String id =
        json(
            mockMvc
                .perform(
                    post(teamsUrl())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ward 3\",\"description\":\"Acute inpatient\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ward 3"))
                .andExpect(jsonPath("$.memberCount").value(0)));

    mockMvc
        .perform(get(teamsUrl() + "/" + id).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Acute inpatient"));

    mockMvc
        .perform(
            patch(teamsUrl() + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ward 4\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ward 4"));

    mockMvc
        .perform(delete(teamsUrl() + "/" + id).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(teamsUrl() + "/" + id).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void refusesASecondTeamWithTheSameName() throws Exception {
    createTeam("Crisis Team");

    mockMvc
        .perform(
            post(teamsUrl())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"crisis team\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void addsAndRemovesAMember() throws Exception {
    Team team = createTeam("Ward 3");

    mockMvc
        .perform(
            post(teamsUrl() + "/" + team.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + member.getId() + "\",\"teamRole\":\"TEAM_MEMBER\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("team-member@example.org"));

    mockMvc
        .perform(
            get(teamsUrl() + "/" + team.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            delete(teamsUrl() + "/" + team.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    assertThat(teamMembers.find(team.getId(), member.getId())).isEmpty();
  }

  @Test
  void refusesToAddTheSamePersonTwice() throws Exception {
    Team team = createTeam("Ward 3");
    addMember(team, member, TeamRole.TEAM_MEMBER);

    mockMvc
        .perform(
            post(teamsUrl() + "/" + team.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + member.getId() + "\",\"teamRole\":\"TEAM_ADMIN\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void refusesToAddSomebodyWhoIsNotInTheOrganisation() throws Exception {
    Team team = createTeam("Ward 3");
    AppUser stranger = user("stranger@example.org", "sub-stranger");

    mockMvc
        .perform(
            post(teamsUrl() + "/" + team.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + stranger.getId() + "\",\"teamRole\":\"TEAM_MEMBER\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void endsTeamMembershipWhenSomebodyLeavesTheOrganisation() throws Exception {
    Team team = createTeam("Ward 3");
    addMember(team, member, TeamRole.TEAM_MEMBER);

    mockMvc
        .perform(
            delete("/api/v1/orgs/" + org.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    assertThat(teamMembers.find(team.getId(), member.getId()))
        .as("keeping a team role after leaving the organisation would be a lingering grant")
        .isEmpty();
  }

  @Test
  void refusesToRemoveTheLastAdministrator() throws Exception {
    // Promote the second member so the admin has somebody to be removed by.
    orgMemberships.saveAndFlush(OrgMembership.of(member.getId(), org.getId(), OrgRole.ORG_ADMIN));

    mockMvc
        .perform(
            delete("/api/v1/orgs/" + org.getId() + "/members/" + admin.getId())
                .header(HttpHeaders.AUTHORIZATION, memberBearer()))
        .andExpect(status().isNoContent());

    // Now only one administrator is left. The last-administrator guard answers before the
    // "use the leave endpoint for yourself" check, because it is the more important refusal.
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + org.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, memberBearer()))
        .andExpect(status().isConflict());
  }

  @Test
  void refusesToDemoteTheLastAdministrator() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/orgs/" + org.getId() + "/members/" + admin.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRole\":\"ORG_MEMBER\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void promotesAMemberToAdministrator() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/orgs/" + org.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRole\":\"ORG_ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgRole").value("ORG_ADMIN"));

    // The newly promoted administrator can immediately act, which only works if their cached
    // principal was evicted.
    mockMvc
        .perform(
            post(teamsUrl())
                .header(HttpHeaders.AUTHORIZATION, memberBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Newly Permitted\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void recordsAnAuditEventForEveryMutation() throws Exception {
    Team team = createTeam("Audited Team");
    addMember(team, member, TeamRole.TEAM_MEMBER);

    mockMvc
        .perform(
            patch(teamsUrl() + "/" + team.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Audited Team Renamed\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete(teamsUrl() + "/" + team.getId() + "/members/" + member.getId())
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete(teamsUrl() + "/" + team.getId()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNoContent());

    assertThat(auditEvents.findByOrgIdOrderByCreatedAtDesc(org.getId()))
        .extracting(event -> event.getAction())
        .containsExactlyInAnyOrder(
            "TEAM_CREATED",
            "TEAM_MEMBER_ADDED",
            "TEAM_UPDATED",
            "TEAM_MEMBER_REMOVED",
            "TEAM_DELETED");
  }

  @Test
  void namesTheActorAndTargetInTheAuditTrail() throws Exception {
    Team team = createTeam("Traceable");

    var event =
        auditEvents.findByOrgIdOrderByCreatedAtDesc(org.getId()).stream()
            .filter(e -> e.getAction().equals("TEAM_CREATED"))
            .findFirst()
            .orElseThrow();

    assertThat(event.getActorUserId()).isEqualTo(admin.getId());
    assertThat(event.getTargetType()).isEqualTo("team");
    assertThat(event.getTargetId()).isEqualTo(team.getId().toString());
    assertThat(event.getCreatedAt()).isNotNull();
  }

  @Test
  void reportsHowManyPeopleAreInEachTeam() throws Exception {
    Team team = createTeam("Counted");
    addMember(team, member, TeamRole.TEAM_MEMBER);

    mockMvc
        .perform(get(teamsUrl()).header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].memberCount").value(1));
  }

  @Test
  void returnsNotFoundForATeamThatNeverExisted() throws Exception {
    mockMvc
        .perform(
            get(teamsUrl() + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, adminBearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void refusesATeamWithNoName() throws Exception {
    mockMvc
        .perform(
            post(teamsUrl())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists());
  }

  private String teamsUrl() {
    return "/api/v1/orgs/" + org.getId() + "/teams";
  }

  private String adminBearer() {
    return tokens.bearerFor(ADMIN_SUB);
  }

  private String memberBearer() {
    return tokens.bearerFor(MEMBER_SUB);
  }

  /** Goes through the API rather than the repository, so audit events are produced as in real use. */
  private Team createTeam(String name) throws Exception {
    mockMvc
        .perform(
            post(teamsUrl())
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isCreated());
    return teams.findByOrgIdOrderByNameAsc(org.getId()).stream()
        .filter(team -> team.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private void addMember(Team team, AppUser user, TeamRole role) throws Exception {
    mockMvc
        .perform(
            post(teamsUrl() + "/" + team.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.getId() + "\",\"teamRole\":\"" + role + "\"}"))
        .andExpect(status().isCreated());
  }

  private AppUser user(String email, String sub) {
    return users.saveAndFlush(AppUser.builder().email(email).cognitoSub(sub).build());
  }

  private String json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
    String body = actions.andReturn().getResponse().getContentAsString();
    return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
  }
}
