package io.simplicity.training.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Authoring a module and learning from it.
 *
 * <p>The interesting assertions are about who cannot see what. Assignment is by team, so being in
 * the organisation is not enough, and a draft is not content until somebody publishes it.
 */
class ModuleLifecycleTest extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String ADMIN = "sub-author";
  private static final String LEARNER = "sub-learner";
  private static final String OUTSIDER = "sub-outsider";

  private Organisation org;
  private Team team;
  private AppUser learner;

  @BeforeEach
  void setUpOrganisation() {
    org =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Riverside")
                .slug("riverside-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    orgMemberships.saveAndFlush(
        OrgMembership.of(user(ADMIN, "author@example.org").getId(), org.getId(), OrgRole.ORG_ADMIN));
    learner = user(LEARNER, "learner@example.org");
    orgMemberships.saveAndFlush(OrgMembership.of(learner.getId(), org.getId(), OrgRole.ORG_MEMBER));
    team = teams.saveAndFlush(Team.builder().orgId(org.getId()).name("Ward 3").build());
  }

  @Test
  void aPublishedModuleReachesTheTeamItIsAssignedTo() throws Exception {
    UUID moduleId = aModuleWithSections("Delivering Simplicity", "Overview", "In practice");
    joinTeam(learner);
    assign(moduleId, team.getId());
    publish(moduleId, false);

    mockMvc
        .perform(get("/api/v1/orgs/{orgId}/learning", org.getId()).header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Delivering Simplicity"))
        .andExpect(jsonPath("$[0].status").value("NOT_STARTED"))
        .andExpect(jsonPath("$[0].sectionCount").value(2));
  }

  @Test
  void aMemberOfNoAssignedTeamCannotReachItEvenKnowingTheId() throws Exception {
    UUID moduleId = aModuleWithSections("Private", "Only section");
    assign(moduleId, team.getId());
    publish(moduleId, false);
    // The learner is in the organisation but not in Ward 3.

    mockMvc
        .perform(get("/api/v1/orgs/{orgId}/learning", org.getId()).header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/learning/{moduleId}", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnpublishedDraftIsNotContent() throws Exception {
    UUID moduleId = aModuleWithSections("Work in progress", "Draft section");
    joinTeam(learner);
    assign(moduleId, team.getId());

    mockMvc
        .perform(get("/api/v1/orgs/{orgId}/learning", org.getId()).header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void finishingTheLastSectionCompletesTheModule() throws Exception {
    UUID moduleId = aModuleWithSections("Two parts", "First", "Second");
    joinTeam(learner);
    assign(moduleId, team.getId());
    publish(moduleId, false);

    JsonNode sections = learnerView(moduleId).get("sections");
    completeSection(sections.get(0).get("sectionId").asText());

    assertThat(learningStatus(moduleId)).isEqualTo("IN_PROGRESS");

    completeSection(sections.get(1).get("sectionId").asText());

    assertThat(learningStatus(moduleId))
        .as("the module completes with its last section, not on a separate call a client might skip")
        .isEqualTo("COMPLETED");
  }

  @Test
  void asubstantiveRevisionMakesACompletedModuleOutstandingAgain() throws Exception {
    UUID moduleId = aModuleWithSections("Protocol", "The protocol");
    joinTeam(learner);
    assign(moduleId, team.getId());
    publish(moduleId, false);
    completeEverySection(moduleId);
    assertThat(learningStatus(moduleId)).isEqualTo("COMPLETED");

    openDraft(moduleId);
    putSections(moduleId, "The revised protocol");
    publish(moduleId, true);

    assertThat(learningStatus(moduleId))
        .as("a rewritten protocol is not covered by having read the old one")
        .isEqualTo("NEEDS_REDOING");
    assertThat(moduleCompletions.count())
        .as("the earlier completion stays: it is a true statement about a version that existed")
        .isEqualTo(1);
  }

  @Test
  void aCorrectionLeavesCompletionsAlone() throws Exception {
    UUID moduleId = aModuleWithSections("Protocol", "The protocol");
    joinTeam(learner);
    assign(moduleId, team.getId());
    publish(moduleId, false);
    completeEverySection(moduleId);

    openDraft(moduleId);
    putSections(moduleId, "The protocol, with the typo fixed");
    publish(moduleId, false);

    assertThat(learningStatus(moduleId))
        .as("a moved comma must not send two hundred clinicians back through the training")
        .isEqualTo("COMPLETED");
  }

  @Test
  void onlyOneDraftMayExistAtATime() throws Exception {
    UUID moduleId = aModuleWithSections("Single", "Only");
    publish(moduleId, false);
    openDraft(moduleId);

    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isConflict());
  }

  @Test
  void anOrdinaryMemberCannotAuthor() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules", org.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Mine now\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anotherOrganisationSeesNothing() throws Exception {
    UUID moduleId = aModuleWithSections("Ours", "Section");
    publish(moduleId, false);

    Organisation other =
        organisations.saveAndFlush(
            Organisation.builder()
                .name("Other")
                .slug("other-" + UUID.randomUUID().toString().substring(0, 6))
                .organisationType(OrganisationType.CLINIC)
                .build());
    AppUser outsider = user(OUTSIDER, "outsider@example.org");
    orgMemberships.saveAndFlush(OrgMembership.of(outsider.getId(), other.getId(), OrgRole.ORG_ADMIN));

    mockMvc
        .perform(
            get("/api/v1/orgs/{orgId}/modules", org.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(OUTSIDER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void reorderingAndRenamingSectionsSurvivesAReplacement() throws Exception {
    UUID moduleId = aModuleWithSections("Ordering", "Alpha", "Beta", "Gamma");
    putSections(moduleId, "Gamma renamed", "Alpha");

    JsonNode module = authorView(moduleId);
    JsonNode sections = module.get("draft").get("sections");
    assertThat(sections.size()).isEqualTo(2);
    assertThat(sections.get(0).get("title").asText()).isEqualTo("Gamma renamed");
    assertThat(sections.get(1).get("title").asText()).isEqualTo("Alpha");
  }

  // -----------------------------------------------------------------------------------------

  private UUID aModuleWithSections(String title, String... sectionTitles) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/orgs/{orgId}/modules", org.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"" + title + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    UUID moduleId = UUID.fromString(body(created).get("moduleId").asText());
    putSections(moduleId, sectionTitles);
    return moduleId;
  }

  private void putSections(UUID moduleId, String... sectionTitles) throws Exception {
    StringBuilder json = new StringBuilder("{\"sections\":[");
    for (int i = 0; i < sectionTitles.length; i++) {
      json.append(i > 0 ? "," : "")
          .append("{\"title\":\"")
          .append(sectionTitles[i])
          .append("\",\"body\":\"Some **markdown**.\"}");
    }
    json.append("]}");

    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.toString()))
        .andExpect(status().isOk());
  }

  private void publish(UUID moduleId, boolean supersedes) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supersedesCompletions\":" + supersedes + "}"))
        .andExpect(status().isOk());
  }

  private void openDraft(UUID moduleId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orgs/{orgId}/modules/{moduleId}/draft", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
        .andExpect(status().isOk());
  }

  private void assign(UUID moduleId, UUID teamId) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/modules/{moduleId}/teams", org.getId(), moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamIds\":[\"" + teamId + "\"]}"))
        .andExpect(status().isOk());
  }

  private void completeSection(String sectionId) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete", org.getId(), sectionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
        .andExpect(status().isOk());
  }

  private void completeEverySection(UUID moduleId) throws Exception {
    for (JsonNode section : learnerView(moduleId).get("sections")) {
      completeSection(section.get("sectionId").asText());
    }
  }

  private String learningStatus(UUID moduleId) throws Exception {
    for (JsonNode module : body(
        mockMvc
            .perform(get("/api/v1/orgs/{orgId}/learning", org.getId()).header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
            .andReturn())) {
      if (module.get("moduleId").asText().equals(moduleId.toString())) {
        return module.get("status").asText();
      }
    }
    return "ABSENT";
  }

  private JsonNode learnerView(UUID moduleId) throws Exception {
    return body(
        mockMvc
            .perform(
                get("/api/v1/orgs/{orgId}/learning/{moduleId}", org.getId(), moduleId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(LEARNER)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode authorView(UUID moduleId) throws Exception {
    return body(
        mockMvc
            .perform(
                get("/api/v1/orgs/{orgId}/modules/{moduleId}", org.getId(), moduleId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode body(MvcResult result) throws Exception {
    return JSON.readTree(result.getResponse().getContentAsString());
  }

  private void joinTeam(AppUser user) {
    teamMembers.saveAndFlush(TeamMember.of(team.getId(), user.getId(), TeamRole.TEAM_MEMBER));
  }

  private String bearer(String sub) {
    return tokens.bearerFor(sub, sub + "@example.org");
  }

  private AppUser user(String sub, String email) {
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
}
