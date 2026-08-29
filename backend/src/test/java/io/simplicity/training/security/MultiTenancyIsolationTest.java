package io.simplicity.training.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * The tenant boundary is the single most important property of this system: a clinician at one
 * hospital must never see another hospital's people or teams.
 *
 * <p>Every organisation-scoped endpoint is exercised from the wrong side of the boundary. The
 * responses must reveal nothing — not the data, and not whether the organisation exists.
 */
class MultiTenancyIsolationTest extends AbstractIntegrationTest {

  private static final String ADMIN_A_SUB = "sub-admin-a";
  private static final String ADMIN_B_SUB = "sub-admin-b";
  private static final String OUTSIDER_SUB = "sub-outsider";

  private Organisation orgA;
  private Organisation orgB;
  private AppUser adminA;
  private AppUser adminB;
  private AppUser memberB;
  private Team teamB;

  @BeforeEach
  void seedTwoOrganisations() {
    orgA = organisation("hospital-a");
    orgB = organisation("hospital-b");

    adminA = user("admin-a@example.org", ADMIN_A_SUB);
    adminB = user("admin-b@example.org", ADMIN_B_SUB);
    memberB = user("member-b@example.org", "sub-member-b");
    user("outsider@example.org", OUTSIDER_SUB);

    orgMemberships.saveAndFlush(OrgMembership.of(adminA.getId(), orgA.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(OrgMembership.of(adminB.getId(), orgB.getId(), OrgRole.ORG_ADMIN));
    orgMemberships.saveAndFlush(OrgMembership.of(memberB.getId(), orgB.getId(), OrgRole.ORG_MEMBER));

    teamB = teams.saveAndFlush(Team.builder().orgId(orgB.getId()).name("Crisis Team").build());
    teamMembers.saveAndFlush(TeamMember.of(teamB.getId(), memberB.getId(), TeamRole.TEAM_MEMBER));
  }

  // --- reading another organisation ---

  @Test
  void anAdminOfOneOrganisationCannotReadAnother() throws Exception {
    mockMvc
        .perform(get("/api/v1/orgs/" + orgB.getId()).header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdminOfOneOrganisationCannotListAnothersMembers() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdminOfOneOrganisationCannotListAnothersTeams() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId() + "/teams")
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdminOfOneOrganisationCannotReadAnothersTeam() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdminOfOneOrganisationCannotListAnothersTeamMembers() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());
  }

  // --- writing to another organisation ---

  @Test
  void anAdminOfOneOrganisationCannotCreateATeamInAnother() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/" + orgB.getId() + "/teams")
                .header(HttpHeaders.AUTHORIZATION, bearerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Injected Team\"}"))
        .andExpect(status().isForbidden());

    assertThat(teams.findByOrgIdOrderByNameAsc(orgB.getId())).hasSize(1);
  }

  @Test
  void anAdminOfOneOrganisationCannotRenameAnothersTeam() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isForbidden());

    assertThat(teams.findById(teamB.getId()).orElseThrow().getName()).isEqualTo("Crisis Team");
  }

  @Test
  void anAdminOfOneOrganisationCannotDeleteAnothersTeam() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());

    assertThat(teams.findById(teamB.getId())).isPresent();
  }

  @Test
  void anAdminOfOneOrganisationCannotRemoveAnothersMember() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + orgB.getId() + "/members/" + memberB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden());

    assertThat(orgMemberships.find(memberB.getId(), orgB.getId())).isPresent();
  }

  @Test
  void anAdminOfOneOrganisationCannotPromoteThemselvesInAnother() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/orgs/" + orgB.getId() + "/members/" + adminA.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRole\":\"ORG_ADMIN\"}"))
        .andExpect(status().isForbidden());

    assertThat(orgMemberships.find(adminA.getId(), orgB.getId())).isEmpty();
  }

  // --- addressing a resource through the wrong organisation's path ---

  @Test
  void cannotReachATeamThroughAnOrganisationThatDoesNotOwnIt() throws Exception {
    // The caller legitimately administers org A, but team B is not org A's to see. This is the
    // case that a naive "is the caller an admin of the org in the path" check would allow.
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgA.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isNotFound());
  }

  @Test
  void cannotDeleteATeamThroughAnOrganisationThatDoesNotOwnIt() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + orgA.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isNotFound());

    assertThat(teams.findById(teamB.getId())).isPresent();
  }

  @Test
  void cannotAddSomebodyFromAnotherOrganisationToATeam() throws Exception {
    Team teamA = teams.saveAndFlush(Team.builder().orgId(orgA.getId()).name("Ward 1").build());

    mockMvc
        .perform(
            post("/api/v1/orgs/" + orgA.getId() + "/teams/" + teamA.getId() + "/members")
                .header(HttpHeaders.AUTHORIZATION, bearerA())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"" + memberB.getId() + "\",\"teamRole\":\"TEAM_MEMBER\"}"))
        .andExpect(status().isBadRequest());

    assertThat(teamMembers.find(teamA.getId(), memberB.getId())).isEmpty();
  }

  // --- role boundaries within one organisation ---

  @Test
  void anOrdinaryMemberCannotCreateATeam() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/" + orgB.getId() + "/teams")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor("sub-member-b"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Member Team\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTeamAdminCanManageTheirTeamButCannotDeleteIt() throws Exception {
    teamMembers.deleteAll(teamMembers.findByTeamId(teamB.getId()));
    teamMembers.saveAndFlush(TeamMember.of(teamB.getId(), memberB.getId(), TeamRole.TEAM_ADMIN));
    String teamAdmin = tokens.bearerFor("sub-member-b");

    mockMvc
        .perform(
            patch("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, teamAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Crisis Response\"}"))
        .andExpect(status().isOk());

    // Deleting a team is an organisation-level act, because other teams' training depends on it.
    mockMvc
        .perform(
            delete("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, teamAdmin))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTeamAdminInOneOrganisationCannotUseThatRoleInAnother() throws Exception {
    Team teamA = teams.saveAndFlush(Team.builder().orgId(orgA.getId()).name("Ward 1").build());
    // The user administers a team in org B and is also a plain member of org A.
    orgMemberships.saveAndFlush(OrgMembership.of(memberB.getId(), orgA.getId(), OrgRole.ORG_MEMBER));
    teamMembers.deleteAll(teamMembers.findByTeamId(teamB.getId()));
    teamMembers.saveAndFlush(TeamMember.of(teamB.getId(), memberB.getId(), TeamRole.TEAM_ADMIN));

    mockMvc
        .perform(
            patch("/api/v1/orgs/" + orgA.getId() + "/teams/" + teamA.getId())
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerFor("sub-member-b"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void somebodyWithNoMembershipAtAllSeesNothing() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId()).header(HttpHeaders.AUTHORIZATION, bearerOutsider()))
        .andExpect(status().isForbidden());
  }

  // --- the response must not leak existence ---

  @Test
  void answersIdenticallyForARealOrganisationAndAnImaginaryOne() throws Exception {
    String realOrgBody =
        mockMvc
            .perform(
                get("/api/v1/orgs/" + orgB.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerOutsider()))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String imaginaryOrgBody =
        mockMvc
            .perform(
                get("/api/v1/orgs/" + UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, bearerOutsider()))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(normalise(realOrgBody))
        .as("a different answer would let anyone discover which organisation ids exist")
        .isEqualTo(normalise(imaginaryOrgBody));
  }

  @Test
  void returnsProblemJsonWithNoInternalDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/orgs/" + orgB.getId()).header(HttpHeaders.AUTHORIZATION, bearerA()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"));
  }

  // --- the happy path still works, so the tests above are not passing vacuously ---

  @Test
  void anAdminCanDoAllOfThatWithinTheirOwnOrganisation() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orgs/" + orgB.getId()).header(HttpHeaders.AUTHORIZATION, bearerB()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("hospital-b"));

    mockMvc
        .perform(
            post("/api/v1/orgs/" + orgB.getId() + "/teams")
                .header(HttpHeaders.AUTHORIZATION, bearerB())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Team\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/v1/orgs/" + orgB.getId() + "/teams/" + teamB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerB()))
        .andExpect(status().isNoContent());
  }

  /**
   * Blanks the two fields that legitimately differ between any two requests: the moment it
   * happened, and the path the caller themselves asked for. Everything else must match exactly.
   */
  private String normalise(String body) {
    return body.replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"?\"")
        .replaceAll("\"instance\":\"[^\"]+\"", "\"instance\":\"?\"");
  }

  private String bearerA() {
    return tokens.bearerFor(ADMIN_A_SUB);
  }

  private String bearerB() {
    return tokens.bearerFor(ADMIN_B_SUB);
  }

  private String bearerOutsider() {
    return tokens.bearerFor(OUTSIDER_SUB);
  }

  private Organisation organisation(String slug) {
    return organisations.saveAndFlush(
        Organisation.builder()
            .name(slug)
            .slug(slug)
            .organisationType(OrganisationType.HOSPITAL)
            .build());
  }

  private AppUser user(String email, String sub) {
    return users.saveAndFlush(AppUser.builder().email(email).cognitoSub(sub).build());
  }
}
